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

import cats.effect.IO
import org.joda.time.DateTime
import scalikejdbc._
import vinyldns.core.task.TaskRepository

import scala.concurrent.duration.FiniteDuration

class MySqlTaskRepository extends TaskRepository {
  /*
   * Transaction that performs the following steps:
   * - Acquires an exclusive row lock for unclaimed and/or expired tasks
   * - Updates in_flight flag, marking that a task is claimed
   * - Commits transaction, releasing row lock
   *
   * `updated IS NULL` case is for the first run where the seeded data does not have an updated time set
   */

  private val START_TRANSACTION =
    sql"""
       |START TRANSACTION;
       """.stripMargin

  private val FETCH_UNCLAIMED_TASK =
    sql"""
       |SELECT *
       |  FROM task
       | WHERE (in_flight = 0
       |    OR updated IS NULL
       |    OR updated < {updatedTimeComparison})
       |   AND name = {taskName} FOR UPDATE;
       """.stripMargin

  private val CLAIM_UNCLAIMED_TASK =
    sql"""
       |UPDATE task
       |   SET in_flight = 1, updated = {currentTime}
       | WHERE (in_flight = 0
       |    OR updated IS NULL
       |    OR updated < {updatedTimeComparison})
       |   AND name = {taskName};
       """.stripMargin

  private val COMMIT =
    sql"""
       |COMMIT;
       """.stripMargin

  private val UNCLAIM_TASK =
    sql"""
         |UPDATE task
         |   SET in_flight = 0, updated = {currentTime}
         | WHERE name = {name}
    """.stripMargin

  def fetchAndClaimTask(name: String, pollingInterval: FiniteDuration): IO[Int] =
    IO {
      val pollingExpirationHours = pollingInterval.toHours * 2
      val currentTime = DateTime.now
      DB.localTx { implicit s =>
        START_TRANSACTION.execute()

        FETCH_UNCLAIMED_TASK
          .bindByName(
            'updatedTimeComparison -> currentTime.minusHours(pollingExpirationHours.toInt),
            'taskName -> name)
          .map(_.string(1))
          .first()
          .apply()

        sql"""SELECT SLEEP(10)""".execute()

        val updateResult = CLAIM_UNCLAIMED_TASK
          .bindByName(
            'updatedTimeComparison -> currentTime.minusHours(pollingExpirationHours.toInt),
            'taskName -> name,
            'currentTime -> currentTime)
          .first()
          .update()
          .apply()

        COMMIT.execute()

        updateResult
      }
    }

  def releaseTask(name: String): IO[Unit] = IO {
    DB.localTx { implicit s =>
      UNCLAIM_TASK.bindByName('currentTime -> DateTime.now, 'name -> name).update().apply()
    }
  }
}
