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

import java.util.UUID

import cats.implicits._
import com.amazonaws.services.dynamodbv2.model._
import org.joda.time.DateTime
import vinyldns.core.domain.record.{ChangeSet, ChangeSetStatus, RecordSetChange}
import vinyldns.core.domain.zone.{Zone, ZoneStatus}
import vinyldns.core.TestMembershipData.abcAuth
import vinyldns.core.TestZoneData.testConnection
import vinyldns.core.TestRecordSetData._

import scala.concurrent.duration._

class DynamoDBRecordChangeRepositoryIntegrationSpec extends DynamoDBIntegrationSpec {

  private val recordChangeTable = "record-change-live"

  private val tableConfig = DynamoDBRepositorySettings(s"$recordChangeTable", 30, 30)

  private var repo: DynamoDBRecordChangeRepository = _

  private val user = abcAuth.signedInUser.userName
  private val auth = abcAuth

  private val zoneA = Zone(
    s"live-test-$user.zone-small.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection
  )
  private val zoneB = Zone(
    s"live-test-$user.zone-large.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection
  )

  private val recordSetA =
    for {
      rsTemplate <- Seq(rsOk, aaaa, cname)
    } yield rsTemplate.copy(
      zoneId = zoneA.id,
      name = s"${rsTemplate.typ.toString}-${zoneA.account}.",
      ttl = 100,
      created = DateTime.now(),
      id = UUID.randomUUID().toString
    )

  private val recordSetB =
    for {
      i <- 1 to 3
    } yield rsOk.copy(
      zoneId = zoneB.id,
      name = s"${rsOk.typ.toString}-${zoneB.account}-$i.",
      ttl = 100,
      created = DateTime.now(),
      id = UUID.randomUUID().toString
    )

  private val updateRecordSetA =
    for {
      rsTemplate <- Seq(rsOk, aaaa, cname)
    } yield rsTemplate.copy(
      zoneId = zoneA.id,
      name = s"${rsTemplate.typ.toString}-${zoneA.account}.",
      ttl = 1000,
      created = DateTime.now(),
      id = UUID.randomUUID().toString
    )

  private val recordSetChangesA = {
    for {
      rs <- recordSetA
    } yield makeTestAddChange(rs, zoneA, auth.userId)
  }.sortBy(_.id)

  private val recordSetChangesB = {
    for {
      rs <- recordSetB
    } yield makeTestAddChange(rs, zoneB, auth.userId)
  }.sortBy(_.id)

  private val recordSetChangesC = {
    for {
      rs <- recordSetA
    } yield makePendingTestDeleteChange(rs, zoneA, auth.userId)
  }.sortBy(_.id)

  private val recordSetChangesD = {
    for {
      rs <- recordSetA
      updateRs <- updateRecordSetA
    } yield makePendingTestUpdateChange(rs, updateRs, zoneA, auth.userId)
  }.sortBy(_.id)

  private val changeSetA = ChangeSet(recordSetChangesA)
  private val changeSetB = ChangeSet(recordSetChangesB)
  private val changeSetC =
    ChangeSet(recordSetChangesC).copy(status = ChangeSetStatus.Applied)
  private val changeSetD = ChangeSet(recordSetChangesD)
    .copy(createdTimestamp = changeSetA.createdTimestamp + 1000) // make sure D is created AFTER A
  private val changeSets = List(changeSetA, changeSetB, changeSetC, changeSetD)

  //This zone is to test listing record changes in correct order
  private val zoneC = Zone(
    s"live-test-$user.record-changes.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection
  )
  private val baseTime = DateTime.now()
  private val timeOrder = List(
    baseTime.minusSeconds(8000),
    baseTime.minusSeconds(7000),
    baseTime.minusSeconds(6000),
    baseTime.minusSeconds(5000),
    baseTime.minusSeconds(4000),
    baseTime.minusSeconds(3000),
    baseTime.minusSeconds(2000),
    baseTime.minusSeconds(1000),
    baseTime
  )

  private val recordSetsC =
    for {
      rsTemplate <- Seq(rsOk, aaaa, cname)
    } yield rsTemplate.copy(
      zoneId = zoneC.id,
      name = s"${rsTemplate.typ.toString}-${zoneC.account}.",
      ttl = 100,
      id = UUID.randomUUID().toString
    )

  private val updateRecordSetsC =
    for {
      rsTemplate <- Seq(rsOk, aaaa, cname)
    } yield rsTemplate.copy(
      zoneId = zoneC.id,
      name = s"${rsTemplate.typ.toString}-${zoneC.account}.",
      ttl = 1000,
      id = UUID.randomUUID().toString
    )

