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
import vinyldns.core.domain.record.RecordType

import scala.collection.JavaConverters._
import scala.util.matching.Regex
import vinyldns.core.domain.zone.ZoneConnection
import vinyldns.core.queue.MessageQueueConfig
import vinyldns.core.repository.DataStoreConfig

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

  lazy val restConfig: Config = vinyldnsConfig.getConfig("rest")
  lazy val monitoringConfig: Config = vinyldnsConfig.getConfig("monitoring")
  lazy val messageQueueConfig: IO[MessageQueueConfig] =
    loadConfigF[IO, MessageQueueConfig](vinyldnsConfig.getConfig("queue"))
  lazy val cryptoConfig: Config = vinyldnsConfig.getConfig("crypto")
  lazy val system: ActorSystem = ActorSystem("VinylDNS", VinylDNSConfig.config)
  lazy val approvedNameServers: List[Regex] =
    vinyldnsConfig.getStringList("approved-name-servers").asScala.toList.map(n => n.r)

  lazy val highValueRegexList: List[Regex] =
    getOptionalStringList("high-value-domains.regex-list").map(n => n.r)

  lazy val highValueIpList: List[IpAddress] =
    getOptionalStringList("high-value-domains.ip-list").flatMap(ip => IpAddress(ip))

  lazy val sharedApprovedTypes: Set[RecordType.Value] =
    if (vinyldnsConfig.hasPath("shared-approved-types")) {
      vinyldnsConfig.as[Set[RecordType.Value]]("shared-approved-types")
    } else Set()

  lazy val defaultZoneConnection: ZoneConnection = {
    val connectionConfig = VinylDNSConfig.vinyldnsConfig.getConfig("defaultZoneConnection")
    val name = connectionConfig.getString("name")
    val keyName = connectionConfig.getString("keyName")
    val key = connectionConfig.getString("key")
    val primaryServer = connectionConfig.getString("primaryServer")
    ZoneConnection(name, keyName, key, primaryServer).encrypted(Crypto.instance)
  }

  lazy val defaultTransferConnection: ZoneConnection = {
    val connectionConfig = VinylDNSConfig.vinyldnsConfig.getConfig("defaultTransferConnection")
    val name = connectionConfig.getString("name")
    val keyName = connectionConfig.getString("keyName")
    val key = connectionConfig.getString("key")
    val primaryServer = connectionConfig.getString("primaryServer")
    ZoneConnection(name, keyName, key, primaryServer).encrypted(Crypto.instance)
  }

  lazy val healthCheckTimeout: IO[Int] =
    loadConfigF[IO, Option[Int]](vinyldnsConfig, "health-check-timeout").map(_.getOrElse(10000))

  def getOptionalStringList(key: String): List[String] =
    if (vinyldnsConfig.hasPath(key)) {
      vinyldnsConfig.getStringList(key).asScala.toList
    } else List()
}
