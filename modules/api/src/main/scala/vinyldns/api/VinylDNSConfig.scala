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
import pureconfig.{CamelCase, ConfigFieldMapping, ProductHint}
import vinyldns.api.VinylDNSConfig.vinyldnsConfig
import vinyldns.api.crypto.Crypto

import scala.collection.JavaConverters._
import scala.util.matching.Regex
import vinyldns.core.domain.zone.ZoneConnection
import vinyldns.core.repository.DataStoreConfig
import vinyldns.dynamodb.repository.{DynamoDBDataStoreSettings, DynamoDBRepositorySettings}

object VinylDNSConfig {

  lazy val config: Config = ConfigFactory.load()
  lazy val vinyldnsConfig: Config = config.getConfig("vinyldns")

  lazy val dynamoConfig = DynamoConfig.dynamoConfig
  lazy val zoneChangeStoreConfig: DynamoDBRepositorySettings = DynamoConfig.zoneChangeStoreConfig
  lazy val recordSetStoreConfig: DynamoDBRepositorySettings = DynamoConfig.recordSetStoreConfig
  lazy val recordChangeStoreConfig: DynamoDBRepositorySettings =
    DynamoConfig.recordChangeStoreConfig
  lazy val usersStoreConfig: DynamoDBRepositorySettings = DynamoConfig.usersStoreConfig
  lazy val groupsStoreConfig: DynamoDBRepositorySettings = DynamoConfig.groupsStoreConfig
  lazy val groupChangesStoreConfig: DynamoDBRepositorySettings =
    DynamoConfig.groupChangesStoreConfig
  lazy val membershipStoreConfig: DynamoDBRepositorySettings = DynamoConfig.membershipStoreConfig

  lazy val restConfig: Config = vinyldnsConfig.getConfig("rest")
  lazy val monitoringConfig: Config = vinyldnsConfig.getConfig("monitoring")
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

object DynamoConfig {
  /* TODO this whole object will be removed once dynamic loading is in place
   * I split it out because of the hint - with that, for the moment we can avoid config
   * changes in dynamo stuff and still use pureconfig
   */

  implicit def hint[T]: ProductHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))
  lazy val dynamoConfig: DynamoDBDataStoreSettings =
    pureconfig.loadConfigOrThrow[DynamoDBDataStoreSettings](vinyldnsConfig, "dynamo")

  lazy val zoneChangeStoreConfig: DynamoDBRepositorySettings =
    pureconfig.loadConfigOrThrow[DynamoDBRepositorySettings](
      vinyldnsConfig.getConfig("zoneChanges.dynamo"))
  lazy val recordSetStoreConfig: DynamoDBRepositorySettings =
    pureconfig.loadConfigOrThrow[DynamoDBRepositorySettings](
      vinyldnsConfig.getConfig("recordSet.dynamo"))
  lazy val recordChangeStoreConfig: DynamoDBRepositorySettings =
    pureconfig.loadConfigOrThrow[DynamoDBRepositorySettings](
      vinyldnsConfig.getConfig("recordChange.dynamo"))
  lazy val usersStoreConfig: DynamoDBRepositorySettings =
    pureconfig.loadConfigOrThrow[DynamoDBRepositorySettings](
      vinyldnsConfig.getConfig("users.dynamo"))
  lazy val groupsStoreConfig: DynamoDBRepositorySettings =
    pureconfig.loadConfigOrThrow[DynamoDBRepositorySettings](
      vinyldnsConfig.getConfig("groups.dynamo"))
  lazy val groupChangesStoreConfig: DynamoDBRepositorySettings =
    pureconfig.loadConfigOrThrow[DynamoDBRepositorySettings](
      vinyldnsConfig.getConfig("groupChanges.dynamo"))
  lazy val membershipStoreConfig: DynamoDBRepositorySettings =
    pureconfig.loadConfigOrThrow[DynamoDBRepositorySettings](
      vinyldnsConfig.getConfig("membership.dynamo"))
}
