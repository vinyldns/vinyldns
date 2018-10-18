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
import cats.syntax.all._
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import pureconfig.module.catseffect.loadConfigF

import scala.collection.JavaConverters._
import scalikejdbc.config.DBs
import scalikejdbc.{ConnectionPool, DataSourceCloser, DataSourceConnectionPool}
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.repository._

class MySqlDataStoreProvider extends DataStoreProvider {

  private val logger = LoggerFactory.getLogger("MySqlDataStoreProvider")
  private val implementedRepositories =
    Set(RepositoryName.zone, RepositoryName.batchChange, RepositoryName.zoneChange)

  def load(config: DataStoreConfig, cryptoAlgebra: CryptoAlgebra): IO[DataStore] =
    for {
      settingsConfig <- loadConfigF[IO, MySqlDataStoreSettings](config.settings)
      _ <- validateRepos(config.repositories)
      _ <- runDBMigrations(settingsConfig)
      _ <- setupDBConnection(settingsConfig)
      store <- initializeRepos()
    } yield store

  def validateRepos(reposConfig: RepositoriesConfig): IO[Unit] = {
    val invalid = reposConfig.keys.diff(implementedRepositories)

    if (invalid.isEmpty) {
      IO.unit
    } else {
      val error = s"Invalid config provided to mysql; unimplemented repos included: $invalid"
      IO.raiseError(DataStoreStartupError(error))
    }
  }

  def initializeRepos(): IO[DataStore] = IO {
    val zones = Some(new MySqlZoneRepository())
    val batchChanges = Some(new MySqlBatchChangeRepository())
    val zoneChanges = Some(new MySqlZoneChangeRepository())
    DataStore(
      zoneRepository = zones,
      batchChangeRepository = batchChanges,
      zoneChangeRepository = zoneChanges)
  }

  def runDBMigrations(settings: MySqlDataStoreSettings): IO[Unit] = IO {
    // Migration needs to happen on the base URL, not the table URL, thus the separate source
    lazy val migrationDataSource: DataSource = {
      val ds = new HikariDataSource()
      ds.setDriverClassName(settings.driver)
      ds.setJdbcUrl(settings.migrationUrl)
      ds.setUsername(settings.user)
      ds.setPassword(settings.password)
      // migrations happen once on startup; without these settings the default number of connections
      // will be created and maintained even though this datasource is no longer needed post-migration
      ds.setMaximumPoolSize(3)
      ds.setMinimumIdle(0)
      ds.setPoolName("flywayPool")
      ds
    }

    logger.info("Running migrations to ready the databases")

    val migration = new Flyway()
    migration.setDataSource(migrationDataSource)
    // flyway changed the default schema table name in v5.0.0
    // this allows to revert to an old naming convention if needed
    settings.migrationSchemaTable.foreach { tableName =>
      migration.setTable(tableName)
    }

    val placeholders = Map("dbName" -> settings.name)
    migration.setPlaceholders(placeholders.asJava)
    migration.setSchemas(settings.name)

    // Runs flyway migrations
    migration.migrate()
    logger.info("migrations complete")
  }

  def setupDBConnection(settings: MySqlDataStoreSettings): IO[Unit] = IO {
    val dataSource: HikariDataSource = {
      val ds = new HikariDataSource()
      ds.setDriverClassName(settings.driver)
      ds.setJdbcUrl(settings.url)
      ds.setUsername(settings.user)
      ds.setPassword(settings.password)
      ds.setConnectionTimeout(settings.connectionTimeoutMillis)
      ds.setMaximumPoolSize(settings.poolMaxSize)
      ds.setMaxLifetime(settings.maxLifeTime)
      ds.setRegisterMbeans(true)
      ds.setPoolName("mysqldbPool")
      ds
    }

    logger.info("configuring connection pool")

    // Configure the connection pool
    ConnectionPool.singleton(
      new DataSourceConnectionPool(dataSource, closer = new HikariCloser(dataSource)))

    logger.info("setting up databases")

    // Sets up all databases with scalikejdbc
    DBs.setupAll()

    logger.info("database init complete")
  }

  def shutdown(): IO[Unit] =
    IO(DBs.closeAll())
      .handleError(e => logger.error(s"exception=${e.getMessage} occurred while shutting down"))

  class HikariCloser(dataSource: HikariDataSource) extends DataSourceCloser {
    override def close(): Unit = dataSource.close()
  }
}
