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
  lazy val mysqlConfig: Config = ConfigFactory.parseString(s"""
    |  mysql {
    |    class-name = "vinyldns.mysql.repository.MySqlDataStoreProvider"
    |
    |    settings {
    |      # JDBC Settings, these are all values in scalikejdbc-config, not our own
    |      # these must be overridden to use MYSQL for production use
    |      # assumes a docker or mysql instance running locally
    |      name = "vinyldns"
    |      driver = "org.mariadb.jdbc.Driver"
    |      migration-url = "jdbc:mariadb://localhost:19002/?user=root&password=pass"
    |      url = "jdbc:mariadb://localhost:19002/vinyldns?user=root&password=pass"
    |      user = "root"
    |      password = "pass"
    |      pool-initial-size = 10
    |      pool-max-size = 20
    |      connection-timeout-millis = 1000
    |      max-life-time = 600000
    |    }
    |
    |    repositories {
    |      # override with any repos that are running in mysql
    |    }
    |  }
     """.stripMargin)

  val dataStoreConfig :DataStoreConfig = pureconfig.loadConfigOrThrow[DataStoreConfig](mysqlConfig, "mysql")

  lazy val instance: DataStore =
    new MySqlDataStoreProvider().load(dataStoreConfig, new NoOpCrypto()).unsafeRunSync()

  lazy val batchChangeRepository: BatchChangeRepository =
  instance.get[BatchChangeRepository](RepositoryName.batchChange).get
  lazy val zoneRepository: ZoneRepository =
    instance.get[ZoneRepository](RepositoryName.zone).get
  lazy val zoneChangeRepository: ZoneChangeRepository =
  instance.get[ZoneChangeRepository](RepositoryName.zoneChange).get
}
