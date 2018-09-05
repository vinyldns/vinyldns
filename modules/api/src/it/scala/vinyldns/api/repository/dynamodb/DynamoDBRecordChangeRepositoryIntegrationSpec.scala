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
import java.util.UUID

import com.amazonaws.services.dynamodbv2.model.{AttributeValue, DeleteItemRequest, ScanRequest}
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}
import org.scalatest.time.{Seconds, Span}
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.core.domain.record.{ChangeSet, ChangeSetStatus, RecordSetChange}
import vinyldns.core.domain.zone.{Zone, ZoneStatus}

import scala.concurrent.duration._
import scala.concurrent.Await

class DynamoDBRecordChangeRepositoryIntegrationSpec
    extends DynamoDBIntegrationSpec
    with Eventually {

  private val recordChangeTable = "record-change-live"

  private val tableConfig = ConfigFactory.parseString(s"""
       | dynamo {
       |   tableName = "$recordChangeTable"
       |   provisionedReads=30
       |   provisionedWrites=30
       | }
    """.stripMargin).withFallback(ConfigFactory.load())

  private var repo: DynamoDBRecordChangeRepository = _

  private val user = abcAuth.signedInUser.userName
  private val auth = abcAuth

  private val zoneA = Zone(
    s"live-test-$user.zone-small.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection)
  private val zoneB = Zone(
    s"live-test-$user.zone-large.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection)

  private val recordSetA =
    for {
      rsTemplate <- Seq(rsOk, aaaa, cname)
    } yield
      rsTemplate.copy(
        zoneId = zoneA.id,
        name = s"${rsTemplate.typ.toString}-${zoneA.account}.",
        ttl = 100,
        created = DateTime.now(),
        id = UUID.randomUUID().toString
      )

  private val recordSetB =
    for {
      i <- 1 to 3
    } yield
      rsOk.copy(
        zoneId = zoneB.id,
        name = s"${rsOk.typ.toString}-${zoneB.account}-$i.",
        ttl = 100,
        created = DateTime.now(),
        id = UUID.randomUUID().toString
      )

  private val updateRecordSetA =
    for {
      rsTemplate <- Seq(rsOk, aaaa, cname)
    } yield
      rsTemplate.copy(
        zoneId = zoneA.id,
        name = s"${rsTemplate.typ.toString}-${zoneA.account}.",
        ttl = 1000,
        created = DateTime.now(),
        id = UUID.randomUUID().toString
      )

  private val recordSetChangesA = {
    for {
      rs <- recordSetA
    } yield RecordSetChangeGenerator.forAdd(rs, zoneA, auth)
  }.sortBy(_.id)

  private val recordSetChangesB = {
    for {
      rs <- recordSetB
    } yield RecordSetChangeGenerator.forAdd(rs, zoneB, auth)
  }.sortBy(_.id)

  private val recordSetChangesC = {
    for {
      rs <- recordSetA
    } yield RecordSetChangeGenerator.forDelete(rs, zoneA, auth)
  }.sortBy(_.id)

  private val recordSetChangesD = {
    for {
      rs <- recordSetA
      updateRs <- updateRecordSetA
    } yield RecordSetChangeGenerator.forUpdate(rs, updateRs, zoneA)
  }.sortBy(_.id)

  private val changeSetA = ChangeSet(recordSetChangesA)
  private val changeSetB = ChangeSet(recordSetChangesB)
  private val changeSetC =
    ChangeSet(recordSetChangesC).copy(status = ChangeSetStatus.Applied)
  private val changeSetD = ChangeSet(recordSetChangesD)
    .copy(createdTimestamp = changeSetA.createdTimestamp + 1000) // make sure D is created AFTER A
  private val changeSets = List(changeSetA, changeSetB, changeSetC, changeSetD)

  //This zone is to test listing record changes in correct order
  private val zoneC = Zone(
    s"live-test-$user.record-changes.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection)
  private val baseTime = DateTime.now()
  private val timeOrder = List(
    baseTime.minusSeconds(8000),
    baseTime.minusSeconds(7000),
    baseTime.minusSeconds(6000),
    baseTime.minusSeconds(5000),
    baseTime.minusSeconds(4000),
    baseTime.minusSeconds(3000),
    baseTime.minusSeconds(2000),
    baseTime.minusSeconds(1000),
    baseTime
  )

  private val recordSetsC =
    for {
      rsTemplate <- Seq(rsOk, aaaa, cname)
    } yield
      rsTemplate.copy(
        zoneId = zoneC.id,
        name = s"${rsTemplate.typ.toString}-${zoneC.account}.",
        ttl = 100,
        id = UUID.randomUUID().toString
      )

  private val updateRecordSetsC =
    for {
      rsTemplate <- Seq(rsOk, aaaa, cname)
    } yield
      rsTemplate.copy(
        zoneId = zoneC.id,
        name = s"${rsTemplate.typ.toString}-${zoneC.account}.",
        ttl = 1000,
        id = UUID.randomUUID().toString
      )

  private val recordSetChangesCreateC = {
    for {
      (rs, index) <- recordSetsC.zipWithIndex
    } yield RecordSetChangeGenerator.forAdd(rs, zoneC, auth).copy(created = timeOrder(index))
  }

  private val recordSetChangesUpdateC = {
    for {
      (rs, index) <- recordSetsC.zipWithIndex
    } yield
      RecordSetChangeGenerator
        .forUpdate(rs, updateRecordSetsC(index), zoneC)
        .copy(created = timeOrder(index + 3))
  }

  private val recordSetChangesDeleteC = {
    for {
      (rs, index) <- recordSetsC.zipWithIndex
    } yield RecordSetChangeGenerator.forDelete(rs, zoneC, auth).copy(created = timeOrder(index + 6))
  }

  private val changeSetCreateC = ChangeSet(recordSetChangesCreateC)
  private val changeSetUpdateC = ChangeSet(recordSetChangesUpdateC)
  private val changeSetDeleteC = ChangeSet(recordSetChangesDeleteC)
  private val changeSetsC = List(changeSetCreateC, changeSetUpdateC, changeSetDeleteC)
  private val recordSetChanges: List[RecordSetChange] =
    (recordSetChangesCreateC ++ recordSetChangesUpdateC ++ recordSetChangesDeleteC)
      .sortBy(_.created.getMillis)
      .toList
      .reverse // Changes are retrieved by time stamp in decending order

  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  def setup(): Unit = {
    repo = new DynamoDBRecordChangeRepository(tableConfig, dynamoDBHelper)

    var notReady = true
    while (notReady) {
      val result = Await.ready(repo.getRecordSetChange("any", "any").unsafeToFuture(), 5.seconds)
      notReady = result.value.get.isFailure
    }

    // Clear the table just in case there is some lagging test data
    clearTable()

    changeSets.foreach { changeSet =>
      // Save the change set
      val savedChangeSet = repo.save(changeSet)

      // Wait until all of the change sets are saved
      Await.result(savedChangeSet.unsafeToFuture(), 5.minutes)
    }

    changeSetsC.foreach { changeSet =>
      // Save the change set
      val savedChangeSet = repo.save(changeSet)

      // Wait until all of the change sets are saved
      Await.result(savedChangeSet.unsafeToFuture(), 5.minutes)
    }
  }

  def tearDown(): Unit =
    clearTable()

  private def clearTable(): Unit = {

    import scala.collection.JavaConverters._

    // clear the table that we work with here
    // NOTE: This is brute force and could be cleaner
    val scanRequest = new ScanRequest()
      .withTableName(recordChangeTable)

    val result =
      dynamoClient.scan(scanRequest).getItems.asScala.map(_.get(repo.RECORD_SET_CHANGE_ID).getS())

    result.foreach(deleteItem)
  }

  private def deleteItem(recordSetChangeId: String): Unit = {
    val key = new util.HashMap[String, AttributeValue]()
    key.put(repo.RECORD_SET_CHANGE_ID, new AttributeValue(recordSetChangeId))
    val request = new DeleteItemRequest().withTableName(recordChangeTable).withKey(key)
    try {
      dynamoClient.deleteItem(request)
    } catch {
      case ex: Throwable =>
        throw new UnexpectedDynamoResponseException(ex.getMessage, ex)
    }
  }

  "DynamoDBRepository" should {
    "get a record change set by id" in {
      val testRecordSetChange = pendingCreateAAAA.copy(id = genString)

      val f =
        for {
          saved <- repo.save(ChangeSet(Seq(testRecordSetChange)))
          retrieved <- repo.getRecordSetChange(saved.zoneId, testRecordSetChange.id)
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { result =>
        result shouldBe Some(testRecordSetChange)
      }
    }

    "get changes by zone id" in {
      val f = repo.getChanges(zoneA.id)
      whenReady(f.unsafeToFuture(), timeout) { result =>
        val sortedResults = result.map { changeSet =>
          changeSet.copy(changes = changeSet.changes.sortBy(_.id))
        }
        sortedResults.size shouldBe 3
        sortedResults should contain(changeSetA)
        sortedResults should contain(changeSetC)
        sortedResults should contain(changeSetD)
      }
    }

    "get pending changes by zone id are sorted by earliest created timestamp" in {
      val f = repo.getPendingChangeSets(zoneA.id)
      whenReady(f.unsafeToFuture(), timeout) { result =>
        val sortedResults = result.map { changeSet =>
          changeSet.copy(changes = changeSet.changes.sortBy(_.id))
        }
        sortedResults.size shouldBe 2
        sortedResults should contain(changeSetA)
        sortedResults should contain(changeSetD)
        sortedResults should not contain changeSetC
        result.head.id should equal(changeSetA.id)
        result(1).id should equal(changeSetD.id)
      }
    }

    "list all record set changes in zone C" in {
      eventually {
        val testFuture = repo.listRecordSetChanges(zoneC.id)
        whenReady(testFuture.unsafeToFuture(), timeout) { result =>
          result.items shouldBe recordSetChanges
        }
      }
    }

    "list record set changes with a page size of one" in {
      val testFuture = repo.listRecordSetChanges(zoneC.id, maxItems = 1)
      whenReady(testFuture.unsafeToFuture(), timeout) { result =>
        {
          result.items shouldBe recordSetChanges.take(1)
        }
      }
    }

    "list record set changes with page size of one and reuse key to get another page with size of two" in {
      val testFuture = repo.listRecordSetChanges(zoneC.id, maxItems = 1)
      whenReady(testFuture.unsafeToFuture(), timeout) { result =>
        {
          val key = result.nextId
          val testFuture2 = repo.listRecordSetChanges(zoneC.id, startFrom = key, maxItems = 2)
          whenReady(testFuture2.unsafeToFuture(), timeout) { result =>
            {
              val page2 = result.items
              page2 shouldBe recordSetChanges.slice(1, 3)
            }
          }
        }
      }
    }

    "return an empty list and nextId of None when passing last record as start" in {
      val testFuture = repo.listRecordSetChanges(zoneC.id, maxItems = 9)
      whenReady(testFuture.unsafeToFuture(), timeout) { result =>
        {
          val key = result.nextId
          val testFuture2 = repo.listRecordSetChanges(zoneC.id, startFrom = key)
          whenReady(testFuture2.unsafeToFuture(), timeout) { result =>
            {
              result.nextId shouldBe None
              result.items shouldBe List()
            }
          }
        }
      }
    }

    "have nextId of None when exhausting record changes" in {
      val testFuture = repo.listRecordSetChanges(zoneC.id, maxItems = 10)
      whenReady(testFuture.unsafeToFuture(), timeout) { result =>
        result.nextId shouldBe None
      }
    }

    "return empty list with startFrom of zero" in {
      val testFuture = repo.listRecordSetChanges(zoneC.id, startFrom = Some("0"))
      whenReady(testFuture.unsafeToFuture(), timeout) { result =>
        {
          result.nextId shouldBe None
          result.items shouldBe List()
        }
      }
    }
  }
}
