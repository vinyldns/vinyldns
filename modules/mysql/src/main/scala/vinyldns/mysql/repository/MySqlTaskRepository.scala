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
import scalikejdbc._
import vinyldns.core.task.TaskRepository

import scala.concurrent.duration.FiniteDuration

class MySqlTaskRepository extends TaskRepository {
  /*
   * Transaction that performs the following steps:
   * - Acquires an exclusive row lock for unclaimed and/or expired tasks
   * - Updates in_flight flag, marking that a task is claimed
   * - Commits transaction, releasing row lock
   *  Claims the task if in_flight=0 (which means that no one else is holding the task)
   *  - OR -
   *  Claims the task if the last time it was updated is older than the task timeout in seconds
   *  (which assumes for example the node running the task died)
   *  - OR -
   *  Claims the task if the last time it was updated is older than the polling interval AND in_flight=0 or expired
   */
  private val CLAIM_UNCLAIMED_TASK =
    sql"""
      |UPDATE task
      |   SET in_flight = 1, updated = NOW()
      | WHERE (in_flight = 0 OR updated < DATE_SUB(NOW(),INTERVAL {timeoutSeconds} SECOND))
      |   AND (updated IS NULL OR updated < DATE_SUB(NOW(),INTERVAL {pollingInterval} SECOND))
      |   AND name = {taskName};
      """.stripMargin

  private val UNCLAIM_TASK =
    sql"""
      |UPDATE task
      |   SET in_flight = 0, updated = NOW()
      | WHERE name = {taskName}
      """.stripMargin

  // In case multiple nodes attempt to insert task at the same time, do not overwrite
  private val PUT_TASK =
    sql"""
         |INSERT IGNORE INTO task(name, in_flight, created, updated)
         |VALUES ({taskName}, 0, NOW(), NULL)
      """.stripMargin

  /**
    * Note - the column in MySQL is datetime with no fractions, so the best we can do is seconds
    * If taskTimeout is less than one second, this will never claim as
    * FiniteDuration.toSeconds results in ZERO OL for something like 500.millis
    * Enforces the invariant that pollingInterval < taskTimeout.  If pollingInterval > taskTimeout, the
    * taskTimeout will be used as the polling interval
    */
  def claimTask(
      name: String,
      taskTimeout: FiniteDuration,
      pollingInterval: FiniteDuration): IO[Boolean] =
    IO {
      DB.localTx { implicit s =>
        val adjustedPollingInterval =
          if (pollingInterval > taskTimeout) taskTimeout else pollingInterval
        val updateResult = CLAIM_UNCLAIMED_TASK
          .bindByName(
            'timeoutSeconds -> taskTimeout.toSeconds,
            'pollingInterval -> adjustedPollingInterval.toSeconds,
            'taskName -> name)
          .first()
          .update()
          .apply()

        updateResult == 1
      }
    }

  def releaseTask(name: String): IO[Unit] = IO {
    DB.localTx { implicit s =>
      UNCLAIM_TASK.bindByName('taskName -> name).update().apply()
    }
  }

  // Save the task, do not overwrite if it is already there
  def saveTask(name: String): IO[Unit] = IO {
    DB.localTx { implicit s =>
      PUT_TASK.bindByName('taskName -> name).update().apply()
      ()
    }
  }
}
