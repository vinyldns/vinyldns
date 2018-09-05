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

package vinyldns.dynamo

import com.typesafe.config.{Config, ConfigFactory}

object DynamoTestConfig {

  lazy val config: Config = ConfigFactory.load()
  lazy val vinyldnsConfig: Config = config.getConfig("vinyldns")
  lazy val dynamoConfig: Config = vinyldnsConfig.getConfig("dynamo")
  lazy val zoneChangeStoreConfig: Config = vinyldnsConfig.getConfig("zoneChanges")
  lazy val recordSetStoreConfig: Config = vinyldnsConfig.getConfig("recordSet")
  lazy val recordChangeStoreConfig: Config = vinyldnsConfig.getConfig("recordChange")
  lazy val usersStoreConfig: Config = vinyldnsConfig.getConfig("users")
  lazy val groupsStoreConfig: Config = vinyldnsConfig.getConfig("groups")
  lazy val groupChangesStoreConfig: Config = vinyldnsConfig.getConfig("groupChanges")
  lazy val membershipStoreConfig: Config = vinyldnsConfig.getConfig("membership")

}
