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
import vinyldns.core.domain.membership.{Group, GroupRepository, GroupStatus}
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
    Group(name = testName, email = "test@email.com")
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
      s.executeUpdate("DELETE FROM groups")
    }

    for (group <- groups) {
      saveGroupData(repo, group).unsafeRunSync()
    }
  }

  override protected def afterAll(): Unit = {
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM groups")
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
  }
}
