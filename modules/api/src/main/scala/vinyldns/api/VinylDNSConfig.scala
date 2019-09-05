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
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import pureconfig.module.catseffect.loadConfigF
import vinyldns.api.crypto.Crypto
import com.comcast.ip4s._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.EnumerationReader._
import vinyldns.api.domain.access.{GlobalAcl, GlobalAcls}
import vinyldns.api.domain.batch.V6DiscoveryNibbleBoundaries
import vinyldns.api.domain.zone.ZoneRecordValidations
import vinyldns.core.domain.DomainHelpers
import vinyldns.core.domain.record.RecordType

import scala.collection.JavaConverters._
import scala.util.matching.Regex
import vinyldns.core.domain.zone.{ConfiguredDnsConnections, DnsBackend, ZoneConnection}
import vinyldns.core.queue.MessageQueueConfig
import vinyldns.core.repository.DataStoreConfig
import vinyldns.core.notifier.NotifierConfig

object VinylDNSConfig {
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  lazy val config: Config = ConfigFactory.load()
  lazy val vinyldnsConfig: Config = config.getConfig("vinyldns")

  lazy val dataStoreConfigs: IO[List[DataStoreConfig]] =
    vinyldnsConfig
      .getStringList("data-stores")
      .asScala
      .toList
      .map { configKey =>
        loadConfigF[IO, DataStoreConfig](vinyldnsConfig, configKey)
      }
      .parSequence

  lazy val notifierConfigs: IO[List[NotifierConfig]] =
    vinyldnsConfig
      .getStringList("notifiers")
      .asScala
      .toList
      .map { configKey =>
        loadConfigF[IO, NotifierConfig](vinyldnsConfig, configKey)
      }
      .parSequence

  lazy val restConfig: Config = vinyldnsConfig.getConfig("rest")
  lazy val monitoringConfig: Config = vinyldnsConfig.getConfig("monitoring")
  lazy val messageQueueConfig: IO[MessageQueueConfig] =
    loadConfigF[IO, MessageQueueConfig](vinyldnsConfig.getConfig("queue"))
  lazy val cryptoConfig: Config = vinyldnsConfig.getConfig("crypto")
  lazy val system: ActorSystem = ActorSystem("VinylDNS", VinylDNSConfig.config)
  lazy val approvedNameServers: List[Regex] =
    ZoneRecordValidations.toCaseIgnoredRegexList(getOptionalStringList("approved-name-servers"))

  lazy val highValueRegexList: List[Regex] =
    ZoneRecordValidations.toCaseIgnoredRegexList(
      getOptionalStringList("high-value-domains.regex-list"))

  lazy val highValueIpList: List[IpAddress] =
    getOptionalStringList("high-value-domains.ip-list").flatMap(ip => IpAddress(ip))

  lazy val sharedApprovedTypes: Set[RecordType.Value] =
    vinyldnsConfig.as[Option[Set[RecordType.Value]]]("shared-approved-types").getOrElse(Set())

  lazy val configuredDnsConnections: ConfiguredDnsConnections = {

    val defaultZoneConnection = {
      val connectionConfig = VinylDNSConfig.vinyldnsConfig.getConfig("defaultZoneConnection")
      val name = connectionConfig.getString("name")
      val keyName = connectionConfig.getString("keyName")
      val key = connectionConfig.getString("key")
      val primaryServer = connectionConfig.getString("primaryServer")
      ZoneConnection(name, keyName, key, primaryServer).encrypted(Crypto.instance)
    }

    val defaultTransferConnection = {
      val connectionConfig = VinylDNSConfig.vinyldnsConfig.getConfig("defaultTransferConnection")
      val name = connectionConfig.getString("name")
      val keyName = connectionConfig.getString("keyName")
      val key = connectionConfig.getString("key")
      val primaryServer = connectionConfig.getString("primaryServer")
      ZoneConnection(name, keyName, key, primaryServer).encrypted(Crypto.instance)
    }

    val dnsBackends = {
      if (vinyldnsConfig.hasPath("backends")) {
        vinyldnsConfig
          .getConfigList("backends")
          .asScala
          .map {
            pureconfig.loadConfigOrThrow[DnsBackend]
          }
          .toList
          .map(_.encrypted(Crypto.instance))
      } else List.empty
    }

    ConfiguredDnsConnections(defaultZoneConnection, defaultTransferConnection, dnsBackends)
  }

  lazy val healthCheckTimeout: IO[Int] =
    loadConfigF[IO, Option[Int]](vinyldnsConfig, "health-check-timeout").map(_.getOrElse(10000))

  def getOptionalStringList(key: String): List[String] =
    if (vinyldnsConfig.hasPath(key)) {
      vinyldnsConfig.getStringList(key).asScala.toList
    } else List()

  lazy val maxZoneSize: Int = vinyldnsConfig.as[Option[Int]]("max-zone-size").getOrElse(60000)
  lazy val defaultTtl: Long = vinyldnsConfig.as[Option[Long]](s"default-ttl").getOrElse(7200L)
  lazy val multiRecordBatchUpdateEnabled: Boolean =
    vinyldnsConfig.as[Option[Boolean]]("enable-multi-record-batch-update").getOrElse(false)
  lazy val manualBatchReviewEnabled: Boolean = vinyldnsConfig
    .as[Option[Boolean]]("manual-batch-review-enabled")
    .getOrElse(false)

  lazy val globalAcl: IO[GlobalAcls] =
    loadConfigF[IO, List[GlobalAcl]](vinyldnsConfig, "global-acl-rules").map(GlobalAcls)

  // defines nibble boundary for ipv6 zone discovery
  // (min of 2, max of 3 means zones of form X.X.ip6-arpa. and X.X.X.ip6-arpa. will be discovered)
  lazy val v6DiscoveryBoundaries: IO[V6DiscoveryNibbleBoundaries] =
    loadConfigF[IO, V6DiscoveryNibbleBoundaries](vinyldnsConfig, "v6-discovery-nibble-boundaries")

  lazy val scheduledChangesEnabled: Boolean = vinyldnsConfig
    .as[Option[Boolean]]("scheduled-changes-enabled")
    .getOrElse(false)

  lazy val domainListRequiringManualReview: List[Regex] =
    ZoneRecordValidations.toCaseIgnoredRegexList(
      getOptionalStringList("manual-review-domains.domain-list"))

  lazy val ipListRequiringManualReview: List[IpAddress] =
    getOptionalStringList("manual-review-domains.ip-list").flatMap(ip => IpAddress(ip))

  lazy val zoneNameListRequiringManualReview: Set[String] = {
    Set() ++ getOptionalStringList("manual-review-domains.zone-name-list").map(zn =>
      DomainHelpers.ensureTrailingDot(zn.toLowerCase))
  }
}
