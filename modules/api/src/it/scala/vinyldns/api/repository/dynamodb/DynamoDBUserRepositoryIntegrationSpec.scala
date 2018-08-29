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

package vinyldns.api.repository.dynamodb

import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Seconds, Span}
import vinyldns.api.domain.membership.User

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class DynamoDBUserRepositoryIntegrationSpec extends DynamoDBIntegrationSpec {

  private implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val userTable = "users-live"

  private val tableConfig = ConfigFactory.parseString(s"""
       | dynamo {
       |   tableName = "$userTable"
       |   provisionedReads=100
       |   provisionedWrites=100
       | }
    """.stripMargin).withFallback(ConfigFactory.load())

  private var repo: DynamoDBUserRepository = _

  private val testUserIds = (for { i <- 0 to 100 } yield s"test-user-$i").toList.sorted
  private val users = testUserIds.map { id =>
    User(id = id, userName = "name" + id, accessKey = s"abc$id", secretKey = "123")
  }

  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  def setup(): Unit = {
    repo = new DynamoDBUserRepository(tableConfig, dynamoDBHelper)

    // wait until the repo is ready, could take time if the table has to be created
    var notReady = true
    while (notReady) {
      val result = Await.ready(repo.getUser("any").unsafeToFuture(), 5.seconds)
      notReady = result.value.get.isFailure
      Thread.sleep(2000)
    }

    // Create all the items
    val results = Future.sequence(users.map(repo.save(_).unsafeToFuture()))

    // Wait until all of the data is stored
    Await.result(results, 5.minutes)
  }

  def tearDown(): Unit = {
    val request = new DeleteTableRequest().withTableName(userTable)
    val deleteTables = dynamoDBHelper.deleteTable(request)
    Await.ready(deleteTables.unsafeToFuture(), 100.seconds)
  }

  "DynamoDBUserRepository" should {
    "retrieve a user" in {
      val f = repo.getUser(testUserIds.head)

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe Some(users.head)
      }
    }
    "returns None when the user does not exist" in {
      val f = repo.getUser("does not exists")

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe None
      }
    }
    "getUsers omits all non existing users" in {
      val getUsers =
        for {
          result <- repo.getUsers(Set("notFound", testUserIds.head), None, Some(100))
        } yield result
      whenReady(getUsers.unsafeToFuture(), timeout) { result =>
        result.users.map(_.id) should contain theSameElementsAs Set(testUserIds.head)
        result.users.map(_.id) should not contain "notFound"
      }
    }
    "returns all the users" in {
      val f = repo.getUsers(testUserIds.toSet, None, None)

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved.users should contain theSameElementsAs users
        retrieved.lastEvaluatedId shouldBe None
      }
    }
    "only return requested users" in {
      val evenUsers = users.filter(_.id.takeRight(1).toInt % 2 == 0)
      val f = repo.getUsers(evenUsers.map(_.id).toSet, None, None)

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved.users should contain theSameElementsAs evenUsers
        retrieved.lastEvaluatedId shouldBe None
      }
    }
    "start at the exclusive start key" in {
      val f = repo.getUsers(testUserIds.toSet, Some(testUserIds(5)), None)

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved.users should not contain users(5) //start key is exclusive
        retrieved.users should contain theSameElementsAs users.slice(6, users.length)
        retrieved.lastEvaluatedId shouldBe None
      }
    }
    "only return the number of items equal to the limit" in {
      val f = repo.getUsers(testUserIds.toSet, None, Some(5))

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved.users.size shouldBe 5
        retrieved.users should contain theSameElementsAs users.take(5)
      }
    }
    "returns the correct lastEvaluatedKey" in {
      val f = repo.getUsers(testUserIds.toSet, None, Some(5))

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved.lastEvaluatedId shouldBe Some(users(4).id) // base 0
        retrieved.users should contain theSameElementsAs users.take(5)
      }
    }
    "return the user if the matching access key" in {
      val f = repo.getUserByAccessKey(users.head.accessKey)

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe Some(users.head)
      }
    }
    "returns None not user has a matching access key" in {
      val f = repo.getUserByAccessKey("does not exists")

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe None
      }
    }
    "returns the super user flag when true" in {
      val testUser = User(
        userName = "testSuper",
        accessKey = "testSuper",
        secretKey = "testUser",
        isSuper = true)

      val f =
        for {
          saved <- repo.save(testUser)
          result <- repo.getUser(saved.id)
        } yield result

      whenReady(f.unsafeToFuture(), timeout) { saved =>
        saved shouldBe Some(testUser)
        saved.get.isSuper shouldBe true
      }
    }
    "returns the super user flag when false" in {
      val testUser = User(userName = "testSuper", accessKey = "testSuper", secretKey = "testUser")

      val f =
        for {
          saved <- repo.save(testUser)
          result <- repo.getUser(saved.id)
        } yield result

      whenReady(f.unsafeToFuture(), timeout) { saved =>
        saved shouldBe Some(testUser)
        saved.get.isSuper shouldBe false
      }
    }
  }
}
