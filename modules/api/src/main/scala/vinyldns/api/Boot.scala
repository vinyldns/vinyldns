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
import vinyldns.api.repository.{ApiDataAccessor, ApiDataAccessorProvider, TestDataLoader}
import vinyldns.api.route.{HealthService, VinylDNSService}
import vinyldns.core.VinylDNSMetrics
import vinyldns.core.repository.DataStoreLoader

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
      crypto <- IO(Crypto.instance) // load crypto
      repoConfigs <- VinylDNSConfig.dataStoreConfigs
      loaderResponse <- DataStoreLoader
        .loadAll[ApiDataAccessor](repoConfigs, crypto, ApiDataAccessorProvider)
      repositories = loaderResponse.accessor
      _ <- TestDataLoader.loadTestData(repositories.userRepository)
      sqsConfig <- IO(VinylDNSConfig.sqsConfig)
      sqsConnection <- IO(SqsConnection(sqsConfig))
      processingDisabled <- IO(VinylDNSConfig.vinyldnsConfig.getBoolean("processing-disabled"))
      processingSignal <- fs2.async.signalOf[IO, Boolean](processingDisabled)
      restHost <- IO(VinylDNSConfig.restConfig.getString("host"))
      restPort <- IO(VinylDNSConfig.restConfig.getInt("port"))
      batchChangeLimit <- IO(VinylDNSConfig.vinyldnsConfig.getInt("batch-change-limit"))
      syncDelay <- IO(VinylDNSConfig.vinyldnsConfig.getInt("sync-delay"))
      _ <- fs2.async.start(
        ProductionZoneCommandHandler.run(sqsConnection, processingSignal, repositories, sqsConfig))
    } yield {
      val zoneValidations = new ZoneValidations(syncDelay)
      val batchChangeValidations = new BatchChangeValidations(batchChangeLimit, AccessValidations)
      val commandBus = new SqsCommandBus(sqsConnection)
      val membershipService = MembershipService(repositories)
      val connectionValidator =
        new ZoneConnectionValidator(VinylDNSConfig.defaultZoneConnection)
      val recordSetService = RecordSetService(repositories, commandBus, AccessValidations)
      val zoneService = ZoneService(
        repositories,
        connectionValidator,
        commandBus,
        zoneValidations,
        AccessValidations)
      val healthService = new HealthService(repositories.zoneRepository)
      val batchChangeConverter =
        new BatchChangeConverter(repositories.batchChangeRepository, commandBus)
      val batchChangeService =
        BatchChangeService(repositories, batchChangeValidations, batchChangeConverter)
      val collectorRegistry = CollectorRegistry.defaultRegistry
      val vinyldnsService = new VinylDNSService(
        membershipService,
        processingSignal,
        zoneService,
        healthService,
        recordSetService,
        batchChangeService,
        collectorRegistry,
        repositories.userRepository,
        repositories.membershipRepository
      )

      DefaultExports.initialize()
      collectorRegistry.register(new DropwizardExports(VinylDNSMetrics.metricsRegistry))

      // Need to register a jvm shut down hook to make sure everything is cleaned up, especially important for
      // running locally.
      sys.ShutdownHookThread {
        logger.error("STOPPING VINYLDNS SERVER...")

        // shutdown sqs gracefully
        sqsConnection.shutdown()

        //shutdown data store provider
        loaderResponse.shutdown()

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
