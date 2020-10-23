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
import cats.effect.{ContextShift, IO, Timer}
import com.typesafe.config.ConfigFactory
import fs2.concurrent.SignallingRef
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.dropwizard.DropwizardExports
import io.prometheus.client.hotspot.DefaultExports
import org.slf4j.LoggerFactory
import vinyldns.api.backend.CommandHandler
import vinyldns.api.config.VinylDNSConfig
import vinyldns.api.domain.access.{AccessValidations, GlobalAcls}
import vinyldns.api.domain.auth.MembershipAuthPrincipalProvider
import vinyldns.api.domain.batch.{BatchChangeConverter, BatchChangeService, BatchChangeValidations}
import vinyldns.api.domain.membership._
import vinyldns.api.domain.record.RecordSetService
import vinyldns.api.domain.zone._
import vinyldns.api.metrics.APIMetrics
import vinyldns.api.repository.{ApiDataAccessor, ApiDataAccessorProvider, TestDataLoader}
import vinyldns.api.route.VinylDNSService
import vinyldns.core.VinylDNSMetrics
import vinyldns.core.domain.backend.BackendResolver
import vinyldns.core.health.HealthService
import vinyldns.core.queue.{MessageCount, MessageQueueLoader}
import vinyldns.core.repository.DataStoreLoader

import scala.concurrent.{ExecutionContext, Future}
import scala.io.{Codec, Source}
import vinyldns.core.notifier.NotifierLoader

object Boot extends App {

  private val logger = LoggerFactory.getLogger("Boot")
  private implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  private implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  private implicit val timer: Timer[IO] = IO.timer(ec)

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
      vinyldnsConfig <- VinylDNSConfig.load()
      system <- IO(ActorSystem("VinylDNS", ConfigFactory.load()))
      loaderResponse <- DataStoreLoader
        .loadAll[ApiDataAccessor](
          vinyldnsConfig.dataStoreConfigs,
          vinyldnsConfig.crypto,
          ApiDataAccessorProvider
        )
      repositories = loaderResponse.accessor
      backendResolver <- BackendResolver.apply(vinyldnsConfig.backendConfigs)
      _ <- TestDataLoader
        .loadTestData(
          repositories.userRepository,
          repositories.groupRepository,
          repositories.zoneRepository,
          repositories.membershipRepository
        )
      messageQueue <- MessageQueueLoader.load(vinyldnsConfig.messageQueueConfig)
      processingSignal <- SignallingRef[IO, Boolean](vinyldnsConfig.serverConfig.processingDisabled)
      msgsPerPoll <- IO.fromEither(MessageCount(vinyldnsConfig.messageQueueConfig.messagesPerPoll))
      notifiers <- NotifierLoader.loadAll(
        vinyldnsConfig.notifierConfigs,
        repositories.userRepository
      )
      _ <- APIMetrics.initialize(vinyldnsConfig.apiMetricSettings)
      _ <- CommandHandler
        .run(
          messageQueue,
          msgsPerPoll,
          processingSignal,
          vinyldnsConfig.messageQueueConfig.pollingInterval,
          repositories.zoneRepository,
          repositories.zoneChangeRepository,
          repositories.recordSetRepository,
          repositories.recordChangeRepository,
          repositories.batchChangeRepository,
          notifiers,
          backendResolver,
          vinyldnsConfig.serverConfig.maxZoneSize
        )
        .start
    } yield {
      val batchAccessValidations = new AccessValidations(
        vinyldnsConfig.globalAcls,
        vinyldnsConfig.batchChangeConfig.allowedRecordTypes
      )
      val recordAccessValidations =
        new AccessValidations(GlobalAcls(Nil), vinyldnsConfig.batchChangeConfig.allowedRecordTypes)
      val zoneValidations = new ZoneValidations(vinyldnsConfig.serverConfig.syncDelay)
      val batchChangeValidations = new BatchChangeValidations(
        batchAccessValidations,
        vinyldnsConfig.highValueDomainConfig,
        vinyldnsConfig.manualReviewConfig,
        vinyldnsConfig.batchChangeConfig,
        vinyldnsConfig.scheduledChangesConfig
      )
      val membershipService = MembershipService(repositories)
      val connectionValidator =
        new ZoneConnectionValidator(
          backendResolver,
          vinyldnsConfig.serverConfig.approvedNameServers,
          vinyldnsConfig.serverConfig.maxZoneSize
        )
      val recordSetService =
        RecordSetService(
          repositories,
          messageQueue,
          recordAccessValidations,
          backendResolver,
          vinyldnsConfig.serverConfig.validateRecordLookupAgainstDnsBackend,
          vinyldnsConfig.highValueDomainConfig,
          vinyldnsConfig.serverConfig.approvedNameServers
        )
      val zoneService = ZoneService(
        repositories,
        connectionValidator,
        messageQueue,
        zoneValidations,
        recordAccessValidations,
        backendResolver,
        vinyldnsConfig.crypto
      )
      val healthService = new HealthService(
        messageQueue.healthCheck :: backendResolver.healthCheck(
          vinyldnsConfig.serverConfig.healthCheckTimeout
        ) ::
          loaderResponse.healthChecks
      )
      val batchChangeConverter =
        new BatchChangeConverter(repositories.batchChangeRepository, messageQueue)
      val authPrincipalProvider =
        new MembershipAuthPrincipalProvider(
          repositories.userRepository,
          repositories.membershipRepository
        )
      val batchChangeService = BatchChangeService(
        repositories,
        batchChangeValidations,
        batchChangeConverter,
        vinyldnsConfig.manualReviewConfig.enabled,
        authPrincipalProvider,
        notifiers,
        vinyldnsConfig.scheduledChangesConfig.enabled,
        vinyldnsConfig.batchChangeConfig.v6DiscoveryNibbleBoundaries,
        vinyldnsConfig.serverConfig.defaultTtl
      )
      val collectorRegistry = CollectorRegistry.defaultRegistry
      val vinyldnsService = new VinylDNSService(
        membershipService,
        processingSignal,
        zoneService,
        healthService,
        recordSetService,
        batchChangeService,
        collectorRegistry,
        authPrincipalProvider,
        vinyldnsConfig
      )

      DefaultExports.initialize()
      collectorRegistry.register(new DropwizardExports(VinylDNSMetrics.metricsRegistry))

      // Need to register a jvm shut down hook to make sure everything is cleaned up, especially important for
      // running locally.
      sys.ShutdownHookThread {
        logger.error("STOPPING VINYLDNS SERVER...")

        //shutdown data store provider
        loaderResponse.shutdown()

        // exit JVM when ActorSystem has been terminated
        system.registerOnTermination(System.exit(0))

        // shut down ActorSystem
        system.terminate()

        ()
      }

      logger.error(
        s"STARTING VINYLDNS SERVER ON ${vinyldnsConfig.httpConfig.host}:${vinyldnsConfig.httpConfig.port}"
      )
      logger.error(banner)

      // Starts up our http server
      implicit val actorSystem: ActorSystem = system
      implicit val materializer: Materializer = ActorMaterializer()
      Http().bindAndHandle(
        vinyldnsService.routes,
        vinyldnsConfig.httpConfig.host,
        vinyldnsConfig.httpConfig.port
      )
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
