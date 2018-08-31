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

import java.util.UUID

import com.amazonaws.services.dynamodbv2.model.{ScanRequest, ScanResult}
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Seconds, Span}
import vinyldns.api.domain.membership.User
import vinyldns.api.domain.record
import vinyldns.api.domain.record.{ChangeSet, ListRecordSetResults, RecordSet, RecordSetChange}
import vinyldns.api.domain.zone.{Zone, ZoneStatus}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class DynamoDBRecordSetRepositoryIntegrationSpec
    extends DynamoDBIntegrationSpec
    with DynamoDBRecordSetConversions {

  private implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global
  private val recordSetTable = "record-sets-live"
  private[repository] val recordSetTableName: String = recordSetTable

  private val tableConfig = ConfigFactory.parseString(s"""
       | dynamo {
       |   tableName = "$recordSetTable"
       |   provisionedReads=50
       |   provisionedWrites=50
       | }
    """.stripMargin).withFallback(ConfigFactory.load())

  import dynamoDBHelper._

  private val repo = new DynamoDBRecordSetRepository(tableConfig, dynamoDBHelper)

  private val users = for (i <- 1 to 3)
    yield User(s"live-test-acct$i", "key", "secret")

  private val zones =
    for {
      acct <- users
      i <- 1 to 3
    } yield
      Zone(
        s"live-test-${acct.userName}.zone$i.",
        "test@test.com",
        status = ZoneStatus.Active,
        connection = testConnection)

  private val rsTemplates = Seq(rsOk, aaaa, cname)

  private val rsQualifiedStatus = Seq("-dotless", "-dotted.")

  private val recordSets =
    for {
      zone <- zones
      rsTemplate <- rsTemplates
      rsQualifiedStatus <- rsQualifiedStatus
    } yield
      rsTemplate.copy(
        zoneId = zone.id,
        name = s"${rsTemplate.typ.toString}-${zone.account}$rsQualifiedStatus",
        ttl = 100,
        created = DateTime.now(),
        id = UUID.randomUUID().toString
      )

  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  def setup(): Unit = {

    // wait until the repo is ready, could take time if the table has to be created
    var notReady = true
    while (notReady) {
      val result = Await.ready(
        repo
          .listRecordSets(
            zoneId = "any",
            startFrom = None,
            maxItems = None,
            recordNameFilter = None)
          .unsafeToFuture(),
        5.seconds)
      notReady = result.value.get.isFailure
      Thread.sleep(1000)
    }

    // Clear the zone just in case there is some lagging test data
    clearTable()

    // Create all the zones
    val savedRecordSets = Future.sequence(recordSets.map(repo.putRecordSet(_).unsafeToFuture()))

    // Wait until all of the zones are done
    Await.result(savedRecordSets, 5.minutes)
  }

  def tearDown(): Unit =
    clearTable()

  private def clearTable(): Unit = {

    import scala.collection.JavaConverters._

    // clear all the zones from the table that we work with here
    val scanRequest = new ScanRequest().withTableName(recordSetTable)

    val scanResult = dynamoClient.scan(scanRequest)

    var counter = 0

    def delete(r: ScanResult) {
      val result = r.getItems.asScala.grouped(25)

      // recurse over the results of the scan, convert each group to a BatchWriteItem with Deletes, and then delete
      // using a blocking call
      result.foreach { group =>
        val recordSetIds = group.map(_.get(DynamoDBRecordSetRepository.RECORD_SET_ID).getS)
        val deletes = recordSetIds.map(deleteRecordSetFromTable)
        val batchDelete = toBatchWriteItemRequest(deletes, recordSetTable)

        dynamoClient.batchWriteItem(batchDelete)

        counter = counter + 25
      }

      if (r.getLastEvaluatedKey != null && !r.getLastEvaluatedKey.isEmpty) {
        val nextScan = new ScanRequest().withTableName(recordSetTable)
        nextScan.setExclusiveStartKey(scanResult.getLastEvaluatedKey)
        val nextScanResult = dynamoClient.scan(scanRequest)
        delete(nextScanResult)
      }
    }

    delete(scanResult)
  }

  "DynamoDBRepository" should {
    "get a record set by id" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = None,
        recordNameFilter = None)
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        foundRecordSet.recordSets should contain(testRecordSet)
      }
    }

    "get a record set count" in {
      val testRecordSet = recordSets.head
      val expected = 6
      val testFuture = repo.getRecordSetCount(testRecordSet.zoneId)
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSetCount =>
        foundRecordSetCount shouldBe expected
      }
    }

    "get a record set by record set id and zone id" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.getRecordSet(testRecordSet.zoneId, testRecordSet.id)
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        foundRecordSet shouldBe Some(testRecordSet)
      }
    }

    "get a record set by zone id, name, type" in {
      val testRecordSet = recordSets.head
      val testFuture =
        repo.getRecordSets(testRecordSet.zoneId, testRecordSet.name, testRecordSet.typ)
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        foundRecordSet shouldBe List(testRecordSet)
      }
    }

    "get a record set by zone id, case-insensitive name, type" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.getRecordSets(
        testRecordSet.zoneId,
        testRecordSet.name.toUpperCase(),
        testRecordSet.typ)
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        foundRecordSet shouldBe List(testRecordSet)
      }
    }

    "get a fully qualified record set by zone id, trailing dot-insensitive name, type" in {
      val testRecordSet = recordSets.find(_.name.endsWith(".")).get
      val testFuture =
        repo.getRecordSets(testRecordSet.zoneId, testRecordSet.name.dropRight(1), testRecordSet.typ)
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        foundRecordSet shouldBe List(testRecordSet)
      }
    }

    "get a relative record set by zone id, trailing dot-insensitive name, type" in {
      val testRecordSet = recordSets.find(_.name.endsWith("dotless")).get
      val testFuture =
        repo.getRecordSets(testRecordSet.zoneId, testRecordSet.name.concat("."), testRecordSet.typ)
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        foundRecordSet shouldBe List(testRecordSet)
      }
    }

    "get a record set by zone id, name" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.getRecordSetsByName(testRecordSet.zoneId, testRecordSet.name)
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        foundRecordSet shouldBe List(testRecordSet)
      }
    }

    "get a record set by zone id, case-insensitive name" in {
      val testRecordSet = recordSets.head
      val testFuture =
        repo.getRecordSetsByName(testRecordSet.zoneId, testRecordSet.name.toUpperCase())
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        foundRecordSet shouldBe List(testRecordSet)
      }
    }

    "get a fully qualified record set by zone id, trailing dot-insensitive name" in {
      val testRecordSet = recordSets.find(_.name.endsWith(".")).get
      val testFuture =
        repo.getRecordSetsByName(testRecordSet.zoneId, testRecordSet.name.dropRight(1))
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        foundRecordSet shouldBe List(testRecordSet)
      }
    }

    "get a relative record set by zone id, trailing dot-insensitive name" in {
      val testRecordSet = recordSets.find(_.name.endsWith("dotless")).get
      val testFuture =
        repo.getRecordSetsByName(testRecordSet.zoneId, testRecordSet.name.concat("."))
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        foundRecordSet shouldBe List(testRecordSet)
      }
    }

    "list record sets with page size of 1 returns recordSets[0] only" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = Some(1),
        recordNameFilter = None)
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        {
          foundRecordSet.recordSets should contain(recordSets(0))
          foundRecordSet.recordSets shouldNot contain(recordSets(1))
          foundRecordSet.nextId.get.split('~')(2) shouldBe recordSets(0).id
        }
      }
    }

    "list record sets with page size of 1 reusing key with page size of 1 returns recordSets[0] and recordSets[1]" in {
      val testRecordSet = recordSets.head
      val testFutureOne = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = Some(1),
        recordNameFilter = None)
      whenReady(testFutureOne.unsafeToFuture(), timeout) { foundRecordSet =>
        {
          foundRecordSet.recordSets should contain(recordSets(0))
          foundRecordSet.recordSets shouldNot contain(recordSets(1))
          val key = foundRecordSet.nextId
          val testFutureTwo = repo.listRecordSets(
            zoneId = testRecordSet.zoneId,
            startFrom = key,
            maxItems = Some(1),
            recordNameFilter = None)
          whenReady(testFutureTwo.unsafeToFuture(), timeout) { foundRecordSet =>
            {
              foundRecordSet.recordSets shouldNot contain(recordSets(0))
              foundRecordSet.recordSets should contain(recordSets(1))
              foundRecordSet.recordSets shouldNot contain(recordSets(2))
              foundRecordSet.nextId.get.split('~')(2) shouldBe recordSets(1).id
            }
          }
        }
      }
    }

    "list record sets page size of 1 then reusing key with page size of 2 returns recordSets[0], recordSets[1,2]" in {
      val testRecordSet = recordSets.head
      val testFutureOne = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = Some(1),
        recordNameFilter = None)
      whenReady(testFutureOne.unsafeToFuture(), timeout) { foundRecordSet =>
        {
          foundRecordSet.recordSets should contain(recordSets(0))
          foundRecordSet.recordSets shouldNot contain(recordSets(1))
          val key = foundRecordSet.nextId
          val testFutureTwo = repo.listRecordSets(
            zoneId = testRecordSet.zoneId,
            startFrom = key,
            maxItems = Some(2),
            recordNameFilter = None)
          whenReady(testFutureTwo.unsafeToFuture(), timeout) { foundRecordSet =>
            {
              foundRecordSet.recordSets shouldNot contain(recordSets(0))
              foundRecordSet.recordSets should contain(recordSets(1))
              foundRecordSet.recordSets should contain(recordSets(2))
              foundRecordSet.nextId.get.split('~')(2) shouldBe recordSets(2).id
            }
          }
        }
      }
    }

    "return an empty list and nextId of None when passing last record as start" in {
      val testRecordSet = recordSets.head
      val testFutureOne = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = Some(6),
        recordNameFilter = None)
      whenReady(testFutureOne.unsafeToFuture(), timeout) { foundRecordSet =>
        {
          foundRecordSet.recordSets should contain(recordSets(0))
          foundRecordSet.recordSets should contain(recordSets(1))
          foundRecordSet.recordSets should contain(recordSets(2))
          foundRecordSet.recordSets should contain(recordSets(3))
          foundRecordSet.recordSets should contain(recordSets(4))
          foundRecordSet.recordSets should contain(recordSets(5))
          val key = foundRecordSet.nextId
          val testFutureTwo = repo.listRecordSets(
            zoneId = testRecordSet.zoneId,
            startFrom = key,
            maxItems = Some(6),
            recordNameFilter = None)
          whenReady(testFutureTwo.unsafeToFuture(), timeout) { foundRecordSet =>
            {
              foundRecordSet.recordSets shouldBe List()
              foundRecordSet.nextId shouldBe None
            }
          }
        }
      }
    }

    "have nextId of None when exhausting recordSets" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = Some(7),
        recordNameFilter = None)
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        {
          foundRecordSet.recordSets should contain(recordSets(0))
          foundRecordSet.recordSets should contain(recordSets(1))
          foundRecordSet.recordSets should contain(recordSets(2))
          foundRecordSet.recordSets should contain(recordSets(3))
          foundRecordSet.recordSets should contain(recordSets(4))
          foundRecordSet.recordSets should contain(recordSets(5))
          foundRecordSet.nextId shouldBe None
        }
      }
    }

    "only retrieve recordSet with name containing 'AAAA'" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = None,
        recordNameFilter = Some("AAAA"))
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        {
          foundRecordSet.recordSets shouldNot contain(recordSets(0))
          foundRecordSet.recordSets shouldNot contain(recordSets(1))
          foundRecordSet.recordSets should contain(recordSets(2))
          foundRecordSet.recordSets should contain(recordSets(3))
        }
      }
    }

    "retrieve all recordSets with names containing 'A'" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = None,
        recordNameFilter = Some("A"))
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        {
          foundRecordSet.recordSets should contain(recordSets(0))
          foundRecordSet.recordSets should contain(recordSets(1))
          foundRecordSet.recordSets should contain(recordSets(2))
          foundRecordSet.recordSets should contain(recordSets(3))
          foundRecordSet.recordSets should contain(recordSets(4))
          foundRecordSet.recordSets should contain(recordSets(5))
        }
      }
    }

    "return an empty list if recordName filter had no match" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = None,
        recordNameFilter = Some("Dummy"))
      whenReady(testFuture.unsafeToFuture(), timeout) { foundRecordSet =>
        {
          foundRecordSet.recordSets shouldBe List()
        }
      }
    }

    "apply a change set" in {
      val newRecordSets =
        for {
          i <- 1 to 1000
        } yield
          aaaa.copy(
            zoneId = "big-apply-zone",
            name = s"$i.apply.test.",
            id = UUID.randomUUID().toString)

      val pendingChanges = newRecordSets.map(RecordSetChange.forAdd(_, zones.head, okAuth))
      val bigPendingChangeSet = ChangeSet(pendingChanges)

      try {
        val f = repo.apply(bigPendingChangeSet)
        Await.result(f.unsafeToFuture(), 1500.seconds)

        // let's fail half of them
        val split = pendingChanges.grouped(pendingChanges.length / 2).toSeq
        val halfSuccess = split.head.map(_.successful)
        val halfFailed = split(1).map(_.failed())
        val halfFailedChangeSet = record.ChangeSet(halfSuccess ++ halfFailed)

        val nextUp = repo.apply(halfFailedChangeSet)
        Await.result(nextUp.unsafeToFuture(), 1500.seconds)

        // let's run our query and see how long until we succeed(which will determine
        // how long it takes DYNAMO to update its index)
        var querySuccessful = false
        var retries = 1
        var recordSetsResult: List[RecordSet] = Nil
        while (!querySuccessful && retries <= 10) {
          // if we query now, we should get half that failed
          val rsQuery = repo.listRecordSets(
            zoneId = "big-apply-zone",
            startFrom = None,
            maxItems = None,
            recordNameFilter = None)
          recordSetsResult =
            Await.result[ListRecordSetResults](rsQuery.unsafeToFuture(), 30.seconds).recordSets
          querySuccessful = recordSetsResult.length == halfSuccess.length
          retries += 1
          Thread.sleep(100)
        }

        querySuccessful shouldBe true

        // the result of the query should be the same as those pending that succeeded
        val expected = halfSuccess.map(_.recordSet)
        recordSetsResult should contain theSameElementsAs expected
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          fail("encountered error running apply test")
      }
    }
  }
}
