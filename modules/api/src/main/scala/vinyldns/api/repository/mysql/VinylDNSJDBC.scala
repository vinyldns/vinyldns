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

package vinyldns.api.repository.mysql

import com.typesafe.config.Config
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._
import scalikejdbc.config.DBs
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}

/* Loads and initializes the MySQL database.  Unsafe, will fail if there are any issues and the app won't start */
class VinylDNSJDBC(config: Config) {

  private val logger = LoggerFactory.getLogger("VinylDNSJDBC")

  val dataSource: DataSource = {
    val ds = new HikariDataSource()
    ds.setDriverClassName(config.getString("default.driver"))
    ds.setJdbcUrl(config.getString("default.url"))
    ds.setUsername(config.getString("default.user"))
    ds.setPassword(config.getString("default.password"))
    ds.setConnectionTimeout(config.getLong("default.connectionTimeoutMillis"))
    ds.setMaximumPoolSize(config.getInt("default.poolMaxSize"))
    ds.setMaxLifetime(config.getLong("default.maxLifeTime"))
    ds.setRegisterMbeans(true)
    ds
  }

  // Migration needs to happen on the base URL, not the table URL, thus the separate source
  lazy val migrationDataSource: DataSource = {
    val ds = new HikariDataSource()
    ds.setDriverClassName(config.getString("default.driver"))
    ds.setJdbcUrl(config.getString("default.migrationUrl"))
    ds.setUsername(config.getString("default.user"))
    ds.setPassword(config.getString("default.password"))
    // migrations happen once on startup; without these settings the default number of connections
    // will be created and maintained even though this datasource is no longer needed post-migration
    ds.setMaximumPoolSize(3)
    ds.setMinimumIdle(0)
    ds
  }

  logger.info("Running migrations to ready the databases")

  val migration = new Flyway()
  migration.setDataSource(migrationDataSource)
  // flyway changed the default schema table name in v5.0.0; this allows to revert to an old naming convention if needed
  if (config.hasPath("default.migrationSchemaTable")) {
    migration.setTable(config.getString("default.migrationSchemaTable"))
  }

  val dbName = config.getString("name")
  val placeholders = Map("dbName" -> dbName)
  migration.setPlaceholders(placeholders.asJava)
  migration.setSchemas(dbName)

  // Runs flyway migrations
  migration.migrate()
  logger.info("migrations complete")

  logger.info("configuring connection pool")

  // Configure the connection pool
  ConnectionPool.singleton(new DataSourceConnectionPool(dataSource))

  logger.info("setting up databases")

  // Sets up all databases with scalikejdbc
  DBs.setupAll()

  logger.info("database init complete")

  val zoneRepository: JdbcZoneRepository = new JdbcZoneRepository()
  val batchChangeRepository: JdbcBatchChangeRepository = new JdbcBatchChangeRepository()
}
