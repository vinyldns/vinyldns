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
import com.amazonaws.services.dynamodbv2.model._
import vinyldns.core.domain.membership.{Group, GroupStatus}
import vinyldns.core.TestMembershipData._

import scala.concurrent.duration._

class DynamoDBGroupRepositoryIntegrationSpec extends DynamoDBIntegrationSpec {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  private val GROUP_TABLE = "groups-live"

  private val tableConfig = DynamoDBRepositorySettings(s"$GROUP_TABLE", 30, 30)

  private var repo: DynamoDBGroupRepository = _

  private val activeGroups =
    for (i <- 1 to 10)
      yield Group(
        s"live-test-group$i",
        s"test$i@test.com",
        Some(s"description$i"),
        memberIds = Set(s"member$i", s"member2$i"),
        adminUserIds = Set(s"member$i", s"member2$i"),
        id = "id-%03d".format(i)
      )

  private val inDbDeletedGroup = Group(
    s"live-test-group-deleted",
    s"test@test.com",
    Some(s"description"),
    memberIds = Set("member1"),
    adminUserIds = Set("member1"),
    id = "id-deleted-group",
    status = GroupStatus.Deleted
  )
  private val groups = activeGroups ++ List(inDbDeletedGroup)

  def setup(): Unit = {
    repo = DynamoDBGroupRepository(tableConfig, dynamoIntegrationConfig).unsafeRunSync()

    // Create all the groups
    val savedGroups = groups.map(repo.save(_)).toList.parSequence

    // Wait until all of the zones are done
    savedGroups.unsafeRunTimed(5.minutes).getOrElse(fail("timeout waiting for data load"))
  }

  def tearDown(): Unit = {
    val request = new DeleteTableRequest().withTableName(GROUP_TABLE)
    val deleteTables = repo.dynamoDBHelper.deleteTable(request)
    deleteTables.unsafeRunSync()
  }

  "DynamoDBGroupRepository" should {
    "get a group by id" in {
      val targetGroup = groups.head
      repo.getGroup(targetGroup.id).unsafeRunSync() shouldBe Some(targetGroup)
    }

    "get all active groups" in {
      repo.getAllGroups().unsafeRunSync() shouldBe activeGroups.toSet
    }

    "not return a deleted group when getting group by id" in {
      val deleted = deletedGroup.copy(memberIds = Set("foo"), adminUserIds = Set("foo"))
      val f =
        for {
          _ <- repo.save(deleted)
          retrieved <- repo.getGroup(deleted.id)
        } yield retrieved

      f.unsafeRunSync() shouldBe None
    }

    "not return a deleted group when getting group by name" in {
      val deleted = deletedGroup.copy(memberIds = Set("foo"), adminUserIds = Set("foo"))
      val f =
        for {
          _ <- repo.save(deleted)
          retrieved <- repo.getGroupByName(deleted.name)
        } yield retrieved

      f.unsafeRunSync() shouldBe None
    }

    "get groups should omit non existing groups" in {
      val f = repo.getGroups(Set(activeGroups.head.id, "thisdoesnotexist"))
      f.unsafeRunSync().map(_.id) should contain theSameElementsAs Set(activeGroups.head.id)
    }

    "returns all the groups" in {
      val f = repo.getGroups(groups.map(_.id).toSet)

      f.unsafeRunSync() should contain theSameElementsAs activeGroups
    }

    "only return requested groups" in {
      val evenGroups = activeGroups.filter(_.id.takeRight(1).toInt % 2 == 0)
      val f = repo.getGroups(evenGroups.map(_.id).toSet)

      f.unsafeRunSync() should contain theSameElementsAs evenGroups
    }

    "return an Empty set if nothing found" in {
      val f = repo.getGroups(Set("notFound"))

      f.unsafeRunSync() shouldBe Set()
    }

    "not return deleted groups" in {
      val deleted = deletedGroup.copy(
        id = "test-deleted-group-get-groups",
        memberIds = Set("foo"),
        adminUserIds = Set("foo")
      )
      val f =
        for {
          _ <- repo.save(deleted)
          retrieved <- repo.getGroups(Set(deleted.id, groups.head.id))
        } yield retrieved

      f.unsafeRunSync().map(_.id) shouldBe Set(groups.head.id)
    }

    "get a group by name" in {
      val targetGroup = groups.head
      repo.getGroupByName(targetGroup.name).unsafeRunSync() shouldBe Some(targetGroup)
    }

    "save a group with no description" in {
      val group = Group(
        "null-description",
        "test@test.com",
        None,
        memberIds = Set("foo"),
        adminUserIds = Set("bar")
      )

      val test =
        for {
          saved <- repo.save(group)
          retrieved <- repo.getGroup(saved.id)
        } yield retrieved

      test.unsafeRunSync().get.description shouldBe None
    }

    "add and delete a group should return successfully" in {
      val deleted = deletedGroup.copy(
        id = "test-deleted-group-get-groups",
        memberIds = Set("foo"),
        adminUserIds = Set("foo")
      )
      val f =
        for {
          _ <- repo.save(deleted)
          retrieved <- repo.delete(deleted)
        } yield retrieved

      f.unsafeRunSync().id shouldBe deleted.id

      val getAfterDeleted =
        for {
          get <- repo.getGroup("test-deleted-group-get-groups")
          getAll <- repo.getAllGroups()
        } yield (get, getAll)

      val (get, getAll) = getAfterDeleted.unsafeRunSync()
      get shouldBe None
      getAll.filter(_.id == "test-deleted-group-get-groups") shouldBe Set.empty
      getAll.filter(_.id == activeGroups.head.id) shouldBe Set(activeGroups.head)
    }
  }
}
