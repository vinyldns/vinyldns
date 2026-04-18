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

import vinyldns.core.domain.config.{AppConfigRepository, AppConfigResponse}
import cats.effect.IO
import org.slf4j.LoggerFactory
import scalikejdbc.{DB, DBSession, WrappedResultSet, scalikejdbcSQLInterpolationImplicitDef}
import vinyldns.core.task.TaskScheduler.monitor

class MySqlAppConfigRepository extends AppConfigRepository {

  private final val logger =
    LoggerFactory.getLogger(classOf[MySqlAppConfigRepository])

  private final val CREATE_APP_CONFIG =
    sql"""
      INSERT INTO app_config (config_key, config_value)
      VALUES ({key}, {value})
    """

  private final val UPDATE_APP_CONFIG =
    sql"""
      UPDATE app_config
      SET config_value = {value}, updated_at = NOW(3)
      WHERE config_key = {key}
    """

  private final val DELETE_APP_CONFIG =
    sql"DELETE FROM app_config WHERE config_key = {key}"

  private final val FETCH_APP_CONFIG =
    sql"""
      SELECT config_key, config_value, created_at, updated_at
      FROM app_config
      WHERE config_key = {key}
    """

  private final val GET_APP_CONFIG =
    sql"""
      SELECT config_key, config_value, created_at, updated_at
      FROM app_config
      ORDER BY config_key
    """

  private final val GET_COUNT_APP_CONFIG =
    sql"SELECT COUNT(*) FROM app_config WHERE config_key = {key}"

  private def fetchRow(key: String)(implicit session: DBSession): Option[AppConfigResponse] = {
    logger.debug(s"Fetching app_config for key=[$key]")
    FETCH_APP_CONFIG
      .bindByName('key -> key)
      .map(toResponse)
      .single
      .apply()
  }

  override def create(key: String, value: String): IO[AppConfigResponse] =
    monitor("repo.AppConfig.create") {
      IO {
        logger.debug(s"Creating app_config key=[$key]")
        DB.localTx { implicit session =>
          val exists =
            GET_COUNT_APP_CONFIG
              .bindByName('key -> key)
              .map(_.int(1))
              .single
              .apply()
              .getOrElse(0)

          if (exists > 0) {
            logger.debug(s"Create failed: key already exists [$key]")
            throw new IllegalArgumentException(
              s"Config key '$key' already exists. Use PUT to update."
            )
          }

          CREATE_APP_CONFIG
            .bindByName('key -> key, 'value -> value)
            .update
            .apply()

          fetchRow(key).getOrElse {
            logger.error(s"Create succeeded but fetch failed for key=[$key]")
            throw new RuntimeException(s"Failed to fetch after insert: $key")
          }
        }
      }
    }

  override def getByKey(key: String): IO[Option[AppConfigResponse]] =
    monitor("repo.AppConfig.getByKey") {
      IO {
        logger.debug(s"Getting app_config by key=[$key]")
        DB.readOnly { implicit session =>
          fetchRow(key)
        }
      }
    }

  override def getAll: IO[List[AppConfigResponse]] =
    monitor("repo.AppConfig.getAll") {
      IO {
        logger.debug("Fetching all app_config entries")
        DB.readOnly { implicit session =>
          GET_APP_CONFIG.map(toResponse).list.apply()
        }
      }
    }

  override def update(key: String, value: String): IO[Option[AppConfigResponse]] =
    monitor("repo.AppConfig.update") {
      IO {
        logger.debug(s"Updating app_config key=[$key]")
        DB.localTx { implicit session =>
          val rows =
            UPDATE_APP_CONFIG
              .bindByName('key -> key, 'value -> value)
              .update
              .apply()

          if (rows == 0) {
            logger.debug(s"Update skipped: key not found [$key]")
            None
          } else {
            fetchRow(key)
          }
        }
      }
    }

  override def delete(key: String): IO[Boolean] =
    monitor("repo.AppConfig.delete") {
      IO {
        logger.debug(s"Deleting app_config key=[$key]")
        DB.localTx { implicit session =>
          val rows =
            DELETE_APP_CONFIG
              .bindByName('key -> key)
              .update
              .apply()

          if (rows == 0)
            logger.debug(s"Delete skipped: key not found [$key]")

          rows > 0
        }
      }
    }

  private val toResponse: WrappedResultSet => AppConfigResponse = rs =>
    AppConfigResponse(
      key = rs.string("config_key"),
      value = rs.string("config_value"),
      createdAt = rs.timestamp("created_at").toInstant.toString,
      updatedAt = rs.timestamp("updated_at").toInstant.toString
    )
}