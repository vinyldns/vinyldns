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

package vinyldns.mysql.queue

import java.util.UUID

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Suite}
import pureconfig._
import pureconfig.generic.auto._
import scalikejdbc._
import vinyldns.core.queue.TestQueueSigning.signing
import vinyldns.mysql.{MySqlConnectionConfig, MySqlConnector}
import vinyldns.mysql.repository.MySqlCommandExecutionRepository

trait IsolatedQueueDatabase extends BeforeAndAfterAll { self: Suite =>
  implicit val propertiesReader: ConfigReader[Map[String, AnyRef]] =
    MySqlConnectionConfig.mySqlPropertiesReader
  protected val databaseName
      : String = "vinyldns_queue_it_" + UUID.randomUUID().toString.replace("-", "")
  protected val poolName: Symbol = Symbol(databaseName)
  protected val queue = new MySqlMessageQueue(100, signing, poolName)
  protected val executions = new MySqlCommandExecutionRepository(poolName)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val endpoint = sys.env.getOrElse("MYSQL_ENDPOINT", "localhost:19002")
    val config = ConfigFactory.parseString(s"""
      name = "$databaseName"
      driver = "org.mariadb.jdbc.Driver"
      migration-url = "jdbc:mariadb://$endpoint/?useMysqlMetadata=true"
      url = "jdbc:mariadb://$endpoint/$databaseName?useMysqlMetadata=true"
      user = "root"
      password = "pass"
      flyway-out-of-order = false
      maximum-pool-size = 5
      minimum-idle = 0
      connection-timeout-millis = 1000
    """)
    val settings = ConfigSource.fromConfig(config).loadOrThrow[MySqlConnectionConfig]
    MySqlConnector.runDBMigrations(settings).unsafeRunSync()
    new MySqlMessageQueueProvider().setupQueueConnection(settings, poolName).unsafeRunSync()
  }

  override protected def afterAll(): Unit =
    try {
      if (ConnectionPool.isInitialized(poolName)) {
        try {
          NamedDB(poolName).autoCommit { implicit session =>
            SQL(s"DROP DATABASE `$databaseName`").execute().apply()
          }
        } finally ConnectionPool.close(poolName)
      }
    } finally super.afterAll()
}
