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
import akka.stream.{Materializer, ActorMaterializer}
import cats.effect.{Timer, IO, ContextShift}
import cats.data.NonEmptyList
import fs2.concurrent.SignallingRef
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.dropwizard.DropwizardExports
import io.prometheus.client.hotspot.DefaultExports
import org.slf4j.LoggerFactory
import vinyldns.api.backend.CommandHandler
import vinyldns.api.config.RuntimeVinylDNSConfig
import vinyldns.api.domain.access.AccessValidations
import vinyldns.api.domain.auth.MembershipAuthPrincipalProvider
import vinyldns.api.domain.batch.{BatchChangeService, BatchChangeConverter, BatchChangeValidations}
import vinyldns.api.domain.config.AppConfigService
import vinyldns.api.domain.membership._
import vinyldns.api.domain.record.RecordSetService
import vinyldns.api.domain.zone._
import vinyldns.api.metrics.APIMetrics
import vinyldns.api.repository.{ApiDataAccessorProvider, ApiDataAccessor, TestDataLoader}
import vinyldns.api.route.VinylDNSService
import vinyldns.core.VinylDNSMetrics
import vinyldns.core.health.HealthService
import vinyldns.core.queue.{MessageQueueLoader, MessageCount}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.io.{Codec, Source}
import vinyldns.core.notifier.NotifierLoader
import vinyldns.core.repository.DataStoreLoader
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

object Boot extends App {

  private val logger = LoggerFactory.getLogger("Boot")
  private val bannerLogger = LoggerFactory.getLogger("BANNER_LOGGER")

