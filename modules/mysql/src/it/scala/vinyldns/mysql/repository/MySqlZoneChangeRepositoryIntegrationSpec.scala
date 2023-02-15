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

import cats.effect.{ContextShift, IO}
import cats.implicits._
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc.DB
import vinyldns.core.domain.membership.User
import vinyldns.core.domain.zone.ZoneChangeStatus.ZoneChangeStatus
import vinyldns.core.domain.zone._
import vinyldns.core.TestZoneData.okZone
import vinyldns.core.TestZoneData.testConnection
import vinyldns.mysql.TestMySqlInstance

import scala.concurrent.duration._
import scala.util.Random

class MySqlZoneChangeRepositoryIntegrationSpec
    extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with Inspectors
    with OptionValues {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)
  private var repo: ZoneChangeRepository = _

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

    val zones: IndexedSeq[Zone] = for { i <- 1 to 3 } yield Zone(
      s"${goodUser.userName}.zone$i.",
      "test@test.com",
      status = ZoneStatus.Active,
      connection = testConnection
    )

    val statuses: List[ZoneChangeStatus] = ZoneChangeStatus.Pending :: ZoneChangeStatus.Failed ::
      ZoneChangeStatus.Synced :: Nil

    val changes
        : IndexedSeq[ZoneChange] = for { zone <- zones; status <- statuses } yield ZoneChange(
      zone,
      zone.account,
      ZoneChangeType.Update,
      status,
      created = Instant.now.truncatedTo(ChronoUnit.MILLIS).minusSeconds(Random.nextInt(1000))
    )
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
    "save a change" in {
      val change = changes(1)
      // save the change
      val saveResponse = repo.save(change).unsafeRunSync()
      saveResponse should equal(change)

      // verify change in repo
      val listResponse = repo.listZoneChanges(change.zone.id).unsafeRunSync()
      listResponse.items should equal(List(change))
    }

    "update a change" in {
      val change = changes(1).copy(systemMessage = Some("original"))
      val updatedChange = change.copy(systemMessage = Some("updated"))
      // save the change
      val firstSaveResponse = repo.save(change).unsafeRunSync()
      firstSaveResponse should equal(change)

      // update change
      val updateResponse = repo.save(updatedChange).unsafeRunSync()
      updateResponse should equal(updatedChange)

      // verify change in repo
      val listResponse = repo.listZoneChanges(change.zone.id).unsafeRunSync()
      listResponse.items should equal(List(updatedChange))
    }

    "get all changes for a zone in order" in {
      val changeSetupResults = changes.map(repo.save(_)).toList.parSequence
      changeSetupResults
        .unsafeRunTimed(5.minutes)
        .getOrElse(
          fail("timeout waiting for changes to save in MySqlZoneChangeRepositoryIntegrationSpec")
        )

      val expectedChanges =
        changes
          .filter(_.zoneId == zones(1).id)
          .sortBy(_.created.toEpochMilli)
          .reverse

      // nextId should be none since default maxItems > 3
      val listResponse = repo.listZoneChanges(zones(1).id).unsafeRunSync()
      listResponse.items should equal(expectedChanges)
      listResponse.nextId should equal(None)
      listResponse.startFrom should equal(None)
    }

    "get zone changes using a maxItems of 1" in {
      val changeSetupResults = changes.map(repo.save(_)).toList.parSequence
      changeSetupResults
        .unsafeRunTimed(5.minutes)
        .getOrElse(
          fail("timeout waiting for changes to save in MySqlZoneChangeRepositoryIntegrationSpec")
        )

      val zoneOneChanges = changes
        .filter(_.zoneId == zones(1).id)
        .sortBy(_.created.toEpochMilli)
        .reverse
      val expectedChanges = List(zoneOneChanges(0))
      val expectedNext = Some(zoneOneChanges(1).created.toEpochMilli.toString)

      val listResponse =
        repo.listZoneChanges(zones(1).id, startFrom = None, maxItems = 1).unsafeRunSync()
      listResponse.items.size should equal(1)
      listResponse.items should equal(expectedChanges)
      listResponse.nextId should equal(expectedNext)
      listResponse.startFrom should equal(None)
    }

    "page zone changes using a startFrom and maxItems" in {
      val changeSetupResults = changes.map(repo.save(_)).toList.parSequence
      changeSetupResults
        .unsafeRunTimed(5.minutes)
        .getOrElse(
          fail("timeout waiting for changes to save in MySqlZoneChangeRepositoryIntegrationSpec")
        )

      val zoneOneChanges = changes
        .filter(_.zoneId == zones(1).id)
        .sortBy(_.created.toEpochMilli)
        .reverse
      val expectedPageOne = List(zoneOneChanges(0))
      val expectedPageOneNext = Some(zoneOneChanges(1).created.toEpochMilli.toString)
      val expectedPageTwo = List(zoneOneChanges(1))
      val expectedPageTwoNext = Some(zoneOneChanges(2).created.toEpochMilli.toString)
      val expectedPageThree = List(zoneOneChanges(2))

      // get first page
      val pageOne =
        repo.listZoneChanges(zones(1).id, startFrom = None, maxItems = 1).unsafeRunSync()
      pageOne.items.size should equal(1)
      pageOne.items should equal(expectedPageOne)
      pageOne.nextId should equal(expectedPageOneNext)
      pageOne.startFrom should equal(None)

      // get second page
      val pageTwo =
        repo.listZoneChanges(zones(1).id, startFrom = pageOne.nextId, maxItems = 1).unsafeRunSync()
      pageTwo.items.size should equal(1)
      pageTwo.items should equal(expectedPageTwo)
      pageTwo.nextId should equal(expectedPageTwoNext)
      pageTwo.startFrom should equal(pageOne.nextId)

      // get final page
      // next id should be none now
      val pageThree =
        repo.listZoneChanges(zones(1).id, startFrom = pageTwo.nextId, maxItems = 1).unsafeRunSync()
      pageThree.items.size should equal(1)
      pageThree.items should equal(expectedPageThree)
      pageThree.nextId should equal(None)
      pageThree.startFrom should equal(pageTwo.nextId)
    }
  }
}
