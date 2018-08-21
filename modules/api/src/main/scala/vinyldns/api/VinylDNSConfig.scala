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
import scala.collection.JavaConverters._
import scala.util.matching.Regex
import vinyldns.api.domain.zone.ZoneConnection

object VinylDNSConfig {

  lazy val config: Config = ConfigFactory.load()
  lazy val vinyldnsConfig: Config = config.getConfig("vinyldns")
  lazy val restConfig: Config = vinyldnsConfig.getConfig("rest")
  lazy val monitoringConfig: Config = vinyldnsConfig.getConfig("monitoring")
  lazy val dataStoreConfig: List[Config] =
    vinyldnsConfig.getStringList("data-stores").asScala.toList.map(vinyldnsConfig.getConfig)

  // TODO dynamo config direct access like this will be removed with pluggable repos work completion
  lazy val dynamoConfig: Config = vinyldnsConfig.getConfig("dynamodb").getConfig("settings")
  lazy val repositoryConfigs: Config =
    vinyldnsConfig.getConfig("dynamodb").getConfig("repositories")
  lazy val zoneChangeStoreConfig: Config = repositoryConfigs.getConfig("zoneChange")
  lazy val recordSetStoreConfig: Config = repositoryConfigs.getConfig("recordSet")
  lazy val recordChangeStoreConfig: Config = repositoryConfigs.getConfig("recordChange")
  lazy val usersStoreConfig: Config = repositoryConfigs.getConfig("user")
  lazy val groupsStoreConfig: Config = repositoryConfigs.getConfig("group")
  lazy val groupChangesStoreConfig: Config = repositoryConfigs.getConfig("groupChange")
  lazy val membershipStoreConfig: Config = repositoryConfigs.getConfig("membership")

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
    ZoneConnection(name, keyName, key, primaryServer).encrypted()
  }

  lazy val defaultTransferConnection: ZoneConnection = {
    val connectionConfig = VinylDNSConfig.vinyldnsConfig.getConfig("defaultTransferConnection")
    val name = connectionConfig.getString("name")
    val keyName = connectionConfig.getString("keyName")
    val key = connectionConfig.getString("key")
    val primaryServer = connectionConfig.getString("primaryServer")
    ZoneConnection(name, keyName, key, primaryServer).encrypted()
  }
}
