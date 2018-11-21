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

package vinyldns.mysql

import com.typesafe.config.{Config, ConfigFactory}
import vinyldns.core.domain.batch.BatchChangeRepository
import vinyldns.core.domain.membership._
import vinyldns.core.domain.zone.{ZoneChangeRepository, ZoneRepository}
import vinyldns.core.crypto.NoOpCrypto
import vinyldns.core.domain.record.{RecordChangeRepository, RecordSetRepository}
import vinyldns.core.repository.{DataStore, DataStoreConfig, LoadedDataStore, RepositoryName}
import vinyldns.mysql.repository.MySqlDataStoreProvider

trait MySqlIntegrationSpec {
  def mysqlConfig: Config

  lazy val dataStoreConfig: DataStoreConfig = pureconfig.loadConfigOrThrow[DataStoreConfig](mysqlConfig)

  lazy val provider = new MySqlDataStoreProvider()

  lazy val providerLoad: LoadedDataStore = provider.load(dataStoreConfig, new NoOpCrypto()).unsafeRunSync()
  lazy val instance: DataStore = providerLoad.dataStore

  lazy val batchChangeRepository: BatchChangeRepository =
    instance.get[BatchChangeRepository](RepositoryName.batchChange).get
  lazy val zoneRepository: ZoneRepository =
    instance.get[ZoneRepository](RepositoryName.zone).get
  lazy val zoneChangeRepository: ZoneChangeRepository =
    instance.get[ZoneChangeRepository](RepositoryName.zoneChange).get
  lazy val userRepository: UserRepository =
    instance.get[UserRepository](RepositoryName.user).get
  lazy val recordSetRepository: RecordSetRepository =
    instance.get[RecordSetRepository](RepositoryName.recordSet).get
  lazy val groupRepository: GroupRepository =
    instance.get[GroupRepository](RepositoryName.group).get
  lazy val recordChangeRepository: RecordChangeRepository =
    instance.get[RecordChangeRepository](RepositoryName.recordChange).get
  lazy val membershipRepository: MembershipRepository =
    instance.get[MembershipRepository](RepositoryName.membership).get
  lazy val groupChangeRepository: GroupChangeRepository =
    instance.get[GroupChangeRepository](RepositoryName.groupChange).get
  lazy val userChangeRepository: UserChangeRepository =
    instance.get[UserChangeRepository](RepositoryName.userChange).get
}

object TestMySqlInstance extends MySqlIntegrationSpec {
  def mysqlConfig: Config = ConfigFactory.load().getConfig("mysql")
}
