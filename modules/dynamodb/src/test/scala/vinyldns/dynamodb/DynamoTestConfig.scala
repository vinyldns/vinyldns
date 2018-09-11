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

package vinyldns.dynamodb

import com.typesafe.config.{Config, ConfigFactory}
import pureconfig.{CamelCase, ConfigFieldMapping, ProductHint}
import vinyldns.dynamodb.repository.{DynamoDBDataStoreSettings, DynamoDBRepositorySettings}

object DynamoTestConfig {

  lazy val config: Config = ConfigFactory.load()
  lazy val vinyldnsConfig: Config = config.getConfig("vinyldns")

  lazy val dynamoConfig: DynamoDBDataStoreSettings =
    pureconfig.loadConfigOrThrow[DynamoDBDataStoreSettings](vinyldnsConfig, "dynamo")

  // TODO these will change when dynamically loaded
  implicit def hint[T]: ProductHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

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