  // Create a ScheduledExecutorService with a new single thread
  private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

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
      _ <- RuntimeVinylDNSConfig.init()
      vinyldnsConfig <- RuntimeVinylDNSConfig.currentIO
      system <- IO(ActorSystem("VinylDNS", RuntimeVinylDNSConfig.getRaw))
      loaderResponse <- DataStoreLoader.loadAll[ApiDataAccessor](
        vinyldnsConfig.dataStoreConfigs,
        vinyldnsConfig.crypto,
        ApiDataAccessorProvider
      )
      repositories = loaderResponse.accessor
      // Load app_config from DB into RuntimeVinylDNSConfig's in-memory store
      _ <- RuntimeVinylDNSConfig.loadFromDb(repositories.appConfigRepository)
      // Apply DB overrides to volatile vars (limits, shared-approved-types, etc.)
      // This also builds the BackendResolver from DB and sets dynamicBackendResolver.
      _ <- RuntimeVinylDNSConfig.applyDbOverrides()
      backendResolver = RuntimeVinylDNSConfig.dynamicBackendResolver
      loadTestData <- RuntimeVinylDNSConfig.loadTestData
      isZoneSyncScheduleAllowed <- RuntimeVinylDNSConfig.isZoneSyncScheduleAllowed
      restHost <- RuntimeVinylDNSConfig.restHost
      restPort <- RuntimeVinylDNSConfig.restPort
      _ <- if (loadTestData) {
        TestDataLoader.loadTestData(
          repositories.userRepository,
          repositories.groupRepository,
          repositories.zoneRepository,
          repositories.membershipRepository)
      } else {
        IO.unit
      }
      messageQueue <- MessageQueueLoader.load(vinyldnsConfig.messageQueueConfig)
      processingSignal <- RuntimeVinylDNSConfig.processingDisabled.flatMap(SignallingRef[IO, Boolean](_))
      effectiveNotifierConfigs <- RuntimeVinylDNSConfig.effectiveNotifierConfigs
      notifiers <- NotifierLoader.loadAll(
        effectiveNotifierConfigs,
        repositories.userRepository,
        repositories.groupRepository
      )
      _ <- APIMetrics.initialize(vinyldnsConfig.apiMetricSettings)
      // Schedule the zone sync task to be executed every 5 seconds
      _ <- if (isZoneSyncScheduleAllowed){ IO(executor.scheduleAtFixedRate(() => {
        val zoneChanges = for {
          zoneChanges <- ZoneSyncScheduleHandler.zoneSyncScheduler(repositories.zoneRepository)
          _ <- if (zoneChanges.nonEmpty) messageQueue.sendBatch(NonEmptyList.fromList(zoneChanges.toList).get) else IO.unit
        } yield ()
        zoneChanges.unsafeRunAsync {
          case Right(_) =>
            logger.debug("Zone sync scheduler ran successfully!")
          case Left(error) =>
            logger.error(s"An error occurred while performing the scheduled zone sync. Error: $error")
        }
      }, 0, 1, TimeUnit.SECONDS)) } else IO.unit
      _ <- CommandHandler.run(
        messageQueue,
        RuntimeVinylDNSConfig.queueMessagesPerPoll.flatMap(n => IO.fromEither(MessageCount(n))),
        processingSignal,
        RuntimeVinylDNSConfig.queuePollingIntervalMillis.map(_.millis),
        repositories.zoneRepository,
        repositories.zoneChangeRepository,
        repositories.recordSetRepository,
        repositories.recordChangeRepository,
        repositories.recordSetCacheRepository,
        repositories.batchChangeRepository,
        notifiers,
        backendResolver,
        RuntimeVinylDNSConfig.maxZoneSize
      ).start
    } yield {
      val batchAccessValidations = new AccessValidations(
        RuntimeVinylDNSConfig.globalAcls
      )
      val recordAccessValidations = new AccessValidations()
      val zoneValidations = new ZoneValidations(RuntimeVinylDNSConfig.syncDelay)
      val batchChangeValidations = new BatchChangeValidations(
        batchAccessValidations,
        RuntimeVinylDNSConfig.manualReviewConfig,
        RuntimeVinylDNSConfig.approvedNameServers
      )
      val membershipService = MembershipService(repositories)

      val appConfigService = AppConfigService(repositories.appConfigRepository)

      val connectionValidator =
        new ZoneConnectionValidator(
          backendResolver,
          RuntimeVinylDNSConfig.approvedNameServers,
          RuntimeVinylDNSConfig.maxZoneSize
        )
      val recordSetService =
        RecordSetService(
          repositories,
          messageQueue,
          recordAccessValidations,
          backendResolver,
          RuntimeVinylDNSConfig.validateRecordLookupAgainstDnsBackend,
          RuntimeVinylDNSConfig.approvedNameServers,
          RuntimeVinylDNSConfig.useRecordSetCache,
          notifiers
        )
      val zoneService = ZoneService(
        repositories,
        connectionValidator,
        messageQueue,
        zoneValidations,
        recordAccessValidations,
        backendResolver,
        vinyldnsConfig.crypto,
        membershipService
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
        RuntimeVinylDNSConfig.manualReviewEnabled,
        authPrincipalProvider,
        notifiers,
        RuntimeVinylDNSConfig.scheduledChangesEnabled,
        vinyldnsConfig.batchChangeConfig.v6DiscoveryNibbleBoundaries,
        RuntimeVinylDNSConfig.defaultTtl
      )
      val collectorRegistry = CollectorRegistry.defaultRegistry
      val vinyldnsService = new VinylDNSService(
        membershipService,
        processingSignal,
        zoneService,
        appConfigService,
        healthService,
        recordSetService,
        batchChangeService,
        collectorRegistry,
        authPrincipalProvider
      )

      DefaultExports.initialize()
      collectorRegistry.register(new DropwizardExports(VinylDNSMetrics.metricsRegistry))

      // Need to register a jvm shut down hook to make sure everything is cleaned up, especially important for
      // running locally.
      sys.ShutdownHookThread {
        logger.info("STOPPING VINYLDNS SERVER...")

        //shutdown data store provider
        loaderResponse.shutdown()

        // exit JVM when ActorSystem has been terminated
        system.registerOnTermination(System.exit(0))

        // shut down ActorSystem
        system.terminate()

        ()
      }

      logger.info(
        s"STARTING VINYLDNS SERVER ON $restHost:$restPort"
      )
      bannerLogger.info(banner)

      // Starts up our http server
      implicit val actorSystem: ActorSystem = system
      implicit val materializer: Materializer = ActorMaterializer()
      Http().bindAndHandle(
        vinyldnsService.routes,
        restHost,
        restPort
      )
    }

  // runApp gives us a Task, we actually have to run it!  Running it will yield a Future, which is our app!
  runApp().unsafeRunAsync {
    case Right(_) =>
      logger.info("VINYLDNS SERVER STARTED SUCCESSFULLY!!")
    case Left(startupFailure) =>
      logger.error(s"VINYLDNS SERVER UNABLE TO START $startupFailure")
      startupFailure.printStackTrace()
      // It doesn't do us much good to keep the application running if it failed to start.
      sys.exit(1)
  }

}
