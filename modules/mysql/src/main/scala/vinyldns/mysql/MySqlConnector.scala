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

import cats.effect.IO
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}
import scalikejdbc.config.DBs

import scala.collection.JavaConverters._

object MySqlConnector {

  private val logger = LoggerFactory.getLogger("MySQLConnector")

  def runDBMigrations(settings: MySqlConnectionSettings): IO[Unit] =
    settings.migrationSettings match {
      case Some(migrationSettings) =>
        IO {
          lazy val migrationDataSource: DataSource = {
            val ds = new HikariDataSource()
            ds.setDriverClassName(settings.driver)
            ds.setJdbcUrl(migrationSettings.migrationUrl)
            ds.setUsername(settings.user)
            ds.setPassword(settings.password)
            // migrations happen once on startup; without these settings the default number of connections
            // will be created and maintained even though this datasource is no longer needed post-migration
            ds.setMaximumPoolSize(migrationSettings.poolMaxSize)
            ds.setMinimumIdle(0)
            ds
          }

          logger.info("Running migrations to ready the databases")

          val migration = new Flyway()
          migration.setDataSource(migrationDataSource)
          // flyway changed the default schema table name in v5.0.0
          // this allows to revert to an old naming convention if needed
          migrationSettings.migrationSchemaTable.foreach { tableName =>
            migration.setTable(tableName)
          }

          val placeholders = Map("dbName" -> settings.name)
          migration.setPlaceholders(placeholders.asJava)
          migration.setSchemas(settings.name)

          // Runs flyway migrations
          migration.migrate()
          logger.info("migrations complete")
        }
      case None => IO(logger.info("Migrations configured off"))
    }

  def setupDBConnection(settings: MySqlConnectionSettings): IO[Unit] = IO {
    val dataSource: DataSource = {
      val ds = new HikariDataSource()
      ds.setDriverClassName(settings.driver)
      ds.setJdbcUrl(settings.url)
      ds.setUsername(settings.user)
      ds.setPassword(settings.password)
      ds.setConnectionTimeout(settings.connectionTimeoutMillis)
      ds.setMaximumPoolSize(settings.poolMaxSize)
      ds.setMaxLifetime(settings.maxLifeTime)
      ds.setRegisterMbeans(true)
      ds
    }

    logger.info("configuring connection pool")

    // Configure the connection pool
    ConnectionPool.singleton(new DataSourceConnectionPool(dataSource))

    logger.info("setting up databases")

    // Sets up all databases with scalikejdbc
    DBs.setupAll()

    logger.info("database init complete")
  }
}
