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

package vinyldns.dynamodb.repository

import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest
import com.typesafe.config.ConfigFactory
import vinyldns.core.crypto.NoOpCrypto
import vinyldns.core.domain.membership.{LockStatus, User}

import scala.concurrent.duration._

class DynamoDBUserRepositoryIntegrationSpec extends DynamoDBIntegrationSpec {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)
  private val userTable = "users-live"

  private val tableConfig = DynamoDBRepositorySettings(s"$userTable", 30, 30)

  private var repo: DynamoDBUserRepository = _

  private val testUserIds = (for { i <- 0 to 100 } yield s"test-user-$i").toList.sorted
  private val users = testUserIds.map { id =>
    User(id = id, userName = "name" + id, accessKey = s"abc$id", secretKey = "123")
  }

  def setup(): Unit = {
    repo = DynamoDBUserRepository(
      tableConfig,
      dynamoIntegrationConfig,
      new NoOpCrypto(ConfigFactory.load())
    ).unsafeRunSync()

    // Create all the items
    val results = users.map(repo.save(_)).parSequence

    // Wait until all of the data is stored
    results.unsafeRunTimed(5.minutes).getOrElse(fail("timeout waiting for data load"))
  }

  def tearDown(): Unit = {
    val request = new DeleteTableRequest().withTableName(userTable)
    repo.dynamoDBHelper.deleteTable(request).unsafeRunSync()
  }

  "DynamoDBUserRepository" should {
    "retrieve a user" in {
      val f = repo.getUser(testUserIds.head)
      f.unsafeRunSync() shouldBe Some(users.head)
    }
    "returns None when the user does not exist" in {
      val f = repo.getUser("does not exists")
      f.unsafeRunSync() shouldBe None
    }
    "getUsers omits all non existing users" in {
      val getUsers = repo.getUsers(Set("notFound", testUserIds.head), None, Some(100))

      val result = getUsers.unsafeRunSync()
      result.users.map(_.id) should contain theSameElementsAs Set(testUserIds.head)
      result.users.map(_.id) should not contain "notFound"
    }
    "returns all the users" in {
      val f = repo.getUsers(testUserIds.toSet, None, None)

      val retrieved = f.unsafeRunSync()
      retrieved.users should contain theSameElementsAs users
      retrieved.lastEvaluatedId shouldBe None
    }
    "only return requested users" in {
      val evenUsers = users.filter(_.id.takeRight(1).toInt % 2 == 0)
      val f = repo.getUsers(evenUsers.map(_.id).toSet, None, None)

      val retrieved = f.unsafeRunSync()
      retrieved.users should contain theSameElementsAs evenUsers
      retrieved.lastEvaluatedId shouldBe None
    }
    "start at the exclusive start key" in {
      val f = repo.getUsers(testUserIds.toSet, Some(testUserIds(5)), None)

      val retrieved = f.unsafeRunSync()
      retrieved.users should not contain users(5) //start key is exclusive
      retrieved.users should contain theSameElementsAs users.slice(6, users.length)
      retrieved.lastEvaluatedId shouldBe None
    }
    "only return the number of items equal to the limit" in {
      val f = repo.getUsers(testUserIds.toSet, None, Some(5))

      val retrieved = f.unsafeRunSync()
      retrieved.users.size shouldBe 5
      retrieved.users should contain theSameElementsAs users.take(5)
    }
    "returns the correct lastEvaluatedKey" in {
      val f = repo.getUsers(testUserIds.toSet, None, Some(5))

      val retrieved = f.unsafeRunSync()
      retrieved.lastEvaluatedId shouldBe Some(users(4).id) // base 0
      retrieved.users should contain theSameElementsAs users.take(5)
    }
    "return the user if the matching access key" in {
      val f = repo.getUserByAccessKey(users.head.accessKey)

      f.unsafeRunSync() shouldBe Some(users.head)
    }
    "returns None not user has a matching access key" in {
      val f = repo.getUserByAccessKey("does not exists")

      f.unsafeRunSync() shouldBe None
    }
    "returns the super user flag when true" in {
      val testUser = User(
        userName = "testSuper",
        accessKey = "testSuper",
        secretKey = "testUser",
        isSuper = true
      )

      val saved = repo.save(testUser).unsafeRunSync()
      val result = repo.getUser(saved.id).unsafeRunSync()
      result shouldBe Some(testUser)
      result.get.isSuper shouldBe true
    }
    "returns the super user flag when false" in {
      val testUser = User(userName = "testSuper", accessKey = "testSuper", secretKey = "testUser")

      val saved = repo.save(testUser).unsafeRunSync()
      val result = repo.getUser(saved.id).unsafeRunSync()
      result shouldBe Some(testUser)
      result.get.isSuper shouldBe false
    }
    "returns the locked flag when true" in {
      val testUser = User(
        userName = "testSuper",
        accessKey = "testSuper",
        secretKey = "testUser",
        lockStatus = LockStatus.Locked
      )

      val saved = repo.save(testUser).unsafeRunSync()
      val result = repo.getUser(saved.id).unsafeRunSync()

      result shouldBe Some(testUser)
      result.get.lockStatus shouldBe LockStatus.Locked
    }
    "returns the locked flag when false" in {
      val f = repo.getUserByAccessKey(users.head.accessKey).unsafeRunSync()

      f shouldBe Some(users.head)
      f.get.lockStatus shouldBe LockStatus.Unlocked
    }
    "returns the support flag when true" in {
      val testUser = User(
        userName = "testSuper",
        accessKey = "testSuper",
        secretKey = "testUser",
        isSupport = true
      )

      val saved = repo.save(testUser).unsafeRunSync()
      val result = repo.getUser(saved.id).unsafeRunSync()

      result shouldBe Some(testUser)
      result.get.isSupport shouldBe true
    }
    "returns the support flag when false" in {
      val f = repo.getUserByAccessKey(users.head.accessKey).unsafeRunSync()

      f shouldBe Some(users.head)
      f.get.isSupport shouldBe false
    }
    "returns the test flag when true" in {
      val testUser = User(userName = "test", accessKey = "test", secretKey = "test", isTest = true)

      val saved = repo.save(testUser).unsafeRunSync()
      val result = repo.getUser(saved.id).unsafeRunSync()

      result shouldBe Some(testUser)
      result.get.isTest shouldBe true
    }
    "returns the test flag when false (default)" in {
      val f = repo.getUserByAccessKey(users.head.accessKey).unsafeRunSync()

      f shouldBe Some(users.head)
      f.get.isTest shouldBe false
    }
  }
}
