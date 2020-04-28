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

import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc.{DB, _}
import vinyldns.mysql.TestMySqlInstance

class MySqlMembershipRepositoryIntegrationSpec
    extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers {

  private val repo: MySqlMembershipRepository = TestMySqlInstance.membershipRepository

  def clear(): Unit =
    DB.localTx { implicit s =>
      s.executeUpdate("DELETE FROM membership")
      ()
    }

  override protected def beforeEach(): Unit = clear()

  override protected def afterAll(): Unit = clear()

  private def generateUserIds(numUserIds: Int): Set[String] = {
    val ids = for {
      i <- 1 to numUserIds
    } yield s"user-id-$i"

    ids.toSet
  }

  private def getAllRecords: List[(String, String)] =
    DB.localTx { implicit s =>
      sql"SELECT user_id, group_id FROM membership"
        .map(res => Tuple2[String, String](res.string(1), res.string(2)))
        .list()
        .apply()
    }

  "MySqlMembershipRepo.addMembers" should {
    "do nothing if member ids is empty" in {
      val originalResult = getAllRecords
      val groupId = "group-id-1"
      val userIds: Set[String] = Set()
      val addResult = repo.saveMembers(groupId, userIds, isAdmin = false).unsafeRunSync()
      addResult shouldBe empty

      // records remain the same as original
      getAllRecords should contain theSameElementsAs originalResult
    }

    "add a member successfully" in {
      val groupId = "group-id-1"
      val userIds = Set("user-id-1")
      val addResult = repo.saveMembers(groupId, userIds, isAdmin = false).unsafeRunSync()

      addResult should contain theSameElementsAs userIds

      val getAllResult = getAllRecords
      val expectedGetAllResult = Set(Tuple2(userIds.head, groupId))

      getAllResult should contain theSameElementsAs expectedGetAllResult
    }

    "ignore if membership is a duplicate" in {
      val groupId = "group-id-1"
      val userIds = Set("user-id-1")

      repo
        .saveMembers(groupId, userIds, isAdmin = false)
        .unsafeRunSync() should contain theSameElementsAs userIds
      repo
        .saveMembers(groupId, userIds, isAdmin = false)
        .unsafeRunSync() should contain theSameElementsAs userIds

      getAllRecords should contain theSameElementsAs List(Tuple2(userIds.head, groupId))
    }

    "add multiple members successfully" in {
      val groupId = "group-id-1"
      val userIds = generateUserIds(10)
      val addResult = repo.saveMembers(groupId, userIds, isAdmin = false).unsafeRunSync()

      addResult should contain theSameElementsAs userIds

      val expectedGetAllResult = userIds.map(Tuple2(_, groupId))

      getAllRecords should contain theSameElementsAs expectedGetAllResult
    }

    "add a group for an existing user" in {
      val groupIdOne = "group-id-1"
      val groupIdTwo = "group-id-2"
      val userIds = Set("user-id-1")

      repo.saveMembers(groupIdOne, userIds, isAdmin = false).unsafeRunSync() shouldBe userIds
      repo.saveMembers(groupIdTwo, userIds, isAdmin = false).unsafeRunSync() shouldBe userIds

      val expectedGroups = Set(groupIdOne, groupIdTwo)
      repo
        .getGroupsForUser(userIds.head)
        .unsafeRunSync() should contain theSameElementsAs expectedGroups
    }
  }

  "MySqlMembershipRepo.removeMembers" should {
    "remove a member successfully from group" in {
      val groupId = "group-id-1"
      val userIds = Set("user-id-1")

      repo.saveMembers(groupId, userIds, isAdmin = false).unsafeRunSync()
      getAllRecords should contain theSameElementsAs Set(Tuple2(userIds.head, groupId))

      repo.removeMembers(groupId, userIds).unsafeRunSync() should contain theSameElementsAs userIds
      getAllRecords shouldBe List()
    }

    "remove multiple members successfully from group" in {
      val groupId = "group-id-1"
      val userIds = generateUserIds(10)

      repo.saveMembers(groupId, userIds, isAdmin = false).unsafeRunSync()
      getAllRecords should contain theSameElementsAs userIds.map(Tuple2(_, groupId))

      val toBeRemoved = userIds.take(5)
      repo
        .removeMembers(groupId, toBeRemoved)
        .unsafeRunSync() should contain theSameElementsAs toBeRemoved
      val expectedUserIds = userIds -- toBeRemoved
      getAllRecords should contain theSameElementsAs expectedUserIds.map(Tuple2(_, groupId))
    }

    "do nothing if the member set is empty" in {
      val groupId = "group-id-1"
      val userIds = Set("user-id-1")

      repo.saveMembers(groupId, userIds, isAdmin = false).unsafeRunSync()
      val originalResult = getAllRecords
      val result = repo.removeMembers(groupId, Set()).unsafeRunSync()
      result shouldBe empty

      getAllRecords should contain theSameElementsAs originalResult
    }
  }

  "MySqlMembershipRepo.getGroupsForUser" should {
    "get all groups for user" in {
      val noisyIds = generateUserIds(10)
      val groupIdOne = "group-id-1"
      val groupIdTwo = "group-id-2"
      val groupIdThree = "group-id-3"

      // make some noise
      repo.saveMembers(groupIdOne, noisyIds, isAdmin = false).unsafeRunSync()
      repo.saveMembers(groupIdTwo, noisyIds, isAdmin = false).unsafeRunSync()
      repo.saveMembers(groupIdThree, noisyIds, isAdmin = false).unsafeRunSync()

      val underTest = Set("user-id-under-test")
      repo.saveMembers(groupIdOne, underTest, isAdmin = false).unsafeRunSync()
      repo.saveMembers(groupIdTwo, underTest, isAdmin = false).unsafeRunSync()
      // not adding to group three

      val expectedGroups = Set(groupIdOne, groupIdTwo)
      repo
        .getGroupsForUser(underTest.head)
        .unsafeRunSync() should contain theSameElementsAs expectedGroups
    }

    "return empty when no groups for user" in {
      val groupId = "group-id-1"
      val noisyIds = generateUserIds(2)
      repo.saveMembers(groupId, noisyIds, isAdmin = false).unsafeRunSync()

      val underTest = Set("user-id-under-test")
      repo.getGroupsForUser(underTest.head).unsafeRunSync() shouldBe Set()
    }
  }

  "MySqlMembershipRepo.getUsersForGroup" should {
    "get correct users based on isAdmin flag" in {
      val groupId = "group-id-1"
      val adminIds = generateUserIds(2)
      val allMemberUserIds = generateUserIds(4)
      val nonAdminIds = allMemberUserIds.diff(adminIds)

      val getMembers = for {
        _ <- repo.saveMembers(groupId, adminIds, isAdmin = true)
        _ <- repo.saveMembers(groupId, nonAdminIds, isAdmin = false)
        allMembers <- repo.getUsersForGroup(groupId, None)
        adminOnlyMembers <- repo.getUsersForGroup(groupId, Some(true))
        nonAdminOnlyMembers <- repo.getUsersForGroup(groupId, Some(false))
      } yield (allMembers, adminOnlyMembers, nonAdminOnlyMembers)

      val (allMemberIds, adminOnlyMembers, nonAdminOnlyMembers) = getMembers.unsafeRunSync()
      allMemberIds shouldBe allMemberUserIds
      adminOnlyMembers shouldBe adminIds
      nonAdminOnlyMembers shouldBe nonAdminIds
    }
  }
}