  private val recordSetChangesCreateC = {
    for {
      (rs, index) <- recordSetsC.zipWithIndex
    } yield makeTestAddChange(rs, zoneC, auth.userId).copy(created = timeOrder(index))
  }

  private val recordSetChangesUpdateC = {
    for {
      (rs, index) <- recordSetsC.zipWithIndex
    } yield makePendingTestUpdateChange(rs, updateRecordSetsC(index), zoneC, auth.userId)
      .copy(created = timeOrder(index + 3))
  }

  private val recordSetChangesDeleteC = {
    for {
      (rs, index) <- recordSetsC.zipWithIndex
    } yield makePendingTestDeleteChange(rs, zoneC, auth.userId).copy(created = timeOrder(index + 6))
  }

  private val changeSetCreateC = ChangeSet(recordSetChangesCreateC)
  private val changeSetUpdateC = ChangeSet(recordSetChangesUpdateC)
  private val changeSetDeleteC = ChangeSet(recordSetChangesDeleteC)
  private val changeSetsC = List(changeSetCreateC, changeSetUpdateC, changeSetDeleteC)
  private val recordSetChanges: List[RecordSetChange] =
    (recordSetChangesCreateC ++ recordSetChangesUpdateC ++ recordSetChangesDeleteC)
      .sortBy(_.created.getMillis)
      .toList
      .reverse // Changes are retrieved by time stamp in decending order

  def setup(): Unit = {
    repo = DynamoDBRecordChangeRepository(tableConfig, dynamoIntegrationConfig).unsafeRunSync()

    changeSets.foreach { changeSet =>
      // Save the change set
      val savedChangeSet = repo.save(changeSet)

      // Wait until all of the change sets are saved
      savedChangeSet.unsafeRunTimed(5.minutes).getOrElse(fail("error in change set load"))
    }

    changeSetsC.foreach { changeSet =>
      // Save the change set
      val savedChangeSet = repo.save(changeSet)

      // Wait until all of the change sets are saved
      savedChangeSet.unsafeRunTimed(5.minutes).getOrElse(fail("error in change set load"))
    }
  }

  def tearDown(): Unit = {
    val request = new DeleteTableRequest().withTableName(recordChangeTable)
    repo.dynamoDBHelper.deleteTable(request).unsafeRunSync()
  }

  "DynamoDBRepository" should {
    "get a record change set by id" in {
      val testRecordSetChange = pendingCreateAAAA.copy(id = genString)

      val f =
        for {
          saved <- repo.save(ChangeSet(Seq(testRecordSetChange)))
          retrieved <- repo.getRecordSetChange(saved.zoneId, testRecordSetChange.id)
        } yield retrieved

      f.unsafeRunSync() shouldBe Some(testRecordSetChange)
    }

    "list all record set changes in zone C" in {
      val testFuture = repo.listRecordSetChanges(zoneC.id)
      testFuture.unsafeRunSync().items shouldBe recordSetChanges
    }

    "list record set changes with a page size of one" in {
      val testFuture = repo.listRecordSetChanges(zoneC.id, maxItems = 1)
      testFuture.unsafeRunSync().items shouldBe recordSetChanges.take(1)
    }

    "list record set changes with page size of one and reuse key to get another page with size of two" in {
      val testFuture = for {
        listOne <- repo.listRecordSetChanges(zoneC.id, maxItems = 1)
        listTwo <- repo.listRecordSetChanges(zoneC.id, startFrom = listOne.nextId, maxItems = 2)
      } yield listTwo

      val result = testFuture.unsafeRunSync()

      val page2 = result.items
      page2 shouldBe recordSetChanges.slice(1, 3)
    }

    "return an empty list and nextId of None when passing last record as start" in {
      val testFuture = for {
        listOne <- repo.listRecordSetChanges(zoneC.id, maxItems = 9)
        listTwo <- repo.listRecordSetChanges(zoneC.id, startFrom = listOne.nextId, maxItems = 2)
      } yield listTwo

      val result = testFuture.unsafeRunSync()

      result.nextId shouldBe None
      result.items shouldBe List()
    }

    "have nextId of None when exhausting record changes" in {
      val testFuture = repo.listRecordSetChanges(zoneC.id, maxItems = 10)
      testFuture.unsafeRunSync().nextId shouldBe None
    }

    "return empty list with startFrom of zero" in {
      val testFuture = repo.listRecordSetChanges(zoneC.id, startFrom = Some("0"))
      val result = testFuture.unsafeRunSync()
      result.nextId shouldBe None
      result.items shouldBe List()
    }
  }
}
