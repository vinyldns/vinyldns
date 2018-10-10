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

  "inserting record sets" should {
    "be idempotent for inserts" in {
      val pendingChanges = generateInserts(okZone, 1000)
      val bigPendingChangeSet = ChangeSet(pendingChanges)
      repo.apply(bigPendingChangeSet).unsafeRunSync()
      repo.apply(bigPendingChangeSet).attempt.unsafeRunSync() shouldBe right
    }
    "work for multiple inserts" in {
      val pendingChanges = generateInserts(okZone, 1000)

      val bigPendingChangeSet = ChangeSet(pendingChanges)
      repo.apply(bigPendingChangeSet).unsafeRunSync()

      // let's make sure we have all 1000 records
      val recordCount = repo.getRecordSetCount(okZone.id).unsafeRunSync()
      recordCount shouldBe 1000
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

}
