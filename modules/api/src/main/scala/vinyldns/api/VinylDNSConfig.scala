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
import com.typesafe.config.{Config, ConfigFactory}
import vinyldns.api.crypto.Crypto

import scala.collection.JavaConverters._
import scala.util.matching.Regex
import vinyldns.core.domain.zone.ZoneConnection
import vinyldns.core.repository.DataStoreConfig

object VinylDNSConfig {

  lazy val config: Config = ConfigFactory.load()
  lazy val vinyldnsConfig: Config = config.getConfig("vinyldns")
  lazy val dynamoConfig: Config = vinyldnsConfig.getConfig("dynamo")
  lazy val restConfig: Config = vinyldnsConfig.getConfig("rest")
  lazy val monitoringConfig: Config = vinyldnsConfig.getConfig("monitoring")
  lazy val accountStoreConfig: Config = vinyldnsConfig.getConfig("accounts")
  lazy val zoneChangeStoreConfig: Config = vinyldnsConfig.getConfig("zoneChanges")
  lazy val recordSetStoreConfig: Config = vinyldnsConfig.getConfig("recordSet")
  lazy val recordChangeStoreConfig: Config = vinyldnsConfig.getConfig("recordChange")
  lazy val usersStoreConfig: Config = vinyldnsConfig.getConfig("users")
  lazy val groupsStoreConfig: Config = vinyldnsConfig.getConfig("groups")
  lazy val groupChangesStoreConfig: Config = vinyldnsConfig.getConfig("groupChanges")
  lazy val membershipStoreConfig: Config = vinyldnsConfig.getConfig("membership")
  lazy val mySqlConfig: DataStoreConfig =
    pureconfig.loadConfigOrThrow[DataStoreConfig](vinyldnsConfig, "mysql")
  lazy val sqsConfig: Config = vinyldnsConfig.getConfig("sqs")
  lazy val cryptoConfig: Config = vinyldnsConfig.getConfig("crypto")
  lazy val system: ActorSystem = ActorSystem("VinylDNS", VinylDNSConfig.config)
  lazy val encryptUserSecrets: Boolean = vinyldnsConfig.getBoolean("encrypt-user-secrets")
  lazy val approvedNameServers: List[Regex] =
    vinyldnsConfig.getStringList("approved-name-servers").asScala.toList.map(n => n.r)

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
}
