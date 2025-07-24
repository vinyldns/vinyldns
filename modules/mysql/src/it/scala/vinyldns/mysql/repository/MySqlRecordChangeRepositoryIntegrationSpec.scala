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
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc._
import vinyldns.core.domain.record.{ChangeSet, RecordChangeRepository, RecordSetChange, RecordSetChangeStatus, RecordSetChangeType, RecordType}
import vinyldns.core.domain.zone.Zone
import vinyldns.mysql.TestMySqlInstance
import vinyldns.mysql.TransactionProvider
import java.time.Instant

class MySqlRecordChangeRepositoryIntegrationSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with EitherMatchers
    with TransactionProvider {
  import vinyldns.core.TestRecordSetData._
  import vinyldns.core.TestZoneData._

  private var repo: RecordChangeRepository = _

  override protected def beforeAll(): Unit =
    repo = TestMySqlInstance.recordChangeRepository

  override protected def beforeEach(): Unit = clear()

  override protected def afterAll(): Unit = clear()

  def clear(): Unit =
    DB.localTx { implicit s =>
      s.executeUpdate("DELETE FROM record_change")
      ()
    }

  def generateInserts(zone: Zone, count: Int): List[RecordSetChange] = {
    val newRecordSets =
      for {
        i <- 1 to count
      } yield aaaa.copy(zoneId = zone.id, name = s"$i-apply-test", id = UUID.randomUUID().toString)

    newRecordSets.map(makeTestAddChange(_, zone)).toList
  }

  def generateSameInserts(zone: Zone, count: Int): List[RecordSetChange] = {
    val newRecordSets =
      for {
        i <- 1 to count
      } yield aaaa.copy(zoneId = zone.id, name = s"apply-test", id = UUID.randomUUID().toString, created = Instant.now.plusSeconds(i))

    newRecordSets.map(makeTestAddChange(_, zone)).toList
  }

  def generateFailedInserts(zone: Zone, count: Int): List[RecordSetChange] = {
    val newRecordSets =
      for {
        i <- 1 to count
      } yield aaaa.copy(zoneId = zone.id, name = s"$i-apply-test", id = UUID.randomUUID().toString)

    newRecordSets.map(makeTestAddChange(_, zone).copy(status= RecordSetChangeStatus.Failed)).toList
  }

  "saving record changes" should {
    "save a batch of inserts" in {
      val inserts = generateInserts(okZone, 1000)
      val saveRecChange = executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(inserts))
      }
      saveRecChange.attempt.unsafeRunSync() shouldBe right
      repo.getRecordSetChange(okZone.id, inserts(0).id).unsafeRunSync() shouldBe inserts.headOption
    }
    "saves record updates" in {
      val updates = generateInserts(okZone, 1).map(_.copy(changeType = RecordSetChangeType.Update))
      val saveRecChange = executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(updates))
      }
      saveRecChange.attempt.unsafeRunSync() shouldBe right
      repo.getRecordSetChange(okZone.id, updates(0).id).unsafeRunSync() shouldBe updates.headOption
    }
    "saves record deletes" in {
      val deletes = generateInserts(okZone, 1).map(_.copy(changeType = RecordSetChangeType.Delete))
      val saveRecChange = executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(deletes))
      }
      saveRecChange.attempt.unsafeRunSync() shouldBe right
      repo.getRecordSetChange(okZone.id, deletes(0).id).unsafeRunSync() shouldBe deletes.headOption
    }
  }
  "list record changes" should {
    "return successfully without start from" in {
      val inserts = generateInserts(okZone, 10)
      val saveRecChange = executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(inserts))
      }
      saveRecChange.attempt.unsafeRunSync() shouldBe right
      val result = repo.listRecordSetChanges(Some(okZone.id), None, 5).unsafeRunSync()
      result.nextId shouldBe defined
      result.maxItems shouldBe 5
      (result.items should have).length(5)
    }
    "page through record changes" in {
      // sort by created desc, so adding additional seconds makes it more current, the last
      val timeSpaced =
        generateInserts(okZone, 5).zipWithIndex.map {
          case (c, i) => c.copy(created = c.created.plusSeconds(i))
        }

      // expect to be sorted by created descending so reverse that
      val expectedOrder = timeSpaced.sortBy(_.created.toEpochMilli).reverse

      val saveRecChange = executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(timeSpaced))
      }
      saveRecChange.attempt.unsafeRunSync() shouldBe right
      val page1 = repo.listRecordSetChanges(Some(okZone.id), None, 2).unsafeRunSync()
      page1.nextId shouldBe Some(2)
      page1.maxItems shouldBe 2
      (page1.items should contain).theSameElementsInOrderAs(expectedOrder.take(2))

      val page2 = repo.listRecordSetChanges(Some(okZone.id), page1.nextId, 2).unsafeRunSync()
      page2.nextId shouldBe Some(4)
      page2.maxItems shouldBe 2
      (page2.items should contain).theSameElementsInOrderAs(expectedOrder.slice(2, 4))

      val page3 = repo.listRecordSetChanges(Some(okZone.id), page2.nextId, 2).unsafeRunSync()
      page3.nextId shouldBe None
      page3.maxItems shouldBe 2
      page3.items should contain theSameElementsAs expectedOrder.slice(4, 5)
    }
    "list a particular recordset's changes by fqdn and record type" in {
      val inserts = generateInserts(okZone, 10)
      val saveRecChange = executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(inserts))
      }
      saveRecChange.attempt.unsafeRunSync() shouldBe right
      val result = repo.listRecordSetChanges(None, None, 5, Some("1-apply-test.ok.zone.recordsets."), Some(RecordType.AAAA)).unsafeRunSync()
      result.nextId shouldBe None
      result.maxItems shouldBe 5
      (result.items should have).length(1)
    }
    "page through a particular recordset's changes by fqdn and record type" in {
      val inserts = generateSameInserts(okZone, 8)
      val saveRecChange = executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(inserts))
      }
      saveRecChange.attempt.unsafeRunSync() shouldBe right
      val page1 = repo.listRecordSetChanges(None, None, 5, Some("apply-test.ok.zone.recordsets."), Some(RecordType.AAAA)).unsafeRunSync()
      page1.nextId shouldBe defined
      page1.maxItems shouldBe 5
      (page1.items should have).length(5)

      val page2 = repo.listRecordSetChanges(None, page1.nextId, 5, Some("apply-test.ok.zone.recordsets."), Some(RecordType.AAAA)).unsafeRunSync()
      page2.nextId shouldBe None
      page2.maxItems shouldBe 5
      (page2.items should have).length(3)
    }
  }

  "list failed record changes" should {
    "return records for failed record changes" in {
      val inserts = generateFailedInserts(okZone, 10)
      val saveRecChange = executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(inserts))
      }
      saveRecChange.attempt.unsafeRunSync() shouldBe right
      val result = repo.listFailedRecordSetChanges(Some(okZone.id),10, 0).unsafeRunSync()
      (result.items  should have).length(10)
      result.maxItems shouldBe 10
      result.items should contain theSameElementsAs(inserts)
    }

    "return empty for success record changes" in {
      val inserts = generateInserts(okZone, 10)
      val saveRecChange = executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(inserts))
      }
      saveRecChange.attempt.unsafeRunSync() shouldBe right
      val result = repo.listFailedRecordSetChanges(Some(okZone.id),5, 0).unsafeRunSync()
      (result.items  should have).length(0)
      result.items shouldBe List()
      result.nextId shouldBe 0
      result.maxItems shouldBe 5
    }
    "page through failed record changes" in {
      // sort by created desc, so adding additional seconds makes it more current, the last
      val timeSpaced =
        generateFailedInserts(okZone, 5).zipWithIndex.map {
          case (c, i) => c.copy(created = c.created.plusSeconds(i))
        }

      // expect to be sorted by created descending so reverse that
      val expectedOrder = timeSpaced.sortBy(_.created.toEpochMilli).reverse

      val saveRecChange = executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(timeSpaced))
      }
      saveRecChange.attempt.unsafeRunSync() shouldBe right
      val page1 = repo.listFailedRecordSetChanges(Some(okZone.id), 2, 0).unsafeRunSync()
      page1.nextId shouldBe 3
      page1.maxItems shouldBe 2
      (page1.items should contain).theSameElementsInOrderAs(expectedOrder.take(2))

      val page2 = repo.listFailedRecordSetChanges(Some(okZone.id), 2, page1.nextId).unsafeRunSync()
      page2.nextId shouldBe 6
      page2.maxItems shouldBe 2
      (page2.items should contain).theSameElementsInOrderAs(expectedOrder.slice(3, 5))

      val page3 = repo.listFailedRecordSetChanges(Some(okZone.id), 2, 4).unsafeRunSync()
      page3.nextId shouldBe 0
      page3.maxItems shouldBe 2
      page3.items should contain theSameElementsAs expectedOrder.slice(4, 5)
    }
  }
}


