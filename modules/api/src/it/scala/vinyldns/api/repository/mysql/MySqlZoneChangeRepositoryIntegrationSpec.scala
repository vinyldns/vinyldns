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
    "successfully save a change" in {
      val change = changes(1)
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

      val expectedChanges =
        changes
          .filter(_.zoneId == zones(1).id)
          .sortBy(_.created.getMillis)
          .reverse

      whenReady(repo.listZoneChanges(zones(1).id).unsafeToFuture(), timeout) { retrieved =>
        retrieved.items should equal(expectedChanges)
        retrieved.nextId should equal(None)
        retrieved.startFrom should equal(None)
      }
    }

    "get zone changes using a maxItems of 1" in {
      val changeSetupResults = changes.map(repo.save(_)).toList.parSequence
      changeSetupResults
        .unsafeRunTimed(5.minutes)
        .getOrElse(
          fail("timeout waiting for changes to save in MySqlZoneChangeRepositoryIntegrationSpec"))

      val zoneOneChanges = changes
        .filter(_.zoneId == zones(1).id)
        .sortBy(_.created.getMillis)
        .reverse
      val expectedChanges = List(zoneOneChanges(0))
      val expectedNext = Some(zoneOneChanges(1).created.getMillis.toString)

      whenReady(
        repo.listZoneChanges(zones(1).id, startFrom = None, maxItems = 1).unsafeToFuture(),
        timeout) { retrieved =>
        retrieved.items.size should equal(1)
        retrieved.items should equal(expectedChanges)
        retrieved.nextId should equal(expectedNext)
        retrieved.startFrom should equal(None)
      }
    }

    "get zone changes using a startFrom and maxItems" in {
      val changeSetupResults = changes.map(repo.save(_)).toList.parSequence
      changeSetupResults
        .unsafeRunTimed(5.minutes)
        .getOrElse(
          fail("timeout waiting for changes to save in MySqlZoneChangeRepositoryIntegrationSpec"))

      val zoneOneChanges = changes
        .filter(_.zoneId == zones(1).id)
        .sortBy(_.created.getMillis)
        .reverse
      val expectedPageOne = List(zoneOneChanges(0))
      val expectedPageOneNext = Some(zoneOneChanges(1).created.getMillis.toString)
      val expectedPageTwo = List(zoneOneChanges(1))
      val expectedPageTwoNext = Some(zoneOneChanges(2).created.getMillis.toString)
      val expectedPageThree = List(zoneOneChanges(2))
      val expectedPageThreeNext = None

      // get first page of 1
      whenReady(
        repo
          .listZoneChanges(zones(1).id, startFrom = None, maxItems = 1)
          .unsafeToFuture(),
        timeout) { retrievedOne =>
        retrievedOne.items.size should equal(1)
        retrievedOne.items should equal(expectedPageOne)
        retrievedOne.nextId should equal(expectedPageOneNext)
        retrievedOne.startFrom should equal(None)

        // get second page of 1
        whenReady(
          repo
            .listZoneChanges(zones(1).id, startFrom = retrievedOne.nextId, maxItems = 1)
            .unsafeToFuture(),
          timeout) { retrievedTwo =>
          retrievedTwo.items.size should equal(1)
          retrievedTwo.items should equal(expectedPageTwo)
          retrievedTwo.nextId should equal(expectedPageTwoNext)
          retrievedTwo.startFrom should equal(retrievedOne.nextId)

          // get final page of 1
          // nextId should be None
          whenReady(
            repo
              .listZoneChanges(zones(1).id, startFrom = retrievedTwo.nextId, maxItems = 1)
              .unsafeToFuture(),
            timeout) { retrievedThree =>
            retrievedThree.items.size should equal(1)
            retrievedThree.items should equal(expectedPageThree)
            retrievedThree.nextId should equal(expectedPageThreeNext)
            retrievedThree.startFrom should equal(retrievedTwo.nextId)
          }
        }
      }
    }
  }
}
