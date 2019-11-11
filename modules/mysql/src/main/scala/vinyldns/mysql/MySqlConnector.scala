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
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

object MySqlConnector {

  private val logger = LoggerFactory.getLogger("MySqlConnector")

  def runDBMigrations(config: MySqlConnectionConfig): IO[Unit] = {
    val migrationConnectionSettings = MySqlDataSourceSettings(
      "flywayConnectionPool",
      config.driver,
      config.migrationUrl,
      config.user,
      config.password,
      minimumIdle = Some(3)
    )

    getDataSource(migrationConnectionSettings).map { migrationDataSource =>
      logger.info("Running migrations to ready the databases")

      val migration = new Flyway()
      migration.setDataSource(migrationDataSource)
      // flyway changed the default schema table name in v5.0.0
      // this allows to revert to an old naming convention if needed
      config.migrationSchemaTable.foreach { tableName =>
        migration.setTable(tableName)
      }

      val placeholders = Map("dbName" -> config.name)
      migration.setPlaceholders(placeholders.asJava)
      migration.setSchemas(config.name)

      // Runs flyway migrations
      migration.migrate()
      logger.info("migrations complete")
    }
  }

  def getDataSource(settings: MySqlDataSourceSettings): IO[HikariDataSource] = IO {

    logger.error(s"Initializing data source with settings: $settings")

    val dsConfig = new HikariConfig()

    dsConfig.setPoolName(settings.poolName)
    dsConfig.setDriverClassName(settings.driver)
    dsConfig.setJdbcUrl(settings.url)
    dsConfig.setUsername(settings.user)
    dsConfig.setPassword(settings.password)

    settings.connectionTimeoutMillis.foreach(dsConfig.setConnectionTimeout)
    settings.idleTimeout.foreach(dsConfig.setIdleTimeout)
    settings.maximumPoolSize.foreach(dsConfig.setMaximumPoolSize)
    settings.maxLifetime.foreach(dsConfig.setMaxLifetime)
    settings.minimumIdle.foreach(dsConfig.setMinimumIdle)
    dsConfig.setRegisterMbeans(settings.registerMbeans)

    settings.mySqlProperties.foreach {
      case (k, v) => dsConfig.addDataSourceProperty(k, v)
    }

    new HikariDataSource(dsConfig)
  }
}
