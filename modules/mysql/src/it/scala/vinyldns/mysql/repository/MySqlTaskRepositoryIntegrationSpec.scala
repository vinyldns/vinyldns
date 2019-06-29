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

import java.time.Instant

import cats.effect.IO
import org.scalatest._
import scalikejdbc.{DB, _}
import vinyldns.mysql.TestMySqlInstance

import scala.concurrent.duration._

class MySqlTaskRepositoryIntegrationSpec extends WordSpec with BeforeAndAfterAll with BeforeAndAfterEach with Matchers {
  private val repo = TestMySqlInstance.taskRepository.asInstanceOf[MySqlTaskRepository]
  private val TASK_NAME = "task_name"

  case class TaskInfo(inFlight: Boolean, updated: Option[Instant])

  override protected def beforeEach(): Unit = clear().unsafeRunSync()

  override protected def afterAll(): Unit = clear().unsafeRunSync()

  def clear(): IO[Unit] = IO {
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM task")
    }
  }

  def ageTaskBySeconds(seconds: Long): IO[Int] = IO {
    DB.localTx { implicit s =>
      sql"UPDATE task SET updated = DATE_SUB(NOW(),INTERVAL {ageSeconds} SECOND)"
        .bindByName('ageSeconds -> seconds)
        .update()
        .apply()
    }
  }

  def getTaskInfo(name: String): IO[TaskInfo] = IO {
    DB.readOnly { implicit s =>
      sql"SELECT in_flight, updated from task WHERE name = {taskName}"
        .bindByName('taskName -> name)
        .map(rs => TaskInfo(rs.boolean(1), rs.timestampOpt(2).map(_.toInstant)))
        .first()
        .apply().getOrElse(throw new RuntimeException(s"TASK $name NOT FOUND"))
    }
  }

  "claimTask" should {
    "return true if non-in-flight task exists task is new" in {
      val f = for {
        _ <- repo.saveTask(TASK_NAME)
        unclaimedTaskExists <- repo.claimTask(TASK_NAME, 1.hour)
      } yield unclaimedTaskExists

      f.unsafeRunSync() shouldBe true
    }
    "return true if non-in-flight task exists and expiration time has elapsed" in {
      val f = for {
        _ <- repo.saveTask(TASK_NAME)
        _ <- repo.claimTask(TASK_NAME, 1.hour)
        _ <- ageTaskBySeconds(100) // Age the task by 100 seconds
        unclaimedTaskExists <- repo.claimTask(TASK_NAME, 1.second)
      } yield unclaimedTaskExists

      f.unsafeRunSync() shouldBe true
    }
    "return false if in-flight task exists and expiration time has not elapsed" in {
      val f = for {
        _ <- repo.saveTask(TASK_NAME)
        _ <- repo.claimTask(TASK_NAME, 1.hour)
        _ <- ageTaskBySeconds(5) // Age the task by only 5 seconds
        unclaimedTaskExists <- repo.claimTask(TASK_NAME, 1.hour)
      } yield unclaimedTaskExists

      f.unsafeRunSync() shouldBe false
    }
    "return false if task does not exist" in {
      val f = for {
        unclaimedTaskExists <- repo.claimTask(TASK_NAME, 1.hour)
      } yield unclaimedTaskExists

      f.unsafeRunSync() shouldBe false
    }
  }

  "release task" should {
    "unset in-flight flag for task and update time" in {
      val f = for {
        _ <- repo.saveTask(TASK_NAME)
        _ <- repo.claimTask(TASK_NAME, 1.hour)
        _ <- ageTaskBySeconds(2)
        oldTaskInfo <- getTaskInfo(TASK_NAME)
        _ <- repo.releaseTask(TASK_NAME)
        newTaskInfo <- getTaskInfo(TASK_NAME)
      } yield (oldTaskInfo, newTaskInfo)

      val (oldTaskInfo, newTaskInfo) = f.unsafeRunSync()

      // make sure the in_flight is unset
      newTaskInfo.inFlight shouldBe false

      // make sure that the updated time is later than the claimed time
      oldTaskInfo.updated shouldBe defined
      newTaskInfo.updated shouldBe defined
      oldTaskInfo.updated.zip(newTaskInfo.updated).foreach {
        case (claimTime, releaseTime) =>
        releaseTime should be > claimTime
      }
    }
  }

  "save task" should {
    "insert a new task" in {
      val f = for {
        _ <- repo.saveTask(TASK_NAME)
        taskInfo <- getTaskInfo(TASK_NAME)
      } yield taskInfo

      val taskInfo = f.unsafeRunSync()
      taskInfo.inFlight shouldBe false
      taskInfo.updated shouldBe empty
    }

    "not replace a task that is already present" in {
      // schedule a task and claim it, then try to reschedule it while it is claimed (bad)
      // the result should be that the task is still claimed / in_flight
      val f = for {
        _ <- repo.saveTask("repeat")
        _ <- repo.claimTask("repeat", 5.seconds)
        firstTaskInfo <- getTaskInfo("repeat")
        _ <- repo.saveTask("repeat")
        secondTaskInfo <- getTaskInfo("repeat")
        _ <- repo.releaseTask("repeat")
      } yield (firstTaskInfo, secondTaskInfo)

      val (first, second) = f.unsafeRunSync()
      first shouldBe second
    }
  }
}
