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

import java.util

import com.amazonaws.services.dynamodbv2.model._
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import vinyldns.core.domain.record.{ChangeSet, NameSort}
import vinyldns.core.TestRecordSetData._
import cats.effect._
import vinyldns.dynamodb.DynamoTestConfig

import scala.concurrent.duration.FiniteDuration

class DynamoDBRecordSetRepositorySpec
    extends WordSpec
    with MockitoSugar
    with Matchers
    with ScalaFutures
    with BeforeAndAfterEach {

  private val dynamoDBHelper = mock[DynamoDBHelper]
  private val recordChangeConfig = DynamoTestConfig.recordChangeStoreConfig

  class TestDynamoRecordSetRepo
      extends DynamoDBRecordSetRepository(recordChangeConfig.tableName, dynamoDBHelper)

  override def beforeEach(): Unit =
    reset(dynamoDBHelper)

  "DynamoDBRecordSetRepository.applyChangeSet" should {
    "return the ChangeSet" in {
      val dynamoResponse = mock[BatchWriteItemResult]
      val unprocessed = mock[java.util.Map[String, AttributeValue]]
      doReturn(null).when(unprocessed).get(anyString())
      doReturn(unprocessed).when(dynamoResponse).getUnprocessedItems

      val store = new TestDynamoRecordSetRepo
      doReturn(IO.pure(dynamoResponse))
        .when(dynamoDBHelper)
        .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration])

      val response = store.apply(pendingChangeSet).unsafeRunSync()

      verify(dynamoDBHelper).batchWriteItem(
        any[String],
        any[BatchWriteItemRequest],
        any[Int],
        any[FiniteDuration]
      )

      response shouldBe pendingChangeSet
    }

    "group change sets into batch writes with 25 in each" in {
      val changes = for (_ <- 1 to 52) yield pendingCreateAAAA
      val batchCaptor = ArgumentCaptor.forClass(classOf[Seq[WriteRequest]])
      val dynamoResponse = mock[BatchWriteItemResult]
      val dynamoRequest = mock[BatchWriteItemRequest]

      val store = new TestDynamoRecordSetRepo

      doReturn(dynamoRequest)
        .when(dynamoDBHelper)
        .toBatchWriteItemRequest(any[Seq[WriteRequest]], anyString)
      doReturn(IO.pure(dynamoResponse))
        .when(dynamoDBHelper)
        .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration])

      val changeSet = ChangeSet(changes)
      val response = store.apply(changeSet).unsafeRunSync()

      verify(dynamoDBHelper, times(3)).batchWriteItem(
        any[String],
        any[BatchWriteItemRequest],
        any[Int],
        any[FiniteDuration]
      )
      verify(dynamoDBHelper, times(3)).toBatchWriteItemRequest(batchCaptor.capture(), anyString)

      // we should have 3 batches
      val batchWrites = batchCaptor.getAllValues

      batchWrites.get(0).size shouldBe 25
      batchWrites.get(1).size shouldBe 25
      batchWrites.get(2).size shouldBe 2

      response shouldBe changeSet
      response.status shouldBe changeSet.status
    }

    "returns a future failure if any batch fails" in {
      val changes = for (_ <- 0 to 52) yield pendingCreateAAAA
      val dynamoResponse = mock[BatchWriteItemResult]
      val unprocessed = mock[java.util.Map[String, AttributeValue]]
      doReturn(null).when(unprocessed).get(anyString())
      doReturn(unprocessed).when(dynamoResponse).getUnprocessedItems

      val store = new TestDynamoRecordSetRepo
      doReturn(IO.pure(dynamoResponse))
        .doThrow(new RuntimeException("failed"))
        .when(dynamoDBHelper)
        .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration])

      val result = store.apply(ChangeSet(changes))
      a[RuntimeException] shouldBe thrownBy(result.unsafeRunSync())
    }
  }

  "DynamoDBRecordSetRepository.getRecordSet(zoneId, recordSetId)" should {
    "call AmazonDynamoDBClient.get when retrieving an record set using an id" in {
      val dynamoResponse = mock[GetItemResult]

      val store = new TestDynamoRecordSetRepo
      val expected = store.toItem(rsOk)
      when(dynamoResponse.getItem).thenReturn(expected)
      when(dynamoDBHelper.getItem(any[GetItemRequest]))
        .thenReturn(IO.pure(dynamoResponse))

      val response = store.getRecordSet(rsOk.zoneId).unsafeRunSync()

      verify(dynamoDBHelper).getItem(any[GetItemRequest])

      response shouldBe Some(rsOk)
    }
    "throw exception when get returns an unexpected response" in {
      when(dynamoDBHelper.getItem(any[GetItemRequest]))
        .thenThrow(new ResourceNotFoundException("bar does not exist"))
      val store = new TestDynamoRecordSetRepo

      a[ResourceNotFoundException] should be thrownBy store.getRecordSet(rsOk.zoneId)

    }
    "return None if not found" in {
      val dynamoResponse = mock[GetItemResult]
      when(dynamoResponse.getItem).thenReturn(null)
      when(dynamoDBHelper.getItem(any[GetItemRequest]))
        .thenReturn(IO.pure(dynamoResponse))

      val store = new DynamoDBRecordSetRepository(recordChangeConfig.tableName, dynamoDBHelper)
      val response = store.getRecordSet(rsOk.zoneId).unsafeRunSync()

      verify(dynamoDBHelper).getItem(any[GetItemRequest])

      response shouldBe None
    }
  }

  "DynamoDBRecordSetRepository.listRecordSets" should {
    "returns empty if no record set exist" in {

      val store = new DynamoDBRecordSetRepository(recordChangeConfig.tableName, dynamoDBHelper)

      val dynamoResponse = mock[QueryResult]
      val expectedItems = new util.ArrayList[util.HashMap[String, AttributeValue]]()

      doReturn(expectedItems).when(dynamoResponse).getItems
      doReturn(null).when(dynamoResponse).getLastEvaluatedKey
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val response = store
        .listRecordSets(
          zoneId = Some(rsOk.zoneId),
          startFrom = None,
          maxItems = None,
          recordNameFilter = None,
          recordTypeFilter = None,
          nameSort = NameSort.ASC,
          recordOwnerGroupFilter = None
        )
        .unsafeRunSync()

      verify(dynamoDBHelper).query(any[QueryRequest])

      response.recordSets shouldBe empty
    }

    "returns all record sets returned" in {
      val store = new TestDynamoRecordSetRepo

      val dynamoResponse = mock[QueryResult]
      val expectedItems = new util.ArrayList[util.Map[String, AttributeValue]]()
      expectedItems.add(store.toItem(rsOk))
      expectedItems.add(store.toItem(aaaa))
      expectedItems.add(store.toItem(cname))

      doReturn(expectedItems).when(dynamoResponse).getItems
      doReturn(null).when(dynamoResponse).getLastEvaluatedKey
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val response =
        store
          .listRecordSets(
            Some(rsOk.zoneId),
            None,
            Some(3),
            None,
            None,
            None,
            NameSort.ASC
          )
          .unsafeRunSync()
      verify(dynamoDBHelper).query(any[QueryRequest])

      (response.recordSets should contain).allOf(rsOk, aaaa, cname)
    }

    "throw exception when query returns an unexpected response" in {
      when(dynamoDBHelper.query(any[QueryRequest]))
        .thenThrow(new ResourceNotFoundException("failed"))
      val store = new TestDynamoRecordSetRepo

      a[ResourceNotFoundException] should be thrownBy store.listRecordSets(
        zoneId = Some(rsOk.zoneId),
        startFrom = None,
        maxItems = None,
        recordNameFilter = None,
        recordTypeFilter = None,
        nameSort = NameSort.ASC,
        recordOwnerGroupFilter = None
      )
    }

    "return an error if used without a zoneId" in {
      val store = new TestDynamoRecordSetRepo
      an[UnsupportedDynamoDBRepoFunction] should be thrownBy store
        .listRecordSets(
          zoneId = None,
          startFrom = None,
          maxItems = None,
          recordNameFilter = None,
          recordTypeFilter = None,
          nameSort = NameSort.ASC,
          recordOwnerGroupFilter = None
        )
        .unsafeRunSync()
    }
  }

  "DynamoDBRecordSetRepository.getRecordSetsByName(zoneId, name)" should {
    "returns empty if no record set exist" in {
      val dynamoResponse = mock[QueryResult]
      val store = new TestDynamoRecordSetRepo

      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])
      doReturn(null).when(dynamoResponse).getLastEvaluatedKey
      doReturn(new java.util.ArrayList[java.util.Map[String, AttributeValue]]())
        .when(dynamoResponse)
        .getItems

      val response = store.getRecordSetsByName(rsOk.zoneId, rsOk.name).unsafeRunSync()
      verify(dynamoDBHelper).query(any[QueryRequest])

      response shouldBe empty
    }
    "call dynamoClient.query when retrieving an existing record set" in {
      val dynamoResponse = mock[QueryResult]
      val store = new TestDynamoRecordSetRepo
      val resultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      resultList.add(store.toItem(rsOk))

      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])
      doReturn(null).when(dynamoResponse).getLastEvaluatedKey
      doReturn(resultList).when(dynamoResponse).getItems

      val response = store.getRecordSetsByName(rsOk.zoneId, rsOk.name).unsafeRunSync()
      verify(dynamoDBHelper).query(any[QueryRequest])

      response shouldBe List(rsOk)
    }
    "throw exception when query returns an unexpected response" in {
      when(dynamoDBHelper.query(any[QueryRequest]))
        .thenThrow(new ResourceNotFoundException("failed"))
      val store = new TestDynamoRecordSetRepo

      a[ResourceNotFoundException] should be thrownBy store
        .getRecordSetsByName(rsOk.zoneId, rsOk.name)
    }
  }

  "DynamoDBRecordSetRepository.getRecordSets(zoneId, name, type)" should {
    "returns empty if no record set exist" in {
      val dynamoResponse = mock[QueryResult]
      val store = new TestDynamoRecordSetRepo

      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])
      doReturn(null).when(dynamoResponse).getLastEvaluatedKey
      doReturn(new java.util.ArrayList[java.util.Map[String, AttributeValue]]())
        .when(dynamoResponse)
        .getItems

      val response = store.getRecordSets(rsOk.zoneId, rsOk.name, rsOk.typ).unsafeRunSync()
      verify(dynamoDBHelper).query(any[QueryRequest])

      response shouldBe empty
    }
    "call dynamoClient.query when retrieving an existing record set" in {
      val dynamoResponse = mock[QueryResult]
      val store = new TestDynamoRecordSetRepo
      val resultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      resultList.add(store.toItem(rsOk))

      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])
      doReturn(null).when(dynamoResponse).getLastEvaluatedKey
      doReturn(resultList).when(dynamoResponse).getItems

      val response = store.getRecordSets(rsOk.zoneId, rsOk.name, rsOk.typ).unsafeRunSync()
      verify(dynamoDBHelper).query(any[QueryRequest])

      response shouldBe List(rsOk)
    }
    "throw exception when query returns an unexpected response" in {
      when(dynamoDBHelper.query(any[QueryRequest])).thenThrow(new ResourceNotFoundException("fail"))
      val store = new DynamoDBRecordSetRepository(recordChangeConfig.tableName, dynamoDBHelper)

      a[ResourceNotFoundException] should be thrownBy store.getRecordSets(
        rsOk.zoneId,
        rsOk.name,
        rsOk.typ
      )
    }
  }

  "DynamoDBRecordSetRepository.getRecordSetCount(zoneId)" should {
    "returns 0 when there is no matching record set" in {
      val dynamoResponse = mock[QueryResult]
      val expectedCount = 0

      doReturn(expectedCount).when(dynamoResponse).getCount
      doReturn(null).when(dynamoResponse).getLastEvaluatedKey
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val store = new TestDynamoRecordSetRepo
      val response = store.getRecordSetCount(rsOk.zoneId).unsafeRunSync()

      verify(dynamoDBHelper).query(any[QueryRequest])
      response shouldBe 0
    }
    "returns the count value when available" in {
      val dynamoResponse = mock[QueryResult]
      val expectedCount = 10
      doReturn(expectedCount).when(dynamoResponse).getCount
      doReturn(null).when(dynamoResponse).getLastEvaluatedKey
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val store = new TestDynamoRecordSetRepo
      val response = store.getRecordSetCount(rsOk.zoneId).unsafeRunSync()

      verify(dynamoDBHelper).query(any[QueryRequest])
      response shouldBe 10
    }
    "returns the aggregated count value if query is multiple pages" in {
      val dynamoResponse1 = mock[QueryResult]
      val dynamoResponse2 = mock[QueryResult]
      val key = new util.HashMap[String, AttributeValue]
      key.put("test", new AttributeValue("test"))

      doReturn(25).when(dynamoResponse1).getCount
      doReturn(25).when(dynamoResponse2).getCount
      doReturn(key).when(dynamoResponse1).getLastEvaluatedKey
      doReturn(null).when(dynamoResponse2).getLastEvaluatedKey

      doReturn(IO.pure(dynamoResponse1))
        .doReturn(IO.pure(dynamoResponse2))
        .when(dynamoDBHelper)
        .query(any[QueryRequest])

      val store = new TestDynamoRecordSetRepo
      val response = store.getRecordSetCount(rsOk.zoneId).unsafeRunSync()

      verify(dynamoDBHelper, times(2)).query(any[QueryRequest])
      response shouldBe 50
    }
    "throw exception when query returns an unexpected response" in {
      when(dynamoDBHelper.query(any[QueryRequest])).thenThrow(new ResourceNotFoundException("fail"))
      val store = new TestDynamoRecordSetRepo
      a[ResourceNotFoundException] should be thrownBy store.getRecordSetCount(rsOk.zoneId)
    }
  }

  "DynamoDBRecordSetRepository.fromItem" should {
    "be able to decode the output of toItem" in {
      val store = new TestDynamoRecordSetRepo
      val blob = store.toItem(rsOk)
      val result = store.fromItem(blob)

      result shouldBe rsOk
    }
    "throw an error when given bad input" in {
      val store = new TestDynamoRecordSetRepo
      val blob = new java.util.HashMap[String, AttributeValue]()
      intercept[UnexpectedDynamoResponseException] {
        store.fromItem(blob)
      }
    }
  }

  "DynamoDBRecordSetRepository.toItem" should {
    "be able to encode an item" in {
      val store = new TestDynamoRecordSetRepo
      val result = store.toItem(rsOk)

      store.fromItem(result) shouldBe rsOk
    }
  }

  "DynamoDBRecordSetRepository.getRecordSetsByFQDNs" should {
    "return an error if used" in {
      val store = new TestDynamoRecordSetRepo
      an[UnsupportedDynamoDBRepoFunction] should be thrownBy store
        .getRecordSetsByFQDNs(Set("test"))
        .unsafeRunSync()
    }
  }

  "DynamoDBRecordSetRepository.getRecordSetIdOwnerGroup" should {
    "return an error if used" in {
      val store = new TestDynamoRecordSetRepo
      an[UnsupportedDynamoDBRepoFunction] should be thrownBy store
        .getFirstOwnedRecordByGroup("someId")
        .unsafeRunSync()
    }
  }

  "DynamoDBRecordSetRepository.deleteRecordSetsInZone" should {
    "return an error if used" in {
      val store = new TestDynamoRecordSetRepo
      an[UnsupportedDynamoDBRepoFunction] should be thrownBy store
        .deleteRecordSetsInZone("zoneId", "zoneName")
        .unsafeRunSync()
    }
  }
}
