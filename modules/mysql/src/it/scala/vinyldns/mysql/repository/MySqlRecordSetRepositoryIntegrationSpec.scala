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
import org.scalatest._
import scalikejdbc.DB
import vinyldns.core.domain.record.{ChangeSet, RecordSetChange}
import vinyldns.core.domain.zone.Zone

class MySqlRecordSetRepositoryIntegrationSpec
  extends WordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with Inspectors
    with OptionValues with EitherMatchers {

  import vinyldns.core.TestRecordSetData._
  import vinyldns.core.TestZoneData._
  private var repo: MySqlRecordSetRepository = _

  override protected def beforeAll(): Unit =
    repo = TestMySqlInstance.recordSetRepository.asInstanceOf[MySqlRecordSetRepository]

  override protected def beforeEach(): Unit =
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

  "inserting record sets" should {
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
      val deletes = existing.take(2).map(makeTestDeleteChange(_, okZone))

      // updates we will just add the letter u to
      val updates = existing.slice(3, 5).map { rs =>
        val update = rs.copy(name = "u" + rs.name)
        makeTestUpdateChange(rs, update, okZone)
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
      repo.getRecordSet(okZone.id, updates(0).recordSet.id).unsafeRunSync().map(_.name) shouldBe Some(updates(0).recordSet.name)
      repo.getRecordSet(okZone.id, updates(1).recordSet.id).unsafeRunSync().map(_.name) shouldBe Some(updates(1).recordSet.name)

      // make sure the new ones are there
      repo.getRecordSet(okZone.id, inserts(0).recordSet.id).unsafeRunSync().map(_.name) shouldBe Some(inserts(0).recordSet.name)
      repo.getRecordSet(okZone.id, inserts(1).recordSet.id).unsafeRunSync().map(_.name) shouldBe Some(inserts(1).recordSet.name)
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
  "get by fqdn" should {
    "return all record sets matching an fqdn" in {
      val newRecordSets = List(
        aaaa.copy(name = "foo"),
        rsOk.copy(name = "foo"),
        aaaa.copy(name = "bar"),
        rsOk.copy(name = "bar")
      )
      val changes = newRecordSets.map(makeTestAddChange(_, okZone))
      val expected = changes.map(_.recordSet)
      repo.apply(ChangeSet(changes)).unsafeRunSync()

      val fqdns = List(FQDN("foo", okZone.name), FQDN("bar", okZone.name))
      val results = repo.getRecordSetsByFQDN(fqdns).unsafeRunSync()
      results should contain theSameElementsAs expected
    }
  }
}
