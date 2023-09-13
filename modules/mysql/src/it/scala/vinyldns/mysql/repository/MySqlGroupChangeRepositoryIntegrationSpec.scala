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
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc.DB
import vinyldns.core.domain.membership.{Group, GroupChange, GroupChangeRepository, GroupChangeType}
import vinyldns.mysql.{TestMySqlInstance, TransactionProvider}

class MySqlGroupChangeRepositoryIntegrationSpec
  extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with TransactionProvider {

  private val repo: GroupChangeRepository = TestMySqlInstance.groupChangeRepository

  def clear(): Unit = {
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM group_change")
    }
    ()
  }

  override protected def beforeEach(): Unit = clear()

  override protected def afterAll(): Unit = clear()

  def generateGroupChanges(groupId: String, numChanges: Int): Seq[GroupChange] = {
    val group = Group(name = "test", id = groupId, email = "test@test.com")
    for {
      i <- 1 to numChanges
    } yield GroupChange(
      group,
      GroupChangeType.Create,
      s"user-$i",
      created = Instant.now.truncatedTo(ChronoUnit.MILLIS).plusSeconds(i)
    )
  }

  def saveGroupChangeData(
                           repo: GroupChangeRepository,
                           groupChange: GroupChange
                         ): IO[GroupChange] =
    executeWithinTransaction { db: DB =>
      repo.save(db, groupChange)
    }

  "MySqlGroupChangeRepository.save" should {
    "successfully save a group change" in {
      val groupChange = generateGroupChanges("group-1", 1).head
      saveGroupChangeData(repo, groupChange).unsafeRunSync() shouldBe groupChange
      repo.getGroupChange(groupChange.id).unsafeRunSync() shouldBe Some(groupChange)
    }

    "on duplicate key update a group change" in {
      val groupChange = generateGroupChanges("group-1", 1).head
      saveGroupChangeData(repo, groupChange).unsafeRunSync() shouldBe groupChange
      repo.getGroupChange(groupChange.id).unsafeRunSync() shouldBe Some(groupChange)

      val groupChangeUpdate =
        groupChange.copy(created = Instant.now.truncatedTo(ChronoUnit.MILLIS).plusSeconds(10000), userId = "updated")
      saveGroupChangeData(repo, groupChangeUpdate).unsafeRunSync() shouldBe groupChangeUpdate
      repo.getGroupChange(groupChangeUpdate.id).unsafeRunSync() shouldBe Some(groupChangeUpdate)
    }
  }

  "MySqlGroupChangeRepository.getGroupChange" should {
    "get a group change if it exists" in {
      val groupChanges = generateGroupChanges("group-1", 10)
      groupChanges.map(saveGroupChangeData(repo, _).unsafeRunSync())

      repo.getGroupChange(groupChanges.head.id).unsafeRunSync() shouldBe Some(groupChanges.head)
    }

    "return None if group change doesn't exist" in {
      val groupChanges = generateGroupChanges("group-1", 10)
      groupChanges.map(saveGroupChangeData(repo, _).unsafeRunSync())

      repo.getGroupChange("no-existo").unsafeRunSync() shouldBe None
    }
  }

  "MySqlGroupChangeRepository.getGroupChanges" should {
    "don't return nextId if page size < maxItems" in {
      val groupId = "group-id-1"
      val changes = generateGroupChanges(groupId, 50)
      changes.map(saveGroupChangeData(repo, _).unsafeRunSync())

      val expectedChanges = changes
        .sortBy(_.created.toEpochMilli)
        .reverse

      val listResponse = repo.getGroupChanges(groupId, None, 100).unsafeRunSync()
      listResponse.changes shouldBe expectedChanges
      listResponse.nextId shouldBe None
    }

    "get group changes properly using a maxItems of 1" in {
      val groupId = "group-id-1"
      val changes = generateGroupChanges(groupId, 50)
      changes.map(saveGroupChangeData(repo, _).unsafeRunSync())

      val changesSorted = changes
        .sortBy(_.created.toEpochMilli)
        .reverse

      val expectedChanges = Seq(changesSorted(0))

      val listResponse =
        repo.getGroupChanges(groupId, startFrom = None, maxItems = 1).unsafeRunSync()
      listResponse.changes shouldBe expectedChanges
      listResponse.nextId shouldBe Some(1)
    }

    "page group changes using a startFrom and maxItems" in {
      val groupId = "group-id-1"
      val changes = generateGroupChanges(groupId, 50)
      changes.map(saveGroupChangeData(repo, _).unsafeRunSync())

      val changesSorted = changes
        .sortBy(_.created.toEpochMilli)
        .reverse

      val expectedPageOne = Seq(changesSorted(0))
      val expectedPageOneNext = Some(1)
      val expectedPageTwo = Seq(changesSorted(1))
      val expectedPageTwoNext = Some(2)
      val expectedPageThree = Seq(changesSorted(2))
      val expectedPageThreeNext = Some(3)

      // get first page
      val pageOne =
        repo.getGroupChanges(groupId, startFrom = None, maxItems = 1).unsafeRunSync()
      pageOne.changes shouldBe expectedPageOne
      pageOne.nextId shouldBe expectedPageOneNext

      // get second page
      val pageTwo =
        repo
          .getGroupChanges(groupId, startFrom = pageOne.nextId, maxItems = 1)
          .unsafeRunSync()
      pageTwo.changes shouldBe expectedPageTwo
      pageTwo.nextId shouldBe expectedPageTwoNext

      // get final page
      val pageThree =
        repo
          .getGroupChanges(groupId, startFrom = pageTwo.nextId, maxItems = 1)
          .unsafeRunSync()
      pageThree.changes shouldBe expectedPageThree
      pageThree.nextId shouldBe expectedPageThreeNext
    }
  }
}
