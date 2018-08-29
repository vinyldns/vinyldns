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
import java.util.Collections

import com.amazonaws.services.dynamodbv2.model._
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class DynamoDBGroupChangeRepositoryIntegrationSpec extends DynamoDBIntegrationSpec with Eventually {
  private implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_.isAfter(_))
  private implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val GROUP_CHANGES_TABLE = "group-changes-live"

  private val tableConfig = ConfigFactory.parseString(s"""
       | dynamo {
       |   tableName = "$GROUP_CHANGES_TABLE"
       |   provisionedReads=30
       |   provisionedWrites=30
       | }
    """.stripMargin).withFallback(ConfigFactory.load())

  private var repo: DynamoDBGroupChangeRepository = _

  private val groupChanges = Seq(okGroupChange, okGroupChangeUpdate, okGroupChangeDelete) ++
    listOfDummyGroupChanges ++ listOfRandomTimeGroupChanges

  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  def setup(): Unit = {
    repo = new DynamoDBGroupChangeRepository(tableConfig, dynamoDBHelper)

    // wait until the repo is ready, could take time if the table has to be created
    var notReady = true
    while (notReady) {
      val result = Await.ready(repo.getGroupChange("any").unsafeToFuture(), 5.seconds)
      notReady = result.value.get.isFailure
      Thread.sleep(2000)
    }

    clearGroupChanges()

    // Create all the changes
    val savedGroupChanges = Future.sequence(groupChanges.map(repo.save(_).unsafeToFuture()))

    // Wait until all of the changes are done
    Await.result(savedGroupChanges, 5.minutes)
  }

  def tearDown(): Unit = {

    val request = new DeleteTableRequest().withTableName(GROUP_CHANGES_TABLE)
    val deleteTables = dynamoDBHelper.deleteTable(request)
    Await.ready(deleteTables.unsafeToFuture(), 100.seconds)
  }

  private def clearGroupChanges(): Unit = {

    import scala.collection.JavaConverters._

    val scanRequest = new ScanRequest().withTableName(GROUP_CHANGES_TABLE)

    val allGroupChanges = dynamoClient.scan(scanRequest).getItems.asScala.map(repo.fromItem)

    val batchWrites = allGroupChanges
      .map { groupChange =>
        val key = new util.HashMap[String, AttributeValue]()
        key.put("group_change_id", new AttributeValue(groupChange.id))
        new WriteRequest().withDeleteRequest(new DeleteRequest().withKey(key))
      }
      .grouped(25)
      .map { deleteRequests =>
        new BatchWriteItemRequest()
          .withRequestItems(Collections.singletonMap(GROUP_CHANGES_TABLE, deleteRequests.asJava))
      }
      .toList

    batchWrites.foreach { batch =>
      dynamoClient.batchWriteItem(batch)
    }
  }

  "DynamoDBGroupChangeRepository" should {
    "get a group change by id" in {
      val targetGroupChange = okGroupChange
      whenReady(repo.getGroupChange(targetGroupChange.id).unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe Some(targetGroupChange)
      }
    }

    "return none when no matching id is found" in {
      whenReady(repo.getGroupChange("NotFound").unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe None
      }
    }

    "save a group change with oldGroup = None" in {
      val targetGroupChange = okGroupChange

      val test =
        for {
          saved <- repo.save(targetGroupChange)
          retrieved <- repo.getGroupChange(saved.id)
        } yield retrieved

      whenReady(test.unsafeToFuture(), timeout) { saved =>
        saved shouldBe Some(targetGroupChange)
      }
    }

    "save a group change with oldGroup set" in {
      val targetGroupChange = okGroupChangeUpdate

      val test =
        for {
          saved <- repo.save(targetGroupChange)
          retrieved <- repo.getGroupChange(saved.id)
        } yield retrieved

      whenReady(test.unsafeToFuture(), timeout) { saved =>
        saved shouldBe Some(targetGroupChange)
      }
    }

    "getGroupChanges should return the recent changes and the correct last key" in {
      whenReady(repo.getGroupChanges(oneUserDummyGroup.id, None, 100).unsafeToFuture(), timeout) {
        retrieved =>
          retrieved.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(0, 100)
          retrieved.lastEvaluatedTimeStamp shouldBe Some(
            listOfDummyGroupChanges(99).created.getMillis.toString)
      }
    }

    "getGroupChanges should start using the time startFrom" in {
      whenReady(
        repo
          .getGroupChanges(
            oneUserDummyGroup.id,
            Some(listOfDummyGroupChanges(50).created.getMillis.toString),
            100)
          .unsafeToFuture(),
        timeout) { retrieved =>
        retrieved.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(51, 151)
        retrieved.lastEvaluatedTimeStamp shouldBe Some(
          listOfDummyGroupChanges(150).created.getMillis.toString)
      }
    }

    "getGroupChanges returns entire page and nextId = None if there are less than maxItems left" in {
      whenReady(
        repo
          .getGroupChanges(
            oneUserDummyGroup.id,
            Some(listOfDummyGroupChanges(200).created.getMillis.toString),
            100)
          .unsafeToFuture(),
        timeout) { retrieved =>
        retrieved.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(201, 300)
        retrieved.lastEvaluatedTimeStamp shouldBe None
      }
    }

    "getGroupChanges returns 3 pages of items" in {
      val test =
        for {
          page1 <- repo.getGroupChanges(oneUserDummyGroup.id, None, 100)
          page2 <- repo.getGroupChanges(oneUserDummyGroup.id, page1.lastEvaluatedTimeStamp, 100)
          page3 <- repo.getGroupChanges(oneUserDummyGroup.id, page2.lastEvaluatedTimeStamp, 100)
          page4 <- repo.getGroupChanges(oneUserDummyGroup.id, page3.lastEvaluatedTimeStamp, 100)
        } yield (page1, page2, page3, page4)
      whenReady(test.unsafeToFuture(), timeout) { retrieved =>
        retrieved._1.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(0, 100)
        retrieved._1.lastEvaluatedTimeStamp shouldBe Some(
          listOfDummyGroupChanges(99).created.getMillis.toString)
        retrieved._2.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(
          100,
          200)
        retrieved._2.lastEvaluatedTimeStamp shouldBe Some(
          listOfDummyGroupChanges(199).created.getMillis.toString)
        retrieved._3.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(
          200,
          300)
        retrieved._3.lastEvaluatedTimeStamp shouldBe Some(
          listOfDummyGroupChanges(299).created.getMillis.toString) // the limit was reached before the end of list
        retrieved._4.changes should contain theSameElementsAs List() // no matches found in the rest of the list
        retrieved._4.lastEvaluatedTimeStamp shouldBe None
      }
    }

    "getGroupChanges should return `maxItem` items" in {
      whenReady(repo.getGroupChanges(oneUserDummyGroup.id, None, 5).unsafeToFuture(), timeout) {
        retrieved =>
          retrieved.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(0, 5)
          retrieved.lastEvaluatedTimeStamp shouldBe Some(
            listOfDummyGroupChanges(4).created.getMillis.toString)
      }
    }

    "getGroupChanges should handle changes inserted in random order" in {
      // group changes have a random time stamp and inserted in random order
      eventually(timeout) {
        whenReady(repo.getGroupChanges(randomTimeGroup.id, None, 100).unsafeToFuture(), timeout) {
          retrieved =>
            val sorted = listOfRandomTimeGroupChanges.sortBy(_.created)
            retrieved.changes should contain theSameElementsAs sorted.slice(0, 100)
            retrieved.lastEvaluatedTimeStamp shouldBe Some(sorted(99).created.getMillis.toString)
        }
      }
    }
  }
}
