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

import java.util.UUID

import cats.implicits._
import com.amazonaws.services.dynamodbv2.model.{ScanRequest, ScanResult}
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import vinyldns.core.domain.membership.User
import vinyldns.core.domain.zone.{Zone, ZoneStatus}
import vinyldns.core.TestZoneData.testConnection
import vinyldns.core.TestRecordSetData._
import vinyldns.core.domain.record._

import scala.concurrent.duration._

class DynamoDBRecordSetRepositoryIntegrationSpec
    extends DynamoDBIntegrationSpec
    with DynamoDBRecordSetConversions {

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

  def setup(): Unit = {

    // wait until the repo is ready, could take time if the table has to be created
    val call = repo.listRecordSets(zoneId = "any", startFrom = None, maxItems = None, recordNameFilter = None)
    waitForRepo(call)

    // Clear the zone just in case there is some lagging test data
    clearTable()

    // Create all the items
    val results = recordSets.map(repo.putRecordSet(_)).toList.parSequence

    // Wait until all of the data is stored
    results.unsafeRunTimed(5.minutes).getOrElse(fail("timeout waiting for data load"))
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

  "DynamoDBRecordSetRepository" should {
    "get a record set by id" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = None,
        recordNameFilter = None)

      testFuture.unsafeRunSync().recordSets should contain(testRecordSet)
    }

    "get a record set count" in {
      val testRecordSet = recordSets.head
      val expected = 6
      val testFuture = repo.getRecordSetCount(testRecordSet.zoneId)
      testFuture.unsafeRunSync() shouldBe expected
    }

    "get a record set by record set id and zone id" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.getRecordSet(testRecordSet.zoneId, testRecordSet.id)
      testFuture.unsafeRunSync() shouldBe Some(testRecordSet)
    }

    "get a record set by zone id, name, type" in {
      val testRecordSet = recordSets.head
      val testFuture =
        repo.getRecordSets(testRecordSet.zoneId, testRecordSet.name, testRecordSet.typ)
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "get a record set by zone id, case-insensitive name, type" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.getRecordSets(
        testRecordSet.zoneId,
        testRecordSet.name.toUpperCase(),
        testRecordSet.typ)
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "get a fully qualified record set by zone id, trailing dot-insensitive name, type" in {
      val testRecordSet = recordSets.find(_.name.endsWith(".")).get
      val testFuture =
        repo.getRecordSets(testRecordSet.zoneId, testRecordSet.name.dropRight(1), testRecordSet.typ)
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "get a relative record set by zone id, trailing dot-insensitive name, type" in {
      val testRecordSet = recordSets.find(_.name.endsWith("dotless")).get
      val testFuture =
        repo.getRecordSets(testRecordSet.zoneId, testRecordSet.name.concat("."), testRecordSet.typ)
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "get a record set by zone id, name" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.getRecordSetsByName(testRecordSet.zoneId, testRecordSet.name)
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "get a record set by zone id, case-insensitive name" in {
      val testRecordSet = recordSets.head
      val testFuture =
        repo.getRecordSetsByName(testRecordSet.zoneId, testRecordSet.name.toUpperCase())
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "get a fully qualified record set by zone id, trailing dot-insensitive name" in {
      val testRecordSet = recordSets.find(_.name.endsWith(".")).get
      val testFuture =
        repo.getRecordSetsByName(testRecordSet.zoneId, testRecordSet.name.dropRight(1))
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "get a relative record set by zone id, trailing dot-insensitive name" in {
      val testRecordSet = recordSets.find(_.name.endsWith("dotless")).get
      val testFuture =
        repo.getRecordSetsByName(testRecordSet.zoneId, testRecordSet.name.concat("."))
      testFuture.unsafeRunSync() shouldBe List(testRecordSet)
    }

    "list record sets with page size of 1 returns recordSets[0] only" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = Some(1),
        recordNameFilter = None)

      val foundRecordSet = testFuture.unsafeRunSync()

      foundRecordSet.recordSets should contain(recordSets(0))
      foundRecordSet.recordSets shouldNot contain(recordSets(1))
      foundRecordSet.nextId.get.split('~')(2) shouldBe recordSets(0).id
    }

    "list record sets with page size of 1 reusing key with page size of 1 returns recordSets[0] and recordSets[1]" in {
      val testRecordSet = recordSets.head
      val testFutureOne = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = Some(1),
        recordNameFilter = None)

      val foundRecordSet = testFutureOne.unsafeRunSync()

      foundRecordSet.recordSets should contain(recordSets(0))
      foundRecordSet.recordSets shouldNot contain(recordSets(1))
      val key = foundRecordSet.nextId
      val testFutureTwo = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = key,
        maxItems = Some(1),
        recordNameFilter = None)

      val foundRecordSetTwo = testFutureTwo.unsafeRunSync()

      foundRecordSetTwo.recordSets shouldNot contain(recordSets(0))
      foundRecordSetTwo.recordSets should contain(recordSets(1))
      foundRecordSetTwo.recordSets shouldNot contain(recordSets(2))
      foundRecordSetTwo.nextId.get.split('~')(2) shouldBe recordSets(1).id
    }

    "list record sets page size of 1 then reusing key with page size of 2 returns recordSets[0], recordSets[1,2]" in {
      val testRecordSet = recordSets.head
      val testFutureOne = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = Some(1),
        recordNameFilter = None)

      val foundRecordSet = testFutureOne.unsafeRunSync()
      foundRecordSet.recordSets should contain(recordSets(0))
      foundRecordSet.recordSets shouldNot contain(recordSets(1))
      val key = foundRecordSet.nextId
      val testFutureTwo = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = key,
        maxItems = Some(2),
        recordNameFilter = None)

      val foundRecordSetTwo = testFutureTwo.unsafeRunSync()
      foundRecordSetTwo.recordSets shouldNot contain(recordSets(0))
      foundRecordSetTwo.recordSets should contain(recordSets(1))
      foundRecordSetTwo.recordSets should contain(recordSets(2))
      foundRecordSetTwo.nextId.get.split('~')(2) shouldBe recordSets(2).id
    }

    "return an empty list and nextId of None when passing last record as start" in {
      val testRecordSet = recordSets.head
      val testFutureOne = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = Some(6),
        recordNameFilter = None)

      val foundRecordSet = testFutureOne.unsafeRunSync()

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

      val foundRecordSetTwo = testFutureTwo.unsafeRunSync()
      foundRecordSetTwo.recordSets shouldBe List()
      foundRecordSetTwo.nextId shouldBe None
    }

    "have nextId of None when exhausting recordSets" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = Some(7),
        recordNameFilter = None)

      val foundRecordSet = testFuture.unsafeRunSync()
      foundRecordSet.recordSets should contain(recordSets(0))
      foundRecordSet.recordSets should contain(recordSets(1))
      foundRecordSet.recordSets should contain(recordSets(2))
      foundRecordSet.recordSets should contain(recordSets(3))
      foundRecordSet.recordSets should contain(recordSets(4))
      foundRecordSet.recordSets should contain(recordSets(5))
      foundRecordSet.nextId shouldBe None
    }

    "only retrieve recordSet with name containing 'AAAA'" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = None,
        recordNameFilter = Some("AAAA"))

      val foundRecordSet = testFuture.unsafeRunSync()
      foundRecordSet.recordSets shouldNot contain(recordSets(0))
      foundRecordSet.recordSets shouldNot contain(recordSets(1))
      foundRecordSet.recordSets should contain(recordSets(2))
      foundRecordSet.recordSets should contain(recordSets(3))
    }

    "retrieve all recordSets with names containing 'A'" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = None,
        recordNameFilter = Some("A"))

      val foundRecordSet = testFuture.unsafeRunSync()
      foundRecordSet.recordSets should contain(recordSets(0))
      foundRecordSet.recordSets should contain(recordSets(1))
      foundRecordSet.recordSets should contain(recordSets(2))
      foundRecordSet.recordSets should contain(recordSets(3))
      foundRecordSet.recordSets should contain(recordSets(4))
      foundRecordSet.recordSets should contain(recordSets(5))
    }

    "return an empty list if recordName filter had no match" in {
      val testRecordSet = recordSets.head
      val testFuture = repo.listRecordSets(
        zoneId = testRecordSet.zoneId,
        startFrom = None,
        maxItems = None,
        recordNameFilter = Some("Dummy"))

      testFuture.unsafeRunSync().recordSets shouldBe List()
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

      val pendingChanges = newRecordSets.map(makeTestAddChange(_, zones.head))

      val bigPendingChangeSet = ChangeSet(pendingChanges)

      try {
        val f = repo.apply(bigPendingChangeSet)

        val apply = f.unsafeRunTimed(1500.seconds)
        if (apply.isEmpty) {
          throw new RuntimeException("change set apply timed out")
        }

        // let's fail half of them
        val split = pendingChanges.grouped(pendingChanges.length / 2).toSeq
        val halfSuccess = split.head.map(_.successful)
        val halfFailed = split(1).map(_.failed())
        val halfFailedChangeSet = ChangeSet(halfSuccess ++ halfFailed)

        val nextUp = repo.apply(halfFailedChangeSet)
        val nextUpApply = nextUp.unsafeRunTimed(1500.seconds)
        if (nextUpApply.isEmpty) {
          throw new RuntimeException("nextUp change set apply timed out")
        }

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

          recordSetsResult = rsQuery.unsafeRunTimed(30.seconds) match {
            case Some(result) => result.recordSets
            case None => throw new RuntimeException("Query timed out")
          }

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
