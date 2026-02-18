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

package vinyldns.api.config

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import com.typesafe.config.Config
import pureconfig._
import pureconfig.error.ConfigReaderException
import pureconfig.generic.auto._
import vinyldns.api.domain.access.{GlobalAcl, GlobalAcls}
import vinyldns.api.metrics.APIMetricsSettings
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.backend.BackendConfigs
import vinyldns.core.domain.zone.ConfiguredDnsConnections
import vinyldns.core.notifier.NotifierConfig
import vinyldns.core.queue.MessageQueueConfig
import vinyldns.core.repository.DataStoreConfig

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

final case class StartupConfig(
                                messageQueueConfig: MessageQueueConfig,
                                notifierConfigs: List[NotifierConfig],
                                dataStoreConfigs: List[DataStoreConfig],
                                backendConfigs: BackendConfigs,
                                dottedHostsConfig: DottedHostsConfig,
                                configuredDnsConnections: ConfiguredDnsConnections,
                                apiMetricSettings: APIMetricsSettings,
                                crypto: CryptoAlgebra
                              )

final case class RuntimeConfig(
                                serverConfig: ServerConfig,
                                limitsconfig: LimitsConfig,
                                validEmailConfig: ValidEmailConfig,
                                httpConfig: HttpConfig,
                                highValueDomainConfig: HighValueDomainConfig,
                                manualReviewConfig: ManualReviewConfig,
                                scheduledChangesConfig: ScheduledChangesConfig,
                                batchChangeConfig: BatchChangeConfig,
                                globalAcls: GlobalAcls
                              )

final case class VinylDNSConfig private (
                                          runtime: RuntimeConfig,
                                          startup: StartupConfig
                                        ) {

  // ---- runtime configs (reloadable) ----
  val serverConfig: ServerConfig = runtime.serverConfig
  val limitsconfig: LimitsConfig = runtime.limitsconfig
  val validEmailConfig: ValidEmailConfig = runtime.validEmailConfig
  val httpConfig: HttpConfig = runtime.httpConfig
  val highValueDomainConfig: HighValueDomainConfig = runtime.highValueDomainConfig
  val manualReviewConfig: ManualReviewConfig = runtime.manualReviewConfig
  val scheduledChangesConfig: ScheduledChangesConfig = runtime.scheduledChangesConfig
  val batchChangeConfig: BatchChangeConfig = runtime.batchChangeConfig
  val globalAcls: GlobalAcls = runtime.globalAcls

  // ---- startup config (non-reloadable)----
  val messageQueueConfig: MessageQueueConfig = startup.messageQueueConfig
  val notifierConfigs: List[NotifierConfig] = startup.notifierConfigs
  val dataStoreConfigs: List[DataStoreConfig] = startup.dataStoreConfigs
  val backendConfigs: BackendConfigs = startup.backendConfigs
  val dottedHostsConfig: DottedHostsConfig = startup.dottedHostsConfig
  val configuredDnsConnections: ConfiguredDnsConnections = startup.configuredDnsConnections
  val apiMetricSettings: APIMetricsSettings = startup.apiMetricSettings
  val crypto: CryptoAlgebra = startup.crypto
}

object VinylDNSConfig {

  private def loadIO[A](
                         config: Config,
                         path: String
                       )(implicit cr: ConfigReader[A], classTag: ClassTag[A]): IO[A] =
    EitherT
      .fromEither[IO](ConfigSource.fromConfig(config).at(path).cursor())
      .subflatMap(cr.from)
      .leftMap(failures => new ConfigReaderException[A](failures))
      .rethrowT

  def load(): IO[VinylDNSConfig] =
    loadFrom(RuntimeVinylDNSConfig.getRaw)

  def loadFrom(config: Config): IO[VinylDNSConfig] = {

    def optionalStringListIO(config: Config, path: String): IO[List[String]] =
      IO {
        if (config.hasPath(path)) config.getStringList(path).asScala.toList else Nil
      }

    def loadFromStringListIO[A](
                                 config: Config,
                                 path: String
                               )(implicit cr: ConfigReader[A], classTag: ClassTag[A]): IO[List[A]] =
      optionalStringListIO(config, path).flatMap { keys =>
        keys.traverse(k => loadIO[A](config, s"vinyldns.$k"))
      }

    for {
      // ---- runtime ----
      limitsconfig <- loadIO[LimitsConfig](config, "vinyldns.api.limits")
      validEmailConfig <- loadIO[ValidEmailConfig](config, "vinyldns.valid-email-config")
      serverConfig <- loadIO[ServerConfig](config, "vinyldns")
      batchChangeConfig <- loadIO[BatchChangeConfig](config, "vinyldns")
      httpConfig <- loadIO[HttpConfig](config, "vinyldns.rest")
      hvdConfig <- loadIO[HighValueDomainConfig](config, "vinyldns.high-value-domains")
      scheduledChangesConfig <- loadIO[ScheduledChangesConfig](config, "vinyldns")
      manualReviewConfig <- loadIO[ManualReviewConfig](config, "vinyldns")
      globalAcls <- loadIO[List[GlobalAcl]](config, "vinyldns.global-acl-rules").map(GlobalAcls.apply)

      // ---- startup ----
      backendConfigs <- loadIO[BackendConfigs](config, "vinyldns.backend")
      dottedHostsConfig <- loadIO[DottedHostsConfig](config, "vinyldns.dotted-hosts")
      messageQueueConfig <- loadIO[MessageQueueConfig](config, "vinyldns.queue")
      dataStoreConfigs <- loadFromStringListIO[DataStoreConfig](config, "vinyldns.data-stores")
      notifierConfigs <- loadFromStringListIO[NotifierConfig](config, "vinyldns.notifiers")
      cryptoConfig <- IO(config.getConfig("vinyldns.crypto"))
      crypto <- CryptoAlgebra.load(cryptoConfig)
      connections <- ConfiguredDnsConnections.load(config, cryptoConfig)
      metricSettings <- loadIO[APIMetricsSettings](config, "vinyldns.metrics")
    } yield VinylDNSConfig(
      runtime = RuntimeConfig(
        serverConfig,
        limitsconfig,
        validEmailConfig,
        httpConfig,
        hvdConfig,
        manualReviewConfig,
        scheduledChangesConfig,
        batchChangeConfig,
        globalAcls
      ),
      startup = StartupConfig(
        messageQueueConfig,
        notifierConfigs,
        dataStoreConfigs,
        backendConfigs,
        dottedHostsConfig,
        connections,
        metricSettings,
        crypto
      )
    )
  }
}
