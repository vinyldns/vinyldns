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
import org.scalatest._
import scalikejdbc.DB
import vinyldns.mysql.TestMySqlInstance

import scala.concurrent.duration._
import scalikejdbc._

class MySqlTaskRepositoryIntegrationSpec extends WordSpec with BeforeAndAfterAll with BeforeAndAfterEach with Matchers {
  private val repo = TestMySqlInstance.taskRepository.asInstanceOf[MySqlTaskRepository]
  private val TASK_NAME = "task_name"
  private val INSERT_STATEMENT =
  sql"""
     |INSERT INTO task (name, in_flight, created, updated)
     |     VALUES ({task_name}, {in_flight}, {created}, {updated})
  """.stripMargin

  private val startDateTime = DateTime.now

  override protected def beforeEach(): Unit = clear().unsafeRunSync()

  override protected def afterAll(): Unit = clear().unsafeRunSync()

  def clear(): IO[Unit] = IO {
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM task")
    }
  }

  def insertTask(inFlight: Int, created: DateTime, updated: DateTime): IO[Unit] = IO {
    DB.localTx { implicit s =>
      INSERT_STATEMENT
        .bindByName('task_name -> TASK_NAME,
          'in_flight -> inFlight,
          'created -> created,
          'updated -> updated
        )
        .update()
        .apply()
    }
  }

  def getTaskInfo: IO[Option[(Boolean, DateTime)]] = IO {
    DB.readOnly { implicit s =>
      sql"SELECT in_flight, updated from task FOR UPDATE"
        .map(rs => (rs.boolean(1), new DateTime(rs.timestamp(2))))
        .first()
        .apply()
    }
  }

  "claimTask" should {
    "return true if non-in-flight task exists and updated time is null" in {
      val f = for {
        _ <- insertTask(0, startDateTime, startDateTime)
        unclaimedTaskExists <- repo.claimTask(TASK_NAME, 1.hour)
      } yield unclaimedTaskExists

      f.unsafeRunSync() shouldBe true
    }
    "return true if non-in-flight task exists and expiration time has elapsed" in {
      val f = for {
        _ <- insertTask(0, startDateTime, startDateTime.minusHours(2))
        unclaimedTaskExists <- repo.claimTask(TASK_NAME, 1.hour)
      } yield unclaimedTaskExists

      f.unsafeRunSync() shouldBe true
    }
    "return false if in-flight task exists and expiration time has not elapsed" in {
      val f = for {
        _ <- insertTask(1, startDateTime, startDateTime)
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
        _ <- insertTask(1, startDateTime, startDateTime)
        _ <- repo.releaseTask(TASK_NAME)
        taskInfo <- getTaskInfo
      } yield taskInfo

      f.unsafeRunSync().foreach { tuple =>
        val (inFlight, updateTime) = tuple
        inFlight shouldBe false
        updateTime should not be startDateTime
      }
    }
  }
}
