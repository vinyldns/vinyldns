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
import vinyldns.core.domain.record.{ChangeSet, RecordChangeRepository, RecordSetChange, RecordSetChangeType}
import vinyldns.core.domain.zone.Zone
import vinyldns.mysql.TestMySqlInstance

class MySqlRecordChangeRepositoryIntegrationSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with EitherMatchers {
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

  def executeWithinTransaction[A](execution: DB => A): A = {
    val db=DB(ConnectionPool.borrow())
    db.beginIfNotYet() // keep the connection open
    db.autoClose(false)
    try {
      execution(db)
    } catch {
      case error: Throwable =>
        db.rollbackIfActive() //Roll back the changes if error occurs
        db.close() //DB Connection Close
        throw error
    }
  }

  "saving record changes" should {
    "save a batch of inserts" in {
      val inserts = generateInserts(okZone, 1000)
      executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(inserts)).attempt.unsafeRunSync() shouldBe right
      }
      repo.getRecordSetChange(okZone.id, inserts(0).id).unsafeRunSync() shouldBe inserts.headOption
    }
    "saves record updates" in {
      val updates = generateInserts(okZone, 1).map(_.copy(changeType = RecordSetChangeType.Update))
      executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(updates)).attempt.unsafeRunSync() shouldBe right
      }
      repo.getRecordSetChange(okZone.id, updates(0).id).unsafeRunSync() shouldBe updates.headOption
    }
    "saves record deletes" in {
      val deletes = generateInserts(okZone, 1).map(_.copy(changeType = RecordSetChangeType.Delete))
      executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(deletes)).attempt.unsafeRunSync() shouldBe right
      }
      repo.getRecordSetChange(okZone.id, deletes(0).id).unsafeRunSync() shouldBe deletes.headOption
    }
  }
  "list record changes" should {
    "return successfully without start from" in {
      val inserts = generateInserts(okZone, 10)
      executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(inserts)).attempt.unsafeRunSync() shouldBe right
      }
      val result = repo.listRecordSetChanges(okZone.id, None, 5).unsafeRunSync()
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
      val expectedOrder = timeSpaced.sortBy(_.created.getMillis).reverse

      executeWithinTransaction { db: DB =>
        repo.save(db, ChangeSet(timeSpaced)).attempt.unsafeRunSync() shouldBe right
      }
      val page1 = repo.listRecordSetChanges(okZone.id, None, 2).unsafeRunSync()
      page1.nextId shouldBe Some(expectedOrder(1).created.getMillis.toString)
      page1.maxItems shouldBe 2
      (page1.items should contain).theSameElementsInOrderAs(expectedOrder.take(2))

      val page2 = repo.listRecordSetChanges(okZone.id, page1.nextId, 2).unsafeRunSync()
      page2.nextId shouldBe Some(expectedOrder(3).created.getMillis.toString)
      page2.maxItems shouldBe 2
      (page2.items should contain).theSameElementsInOrderAs(expectedOrder.slice(2, 4))

      val page3 = repo.listRecordSetChanges(okZone.id, page2.nextId, 2).unsafeRunSync()
      page3.nextId shouldBe None
      page3.maxItems shouldBe 2
      page3.items should contain theSameElementsAs expectedOrder.slice(4, 5)
    }
  }
}
