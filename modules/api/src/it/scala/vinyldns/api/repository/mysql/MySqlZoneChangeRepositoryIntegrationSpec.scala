package vinyldns.api.repository.mysql

import java.util.UUID

import org.joda.time.DateTime
import cats.implicits._
import org.scalatest._
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import scalikejdbc.DB
import vinyldns.api.{GroupTestData, ResultHelpers, VinylDNSTestData}
import vinyldns.api.domain.dns.DnsConversions
import vinyldns.core.domain.membership.User
import vinyldns.core.domain.zone.ZoneChangeStatus.ZoneChangeStatus
import vinyldns.core.domain.zone._

import scala.concurrent.duration._
import scala.util.Random

class MySqlZoneChangeRepositoryIntegrationSpec
    extends WordSpec
    with BeforeAndAfterAll
    with DnsConversions
    with VinylDNSTestData
    with GroupTestData
    with ResultHelpers
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with Inspectors
    with OptionValues {

  private var repo: ZoneChangeRepository = _
  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  object TestData {
    def randomZoneChange: ZoneChange =
      ZoneChange(
        zone = okZone,
        userId = UUID.randomUUID().toString,
        changeType = ZoneChangeType.Create,
        status = ZoneChangeStatus.Synced,
        systemMessage = Some("test")
      )

    val goodUser: User = User(s"live-test-acct", "key", "secret")

    val zones: IndexedSeq[Zone] = for { i <- 1 to 3 } yield
      Zone(
        s"${goodUser.userName}.zone$i.",
        "test@test.com",
        status = ZoneStatus.Active,
        connection = testConnection)

    val statuses: List[ZoneChangeStatus] = ZoneChangeStatus.Pending :: ZoneChangeStatus.Failed ::
      ZoneChangeStatus.Synced :: Nil

    val changes: IndexedSeq[ZoneChange] = for { zone <- zones; status <- statuses } yield
      ZoneChange(
        zone,
        zone.account,
        ZoneChangeType.Update,
        status,
        created = DateTime.now().minusSeconds(Random.nextInt(1000)))

    val constantDateTime: DateTime = DateTime.now()
    val changesWithSameCreated
      : IndexedSeq[ZoneChange] = for { zone <- zones; status <- statuses } yield
      ZoneChange(zone, zone.account, ZoneChangeType.Update, status, created = constantDateTime)
  }

  import TestData._

  override protected def beforeAll(): Unit =
    repo = TestMySqlInstance.zoneChangeRepository

  override protected def beforeEach(): Unit =
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM zone_change")
    }

  override protected def afterAll(): Unit = {
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM zone_change")
    }
    super.afterAll()
  }

  "MySqlZoneChangeRepository" should {
    implicit def dateTimeOrdering: Ordering[MySqlZoneChangePagingKey] = Ordering.fromLessThan(_.isAfter(_))

    "successfully save a change" in {
      val change = changes(1)
      whenReady(repo.listZoneChanges(change.zone.id).unsafeToFuture(), timeout) { retrieved =>
        retrieved.items.length should equal(0)
      }

      whenReady(repo.save(change).unsafeToFuture(), timeout) { saved =>
        saved should equal(change)
      }

      whenReady(repo.listZoneChanges(change.zone.id).unsafeToFuture(), timeout) { retrieved =>
        retrieved.items should equal(List(change))
      }
    }

    "get all changes for a zone in order" in {
      val changeSetupResults = changes.map(repo.save(_)).toList.parSequence
      changeSetupResults
        .unsafeRunTimed(5.minutes)
        .getOrElse(
          fail("timeout waiting for changes to save in MySqlZoneChangeRepositoryIntegrationSpec"))

      val f = repo.listZoneChanges(zones(1).id).unsafeToFuture()
      val expectedChanges = changes.filter(_.zoneId == zones(1).id).sortBy(MySqlZoneChangePagingKey(_))
      whenReady(f, timeout) { retrieved =>
        retrieved.items should equal(expectedChanges)
        retrieved.nextId should equal(None)
        retrieved.startFrom should equal(None)
      }
    }

    "get all changes for a zone in order when there are duplicate created times" in {
      val testChanges = changes ++ changesWithSameCreated
      val changeSetupResults = testChanges.map(repo.save(_)).toList.parSequence
      changeSetupResults
        .unsafeRunTimed(5.minutes)
        .getOrElse(
          fail("timeout waiting for changes to save in MySqlZoneChangeRepositoryIntegrationSpec"))

      val f = repo.listZoneChanges(zones(1).id).unsafeToFuture()
      val expectedChanges = testChanges.filter(_.zoneId == zones(1).id).sortBy(_.created)
      whenReady(f, timeout) { retrieved =>
        retrieved.items should equal(expectedChanges)
        retrieved.nextId should equal(None)
        retrieved.startFrom should equal(None)
      }
    }
  }
}
