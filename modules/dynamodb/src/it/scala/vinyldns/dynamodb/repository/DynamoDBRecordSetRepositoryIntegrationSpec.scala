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

import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model._
import org.joda.time.DateTime
import vinyldns.core.domain.membership.User
import vinyldns.core.domain.zone.{Zone, ZoneStatus}
import vinyldns.core.TestZoneData.{okZone, testConnection}
import vinyldns.core.TestRecordSetData._
import vinyldns.core.domain.record._

import scala.concurrent.duration._

class DynamoDBRecordSetRepositoryIntegrationSpec
    extends DynamoDBIntegrationSpec
    with DynamoDBRecordSetConversions {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)
  private val recordSetTable = "record-sets-live"
  private[repository] val recordSetTableName: String = recordSetTable

  private val tableConfig = DynamoDBRepositorySettings(s"$recordSetTable", 30, 30)

  private var repo: DynamoDBRecordSetRepository = _

  private val users =
    for (i <- 1 to 3)
      yield User(s"live-test-acct$i", "key", "secret")

  private val zones =
    for {
      acct <- users
      i <- 1 to 3
    } yield Zone(
      s"live-test-${acct.userName}.zone$i.",
      "test@test.com",
      status = ZoneStatus.Active,
      connection = testConnection
    )

  private val rsTemplates = Seq(rsOk, aaaa, cname)

  private val rsQualifiedStatus = Seq("-dotless", "-dotted.")

  private val recordSets =
    for {
      zone <- zones
      rsTemplate <- rsTemplates
      rsQualifiedStatus <- rsQualifiedStatus
    } yield rsTemplate.copy(
      zoneId = zone.id,
      name = s"${rsTemplate.typ.toString}-${zone.account}$rsQualifiedStatus",
      ttl = 100,
      created = DateTime.now(),
      id = UUID.randomUUID().toString
    )

  def setup(): Unit = {
    repo = DynamoDBRecordSetRepository(tableConfig, dynamoIntegrationConfig).unsafeRunSync()

    // Create all the items
    val results = recordSets.map(repo.putRecordSet(_)).toList.parSequence

    // Wait until all of the data is stored
    results.unsafeRunTimed(5.minutes).getOrElse(fail("timeout waiting for data load"))
  }

  def tearDown(): Unit = {
    val request = new DeleteTableRequest().withTableName(recordSetTable)
    repo.dynamoDBHelper.deleteTable(request).unsafeRunSync()
  }

  "DynamoDBRecordSetRepository" should {
    "fail to return records with listRecordSets if zoneId not given" in {
      val testFuture = repo.listRecordSets(
        zoneId = None,
        startFrom = None,
        maxItems = None,
        recordNameFilter = None,
        recordTypeFilter = None,
        nameSort = NameSort.ASC
      )

      assertThrows[UnsupportedDynamoDBRepoFunction](testFuture.unsafeRunSync())
    }

    "get a record set by id" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = Some(testRecordSet.zoneId),
        startFrom = None,
        maxItems = None,
        recordNameFilter = None,
        recordTypeFilter = None,
        nameSort = NameSort.ASC
      )

      testFuture.unsafeRunSync().recordSets should contain(testRecordSet)
    }

    "get a record set count" in {
      val testRecordSet = recordSets.head
      val expected = 6
      val testFuture = repo.getRecordSetCount(testRecordSet.zoneId)
      testFuture.unsafeRunSync() shouldBe expected
    }

    "get a record set by record set id and zone id" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.getRecordSet(testRecordSet.id)
      testFuture.unsafeRunSync() shouldBe Some(testRecordSet)
    }

    "get a record set by zone id, name, type" in {
      val testRecordSet = recordSets.head
      val testFuture =
        repo.getRecordSets(testRecordSet.zoneId, testRecordSet.name, testRecordSet.typ)
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "get a record set by zone id, case-insensitive name, type" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.getRecordSets(
        testRecordSet.zoneId,
        testRecordSet.name.toUpperCase(),
        testRecordSet.typ
      )
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "get a fully qualified record set by zone id, trailing dot-insensitive name, type" in {
      val testRecordSet = recordSets.find(_.name.endsWith(".")).get
      val testFuture =
        repo.getRecordSets(testRecordSet.zoneId, testRecordSet.name.dropRight(1), testRecordSet.typ)
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "get a relative record set by zone id, trailing dot-insensitive name, type" in {
      val testRecordSet = recordSets.find(_.name.endsWith("dotless")).get
      val testFuture =
        repo.getRecordSets(testRecordSet.zoneId, testRecordSet.name.concat("."), testRecordSet.typ)
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "get a record set by zone id, name" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.getRecordSetsByName(testRecordSet.zoneId, testRecordSet.name)
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "get a record set by zone id, case-insensitive name" in {
      val testRecordSet = recordSets.head
      val testFuture =
        repo.getRecordSetsByName(testRecordSet.zoneId, testRecordSet.name.toUpperCase())
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "get a fully qualified record set by zone id, trailing dot-insensitive name" in {
      val testRecordSet = recordSets.find(_.name.endsWith(".")).get
      val testFuture =
        repo.getRecordSetsByName(testRecordSet.zoneId, testRecordSet.name.dropRight(1))
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "get a relative record set by zone id, trailing dot-insensitive name" in {
      val testRecordSet = recordSets.find(_.name.endsWith("dotless")).get
      val testFuture =
        repo.getRecordSetsByName(testRecordSet.zoneId, testRecordSet.name.concat("."))
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "list record sets with page size of 1 returns recordSets[0] only" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = Some(testRecordSet.zoneId),
        startFrom = None,
        maxItems = Some(1),
        recordNameFilter = None,
        recordTypeFilter = None,
        nameSort = NameSort.ASC
      )

      val foundRecordSet = testFuture.unsafeRunSync()

      foundRecordSet.recordSets should contain(recordSets(0))
      foundRecordSet.recordSets shouldNot contain(recordSets(1))
      foundRecordSet.nextId.get.split('~')(2) shouldBe recordSets(0).id
    }

    "list record sets with page size of 1 reusing key with page size of 1 returns recordSets[0] and recordSets[1]" in {
      val testRecordSet = recordSets.head
      val testFutureOne = repo.listRecordSets(
        zoneId = Some(testRecordSet.zoneId),
        startFrom = None,
        maxItems = Some(1),
        recordNameFilter = None,
        recordTypeFilter = None,
        nameSort = NameSort.ASC
      )

      val foundRecordSet = testFutureOne.unsafeRunSync()

      foundRecordSet.recordSets should contain(recordSets(0))
      foundRecordSet.recordSets shouldNot contain(recordSets(1))
      val key = foundRecordSet.nextId
      val testFutureTwo = repo.listRecordSets(
        zoneId = Some(testRecordSet.zoneId),
        startFrom = key,
        maxItems = Some(1),
        recordNameFilter = None,
        recordTypeFilter = None,
        nameSort = NameSort.ASC
      )

      val foundRecordSetTwo = testFutureTwo.unsafeRunSync()

      foundRecordSetTwo.recordSets shouldNot contain(recordSets(0))
      foundRecordSetTwo.recordSets should contain(recordSets(1))
      foundRecordSetTwo.recordSets shouldNot contain(recordSets(2))
      foundRecordSetTwo.nextId.get.split('~')(2) shouldBe recordSets(1).id
    }

    "list record sets page size of 1 then reusing key with page size of 2 returns recordSets[0], recordSets[1,2]" in {
      val testRecordSet = recordSets.head
      val testFutureOne = repo.listRecordSets(
        zoneId = Some(testRecordSet.zoneId),
        startFrom = None,
        maxItems = Some(1),
        recordNameFilter = None,
        recordTypeFilter = None,
        nameSort = NameSort.ASC
      )

      val foundRecordSet = testFutureOne.unsafeRunSync()
      foundRecordSet.recordSets should contain(recordSets(0))
      foundRecordSet.recordSets shouldNot contain(recordSets(1))
      val key = foundRecordSet.nextId
      val testFutureTwo = repo.listRecordSets(
        zoneId = Some(testRecordSet.zoneId),
        startFrom = key,
        maxItems = Some(2),
        recordNameFilter = None,
        recordTypeFilter = None,
        nameSort = NameSort.ASC
      )

      val foundRecordSetTwo = testFutureTwo.unsafeRunSync()
      foundRecordSetTwo.recordSets shouldNot contain(recordSets(0))
      foundRecordSetTwo.recordSets should contain(recordSets(1))
      foundRecordSetTwo.recordSets should contain(recordSets(2))
      foundRecordSetTwo.nextId.get.split('~')(2) shouldBe recordSets(2).id
    }

    "return an empty list and nextId of None when passing last record as start" in {
      val testRecordSet = recordSets.head
      val testFutureOne = repo.listRecordSets(
        zoneId = Some(testRecordSet.zoneId),
        startFrom = None,
        maxItems = Some(6),
        recordNameFilter = None,
        recordTypeFilter = None,
        nameSort = NameSort.ASC
      )

      val foundRecordSet = testFutureOne.unsafeRunSync()

      foundRecordSet.recordSets should contain(recordSets(0))
      foundRecordSet.recordSets should contain(recordSets(1))
      foundRecordSet.recordSets should contain(recordSets(2))
      foundRecordSet.recordSets should contain(recordSets(3))
      foundRecordSet.recordSets should contain(recordSets(4))
      foundRecordSet.recordSets should contain(recordSets(5))
      val key = foundRecordSet.nextId

      val testFutureTwo = repo.listRecordSets(
        zoneId = Some(testRecordSet.zoneId),
        startFrom = key,
        maxItems = Some(6),
        recordNameFilter = None,
        recordTypeFilter = None,
        nameSort = NameSort.ASC
      )

      val foundRecordSetTwo = testFutureTwo.unsafeRunSync()
      foundRecordSetTwo.recordSets shouldBe List()
      foundRecordSetTwo.nextId shouldBe None
    }

    "have nextId of None when exhausting recordSets" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = Some(testRecordSet.zoneId),
        startFrom = None,
        maxItems = Some(7),
        recordNameFilter = None,
        recordTypeFilter = None,
        nameSort = NameSort.ASC
      )

      val foundRecordSet = testFuture.unsafeRunSync()
      foundRecordSet.recordSets should contain(recordSets(0))
      foundRecordSet.recordSets should contain(recordSets(1))
      foundRecordSet.recordSets should contain(recordSets(2))
      foundRecordSet.recordSets should contain(recordSets(3))
      foundRecordSet.recordSets should contain(recordSets(4))
      foundRecordSet.recordSets should contain(recordSets(5))
      foundRecordSet.nextId shouldBe None
    }

    "only retrieve recordSet with name containing 'AAAA'" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = Some(testRecordSet.zoneId),
        startFrom = None,
        maxItems = None,
        recordNameFilter = Some("AAAA"),
        recordTypeFilter = None,
        nameSort = NameSort.ASC
      )

      val foundRecordSet = testFuture.unsafeRunSync()
      foundRecordSet.recordSets shouldNot contain(recordSets(0))
      foundRecordSet.recordSets shouldNot contain(recordSets(1))
      foundRecordSet.recordSets should contain(recordSets(2))
      foundRecordSet.recordSets should contain(recordSets(3))
    }

    "retrieve all recordSets with names containing 'A'" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = Some(testRecordSet.zoneId),
        startFrom = None,
        maxItems = None,
        recordNameFilter = Some("A"),
        recordTypeFilter = None,
        nameSort = NameSort.ASC
      )

      val foundRecordSet = testFuture.unsafeRunSync()
      foundRecordSet.recordSets should contain(recordSets(0))
      foundRecordSet.recordSets should contain(recordSets(1))
      foundRecordSet.recordSets should contain(recordSets(2))
      foundRecordSet.recordSets should contain(recordSets(3))
      foundRecordSet.recordSets should contain(recordSets(4))
      foundRecordSet.recordSets should contain(recordSets(5))
    }

    "return an empty list if recordName filter had no match" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = Some(testRecordSet.zoneId),
        startFrom = None,
        maxItems = None,
        recordNameFilter = Some("Dummy"),
        recordTypeFilter = None,
        nameSort = NameSort.ASC
      )

      testFuture.unsafeRunSync().recordSets shouldBe List()
    }

    "apply a change set" in {
      val newRecordSets =
        for {
          i <- 1 to 1000
        } yield aaaa.copy(
          zoneId = "big-apply-zone",
          name = s"$i.apply.test.",
          id = UUID.randomUUID().toString
        )

      val pendingChanges = newRecordSets.map(makeTestAddChange(_, zones.head))

      val bigPendingChangeSet = ChangeSet(pendingChanges)

      try {
        val f = repo.apply(bigPendingChangeSet)

        val apply = f.unsafeRunTimed(1500.seconds)
        if (apply.isEmpty) {
          throw new RuntimeException("change set apply timed out")
        }

        // let's fail half of them
        val split = pendingChanges.grouped(pendingChanges.length / 2).toSeq
        val halfSuccess = split.head.map(_.successful)
        val halfFailed = split(1).map(_.failed())
        val halfFailedChangeSet = ChangeSet(halfSuccess ++ halfFailed)

        val nextUp = repo.apply(halfFailedChangeSet)
        val nextUpApply = nextUp.unsafeRunTimed(1500.seconds)
        if (nextUpApply.isEmpty) {
          throw new RuntimeException("nextUp change set apply timed out")
        }

        // let's run our query and see how long until we succeed(which will determine
        // how long it takes DYNAMO to update its index)
        var querySuccessful = false
        var retries = 1
        var recordSetsResult: List[RecordSet] = Nil
        while (!querySuccessful && retries <= 10) {
          // if we query now, we should get half that failed
          val rsQuery = repo.listRecordSets(
            zoneId = Some("big-apply-zone"),
            startFrom = None,
            maxItems = None,
            recordNameFilter = None,
            recordTypeFilter = None,
            nameSort = NameSort.ASC
          )

          recordSetsResult = rsQuery.unsafeRunTimed(30.seconds) match {
            case Some(result) => result.recordSets
            case None => throw new RuntimeException("Query timed out")
          }

          querySuccessful = recordSetsResult.length == halfSuccess.length
          retries += 1
          Thread.sleep(100)
        }

        querySuccessful shouldBe true

        // the result of the query should be the same as those pending that succeeded
        val expected = halfSuccess.map(_.recordSet)
        recordSetsResult should contain theSameElementsAs expected
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          fail("encountered error running apply test")
      }
    }

    "apply successful and pending creates, and delete failed creates" in {
      val zone = okZone
      val recordForSuccess = RecordSet(
        "test-create-converter",
        "createSuccess",
        RecordType.A,
        123,
        RecordSetStatus.Active,
        DateTime.now
      )
      val recordForPending = RecordSet(
        "test-create-converter",
        "createPending",
        RecordType.A,
        123,
        RecordSetStatus.Pending,
        DateTime.now
      )
      val recordForFailed = RecordSet(
        "test-create-converter",
        "failed",
        RecordType.A,
        123,
        RecordSetStatus.Inactive,
        DateTime.now
      )

      val successfulChange =
        RecordSetChange(
          zone,
          recordForSuccess,
          "abc",
          RecordSetChangeType.Create,
          RecordSetChangeStatus.Complete
        )

      val pendingChange =
        successfulChange.copy(recordSet = recordForPending, status = RecordSetChangeStatus.Pending)
      val failedChange =
        successfulChange.copy(recordSet = recordForFailed, status = RecordSetChangeStatus.Failed)

      // to be deleted - assume this was already saved as pending
      val existingPending = failedChange.copy(
        recordSet = recordForFailed.copy(status = RecordSetStatus.Pending),
        status = RecordSetChangeStatus.Pending
      )
      repo.apply(ChangeSet(existingPending)).unsafeRunSync()
      repo.getRecordSet(failedChange.recordSet.id).unsafeRunSync() shouldBe
        Some(existingPending.recordSet)

      repo.apply(ChangeSet(Seq(successfulChange, pendingChange, failedChange))).unsafeRunSync()

      // success and pending changes have records saved
      repo
        .getRecordSet(successfulChange.recordSet.id)
        .unsafeRunSync() shouldBe
        Some(successfulChange.recordSet)
      repo
        .getRecordSet(pendingChange.recordSet.id)
        .unsafeRunSync() shouldBe
        Some(pendingChange.recordSet)

      // check that the pending record was deleted because of failed record change
      repo
        .getRecordSet(failedChange.recordSet.id)
        .unsafeRunSync() shouldBe None
    }

    "apply successful updates and revert records for failed updates" in {
      val oldSuccess = aaaa.copy(zoneId = "test-update-converter", ttl = 100, id = "success")
      val updateSuccess = oldSuccess.copy(ttl = 200)

      val oldPending = aaaa.copy(zoneId = "test-update-converter", ttl = 100, id = "pending")
      val updatePending = oldPending.copy(ttl = 200, status = RecordSetStatus.PendingUpdate)

      val oldFailure = aaaa.copy(zoneId = "test-update-converter", ttl = 100, id = "failed")
      val updateFailure = oldFailure.copy(ttl = 200, status = RecordSetStatus.Inactive)

      val successfulUpdate = makeCompleteTestUpdateChange(oldSuccess, updateSuccess)
      val pendingUpdate = makePendingTestUpdateChange(oldPending, updatePending)
      val failedUpdate = pendingUpdate.copy(
        recordSet = updateFailure,
        updates = Some(oldFailure),
        status = RecordSetChangeStatus.Failed
      )
      val updateChanges = Seq(successfulUpdate, pendingUpdate, failedUpdate)
      val updateChangeSet = ChangeSet(updateChanges)

      // save old recordsets
      val oldAddChanges = updateChanges
        .map(
          _.copy(changeType = RecordSetChangeType.Create, status = RecordSetChangeStatus.Complete)
        )
      val oldChangeSet = ChangeSet(oldAddChanges)
      repo.apply(oldChangeSet).unsafeRunSync() shouldBe oldChangeSet

      // apply updates
      repo.apply(updateChangeSet).unsafeRunSync() shouldBe updateChangeSet

      // ensure that success and pending updates store the new recordsets
      repo
        .getRecordSet(successfulUpdate.recordSet.id)
        .unsafeRunSync() shouldBe
        Some(successfulUpdate.recordSet)

      repo
        .getRecordSet(pendingUpdate.recordSet.id)
        .unsafeRunSync() shouldBe
        Some(pendingUpdate.recordSet)

      // ensure that failure update store the old recordset
      repo
        .getRecordSet(failedUpdate.recordSet.id)
        .unsafeRunSync() shouldBe
        failedUpdate.updates
      repo
        .getRecordSet(failedUpdate.recordSet.id)
        .unsafeRunSync() shouldNot
        be(Some(failedUpdate.recordSet))
    }

    "apply successful deletes, save pending deletes, and revert failed deletes" in {
      val oldSuccess = aaaa.copy(zoneId = "test-update-converter", id = "success")
      val oldPending = aaaa.copy(zoneId = "test-update-converter", id = "pending")
      val oldFailure = aaaa.copy(zoneId = "test-update-converter", id = "failed")

      val successfulDelete =
        makePendingTestDeleteChange(oldSuccess).copy(status = RecordSetChangeStatus.Complete)
      val pendingDelete =
        makePendingTestDeleteChange(oldPending).copy(status = RecordSetChangeStatus.Pending)
      val failedDelete =
        makePendingTestDeleteChange(oldFailure).copy(status = RecordSetChangeStatus.Failed)

      val deleteChanges = Seq(successfulDelete, pendingDelete, failedDelete)
      val deleteChangeSet = ChangeSet(deleteChanges)

      // save old recordsets
      val oldAddChanges = deleteChanges
        .map(
          _.copy(changeType = RecordSetChangeType.Create, status = RecordSetChangeStatus.Complete)
        )
      val oldChangeSet = ChangeSet(oldAddChanges)
      repo.apply(oldChangeSet).unsafeRunSync() shouldBe oldChangeSet

      // apply deletes
      repo.apply(deleteChangeSet).unsafeRunSync() shouldBe deleteChangeSet

      // ensure that successful change deletes the recordset
      repo
        .getRecordSet(successfulDelete.recordSet.id)
        .unsafeRunSync() shouldBe None

      // ensure that pending change saves the recordset
      repo
        .getRecordSet(pendingDelete.recordSet.id)
        .unsafeRunSync() shouldBe
        Some(pendingDelete.recordSet)

      // ensure that failed delete keeps the recordset
      repo
        .getRecordSet(failedDelete.recordSet.id)
        .unsafeRunSync() shouldBe
        failedDelete.updates
    }
  }

}
