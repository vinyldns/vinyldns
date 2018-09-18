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

package vinyldns.mysql.repository

import com.typesafe.config.{Config, ConfigFactory}
import vinyldns.core.domain.batch.BatchChangeRepository
import vinyldns.core.domain.zone.{ZoneChangeRepository, ZoneRepository}
import vinyldns.core.crypto.NoOpCrypto
import vinyldns.core.repository.{DataStore, DataStoreConfig, RepositoryName}

object TestMySqlInstance {
  lazy val mysqlConfig: Config = ConfigFactory.load()

  val dataStoreConfig: DataStoreConfig = pureconfig.loadConfigOrThrow[DataStoreConfig](mysqlConfig, "mysql")

  lazy val instance: DataStore =
    new MySqlDataStoreProvider().load(dataStoreConfig, new NoOpCrypto()).unsafeRunSync()

  lazy val batchChangeRepository: BatchChangeRepository =
    instance.get[BatchChangeRepository](RepositoryName.batchChange).get
  lazy val zoneRepository: ZoneRepository =
    instance.get[ZoneRepository](RepositoryName.zone).get
  lazy val zoneChangeRepository: ZoneChangeRepository =
    instance.get[ZoneChangeRepository](RepositoryName.zoneChange).get
}
