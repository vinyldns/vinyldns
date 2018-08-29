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

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class DynamoDBMembershipRepositoryIntegrationSpec extends DynamoDBIntegrationSpec {

  private implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global
  private val membershipTable = "membership-live"
  private val tableConfig = ConfigFactory.parseString(s"""
       | dynamo {
       |   tableName = "$membershipTable"
       |   provisionedReads=100
       |   provisionedWrites=100
       | }
    """.stripMargin).withFallback(ConfigFactory.load())

  private var repo: DynamoDBMembershipRepository = _

  private val testUserIds = for (i <- 0 to 5) yield s"test-user-$i"
  private val testGroupIds = for (i <- 0 to 5) yield s"test-group-$i"

  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  def setup(): Unit = {
    repo = new DynamoDBMembershipRepository(tableConfig, dynamoDBHelper)

    // wait until the repo is ready, could take time if the table has to be created
    var notReady = true
    while (notReady) {
      val result = Await.ready(repo.getGroupsForUser("any").unsafeToFuture(), 5.seconds)
      notReady = result.value.get.isFailure
      Thread.sleep(2000)
    }

    // Create all the items
    val results =
      Future.sequence(testGroupIds.map(repo.addMembers(_, testUserIds.toSet).unsafeToFuture()))

    // Wait until all of the data is stored
    Await.result(results, 5.minutes)
  }

  def tearDown(): Unit = {
    val results =
      Future.sequence(testGroupIds.map(repo.removeMembers(_, testUserIds.toSet).unsafeToFuture()))

    // Wait until all of the data is stored
    Await.result(results, 5.minutes)
  }

  "DynamoDBMembershipRepository" should {
    val groupId = genString
    val user1 = genString
    val user2 = genString
    "add members successfully" in {
      whenReady(repo.addMembers(groupId, Set(user1, user2)).unsafeToFuture(), timeout) {
        memberIds =>
          memberIds should contain theSameElementsAs Set(user1, user2)
      }
    }

    "add a group to an existing user" in {
      val group1 = genString
      val group2 = genString
      val user1 = genString
      val f =
        for {
          _ <- repo.addMembers(group1, Set(user1))
          _ <- repo.addMembers(group2, Set(user1))
          userGroups <- repo.getGroupsForUser(user1)
        } yield userGroups

      whenReady(f.unsafeToFuture(), timeout) { userGroups =>
        userGroups should contain theSameElementsAs Set(group1, group2)
      }
    }

    "return an empty set when getting groups for a user that does not exist" in {
      whenReady(repo.getGroupsForUser("notHere").unsafeToFuture(), timeout) { groupIds =>
        groupIds shouldBe empty
      }
    }

    "remove members successfully" in {
      val group1 = genString
      val group2 = genString
      val user1 = genString
      val f =
        for {
          _ <- repo.addMembers(group1, Set(user1))
          _ <- repo.addMembers(group2, Set(user1))
          _ <- repo.removeMembers(group1, Set(user1))
          userGroups <- repo.getGroupsForUser(user1)
        } yield userGroups

      whenReady(f.unsafeToFuture(), timeout) { userGroups =>
        userGroups should contain theSameElementsAs Set(group2)
      }
    }

    "remove members not in group" in {
      val group1 = genString
      val user1 = genString
      val user2 = genString
      val f =
        for {
          _ <- repo.addMembers(group1, Set(user1))
          _ <- repo.removeMembers(group1, Set(user2))
          userGroups <- repo.getGroupsForUser(user2)
        } yield userGroups

      whenReady(f.unsafeToFuture(), timeout) { userGroups =>
        userGroups shouldBe empty
      }
    }

    "remove all groups for user" in {
      val group1 = genString
      val group2 = genString
      val group3 = genString
      val user1 = genString
      val f =
        for {
          _ <- repo.addMembers(group1, Set(user1))
          _ <- repo.addMembers(group2, Set(user1))
          _ <- repo.addMembers(group3, Set(user1))
          _ <- repo.removeMembers(group1, Set(user1))
          _ <- repo.removeMembers(group2, Set(user1))
          _ <- repo.removeMembers(group3, Set(user1))
          userGroups <- repo.getGroupsForUser(user1)
        } yield userGroups

      whenReady(f.unsafeToFuture(), timeout) { userGroups =>
        userGroups shouldBe empty
      }
    }

    "retrieve all of the groups for a user" in {
      val f = repo.getGroupsForUser(testUserIds.head)

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        testGroupIds.foreach(groupId => retrieved should contain(groupId))
      }
    }

    "remove members from a group" in {
      val membersToRemove = testUserIds.toList.sorted.take(2).toSet
      val groupsRemoved = testGroupIds.toList.sorted.take(2)

      val f =
        Future.sequence(groupsRemoved.map(repo.removeMembers(_, membersToRemove).unsafeToFuture()))

      Await.result(f, 5.minutes)

      whenReady(repo.getGroupsForUser(membersToRemove.head).unsafeToFuture(), timeout) {
        groupsRetrieved =>
          forAll(groupsRetrieved) { groupId =>
            groupsRemoved should not contain groupId
          }
      }
    }
  }
}
