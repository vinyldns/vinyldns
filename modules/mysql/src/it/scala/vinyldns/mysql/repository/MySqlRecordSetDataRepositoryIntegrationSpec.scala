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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc.DB
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.Zone
import vinyldns.mysql.TestMySqlInstance
import vinyldns.mysql.TransactionProvider

class MySqlRecordSetDataRepositoryIntegrationSpec
  extends AnyWordSpec
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with Matchers
    with EitherMatchers
    with TransactionProvider {

  import vinyldns.core.TestRecordSetData._
  import vinyldns.core.TestZoneData._
  private val repo = TestMySqlInstance.recordSetDataRepository.asInstanceOf[MySqlRecordSetDataRepository]

  override protected def beforeEach(): Unit = clear()

  override protected def afterAll(): Unit = clear()

  def clear(): Unit =
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM recordset_data")
    }

  def generateInserts(zone: Zone, count: Int, word: String = "insert"): List[RecordSetChange] = {
    val newRecordSetDatas =
      for {
        i <- 1 to count
      } yield aaaa.copy(
        zoneId = zone.id,
        name = s"$i-${word}-apply-test",
        id = UUID.randomUUID().toString
      )

    newRecordSetDatas.map(makeTestAddChange(_, zone)).toList
  }

  def insert(zone: Zone, count: Int, word: String = "insert"): List[RecordSetChange] = {
    val pendingChanges = generateInserts(zone, count, word)
    val bigPendingChangeSet = ChangeSet(pendingChanges)
    executeWithinTransaction { db: DB =>
      repo.save(db, bigPendingChangeSet)
    }.unsafeRunSync()
    pendingChanges

  }

  def insert(changes: List[RecordSetChange]): Unit = {
    val bigPendingChangeSet = ChangeSet(changes)
    executeWithinTransaction { db: DB =>
      repo.save(db, bigPendingChangeSet)
    }.unsafeRunSync()
    ()
  }

  def recordSetDataWithFQDN(recordSet: RecordSet, zone: Zone): RecordSet =
    recordSet.copy(fqdn = Some(s"""${recordSet.name}.${zone.name}"""))

  "apply" should {
    "properly revert changes that fail processing" in {
      val existing = insert(okZone, 2).map(_.recordSet)

      val addChange = makeTestAddChange(rsOk.copy(id = UUID.randomUUID().toString))
        .copy(status = RecordSetChangeStatus.Failed)
      val updateChange =
        makePendingTestUpdateChange(existing.head, existing.head.copy(name = "updated-name"))
          .copy(status = RecordSetChangeStatus.Failed)
      val deleteChange = makePendingTestDeleteChange(existing(1))
        .copy(status = RecordSetChangeStatus.Failed)
      executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(Seq(addChange, updateChange, deleteChange)))
      }
      repo.getRecordSetData(rsOk.id).unsafeRunSync() shouldBe None
      repo.getRecordSetData(existing.head.id).unsafeRunSync() shouldBe Some(
        recordSetDataWithFQDN(existing.head, okZone)
      )
      repo.getRecordSetData(existing(1).id).unsafeRunSync() shouldBe Some(
        recordSetDataWithFQDN(existing(1), okZone)
      )
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
      executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(existingPending))
      }.attempt.unsafeRunSync()
      repo.getRecordSetData(failedChange.recordSet.id).unsafeRunSync() shouldBe
        Some(
          existingPending.recordSet
            .copy(fqdn = Some(s"""${failedChange.recordSet.name}.${okZone.name}"""))
        )
      executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(Seq(successfulChange, pendingChange, failedChange)))
      }.attempt.unsafeRunSync()

      // success and pending changes have records saved
      repo
        .getRecordSetData(successfulChange.recordSet.id)
        .unsafeRunSync() shouldBe
        Some(recordSetDataWithFQDN(successfulChange.recordSet, okZone))
      repo
        .getRecordSetData(pendingChange.recordSet.id)
        .unsafeRunSync() shouldBe
        Some(recordSetDataWithFQDN(pendingChange.recordSet, okZone))

      // check that the pending record was deleted because of failed record change
      repo
        .getRecordSetData(failedChange.recordSet.id)
        .unsafeRunSync() shouldBe None
    }
}
"inserting record sets" should {
"be idempotent for inserts" in {
 val pendingChanges = generateInserts(okZone, 1000)
 val bigPendingChangeSet = ChangeSet(pendingChanges)
 val saveRecSets = executeWithinTransaction { db: DB =>
   repo.save(db, bigPendingChangeSet)
   repo.save(db, bigPendingChangeSet)
 }
 saveRecSets.attempt.unsafeRunSync() shouldBe right
}
"work for multiple inserts" in {
 val pendingChanges = generateInserts(okZone, 20)

 val bigPendingChangeSet = ChangeSet(pendingChanges)
 executeWithinTransaction { db: DB =>
   repo.save(db, bigPendingChangeSet)
 }.attempt.unsafeRunSync()
 // let's make sure we have all 1000 records
 val recordCount = repo.getRecordSetDataCount(okZone.id).unsafeRunSync()
 recordCount shouldBe 20
}
"work for deletes, updates, and inserts" in {
 // create some record sets to be updated
 val existing = insert(okZone, 10).map(_.recordSet)

 // update a few, delete a few
 val deletes = existing
   .take(2)
   .map(makePendingTestDeleteChange(_, okZone).copy(status = RecordSetChangeStatus.Complete))

 // updates we will just add the letter u to
 val updates = existing.slice(3, 5).map { rs =>
   val update = rs.copy(name = "u" + rs.name)
   makeCompleteTestUpdateChange(rs, update, okZone)
 }

 // insert a few more
 val inserts = generateInserts(okZone, 2, "more-inserts")

 // exercise the entire change set
 val cs = ChangeSet(deletes ++ updates ++ inserts)
 executeWithinTransaction { db: DB =>
   repo.save(db, cs)
 }.attempt.unsafeRunSync()
 // make sure the deletes are gone
 repo.getRecordSetData(deletes(0).recordSet.id).unsafeRunSync() shouldBe None
 repo.getRecordSetData(deletes(1).recordSet.id).unsafeRunSync() shouldBe None

 // make sure the updates are updated
 repo.getRecordSetData(updates(0).recordSet.id).unsafeRunSync().map(_.name) shouldBe
   Some(updates(0).recordSet.name)
 repo.getRecordSetData(updates(1).recordSet.id).unsafeRunSync().map(_.name) shouldBe
   Some(updates(1).recordSet.name)

 // make sure the new ones are there
 repo.getRecordSetData(inserts(0).recordSet.id).unsafeRunSync().map(_.name) shouldBe
   Some(inserts(0).recordSet.name)
 repo.getRecordSetData(inserts(1).recordSet.id).unsafeRunSync().map(_.name) shouldBe
   Some(inserts(1).recordSet.name)
}
}
  "get record sets by name and type" should {
    "return a record set data when there is a match" in {
      val existing = insert(okZone, 1).map(_.recordSet)
      val results =
        repo.getRecordSetDatas(okZone.id, existing.head.typ).unsafeRunSync()
      results.headOption shouldBe Some(recordSetDataWithFQDN(existing.head, okZone))
    }
}
"get record set data by id" should {
"return a record set data when there is a match" in {
 val existing = insert(okZone, 1).map(_.recordSet)
 val result = repo.getRecordSetData(existing.head.id).unsafeRunSync()
 result shouldBe Some(recordSetDataWithFQDN(existing.head, okZone))
}
"return none when there is no match" in {
 insert(okZone, 1).map(_.recordSet)
 val result = repo.getRecordSetData("not-there").unsafeRunSync()
 result shouldBe None
}
}
"get record set count for zone" should {
"return the correct number of records in the zone" in {
 insert(okZone, 10)
 repo.getRecordSetDataCount(okZone.id).unsafeRunSync() shouldBe 10
}
}
"get record set datas by name" should {
"return a record set when there is a match" in {
 val newRecordSets = List(
   aaaa.copy(name = "foo"),
   rsOk.copy(name = "foo")
 )
 val changes = newRecordSets.map(makeTestAddChange(_, okZone))
 executeWithinTransaction { db: DB =>
   repo.save(db, ChangeSet(changes))
 }.attempt.unsafeRunSync()

}
}
"getRecordSetDatasByFQDNs" should {
"omit all non existing recordsets" in {
 val rname1 = "test-fqdn-omit-1"
 val rname2 = "test-fqdn-omit-2"

 val fqdn1 = s"$rname1.${okZone.name}"
 val fqdn2 = s"$rname2.${okZone.name}"

 val change1 = makeTestAddChange(aaaa.copy(name = rname1), okZone)
 val change2 = makeTestAddChange(aaaa.copy(name = rname2), okZone)

 insert(List(change1, change2))
 val result = repo.getRecordSetDatasByFQDNs(Set("no-existo", fqdn1, fqdn2)).unsafeRunSync()
 result should contain theSameElementsAs List(
   recordSetDataWithFQDN(change1.recordSet, okZone),
   recordSetDataWithFQDN(change2.recordSet, okZone)
 )
}

"return records of different types with the same fqdn" in {
 val rname = "test-fqdn-same-type"
 val fqdn = s"$rname.${okZone.name}"

 val aaaaChange = makeTestAddChange(aaaa.copy(name = rname), okZone)
 val mxChange = makeTestAddChange(mx.copy(name = rname), okZone)

 insert(List(aaaaChange, mxChange))
 val result = repo.getRecordSetDatasByFQDNs(Set(fqdn)).unsafeRunSync()
 result should contain theSameElementsAs List(
   recordSetDataWithFQDN(aaaaChange.recordSet, okZone),
   recordSetDataWithFQDN(mxChange.recordSet, okZone)
 )
}

"return an empty list when given no ids" in {
 val result = repo.getRecordSetDatasByFQDNs(Set[String]()).unsafeRunSync()
 result shouldBe List()
}

"do case insensitive search" in {
 val rname1 = "ci-fqdn"
 val rname2 = "cI-fQdN"

 val fqdn1 = s"$rname1.${okZone.name}"
 val fqdn2 = s"$rname2.${okZone.name}"

 val change1 = makeTestAddChange(aaaa.copy(name = rname1), okZone)
 val change2 = makeTestAddChange(mx.copy(name = rname2), okZone)

 insert(List(change1, change2))
 val result1 = repo.getRecordSetDatasByFQDNs(Set(fqdn1)).unsafeRunSync()
 result1 should contain theSameElementsAs List(
   recordSetDataWithFQDN(change1.recordSet, okZone),
   recordSetDataWithFQDN(change2.recordSet, okZone)
 )
 val result2 = repo.getRecordSetDatasByFQDNs(Set(fqdn2)).unsafeRunSync()
 result2 should contain theSameElementsAs List(
   recordSetDataWithFQDN(change1.recordSet, okZone),
   recordSetDataWithFQDN(change2.recordSet, okZone)
 )
}
}

"deleteRecordSetDatasInZone" should {
"delete recordsetdatas from table with matching zone id" in {
 insert(okZone, 20)
 insert(abcZone, 10)

 repo.getRecordSetDataCount(okZone.id).unsafeRunSync() shouldBe 20
 repo.getRecordSetDataCount(abcZone.id).unsafeRunSync() shouldBe 10
 executeWithinTransaction { db: DB =>
   repo.deleteRecordSetDatasInZone(db, okZone.id, okZone.name)}.unsafeRunSync() should not be a[Throwable]

 repo.getRecordSetDataCount(okZone.id).unsafeRunSync() shouldBe 0
 repo.getRecordSetDataCount(abcZone.id).unsafeRunSync() shouldBe 10
}

"not fail if there is nothing to delete" in {
 repo.getRecordSetDataCount(okZone.id).unsafeRunSync() shouldBe 0
 executeWithinTransaction { db: DB =>
   repo.deleteRecordSetDatasInZone(db,okZone.id, okZone.name)}.unsafeRunSync() should not be a[Throwable]
}
}
}
