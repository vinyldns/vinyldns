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
import scalikejdbc.DB
import vinyldns.core.domain.membership.{Group, GroupRepository, GroupStatus, MemberStatus, MembershipAccess, MembershipAccessStatus}
import vinyldns.mysql.{TestMySqlInstance, TransactionProvider}
import cats.effect.IO

class MySqlGroupRepositoryIntegrationSpec
  extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with Inspectors
    with OptionValues
    with TransactionProvider {

  private val repo: GroupRepository = TestMySqlInstance.groupRepository

  private val testGroupNames = (for { i <- 0 to 100 } yield s"test-group-$i").toList.sorted
  private val groups = testGroupNames.map { testName =>
    Group(name = testName, email = "test@email.com", membershipAccessStatus = Some(MembershipAccessStatus(Set(),Set(),Set())))
  }

  def saveGroupData(
                     repo: GroupRepository,
                     group: Group
                   ): IO[Group] =
    executeWithinTransaction { db: DB =>
      repo.save(db, group)
    }

  override protected def beforeAll(): Unit = {
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM `groups`")
    }

    for (group <- groups) {
      saveGroupData(repo, group).unsafeRunSync()
    }
  }

  override protected def afterAll(): Unit = {
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM `groups`")
    }
    super.afterAll()
  }

  "MySqlGroupRepository.save" should {
    "return user after save" in {
      saveGroupData(repo, groups.head).unsafeRunSync() shouldBe groups.head
    }
  }

  "MySqlGroupRepository.delete" should {
    "delete a group" in {
      val toBeDeleted = groups.head.copy(id = "to-be-deleted")
      saveGroupData(repo, toBeDeleted).unsafeRunSync() shouldBe toBeDeleted
      repo.getGroup(toBeDeleted.id).unsafeRunSync() shouldBe Some(toBeDeleted)

      val deleted = toBeDeleted.copy(status = GroupStatus.Deleted)
      repo.delete(deleted).unsafeRunSync() shouldBe deleted
      repo.getGroup(deleted.id).unsafeRunSync() shouldBe None
    }
  }

  "MySqlGroupRepository.getGroup" should {
    "retrieve a group" in {
      repo.getGroup(groups.head.id).unsafeRunSync() shouldBe Some(groups.head)
    }

    "returns None when group does not exist" in {
      repo.getGroup("no-existo").unsafeRunSync() shouldBe None
    }
  }

  "MySqlGroupRepository.getGroupsByName" should {
    "omits all non existing groups" in {
      val result = repo.getGroupsByName(Set("no-existo", groups.head.name)).unsafeRunSync()
      result should contain theSameElementsAs Set(groups.head)
    }

    "returns correct list of groups" in {
      val names = Set(groups(0).name, groups(1).name, groups(2).name)
      val result = repo.getGroupsByName(names).unsafeRunSync()
      result should contain theSameElementsAs groups.take(3).toSet
    }

    "returns empty list when given no names" in {
      val result = repo.getGroupsByName(Set[String]()).unsafeRunSync()
      result should contain theSameElementsAs Set()
    }
  }

  "MySqlGroupRepository.getGroupByName" should {
    "retrieve a group" in {
      repo.getGroupByName(groups.head.name).unsafeRunSync() shouldBe Some(groups.head)
    }

    "returns None when group does not exist" in {
      repo.getGroupByName("no-existo").unsafeRunSync() shouldBe None
    }
  }

  "MySqlGroupRepository.getGroupsByName" should {
    "retrieve a group" in {
      repo.getGroupsByName(groups.head.name).unsafeRunSync() shouldBe Set(groups.head)
    }

    "retrieve groups with wildcard character" in {
      repo.getGroupsByName("*-group-*").unsafeRunSync() shouldBe groups.toSet
    }

    "returns empty set when group does not exist" in {
      repo.getGroupsByName("no-existo").unsafeRunSync() shouldBe Set()
    }
  }

  "MySqlGroupRepository.getAllGroups" should {
    "retrieve all groups" in {
      repo.getAllGroups().unsafeRunSync() should contain theSameElementsAs groups.toSet
    }
  }

  "MySqlGroupRepository.getGroups" should {
    "omits all non existing groups" in {
      val result = repo.getGroups(Set("no-existo", groups.head.id)).unsafeRunSync()
      result should contain theSameElementsAs Set(groups.head)
    }

    "returns correct list of groups" in {
      val ids = Set(groups(0).id, groups(1).id, groups(2).id)
      val result = repo.getGroups(ids).unsafeRunSync()
      result should contain theSameElementsAs groups.take(3).toSet
    }

    "returns empty list when given no ids" in {
      val result = repo.getGroups(Set[String]()).unsafeRunSync()
      result should contain theSameElementsAs Set()
    }
    "MySqlGroupRepository membership access" should {
      "returns empty list when given no ids" in {
        val result = repo.getGroups(Set[String]()).unsafeRunSync()
        result should contain theSameElementsAs Set()
      }

      "save and retrieve a group with membership access status" in {
        val pendingMembers = Set(
          MembershipAccess(userId = "user1", submittedBy = "admin1", status = MemberStatus.PendingReview.toString),
          MembershipAccess(userId = "user2", submittedBy = "admin1", status = MemberStatus.PendingReview.toString)
        )
        val rejectedMembers = Set(
          MembershipAccess(userId = "user3", submittedBy = "admin1", status = MemberStatus.Rejected.toString)
        )
        val approvedMembers = Set(
          MembershipAccess(userId = "user4", submittedBy = "admin1", status = MemberStatus.Approved.toString)
        )

        val groupWithAccess = Group(
          name = "group-with-access",
          email = "access@test.com",
          membershipAccessStatus = Some(MembershipAccessStatus(pendingMembers, rejectedMembers, approvedMembers))
        )

        saveGroupData(repo, groupWithAccess).unsafeRunSync()
        val retrieved = repo.getGroup(groupWithAccess.id).unsafeRunSync()

        retrieved shouldBe Some(groupWithAccess)
        retrieved.get.membershipAccessStatus.isDefined shouldBe true
        val accessStatus = retrieved.get.membershipAccessStatus.get
        accessStatus.pendingReviewMember should contain theSameElementsAs pendingMembers
        accessStatus.rejectedMember should contain theSameElementsAs rejectedMembers
        accessStatus.approvedMember should contain theSameElementsAs approvedMembers
      }

      "update membership access for an existing group" in {
        val group = groups.head
        val updatedPendingMembers = Set(
          MembershipAccess(userId = "newuser1", submittedBy = "admin2", status = MemberStatus.PendingReview.toString)
        )
        val updatedRejectedMembers = Set(
          MembershipAccess(userId = "newuser2", submittedBy = "admin2", status = MemberStatus.Rejected.toString)
        )
        val updatedApprovedMembers = Set(
          MembershipAccess(userId = "newuser3", submittedBy = "admin2", status = MemberStatus.Approved.toString)
        )

        val updatedGroup = group.copy(
          membershipAccessStatus = Some(MembershipAccessStatus(updatedPendingMembers, updatedRejectedMembers, updatedApprovedMembers))
        )

        saveGroupData(repo, updatedGroup).unsafeRunSync()
        val result = repo.getGroup(group.id).unsafeRunSync()

        result.flatMap(_.membershipAccessStatus).map(_.pendingReviewMember) shouldBe Some(updatedPendingMembers)
        result.flatMap(_.membershipAccessStatus).map(_.rejectedMember) shouldBe Some(updatedRejectedMembers)
        result.flatMap(_.membershipAccessStatus).map(_.approvedMember) shouldBe Some(updatedApprovedMembers)
      }

      "handle empty membership access sets" in {
        val groupWithEmptyAccess = Group(
          name = "empty-access-group",
          email = "empty@test.com",
          membershipAccessStatus = Some(MembershipAccessStatus(Set(), Set(), Set()))
        )

        saveGroupData(repo, groupWithEmptyAccess).unsafeRunSync()
        val result = repo.getGroup(groupWithEmptyAccess.id).unsafeRunSync()

        result.flatMap(_.membershipAccessStatus).map(_.pendingReviewMember) shouldBe Some(Set())
        result.flatMap(_.membershipAccessStatus).map(_.rejectedMember) shouldBe Some(Set())
        result.flatMap(_.membershipAccessStatus).map(_.approvedMember) shouldBe Some(Set())
      }

      "handle null membership access status" in {
        val groupWithNoAccess = Group(
          name = "no-access-group",
          email = "noaccess@test.com",
          membershipAccessStatus = None
        )

        saveGroupData(repo, groupWithNoAccess).unsafeRunSync()
        val result = repo.getGroup(groupWithNoAccess.id).unsafeRunSync()

        result.flatMap(_.membershipAccessStatus) shouldBe Some(MembershipAccessStatus(Set(),Set(),Set()))
      }
    }
  }
}
