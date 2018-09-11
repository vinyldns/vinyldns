/*
 * Copyright 2018 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vinyldns.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, Materializer}
import cats.effect.IO
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.dropwizard.DropwizardExports
import io.prometheus.client.hotspot.DefaultExports
import org.slf4j.LoggerFactory
import vinyldns.api.crypto.Crypto
import vinyldns.api.domain.AccessValidations
import vinyldns.api.domain.batch.{BatchChangeConverter, BatchChangeService, BatchChangeValidations}
import vinyldns.api.domain.membership._
import vinyldns.api.domain.record.RecordSetService
import vinyldns.api.domain.zone._
import vinyldns.api.engine.ProductionZoneCommandHandler
import vinyldns.api.engine.sqs.{SqsCommandBus, SqsConnection}
import vinyldns.dynamodb.repository._
import vinyldns.api.repository.TestDataLoader
import vinyldns.api.repository.mysql.MySqlDataStoreProvider
import vinyldns.api.route.{HealthService, VinylDNSService}
import vinyldns.core.VinylDNSMetrics
import vinyldns.core.domain.batch.BatchChangeRepository
import vinyldns.core.domain.zone.ZoneRepository
import vinyldns.core.repository.{DataStoreStartupError, RepositoryName}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.{Codec, Source}

object Boot extends App {

  private val logger = LoggerFactory.getLogger("Boot")
  private implicit val system: ActorSystem = VinylDNSConfig.system
  private implicit val materializer: Materializer = ActorMaterializer()
  private implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  def vinyldnsBanner(): IO[String] = IO {
    val stream = getClass.getResourceAsStream("/vinyldns-ascii.txt")
    val vinyldnsBannerText = "\n" + Source.fromInputStream(stream)(Codec.UTF8).mkString + "\n"
    stream.close()
    vinyldnsBannerText
  }

  /* Boot straps the entire application, if anything fails, we all fail! */
  def runApp(): IO[Future[Http.ServerBinding]] =
    // Use an effect type to lift anything that can fail into the effect type.  This ensures
    // that if anything fails, the app does not start!
    for {
      banner <- vinyldnsBanner()
      _ <- Crypto.loadCrypto(VinylDNSConfig.cryptoConfig) // load crypto
      // TODO datastore loading will not be hardcoded by type here
      mySqlDataStore <- new MySqlDataStoreProvider().load(VinylDNSConfig.mySqlConfig)
      zoneRepo <- IO.fromEither(
        mySqlDataStore
          .get[ZoneRepository](RepositoryName.zone)
          .toRight[Throwable](DataStoreStartupError("Missing zone repository")))
      batchChangeRepo <- IO.fromEither(
        mySqlDataStore
          .get[BatchChangeRepository](RepositoryName.batchChange)
          .toRight[Throwable](DataStoreStartupError("Missing zone repository")))
      // TODO this also will all be removed with dynamic loading
      userRepo <- DynamoDBUserRepository(
        VinylDNSConfig.usersStoreConfig,
        VinylDNSConfig.dynamoConfig)
      groupRepo <- DynamoDBGroupRepository(
        VinylDNSConfig.groupsStoreConfig,
        VinylDNSConfig.dynamoConfig)
      membershipRepo <- DynamoDBMembershipRepository(
        VinylDNSConfig.membershipStoreConfig,
        VinylDNSConfig.dynamoConfig)
      groupChangeRepo <- DynamoDBGroupChangeRepository(
        VinylDNSConfig.groupChangesStoreConfig,
        VinylDNSConfig.dynamoConfig)
      recordSetRepo <- DynamoDBRecordSetRepository(
        VinylDNSConfig.recordSetStoreConfig,
        VinylDNSConfig.dynamoConfig)
      recordChangeRepo <- DynamoDBRecordChangeRepository(
        VinylDNSConfig.recordChangeStoreConfig,
        VinylDNSConfig.dynamoConfig)
      zoneChangeRepo <- DynamoDBZoneChangeRepository(
        VinylDNSConfig.zoneChangeStoreConfig,
        VinylDNSConfig.dynamoConfig)
      _ <- TestDataLoader.loadTestData(userRepo)
      sqsConfig <- IO(VinylDNSConfig.sqsConfig)
      sqsConnection <- IO(SqsConnection(sqsConfig))
      processingDisabled <- IO(VinylDNSConfig.vinyldnsConfig.getBoolean("processing-disabled"))
      processingSignal <- fs2.async.signalOf[IO, Boolean](processingDisabled)
      restHost <- IO(VinylDNSConfig.restConfig.getString("host"))
      restPort <- IO(VinylDNSConfig.restConfig.getInt("port"))
      batchChangeLimit <- IO(VinylDNSConfig.vinyldnsConfig.getInt("batch-change-limit"))
      syncDelay <- IO(VinylDNSConfig.vinyldnsConfig.getInt("sync-delay"))
      _ <- fs2.async.start(
        ProductionZoneCommandHandler.run(
          sqsConnection,
          processingSignal,
          zoneRepo,
          zoneChangeRepo,
          recordChangeRepo,
          recordSetRepo,
          batchChangeRepo,
          sqsConfig))
    } yield {
      val zoneValidations = new ZoneValidations(syncDelay)
      val batchChangeValidations = new BatchChangeValidations(batchChangeLimit, AccessValidations)
      val commandBus = new SqsCommandBus(sqsConnection)
      val membershipService =
        new MembershipService(groupRepo, userRepo, membershipRepo, zoneRepo, groupChangeRepo)
      val connectionValidator =
        new ZoneConnectionValidator(VinylDNSConfig.defaultZoneConnection)
      val recordSetService = new RecordSetService(
        zoneRepo,
        recordSetRepo,
        recordChangeRepo,
        userRepo,
        commandBus,
        AccessValidations)
      val zoneService = new ZoneService(
        zoneRepo,
        groupRepo,
        userRepo,
        zoneChangeRepo,
        connectionValidator,
        commandBus,
        zoneValidations,
        AccessValidations)
      val healthService = new HealthService(zoneRepo)
      val batchChangeConverter = new BatchChangeConverter(batchChangeRepo, commandBus)
      val batchChangeService = new BatchChangeService(
        zoneRepo,
        recordSetRepo,
        batchChangeValidations,
        batchChangeRepo,
        batchChangeConverter)
      val collectorRegistry = CollectorRegistry.defaultRegistry
      val vinyldnsService = new VinylDNSService(
        membershipService,
        processingSignal,
        zoneService,
        healthService,
        recordSetService,
        batchChangeService,
        collectorRegistry)

      DefaultExports.initialize()
      collectorRegistry.register(new DropwizardExports(VinylDNSMetrics.metricsRegistry))

      // Need to register a jvm shut down hook to make sure everything is cleaned up, especially important for
      // running locally.
      sys.ShutdownHookThread {
        logger.error("STOPPING VINYLDNS SERVER...")

        // shutdown sqs gracefully
        sqsConnection.shutdown()

        // exit JVM when ActorSystem has been terminated
        system.registerOnTermination(System.exit(0))

        // shut down ActorSystem
        system.terminate()

        ()
      }

      logger.error(s"STARTING VINYLDNS SERVER ON $restHost:$restPort")
      logger.error(banner)

      // Starts up our http server
      Http().bindAndHandle(vinyldnsService.routes, restHost, restPort)
    }

  // runApp gives us a Task, we actually have to run it!  Running it will yield a Future, which is our app!
  runApp().unsafeRunAsync {
    case Right(_) =>
      logger.error("VINYLDNS SERVER STARTED SUCCESSFULLY!!")
    case Left(startupFailure) =>
      logger.error(s"VINYLDNS SERVER UNABLE TO START $startupFailure")
      startupFailure.printStackTrace()
  }
}
