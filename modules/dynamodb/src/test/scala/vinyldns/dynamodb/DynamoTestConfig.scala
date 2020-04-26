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
import vinyldns.core.repository.{DataStoreConfig, RepositoriesConfig}
import vinyldns.dynamodb.repository.DynamoDBRepositorySettings
import pureconfig._
import pureconfig.generic.auto._

object DynamoTestConfig {

  lazy val config: Config = ConfigFactory.load()

  lazy val dynamoDBConfig: DataStoreConfig =
    ConfigSource.fromConfig(config).at("dynamodb").loadOrThrow[DataStoreConfig]

  lazy val baseReposConfigs: RepositoriesConfig = dynamoDBConfig.repositories
  lazy val zoneChangeStoreConfig: DynamoDBRepositorySettings =
    ConfigSource.fromConfig(baseReposConfigs.zoneChange.get).loadOrThrow[DynamoDBRepositorySettings]

  lazy val recordChangeStoreConfig: DynamoDBRepositorySettings =
    ConfigSource
      .fromConfig(baseReposConfigs.recordChange.get)
      .loadOrThrow[DynamoDBRepositorySettings]

  // Needed for testing DynamoDBUserRepository, but can't include in config directly due to not being implemented
  lazy val usertableConfig: Config = ConfigFactory.parseString("""
      |  table-name = "users"
      |  provisioned-reads = 30
      |  provisioned-writes = 30
    """.stripMargin)

  lazy val usersStoreConfig: DynamoDBRepositorySettings =
    ConfigSource.fromConfig(usertableConfig).loadOrThrow[DynamoDBRepositorySettings]

  lazy val groupsStoreConfig: DynamoDBRepositorySettings =
    ConfigSource.fromConfig(baseReposConfigs.group.get).loadOrThrow[DynamoDBRepositorySettings]

  lazy val groupChangesStoreConfig: DynamoDBRepositorySettings =
    ConfigSource
      .fromConfig(baseReposConfigs.groupChange.get)
      .loadOrThrow[DynamoDBRepositorySettings]

  lazy val membershipStoreConfig: DynamoDBRepositorySettings =
    ConfigSource.fromConfig(baseReposConfigs.membership.get).loadOrThrow[DynamoDBRepositorySettings]
}
