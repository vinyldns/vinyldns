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

import cats.effect.IO
import org.slf4j.LoggerFactory
import pureconfig.ConfigReader
import pureconfig.module.catseffect.loadConfigF
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}
import scalikejdbc.config.DBs
import vinyldns.core.queue.{MessageQueue, MessageQueueConfig, MessageQueueProvider}
import vinyldns.mysql.{HikariCloser, MySqlConnectionConfig, MySqlDataSourceSettings}
import vinyldns.mysql.MySqlConnector._

class MySqlMessageQueueProvider extends MessageQueueProvider {

  private val logger = LoggerFactory.getLogger(classOf[MySqlMessageQueueProvider])

  implicit val mySqlPropertiesReader: ConfigReader[Map[String, AnyRef]] =
    MySqlConnectionConfig.mySqlPropertiesReader

  def load(config: MessageQueueConfig): IO[MessageQueue] =
    for {
      connectionSettings <- loadConfigF[IO, MySqlConnectionConfig](config.settings)
      _ <- runDBMigrations(connectionSettings)
      _ <- setupQueueConnection(connectionSettings)
    } yield new MySqlMessageQueue(config.maxRetries)

  def setupQueueConnection(config: MySqlConnectionConfig): IO[Unit] = {
    val queueConnectionSettings = MySqlDataSourceSettings(config, "mysqlQueuePool")

    getDataSource(queueConnectionSettings).map { dataSource =>
      logger.error("configuring connection pool for queue")

      // note this is being called 2x in the case you use the mysql datastores and
      // loader. That should be ok
      DBs.loadGlobalSettings()

      // Configure the connection pool
      ConnectionPool.add(
        MySqlMessageQueue.QUEUE_CONNECTION_NAME,
        new DataSourceConnectionPool(dataSource, closer = new HikariCloser(dataSource))
      )

      logger.error("queue connection pool init complete")
    }
  }

}
