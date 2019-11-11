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

import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model._
import org.joda.time.DateTime
import vinyldns.core.domain.membership.User
import vinyldns.core.domain.zone._
import vinyldns.core.TestZoneData._
import vinyldns.core.TestMembershipData.now

import scala.concurrent.duration._
import scala.util.Random

class DynamoDBZoneChangeRepositoryIntegrationSpec extends DynamoDBIntegrationSpec {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  private val zoneChangeTable = "zone-changes-live"

  private val tableConfig = DynamoDBRepositorySettings(s"$zoneChangeTable", 30, 30)

  private var repo: DynamoDBZoneChangeRepository = _

  private val goodUser = User(s"live-test-acct", "key", "secret")

  private val okZones = for { i <- 1 to 3 } yield Zone(
    s"${goodUser.userName}.zone$i.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection
  )

  private val zones = okZones

  private val statuses = {
    import vinyldns.core.domain.zone.ZoneChangeStatus._
    Pending :: Complete :: Failed :: Synced :: Nil
  }
  private val changes = for { zone <- zones; status <- statuses } yield ZoneChange(
    zone,
    zone.account,
    ZoneChangeType.Update,
    status,
    created = now.minusSeconds(Random.nextInt(1000))
  )

  def setup(): Unit = {
    repo = DynamoDBZoneChangeRepository(tableConfig, dynamoIntegrationConfig).unsafeRunSync()

    // Create all the zones
    val results = changes.map(repo.save(_)).toList.parSequence

    results.unsafeRunTimed(5.minutes).getOrElse(fail("timeout waiting for data load"))
  }

  def tearDown(): Unit = {
    val request = new DeleteTableRequest().withTableName(zoneChangeTable)
    repo.dynamoDBHelper.deleteTable(request).unsafeRunSync()
  }

  "DynamoDBRepository" should {

    implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_.isAfter(_))

    "get all changes for a zone" in {
      val retrieved = repo.listZoneChanges(okZones(1).id).unsafeRunSync()
      val expectedChanges = changes.filter(_.zoneId == okZones(1).id).sortBy(_.created)
      retrieved.items should equal(expectedChanges)
    }

    "get zone changes with a page size of one" in {
      val testFuture = repo.listZoneChanges(zoneId = okZones(1).id, startFrom = None, maxItems = 1)

      val retrieved = testFuture.unsafeRunSync()
      val result = retrieved.items
      val expectedChanges = changes.filter(_.zoneId == okZones(1).id)
      result.size shouldBe 1
      expectedChanges should contain(result.head)
    }

    "get zone changes with page size of one and reuse key to get another page with size of two" in {
      val testFuture = repo.listZoneChanges(zoneId = okZones(1).id, startFrom = None, maxItems = 1)

      val retrieved = testFuture.unsafeRunSync()
      val result1 = retrieved.items.map(_.id).toSet
      val key = retrieved.nextId

      val testFuture2 =
        repo.listZoneChanges(zoneId = okZones(1).id, startFrom = key, maxItems = 2)

      val result2 = testFuture2.unsafeRunSync().items
      val expectedChanges =
        changes.filter(_.zoneId == okZones(1).id).sortBy(_.created).slice(1, 3)

      result2.size shouldBe 2
      result2 should equal(expectedChanges)
      result2 shouldNot contain(result1.head)
    }

    "return an empty list and nextId of None when passing last record as start" in {
      val listZones = for {
        testFuture <- repo.listZoneChanges(zoneId = okZones(1).id, startFrom = None, maxItems = 4)
        testFuture2 <- repo.listZoneChanges(zoneId = okZones(1).id, startFrom = testFuture.nextId)
      } yield testFuture2

      val retrieved = listZones.unsafeRunSync()
      val result = retrieved.items
      result shouldBe List()
      retrieved.nextId shouldBe None
    }

    "have nextId of None when exhausting record changes" in {
      val testFuture = repo.listZoneChanges(zoneId = okZones(1).id, startFrom = None, maxItems = 10)
      val retrieved = testFuture.unsafeRunSync()
      val result = retrieved.items
      val expectedChanges = changes.filter(_.zoneId == okZones(1).id).sortBy(_.created)
      result.size shouldBe 4
      result should equal(expectedChanges)
      retrieved.nextId shouldBe None
    }
  }
}
