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
import java.util.UUID

import cats.scalatest.EitherMatchers
import org.joda.time.DateTime
import org.scalatest._
import scalikejdbc.DB
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.Zone
import vinyldns.mysql.TestMySqlInstance

class MySqlRecordSetRepositoryIntegrationSpec
  extends WordSpec
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with Matchers
    with EitherMatchers {

  import vinyldns.core.TestRecordSetData._
  import vinyldns.core.TestZoneData._
  private val repo = TestMySqlInstance.recordSetRepository.asInstanceOf[MySqlRecordSetRepository]

  override protected def beforeEach(): Unit = clear()

  override protected def afterAll(): Unit = clear()

  def clear(): Unit =
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM recordset")
    }

  def generateInserts(zone: Zone, count: Int): List[RecordSetChange] = {
    val newRecordSets =
      for {
        i <- 1 to count
      } yield
        aaaa.copy(
          zoneId = zone.id,
          name = s"$i-apply-test",
          id = UUID.randomUUID().toString)

    newRecordSets.map(makeTestAddChange(_, zone)).toList
  }

  def insert(zone: Zone, count: Int): List[RecordSetChange] = {
    val pendingChanges = generateInserts(zone, count)
    val bigPendingChangeSet = ChangeSet(pendingChanges)
    repo.apply(bigPendingChangeSet).unsafeRunSync()
    pendingChanges
  }

  def insert(changes: List[RecordSetChange]): Unit = {
    val bigPendingChangeSet = ChangeSet(changes)
    repo.apply(bigPendingChangeSet).unsafeRunSync()
    ()
  }

  "apply" should {
    "properly revert changes that fail processing" in {
      val existing = insert(okZone, 2).map(_.recordSet)

      val addChange = makeTestAddChange(rsOk.copy(id = UUID.randomUUID().toString))
        .copy(status = RecordSetChangeStatus.Failed)
      val updateChange = makePendingTestUpdateChange(existing(0), existing(0).copy(name = "updated-name"))
        .copy(status = RecordSetChangeStatus.Failed)
      val deleteChange = makePendingTestDeleteChange(existing(1))
        .copy(status = RecordSetChangeStatus.Failed)

      repo.apply(ChangeSet(Seq(addChange, updateChange, deleteChange))).unsafeRunSync()
      repo.getRecordSet(rsOk.zoneId, rsOk.id).unsafeRunSync() shouldBe None
      repo.getRecordSet(existing(0).zoneId, existing(0).id).unsafeRunSync() shouldBe Some(existing(0))
      repo.getRecordSet(existing(1).zoneId, existing(1).id).unsafeRunSync() shouldBe Some(existing(1))
    }

    "apply successful and pending creates, and delete failed creates" in {
      val zone = okZone
      val recordForSuccess = RecordSet("test-create-converter", "createSuccess", RecordType.A, 123,
        RecordSetStatus.Active, DateTime.now)
      val recordForPending = RecordSet("test-create-converter", "createPending", RecordType.A, 123,
        RecordSetStatus.Pending, DateTime.now)
      val recordForFailed = RecordSet("test-create-converter", "failed", RecordType.A, 123,
        RecordSetStatus.Inactive, DateTime.now)

      val successfulChange =
        RecordSetChange(
          zone,
          recordForSuccess,
          "abc",
          RecordSetChangeType.Create,
          RecordSetChangeStatus.Complete)

      val pendingChange = successfulChange.copy(recordSet = recordForPending, status = RecordSetChangeStatus.Pending)
      val failedChange = successfulChange.copy(recordSet = recordForFailed, status = RecordSetChangeStatus.Failed)

      // to be deleted - assume this was already saved as pending
      val existingPending = failedChange.copy(recordSet = recordForFailed.copy(status = RecordSetStatus.Pending),
        status = RecordSetChangeStatus.Pending)
      repo.apply(ChangeSet(existingPending)).unsafeRunSync()
      repo.getRecordSet(recordForFailed.zoneId, failedChange.recordSet.id).unsafeRunSync() shouldBe
        Some(existingPending.recordSet)

      repo.apply(ChangeSet(Seq(successfulChange, pendingChange, failedChange))).unsafeRunSync()

      // success and pending changes have records saved
      repo.getRecordSet(successfulChange.recordSet.zoneId, successfulChange.recordSet.id).unsafeRunSync() shouldBe
        Some(successfulChange.recordSet)
      repo.getRecordSet(pendingChange.recordSet.zoneId, pendingChange.recordSet.id).unsafeRunSync() shouldBe
        Some(pendingChange.recordSet)

      // check that the pending record was deleted because of failed record change
      repo.getRecordSet(failedChange.recordSet.zoneId, failedChange.recordSet.id).unsafeRunSync() shouldBe None
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
        status = RecordSetChangeStatus.Failed)
      val updateChanges = Seq(successfulUpdate, pendingUpdate, failedUpdate)
      val updateChangeSet = ChangeSet(updateChanges)

      // save old recordsets
      val oldAddChanges = updateChanges
        .map(_.copy(changeType = RecordSetChangeType.Create, status = RecordSetChangeStatus.Complete))
      val oldChangeSet = ChangeSet(oldAddChanges)
      repo.apply(oldChangeSet).unsafeRunSync() shouldBe oldChangeSet

      // apply updates
      repo.apply(updateChangeSet).unsafeRunSync() shouldBe updateChangeSet

      // ensure that success and pending updates store the new recordsets
      repo.getRecordSet(successfulUpdate.recordSet.zoneId, successfulUpdate.recordSet.id).unsafeRunSync() shouldBe
        Some(successfulUpdate.recordSet)

      repo.getRecordSet(pendingUpdate.recordSet.zoneId, pendingUpdate.recordSet.id).unsafeRunSync() shouldBe
        Some(pendingUpdate.recordSet)

      // ensure that failure update store the old recordset
      repo.getRecordSet(failedUpdate.recordSet.zoneId, failedUpdate.recordSet.id).unsafeRunSync() shouldBe
        failedUpdate.updates
      repo.getRecordSet(failedUpdate.recordSet.zoneId, failedUpdate.recordSet.id).unsafeRunSync() shouldNot
        be(Some(failedUpdate.recordSet))
    }

    "apply successful deletes, save pending deletes, and revert failed deletes" in {
      val oldSuccess = aaaa.copy(zoneId = "test-update-converter", id = "success")
      val oldPending = aaaa.copy(zoneId = "test-update-converter", id = "pending")
      val oldFailure = aaaa.copy(zoneId = "test-update-converter", id = "failed")

      val successfulDelete = makePendingTestDeleteChange(oldSuccess).copy(status = RecordSetChangeStatus.Complete)
      val pendingDelete = makePendingTestDeleteChange(oldPending).copy(status = RecordSetChangeStatus.Pending)
      val failedDelete = makePendingTestDeleteChange(oldFailure).copy(status = RecordSetChangeStatus.Failed)

      val deleteChanges = Seq(successfulDelete, pendingDelete, failedDelete)
      val deleteChangeSet = ChangeSet(deleteChanges)

      // save old recordsets
      val oldAddChanges = deleteChanges
        .map(_.copy(changeType = RecordSetChangeType.Create, status = RecordSetChangeStatus.Complete))
      val oldChangeSet = ChangeSet(oldAddChanges)
      repo.apply(oldChangeSet).unsafeRunSync() shouldBe oldChangeSet

      // apply deletes
      repo.apply(deleteChangeSet).unsafeRunSync() shouldBe deleteChangeSet

      // ensure that successful change deletes the recordset
      repo.getRecordSet(successfulDelete.recordSet.zoneId, successfulDelete.recordSet.id).unsafeRunSync() shouldBe None

      // ensure that pending change saves the recordset
      repo.getRecordSet(pendingDelete.recordSet.zoneId, pendingDelete.recordSet.id).unsafeRunSync() shouldBe
        Some(pendingDelete.recordSet)

      // ensure that failed delete keeps the recordset
      repo.getRecordSet(failedDelete.recordSet.zoneId, failedDelete.recordSet.id).unsafeRunSync() shouldBe
        failedDelete.updates
    }
  }

  "inserting record sets" should {
    "properly add and delete DS records" in {
      val addChange = makeTestAddChange(ds, okZone)
      val testRecord = addChange.recordSet
      val deleteChange = makePendingTestDeleteChange(testRecord, okZone).copy(status = RecordSetChangeStatus.Complete)

      val dbCalls = for {
        _ <- repo.apply(ChangeSet(addChange))
        get <- repo.getRecordSet(testRecord.zoneId, testRecord.id)
        _ <- repo.apply(ChangeSet(deleteChange))
        finalGet <- repo.getRecordSet(testRecord.zoneId, testRecord.id)
      } yield (get, finalGet)

      val (get, finalGet) = dbCalls.unsafeRunSync()
      get shouldBe Some(testRecord)
      finalGet shouldBe None
    }
    "be idempotent for inserts" in {
      val pendingChanges = generateInserts(okZone, 1000)
      val bigPendingChangeSet = ChangeSet(pendingChanges)
      repo.apply(bigPendingChangeSet).unsafeRunSync()
      repo.apply(bigPendingChangeSet).attempt.unsafeRunSync() shouldBe right
    }
    "work for multiple inserts" in {
      val pendingChanges = generateInserts(okZone, 20)

      val bigPendingChangeSet = ChangeSet(pendingChanges)
      repo.apply(bigPendingChangeSet).unsafeRunSync()

      // let's make sure we have all 1000 records
      val recordCount = repo.getRecordSetCount(okZone.id).unsafeRunSync()
      recordCount shouldBe 20
    }
    "works for deletes, updates, and inserts" in {
      // create some record sets to be updated
      val existing = insert(okZone, 10).map(_.recordSet)

      // update a few, delete a few
      val deletes = existing.take(2).map(makePendingTestDeleteChange(_, okZone).copy(status = RecordSetChangeStatus.Complete))

      // updates we will just add the letter u to
      val updates = existing.slice(3, 5).map { rs =>
        val update = rs.copy(name = "u" + rs.name)
        makeCompleteTestUpdateChange(rs, update, okZone)
      }

      // insert a few more
      val inserts = generateInserts(okZone, 2)

      // exercise the entire change set
      val cs = ChangeSet(deletes ++ updates ++ inserts)
      repo.apply(cs).unsafeRunSync()

      // make sure the deletes are gone
      repo.getRecordSet(okZone.id, deletes(0).recordSet.id).unsafeRunSync() shouldBe None
      repo.getRecordSet(okZone.id, deletes(1).recordSet.id).unsafeRunSync() shouldBe None

      // make sure the updates are updated
      repo.getRecordSet(okZone.id, updates(0).recordSet.id).unsafeRunSync().map(_.name) shouldBe
        Some(updates(0).recordSet.name)
      repo.getRecordSet(okZone.id, updates(1).recordSet.id).unsafeRunSync().map(_.name) shouldBe
        Some(updates(1).recordSet.name)

      // make sure the new ones are there
      repo.getRecordSet(okZone.id, inserts(0).recordSet.id).unsafeRunSync().map(_.name) shouldBe
        Some(inserts(0).recordSet.name)
      repo.getRecordSet(okZone.id, inserts(1).recordSet.id).unsafeRunSync().map(_.name) shouldBe
        Some(inserts(1).recordSet.name)
    }
  }

  "list record sets" should {
    "return all record sets in a zone when optional params are not set" in {
      val existing = insert(okZone, 10).map(_.recordSet)
      val found = repo.listRecordSets(okZone.id, None, None, None).unsafeRunSync()
      found.recordSets should contain theSameElementsAs existing
    }
    "return record sets after the startFrom when set" in {
      // load 5, start after the 3rd, we should get back the last two
      val existing = insert(okZone, 5).map(_.recordSet).sortBy(_.name)
      val startFrom = Some(existing(2).name)
      val found = repo.listRecordSets(okZone.id, startFrom, None, None).unsafeRunSync()

      found.recordSets should contain theSameElementsInOrderAs existing.drop(3)
    }
    "return the record sets after the startFrom respecting maxItems" in {
      // load 5, start after the 2nd, take 2, we should get back the 3rd and 4th
      val existing = insert(okZone, 5).map(_.recordSet).sortBy(_.name)
      val startFrom = Some(existing(1).name)
      val found = repo.listRecordSets(okZone.id, startFrom, Some(2), None).unsafeRunSync()

      found.recordSets should contain theSameElementsInOrderAs existing.slice(2, 4)
    }
    "return the record sets after startFrom respecting maxItems and filter" in {
      // load some deterministic names so we can filter and respect max items
      val recordNames = List("aaa", "bbb", "ccc", "ddd", "eeez", "fffz", "ggg", "hhhz", "iii", "jjj")

      // our search will be filtered by records with "z"
      val expectedNames = recordNames.filter(_.contains("z"))

      val newRecordSets =
        for {
          n <- recordNames
        } yield
          aaaa.copy(
            zoneId = okZone.id,
            name = n,
            id = UUID.randomUUID().toString)

      val changes = newRecordSets.map(makeTestAddChange(_, okZone))
      insert(changes)

      // start after the second, pulling 3 records that have "z"
      val startFrom = Some(newRecordSets(1).name)
      val found = repo.listRecordSets(okZone.id, startFrom, Some(3), Some("z")).unsafeRunSync()
      found.recordSets.map(_.name) should contain theSameElementsInOrderAs expectedNames
    }
    "pages through the list properly" in {
      // load 5 records, pages of 2, last page should have 1 result and no next id
      val existing = insert(okZone, 5).map(_.recordSet).sortBy(_.name)
      val page1 = repo.listRecordSets(okZone.id, None, Some(2), None).unsafeRunSync()
      page1.recordSets should contain theSameElementsInOrderAs existing.slice(0, 2)
      page1.nextId shouldBe Some(page1.recordSets(1).name)

      val page2 = repo.listRecordSets(okZone.id, page1.nextId, Some(2), None).unsafeRunSync()
      page2.recordSets should contain theSameElementsInOrderAs existing.slice(2, 4)
      page2.nextId shouldBe Some(page2.recordSets(1).name)

      val page3 = repo.listRecordSets(okZone.id, page2.nextId, Some(2), None).unsafeRunSync()
      page3.recordSets should contain theSameElementsInOrderAs existing.slice(4, 5)
      page3.nextId shouldBe None
    }
  }
  "get record sets by name and type" should {
    "return a record set when there is a match" in {
      val existing = insert(okZone, 1).map(_.recordSet)
      val results = repo.getRecordSets(okZone.id, existing(0).name, existing(0).typ).unsafeRunSync()
      results.headOption shouldBe existing.headOption
    }
    "return none when there is no match" in {
      val existing = insert(okZone, 1).map(_.recordSet)
      val results = repo.getRecordSets(okZone.id, "not-there", existing(0).typ).unsafeRunSync()
      results shouldBe empty
    }
  }
  "get record set by id" should {
    "return a record set when there is a match" in {
      val existing = insert(okZone, 1).map(_.recordSet)
      val result = repo.getRecordSet(okZone.id, existing(0).id).unsafeRunSync()
      result shouldBe existing.headOption
    }
    "return none when there is no match" in {
      insert(okZone, 1).map(_.recordSet)
      val result = repo.getRecordSet(okZone.id, "not-there").unsafeRunSync()
      result shouldBe None
    }
  }
  "get record set count for zone" should {
    "return the correct number of records in the zone" in {
      insert(okZone, 10)
      repo.getRecordSetCount(okZone.id).unsafeRunSync() shouldBe 10
    }
  }
  "get record sets by name" should {
    "return a record set when there is a match" in {
      val newRecordSets = List(
        aaaa.copy(name = "foo"),
        rsOk.copy(name = "foo")
      )
      val changes = newRecordSets.map(makeTestAddChange(_, okZone))
      val expected = changes.map(_.recordSet)
      repo.apply(ChangeSet(changes)).unsafeRunSync()
      val results = repo.getRecordSetsByName(okZone.id, "foo").unsafeRunSync()
      results should contain theSameElementsAs expected
    }
    "return none when there is no match" in {
      insert(okZone, 1).map(_.recordSet)
      val results = repo.getRecordSetsByName(okZone.id, "not-there").unsafeRunSync()
      results shouldBe empty
    }
  }
  "getRecordSetsByFQDNs" should {
    "omit all non existing recordsets" in {
      val rname1 = "test-fqdn-omit-1"
      val rname2 = "test-fqdn-omit-2"

      val fqdn1 = s"$rname1.${okZone.name}"
      val fqdn2 = s"$rname2.${okZone.name}"

      val change1 = makeTestAddChange(aaaa.copy(name=rname1), okZone)
      val change2 = makeTestAddChange(aaaa.copy(name=rname2), okZone)

      insert(List(change1, change2))
      val result = repo.getRecordSetsByFQDNs(Set("no-existo", fqdn1, fqdn2)).unsafeRunSync()
      result should contain theSameElementsAs List(change1.recordSet, change2.recordSet)
    }

    "return records of different types with the same fqdn" in {
      val rname = "test-fqdn-same-type"
      val fqdn = s"$rname.${okZone.name}"

      val aaaaChange = makeTestAddChange(aaaa.copy(name=rname), okZone)
      val mxChange = makeTestAddChange(mx.copy(name=rname), okZone)

      insert(List(aaaaChange, mxChange))
      val result = repo.getRecordSetsByFQDNs(Set(fqdn)).unsafeRunSync()
      result should contain theSameElementsAs List(aaaaChange.recordSet, mxChange.recordSet)
    }

    "return an empty list when given no ids" in {
      val result = repo.getRecordSetsByFQDNs(Set[String]()).unsafeRunSync()
      result shouldBe List()
    }

    "do case insensitive search" in {
      val rname1 = "ci-fqdn"
      val rname2 = "cI-fQdN"

      val fqdn1 = s"$rname1.${okZone.name}"
      val fqdn2 = s"$rname2.${okZone.name}"

      val change1 = makeTestAddChange(aaaa.copy(name=rname1), okZone)
      val change2 = makeTestAddChange(mx.copy(name=rname2), okZone)

      insert(List(change1, change2))
      val result1 = repo.getRecordSetsByFQDNs(Set(fqdn1)).unsafeRunSync()
      result1 should contain theSameElementsAs List(change1.recordSet, change2.recordSet)
      val result2 = repo.getRecordSetsByFQDNs(Set(fqdn2)).unsafeRunSync()
      result2 should contain theSameElementsAs List(change1.recordSet, change2.recordSet)
    }
  }
}
