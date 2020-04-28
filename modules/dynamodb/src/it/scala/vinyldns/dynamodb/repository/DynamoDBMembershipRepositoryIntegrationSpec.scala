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

import scala.concurrent.duration._

class DynamoDBMembershipRepositoryIntegrationSpec extends DynamoDBIntegrationSpec {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)
  private val membershipTable = "membership-live"

  private val tableConfig = DynamoDBRepositorySettings(s"$membershipTable", 30, 30)

  private var repo: DynamoDBMembershipRepository = _

  private val testUserIds = for (i <- 0 to 5) yield s"test-user-$i"
  private val testGroupIds = for (i <- 0 to 5) yield s"test-group-$i"

  def setup(): Unit = {
    repo = DynamoDBMembershipRepository(tableConfig, dynamoIntegrationConfig).unsafeRunSync()

    // Create all the items
    val results =
      testGroupIds.map(repo.saveMembers(_, testUserIds.toSet, isAdmin = false)).toList.parSequence

    // Wait until all of the data is stored
    results.unsafeRunTimed(5.minutes).getOrElse(fail("timeout waiting for data load"))
  }

  def tearDown(): Unit = {
    val results = testGroupIds.map(repo.removeMembers(_, testUserIds.toSet)).toList.parSequence
    results.unsafeRunSync()
  }

  "DynamoDBMembershipRepository" should {
    val groupId = genString
    val user1 = genString
    val user2 = genString
    "add members successfully" in {
      repo
        .saveMembers(groupId, Set(user1, user2), isAdmin = false)
        .unsafeRunSync() should contain theSameElementsAs Set(user1, user2)
    }

    "add members with no member ids invokes no change" in {
      val user1 = genString
      repo.saveMembers(groupId, Set(user1), isAdmin = false).unsafeRunSync()

      val originalResult = repo.getGroupsForUser(user1).unsafeRunSync()
      repo.saveMembers(groupId, Set(), isAdmin = false).unsafeRunSync()
      repo.getGroupsForUser(user1).unsafeRunSync() should contain theSameElementsAs originalResult
    }

    "add a group to an existing user" in {
      val group1 = genString
      val group2 = genString
      val user1 = genString
      val f =
        for {
          _ <- repo.saveMembers(group1, Set(user1), isAdmin = false)
          _ <- repo.saveMembers(group2, Set(user1), isAdmin = false)
          userGroups <- repo.getGroupsForUser(user1)
        } yield userGroups

      f.unsafeRunSync() should contain theSameElementsAs Set(group1, group2)
    }

    "return an empty set when getting groups for a user that does not exist" in {
      repo.getGroupsForUser("notHere").unsafeRunSync() shouldBe empty
    }

    "remove members successfully" in {
      val group1 = genString
      val group2 = genString
      val user1 = genString
      val f =
        for {
          _ <- repo.saveMembers(group1, Set(user1), isAdmin = false)
          _ <- repo.saveMembers(group2, Set(user1), isAdmin = false)
          _ <- repo.removeMembers(group1, Set(user1))
          userGroups <- repo.getGroupsForUser(user1)
        } yield userGroups

      f.unsafeRunSync() should contain theSameElementsAs Set(group2)
    }

    "remove members not in group" in {
      val group1 = genString
      val user1 = genString
      val user2 = genString
      val f =
        for {
          _ <- repo.saveMembers(group1, Set(user1), isAdmin = false)
          _ <- repo.removeMembers(group1, Set(user2))
          userGroups <- repo.getGroupsForUser(user2)
        } yield userGroups

      f.unsafeRunSync() shouldBe empty
    }

    "remove members with no member ids invokes no change" in {
      val user1 = genString
      repo.saveMembers(groupId, Set(user1), isAdmin = false).unsafeRunSync()

      val originalResult = repo.getGroupsForUser(user1).unsafeRunSync()
      repo.removeMembers(groupId, Set()).unsafeRunSync()
      repo.getGroupsForUser(user1).unsafeRunSync() should contain theSameElementsAs originalResult
    }

    "remove all groups for user" in {
      val group1 = genString
      val group2 = genString
      val group3 = genString
      val user1 = genString
      val f =
        for {
          _ <- repo.saveMembers(group1, Set(user1), isAdmin = false)
          _ <- repo.saveMembers(group2, Set(user1), isAdmin = false)
          _ <- repo.saveMembers(group3, Set(user1), isAdmin = false)
          _ <- repo.removeMembers(group1, Set(user1))
          _ <- repo.removeMembers(group2, Set(user1))
          _ <- repo.removeMembers(group3, Set(user1))
          userGroups <- repo.getGroupsForUser(user1)
        } yield userGroups

      f.unsafeRunSync() shouldBe empty
    }

    "retrieve all of the groups for a user" in {
      val f = repo.getGroupsForUser(testUserIds.head)

      val retrieved = f.unsafeRunSync()
      testGroupIds.foreach(groupId => retrieved should contain(groupId))
    }

    "remove members from a group" in {
      val membersToRemove = testUserIds.toList.sorted.take(2).toSet
      val groupsRemoved = testGroupIds.toList.sorted.take(2)

      groupsRemoved.map(repo.removeMembers(_, membersToRemove)).parSequence.unsafeRunSync()

      val groupsRetrieved = repo.getGroupsForUser(membersToRemove.head).unsafeRunSync()
      forAll(groupsRetrieved) { groupId =>
        groupsRemoved should not contain groupId
      }
    }
  }
}
