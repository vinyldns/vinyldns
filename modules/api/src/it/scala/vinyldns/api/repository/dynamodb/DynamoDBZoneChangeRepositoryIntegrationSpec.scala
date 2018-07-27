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

package vinyldns.api.repository.dynamodb

import java.util

import com.amazonaws.services.dynamodbv2.model.{AttributeValue, DeleteItemRequest, ScanRequest}
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Seconds, Span}
import vinyldns.api.domain.membership.User
import vinyldns.api.domain.zone._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Random

class DynamoDBZoneChangeRepositoryIntegrationSpec extends DynamoDBIntegrationSpec {

  private implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val zoneChangeTable = "zone-changes-live"

  private val tableConfig = ConfigFactory.parseString(s"""
      | dynamo {
      |   tableName = "$zoneChangeTable"
      |   provisionedReads=30
      |   provisionedWrites=30
      | }
    """.stripMargin).withFallback(ConfigFactory.load())

  private var repo: DynamoDBZoneChangeRepository = _

  private val goodUser = User(s"live-test-acct", "key", "secret")

  private val okZones = for { i <- 1 to 3 } yield
    Zone(
      s"${goodUser.userName}.zone$i.",
      "test@test.com",
      status = ZoneStatus.Active,
      connection = testConnection)

  private val zones = okZones

  private val statuses = {
    import vinyldns.api.domain.zone.ZoneChangeStatus._
    Pending :: Complete :: Failed :: Synced :: Nil
  }
  private val changes = for { zone <- zones; status <- statuses } yield
    ZoneChange(
      zone,
      zone.account,
      ZoneChangeType.Update,
      status,
      created = now.minusSeconds(Random.nextInt(1000)))

  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  def setup(): Unit = {
    repo = new DynamoDBZoneChangeRepository(tableConfig, dynamoDBHelper)

    // wait until the repo is ready, could take time if the table has to be created
    var notReady = true
    while (notReady) {
      val result = Await.ready(repo.listZoneChanges("any"), 5.seconds)
      notReady = result.value.get.isFailure
    }

    // Clear the zone just in case there is some lagging test data
    clearChanges()

    // Create all the zones
    val savedChanges = Future.sequence(changes.map(repo.save))

    // Wait until all of the zones are done
    Await.result(savedChanges, 5.minutes)
  }

  def tearDown(): Unit =
    clearChanges()

  private def clearChanges(): Unit = {

    import scala.collection.JavaConverters._

    // clear all the zones from the table that we work with here
    // NOTE: This is brute force and could be cleaner
    val scanRequest = new ScanRequest()
      .withTableName(zoneChangeTable)

    val result = dynamoClient
      .scan(scanRequest)
      .getItems
      .asScala
      .map(i => (i.get("zone_id").getS, i.get("change_id").getS))

    result.foreach(Function.tupled(deleteZoneChange))
  }

  private def deleteZoneChange(zoneId: String, changeId: String): Unit = {
    val key = new util.HashMap[String, AttributeValue]()
    key.put("zone_id", new AttributeValue(zoneId))
    key.put("change_id", new AttributeValue(changeId))
    val request = new DeleteItemRequest().withTableName(zoneChangeTable).withKey(key)
    try {
      dynamoClient.deleteItem(request)
    } catch {
      case ex: Throwable =>
        throw new UnexpectedDynamoResponseException(ex.getMessage, ex)
    }
  }

  "DynamoDBRepository" should {

    implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_.isAfter(_))

    "get all changes for a zone" in {
      val testFuture = repo.listZoneChanges(okZones(1).id)
      whenReady(testFuture, timeout) { retrieved =>
        val expectedChanges = changes.filter(_.zoneId == okZones(1).id).sortBy(_.created)
        retrieved.items should equal(expectedChanges)
      }
    }

    "get pending and complete changes for a zone" in {
      val testFuture = repo.getPending(okZones(1).id)
      whenReady(testFuture, timeout) { retrieved =>
        val expectedChangeIds = changes
          .filter(c =>
            c.zoneId == okZones(1).id
              && (c.status == ZoneChangeStatus.Pending || c.status == ZoneChangeStatus.Complete))
          .map(_.id)
          .toSet

        retrieved.map(_.id).toSet should contain theSameElementsAs expectedChangeIds
        retrieved.sortBy(_.created.getMillis) should equal(
          changes
            .filter(c =>
              c.zoneId == okZones(1).id &&
                (c.status == ZoneChangeStatus.Pending || c.status == ZoneChangeStatus.Complete))
            .sortBy(_.created.getMillis))
      }
    }

    "get zone changes with a page size of one" in {
      val testFuture = repo.listZoneChanges(zoneId = okZones(1).id, startFrom = None, maxItems = 1)
      whenReady(testFuture, timeout) { retrieved =>
        {
          val result = retrieved.items
          val expectedChanges = changes.filter(_.zoneId == okZones(1).id)
          result.size shouldBe 1
          expectedChanges should contain(result.head)
        }
      }
    }

    "get zone changes with page size of one and reuse key to get another page with size of two" in {
      val testFuture = repo.listZoneChanges(zoneId = okZones(1).id, startFrom = None, maxItems = 1)
      whenReady(testFuture, timeout) { retrieved =>
        {
          val result1 = retrieved.items.map(_.id).toSet
          val key = retrieved.nextId
          val testFuture2 =
            repo.listZoneChanges(zoneId = okZones(1).id, startFrom = key, maxItems = 2)
          whenReady(testFuture2, timeout) { retrieved =>
            {
              val result2 = retrieved.items
              val expectedChanges =
                changes.filter(_.zoneId == okZones(1).id).sortBy(_.created).slice(1, 3)

              result2.size shouldBe 2
              result2 should equal(expectedChanges)
              result2 shouldNot contain(result1.head)
            }
          }
        }
      }
    }

    "return an empty list and nextId of None when passing last record as start" in {
      val testFuture = repo.listZoneChanges(zoneId = okZones(1).id, startFrom = None, maxItems = 4)
      whenReady(testFuture, timeout) { retrieved =>
        {
          val key = retrieved.nextId
          val testFuture2 = repo.listZoneChanges(zoneId = okZones(1).id, startFrom = key)
          whenReady(testFuture2, timeout) { retrieved =>
            {
              val result2 = retrieved.items
              result2 shouldBe List()
              retrieved.nextId shouldBe None
            }
          }
        }
      }
    }

    "have nextId of None when exhausting record changes" in {
      val testFuture = repo.listZoneChanges(zoneId = okZones(1).id, startFrom = None, maxItems = 10)
      whenReady(testFuture, timeout) { retrieved =>
        {
          val result = retrieved.items
          val expectedChanges = changes.filter(_.zoneId == okZones(1).id).sortBy(_.created)
          result.size shouldBe 4
          result should equal(expectedChanges)
          retrieved.nextId shouldBe None
        }
      }
    }
  }
}
