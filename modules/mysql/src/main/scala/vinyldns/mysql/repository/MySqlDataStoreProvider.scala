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

import cats.effect._
import cats.implicits._
import org.slf4j.LoggerFactory
import pureconfig.ConfigReader
import pureconfig.module.catseffect.loadConfigF
import scalikejdbc.config.DBs
import scalikejdbc._
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.repository._
import vinyldns.core.health.HealthCheck._
import vinyldns.mysql.{HikariCloser, MySqlConnectionConfig, MySqlDataSourceSettings}
import vinyldns.mysql.MySqlConnector._

class MySqlDataStoreProvider extends DataStoreProvider {

  private val logger = LoggerFactory.getLogger(classOf[MySqlDataStoreProvider])

  implicit val mySqlPropertiesReader: ConfigReader[Map[String, AnyRef]] =
    MySqlConnectionConfig.mySqlPropertiesReader

  def load(config: DataStoreConfig, cryptoAlgebra: CryptoAlgebra): IO[LoadedDataStore] =
    for {
      settingsConfig <- loadConfigF[IO, MySqlConnectionConfig](config.settings)
      _ <- runDBMigrations(settingsConfig)
      _ <- setupDBConnection(settingsConfig)
      store <- initializeRepos(cryptoAlgebra)
    } yield new LoadedDataStore(store, shutdown(), checkHealth())

  def initializeRepos(cryptoAlgebra: CryptoAlgebra): IO[DataStore] = IO {
    val zones = Some(new MySqlZoneRepository())
    val batchChanges = Some(new MySqlBatchChangeRepository())
    val zoneChanges = Some(new MySqlZoneChangeRepository())
    val users = Some(new MySqlUserRepository(cryptoAlgebra))
    val recordSets = Some(new MySqlRecordSetRepository())
    val groups = Some(new MySqlGroupRepository())
    val recordChanges = Some(new MySqlRecordChangeRepository())
    val membership = Some(new MySqlMembershipRepository())
    val groupChanges = Some(new MySqlGroupChangeRepository())
    val userChanges = Some(new MySqlUserChangeRepository())
    DataStore(
      zoneRepository = zones,
      batchChangeRepository = batchChanges,
      zoneChangeRepository = zoneChanges,
      userRepository = users,
      recordSetRepository = recordSets,
      groupRepository = groups,
      recordChangeRepository = recordChanges,
      membershipRepository = membership,
      groupChangeRepository = groupChanges,
      userChangeRepository = userChanges
    )
  }

  def setupDBConnection(config: MySqlConnectionConfig): IO[Unit] = {
    val dbConnectionSettings = MySqlDataSourceSettings(config, "mysqlDbPool")

    getDataSource(dbConnectionSettings).map { dataSource =>
      logger.error("configuring connection pool")

      // pulled out of DBs.setupAll since we're no longer using the db. structure for config
      DBs.loadGlobalSettings()

      // Configure the connection pool
      ConnectionPool.singleton(
        new DataSourceConnectionPool(dataSource, closer = new HikariCloser(dataSource)))

      logger.error("database init complete")
    }
  }

  private def shutdown(): IO[Unit] =
    IO(DBs.close())
      .handleError(e => logger.error(s"exception occurred while shutting down", e))

  private final val HEALTH_CHECK =
    sql"""
         |SELECT 1
         |  FROM DUAL
      """.stripMargin

  private def checkHealth(): HealthCheck =
    IO {
      DB.readOnly { implicit s =>
        HEALTH_CHECK.map(_ => ()).first.apply()
      }
    }.attempt.asHealthCheck
}
