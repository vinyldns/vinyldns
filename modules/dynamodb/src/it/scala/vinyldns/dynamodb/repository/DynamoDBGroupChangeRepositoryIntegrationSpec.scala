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
import org.joda.time.DateTime
import vinyldns.core.TestMembershipData._
import vinyldns.core.domain.membership.{Group, GroupChange, GroupChangeType}

import scala.concurrent.duration._
import scala.util.Random

class DynamoDBGroupChangeRepositoryIntegrationSpec extends DynamoDBIntegrationSpec {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)
  private implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_.isAfter(_))

  private val GROUP_CHANGES_TABLE = "group-changes-live"

  private val tableConfig = DynamoDBRepositorySettings(s"$GROUP_CHANGES_TABLE", 30, 30)

  private var repo: DynamoDBGroupChangeRepository = _

  private val randomTimeGroup: Group = Group(
    "randomTime",
    "test@test.com",
    Some("changes have random time stamp"),
    memberIds = Set(listOfDummyUsers(0).id)
  )
  // making distinct, multiple changes with the same time throws this test
  private val randomTimes: List[Int] = List.range(0, 200).map(_ => Random.nextInt(1000)).distinct

  private val listOfRandomTimeGroupChanges: List[GroupChange] = randomTimes.zipWithIndex.map {
    case (randomTime, i) =>
      GroupChange(
        randomTimeGroup,
        GroupChangeType.Update,
        dummyUser.id,
        created = now.minusSeconds(randomTime),
        id = s"random-time-$i"
      )
  }

  private val groupChanges = Seq(okGroupChange, okGroupChangeUpdate, okGroupChangeDelete) ++
    listOfDummyGroupChanges ++ listOfRandomTimeGroupChanges

  def setup(): Unit = {
    repo = DynamoDBGroupChangeRepository(tableConfig, dynamoIntegrationConfig).unsafeRunSync()

    // Create all the changes
    val savedGroupChanges = groupChanges.map(repo.save(_)).toList.parSequence

    // Wait until all of the changes are done
    savedGroupChanges.unsafeRunTimed(5.minutes).getOrElse(fail("timeout waiting for data load"))
  }

  def tearDown(): Unit = {
    val request = new DeleteTableRequest().withTableName(GROUP_CHANGES_TABLE)
    repo.dynamoDBHelper.deleteTable(request).unsafeRunSync()
  }

  "DynamoDBGroupChangeRepository" should {
    "get a group change by id" in {
      val targetGroupChange = okGroupChange
      repo.getGroupChange(targetGroupChange.id).unsafeRunSync() shouldBe Some(targetGroupChange)
    }

    "return none when no matching id is found" in {
      repo.getGroupChange("NotFound").unsafeRunSync() shouldBe None
    }

    "save a group change with oldGroup = None" in {
      val targetGroupChange = okGroupChange

      val test =
        for {
          saved <- repo.save(targetGroupChange)
          retrieved <- repo.getGroupChange(saved.id)
        } yield retrieved

      test.unsafeRunSync() shouldBe Some(targetGroupChange)
    }

    "save a group change with oldGroup set" in {
      val targetGroupChange = okGroupChangeUpdate

      val test =
        for {
          saved <- repo.save(targetGroupChange)
          retrieved <- repo.getGroupChange(saved.id)
        } yield retrieved

      test.unsafeRunSync() shouldBe Some(targetGroupChange)
    }

    "getGroupChanges should return the recent changes and the correct last key" in {
      val retrieved = repo.getGroupChanges(oneUserDummyGroup.id, None, 100).unsafeRunSync()
      retrieved.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(0, 100)
      retrieved.lastEvaluatedTimeStamp shouldBe Some(
        listOfDummyGroupChanges(99).created.getMillis.toString
      )
    }

    "getGroupChanges should start using the time startFrom" in {
      val retrieved = repo
        .getGroupChanges(
          oneUserDummyGroup.id,
          Some(listOfDummyGroupChanges(50).created.getMillis.toString),
          100
        )
        .unsafeRunSync()
      retrieved.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(51, 151)
      retrieved.lastEvaluatedTimeStamp shouldBe Some(
        listOfDummyGroupChanges(150).created.getMillis.toString
      )
    }

    "getGroupChanges returns entire page and nextId = None if there are less than maxItems left" in {
      val retrieved = repo
        .getGroupChanges(
          oneUserDummyGroup.id,
          Some(listOfDummyGroupChanges(200).created.getMillis.toString),
          100
        )
        .unsafeRunSync()
      retrieved.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(201, 300)
      retrieved.lastEvaluatedTimeStamp shouldBe None
    }

    "getGroupChanges returns 3 pages of items" in {
      val page1 = repo.getGroupChanges(oneUserDummyGroup.id, None, 100).unsafeRunSync()
      val page2 = repo
        .getGroupChanges(oneUserDummyGroup.id, page1.lastEvaluatedTimeStamp, 100)
        .unsafeRunSync()
      val page3 = repo
        .getGroupChanges(oneUserDummyGroup.id, page2.lastEvaluatedTimeStamp, 100)
        .unsafeRunSync()
      val page4 = repo
        .getGroupChanges(oneUserDummyGroup.id, page3.lastEvaluatedTimeStamp, 100)
        .unsafeRunSync()

      page1.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(0, 100)
      page1.lastEvaluatedTimeStamp shouldBe Some(
        listOfDummyGroupChanges(99).created.getMillis.toString
      )
      page2.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(100, 200)
      page2.lastEvaluatedTimeStamp shouldBe Some(
        listOfDummyGroupChanges(199).created.getMillis.toString
      )
      page3.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(200, 300)
      page3.lastEvaluatedTimeStamp shouldBe Some(
        listOfDummyGroupChanges(299).created.getMillis.toString
      ) // the limit was reached before the end of list
      page4.changes should contain theSameElementsAs List() // no matches found in the rest of the list
      page4.lastEvaluatedTimeStamp shouldBe None
    }

    "getGroupChanges should return `maxItem` items" in {
      val retrieved = repo.getGroupChanges(oneUserDummyGroup.id, None, 5).unsafeRunSync()
      retrieved.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(0, 5)
      retrieved.lastEvaluatedTimeStamp shouldBe Some(
        listOfDummyGroupChanges(4).created.getMillis.toString
      )
    }

    "getGroupChanges should handle changes inserted in random order" in {
      // group changes have a random time stamp and inserted in random order
      val retrieved = repo.getGroupChanges(randomTimeGroup.id, None, 100).unsafeRunSync()
      val sorted = listOfRandomTimeGroupChanges.sortBy(_.created)
      retrieved.changes should contain theSameElementsAs sorted.slice(0, 100)
      retrieved.lastEvaluatedTimeStamp shouldBe Some(sorted(99).created.getMillis.toString)
    }
  }
}
