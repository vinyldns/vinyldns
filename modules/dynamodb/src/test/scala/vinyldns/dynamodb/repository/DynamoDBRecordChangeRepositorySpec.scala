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

import com.amazonaws.services.dynamodbv2.model._
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.domain.record.ChangeSet
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import cats.effect._
import org.scalatest.BeforeAndAfterEach
import vinyldns.dynamodb.DynamoTestConfig

import scala.concurrent.duration.FiniteDuration

class DynamoDBRecordChangeRepositorySpec
    extends AnyWordSpec
    with MockitoSugar
    with Matchers
    with ScalaFutures
    with BeforeAndAfterEach {

  private val dynamoDBHelper = mock[DynamoDBHelper]
  private val recordSetConfig = DynamoTestConfig.recordChangeStoreConfig
  private val recordChangeTable = recordSetConfig.tableName

  class TestRepo extends DynamoDBRecordChangeRepository(recordChangeTable, dynamoDBHelper)

  override def beforeEach(): Unit =
    reset(dynamoDBHelper)

  "DynamoDBRecordChangeRepository.save" should {
    "group change sets into batch writes with 25 in each" in {
      val changes = for (_ <- 1 to 52) yield pendingCreateAAAA
      val changeSet = ChangeSet(changes)

      val batchCaptor = ArgumentCaptor.forClass(classOf[Seq[WriteRequest]])
      val dynamoResponse = mock[BatchWriteItemResult]
      val dynamoRequest = mock[BatchWriteItemRequest]

      doReturn(dynamoRequest)
        .when(dynamoDBHelper)
        .toBatchWriteItemRequest(any[Seq[WriteRequest]], anyString)
      doReturn(IO.pure(dynamoResponse))
        .when(dynamoDBHelper)
        .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration])

      val store = new TestRepo
      val response = store.save(changeSet).unsafeRunSync()

      verify(dynamoDBHelper, times(3))
        .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration])
      verify(dynamoDBHelper, times(3)).toBatchWriteItemRequest(batchCaptor.capture(), anyString)

      response shouldBe changeSet

      // we should have 3 batches
      val batchWrites = batchCaptor.getAllValues

      batchWrites.get(0).size shouldBe 25
      batchWrites.get(1).size shouldBe 25
      batchWrites.get(2).size shouldBe 2
    }

    "returns a future failure if the first batch fails" in {
      val changes = for (_ <- 0 to 52) yield pendingCreateAAAA
      val changeSet = ChangeSet(changes)

      val dynamoResponse = mock[BatchWriteItemResult]
      val unprocessed = mock[java.util.Map[String, AttributeValue]]
      doReturn(null).when(unprocessed).get(anyString())
      doReturn(unprocessed).when(dynamoResponse).getUnprocessedItems

      val store = new TestRepo

      doThrow(new RuntimeException("failed")) //fail on the first batch
        .when(dynamoDBHelper)
        .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration])

      val result = store.save(changeSet)
      a[RuntimeException] shouldBe thrownBy(result.unsafeRunSync())
    }

    "returns a future failure if any batch fails" in {
      val changes = for (_ <- 0 to 52) yield pendingCreateAAAA
      val changeSet = ChangeSet(changes)

      val dynamoResponse = mock[BatchWriteItemResult]
      val unprocessed = mock[java.util.Map[String, AttributeValue]]
      doReturn(null).when(unprocessed).get(anyString())
      doReturn(unprocessed).when(dynamoResponse).getUnprocessedItems

      val store = new TestRepo

      when(
        dynamoDBHelper
          .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration])
      ).thenReturn(IO.pure(dynamoResponse))
        .thenThrow(new RuntimeException("failed")) //fail on the second batch

      val result = store.save(changeSet)
      a[RuntimeException] shouldBe thrownBy(result.unsafeRunSync())
    }
  }

  "DynamoDBRecordChangeRepository.getRecordSetChange(zoneId, recordSetChangeId)" should {
    "call AmazonDynamoDBClient.get when retrieving an record set using an id" in {
      val dynamoResponse = mock[QueryResult]

      val store = new TestRepo
      val expected = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      expected.add(store.toItem(pendingChangeSet, pendingCreateAAAA))
      when(dynamoResponse.getItems).thenReturn(expected)
      when(dynamoDBHelper.query(any[QueryRequest])).thenReturn(IO.pure(dynamoResponse))

      val response = store.getRecordSetChange(zoneActive.id, pendingCreateAAAA.id).unsafeRunSync()

      verify(dynamoDBHelper).query(any[QueryRequest])

      response shouldBe Some(pendingCreateAAAA)
    }
    "throw exception when get returns an unexpected response" in {
      when(dynamoDBHelper.query(any[QueryRequest]))
        .thenThrow(new ResourceNotFoundException("bar does not exist"))
      val store = new TestRepo

      a[ResourceNotFoundException] should be thrownBy store.getRecordSetChange(
        zoneActive.id,
        pendingCreateAAAA.id
      )
    }
    "return None if not found" in {
      val dynamoResponse = mock[QueryResult]
      when(dynamoResponse.getItems)
        .thenReturn(new java.util.ArrayList[java.util.Map[String, AttributeValue]]())
      when(dynamoDBHelper.query(any[QueryRequest])).thenReturn(IO.pure(dynamoResponse))

      val store = new DynamoDBRecordChangeRepository(recordChangeTable, dynamoDBHelper)
      val response = store.getRecordSetChange(zoneActive.id, pendingCreateAAAA.id).unsafeRunSync()

      verify(dynamoDBHelper).query(any[QueryRequest])

      response shouldBe None
    }
  }

  "DynamoDBRecordChangeRepository.toRecordSetChange" should {
    "be able to decode the output of toItem" in {
      val store = new TestRepo
      val blob = store.toItem(pendingChangeSet, pendingCreateAAAA)
      val result = store.toRecordSetChange(blob)

      result shouldBe pendingCreateAAAA
    }
    "throw an error when given bad input" in {
      val store = new TestRepo
      val blob = new java.util.HashMap[String, AttributeValue]()
      intercept[UnexpectedDynamoResponseException] {
        store.toRecordSetChange(blob)
      }
    }
  }

  "DynamoDBRecordChangeRepository.toItem" should {
    "be able to encode an item" in {
      val store = new TestRepo
      val result = store.toItem(pendingChangeSet, pendingCreateAAAA)

      store.toRecordSetChange(result) shouldBe pendingCreateAAAA
    }
  }

}
