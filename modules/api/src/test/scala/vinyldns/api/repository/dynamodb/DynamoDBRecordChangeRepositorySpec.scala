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

import com.amazonaws.services.dynamodbv2.model._
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import vinyldns.api.domain.record.{ChangeSet, RecordSetChange}
import vinyldns.api.domain.zone.Zone
import vinyldns.api.{ResultHelpers, VinylDNSConfig, VinylDNSTestData}

import cats.effect._
import scala.concurrent.duration.FiniteDuration

class DynamoDBRecordChangeRepositorySpec
    extends WordSpec
    with MockitoSugar
    with Matchers
    with VinylDNSTestData
    with ResultHelpers
    with ScalaFutures
    with BeforeAndAfterEach {

  private val dynamoDBHelper = mock[DynamoDBHelper]
  private val recordSetconfig = VinylDNSConfig.recordChangeStoreConfig
  private val recordChangeTable = recordSetconfig.getString("dynamo.tableName")

  class TestRepo extends DynamoDBRecordChangeRepository(recordSetconfig, dynamoDBHelper)

  override def beforeEach(): Unit = {
    reset(dynamoDBHelper)
    doNothing().when(dynamoDBHelper).setupTable(any[CreateTableRequest])
  }

  "DynamoDBRecordChangeRepository.apply" should {
    "call setup table when it is built" in {
      val setupTableCaptor = ArgumentCaptor.forClass(classOf[CreateTableRequest])

      val store = new TestRepo
      verify(dynamoDBHelper).setupTable(setupTableCaptor.capture())

      val createTable = setupTableCaptor.getValue

      createTable.getTableName shouldBe recordChangeTable
      (createTable.getAttributeDefinitions should contain).only(store.tableAttributes: _*)
      createTable.getKeySchema.get(0).getAttributeName shouldBe store.RECORD_SET_CHANGE_ID
      createTable.getKeySchema.get(0).getKeyType shouldBe KeyType.HASH.toString
      createTable.getGlobalSecondaryIndexes.toArray() shouldBe store.secondaryIndexes.toArray
      createTable.getProvisionedThroughput.getReadCapacityUnits shouldBe 30L
      createTable.getProvisionedThroughput.getWriteCapacityUnits shouldBe 30L
    }

    "fail when an exception is thrown setting up the table" in {

      doThrow(new RuntimeException("fail")).when(dynamoDBHelper).setupTable(any[CreateTableRequest])

      a[RuntimeException] should be thrownBy new TestRepo
    }
  }

  "DynamoDBRecordChangeRepository.save" should {
    "group change sets into batch writes with 25 in each" in {
      val changes = for (i <- 1 to 52) yield pendingCreateAAAA
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
      val response = await[ChangeSet](store.save(changeSet))

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
      val changes = for (i <- 0 to 52) yield pendingCreateAAAA
      val changeSet = ChangeSet(changes)

      val dynamoResponse = mock[BatchWriteItemResult]
      val unprocessed = mock[java.util.Map[String, AttributeValue]]
      doReturn(null).when(unprocessed).get(anyString())
      doReturn(unprocessed).when(dynamoResponse).getUnprocessedItems

      val store = new TestRepo

      doThrow(new RuntimeException("failed")) //fail on the first batch
        .when(dynamoDBHelper)
        .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration])

      val result = store.save(changeSet).unsafeToFuture()
      whenReady(result.failed)(_ shouldBe a[RuntimeException])
    }

    "returns a future failure if any batch fails" in {
      val changes = for (i <- 0 to 52) yield pendingCreateAAAA
      val changeSet = ChangeSet(changes)

      val dynamoResponse = mock[BatchWriteItemResult]
      val unprocessed = mock[java.util.Map[String, AttributeValue]]
      doReturn(null).when(unprocessed).get(anyString())
      doReturn(unprocessed).when(dynamoResponse).getUnprocessedItems

      val store = new TestRepo

      when(
        dynamoDBHelper
          .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration]))
        .thenReturn(IO.pure(dynamoResponse))
        .thenThrow(new RuntimeException("failed")) //fail on the second batch

      val result = store.save(changeSet).unsafeToFuture()
      whenReady(result.failed)(_ shouldBe a[RuntimeException])
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

      val response = await[Option[RecordSetChange]](
        store.getRecordSetChange(zoneActive.id, pendingCreateAAAA.id))

      verify(dynamoDBHelper).query(any[QueryRequest])

      response shouldBe Some(pendingCreateAAAA)
    }
    "throw exception when get returns an unexpected response" in {
      when(dynamoDBHelper.query(any[QueryRequest]))
        .thenThrow(new ResourceNotFoundException("bar does not exist"))
      val store = new TestRepo

      a[ResourceNotFoundException] should be thrownBy store.getRecordSetChange(
        zoneActive.id,
        pendingCreateAAAA.id)
    }
    "return None if not found" in {
      val dynamoResponse = mock[QueryResult]
      when(dynamoResponse.getItems)
        .thenReturn(new java.util.ArrayList[java.util.Map[String, AttributeValue]]())
      when(dynamoDBHelper.query(any[QueryRequest])).thenReturn(IO.pure(dynamoResponse))

      val store = new DynamoDBRecordChangeRepository(recordSetconfig, dynamoDBHelper)
      val response = await[Option[RecordSetChange]](
        store.getRecordSetChange(zoneActive.id, pendingCreateAAAA.id))

      verify(dynamoDBHelper).query(any[QueryRequest])

      response shouldBe None
    }
  }

  "DynamoDBRecordChangeRepository.getChanges(zoneId)" should {
    "returns empty is no changes exist" in {
      val dynamoResponse = mock[QueryResult]
      when(dynamoResponse.getItems)
        .thenReturn(new java.util.ArrayList[java.util.Map[String, AttributeValue]]())
      when(dynamoDBHelper.query(any[QueryRequest])).thenReturn(IO.pure(dynamoResponse))

      val store = new TestRepo
      val response = await[List[Zone]](store.getChanges(zoneActive.id))

      verify(dynamoDBHelper).query(any[QueryRequest])

      response shouldBe empty
    }
    "call dynamoDBHelper.query when retrieving an existing change set" in {
      val dynamoResponse = mock[QueryResult]
      val store = new TestRepo

      val resultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      resultList.add(store.toItem(pendingChangeSet, pendingCreateAAAA))
      resultList.add(store.toItem(pendingChangeSet, pendingCreateCNAME))

      when(dynamoDBHelper.query(any[QueryRequest])).thenReturn(IO.pure(dynamoResponse))
      when(dynamoResponse.getItems).thenReturn(resultList)

      val response = await[List[ChangeSet]](store.getChanges(zoneActive.id))

      verify(dynamoDBHelper).query(any[QueryRequest])
      response should contain(pendingChangeSet)
    }
    "throw exception when query returns an unexpected response" in {
      when(dynamoDBHelper.query(any[QueryRequest]))
        .thenThrow(new ResourceNotFoundException("failed"))
      val store = new TestRepo

      a[ResourceNotFoundException] should be thrownBy store.getChanges(zoneActive.id)
    }
  }

  "DynamoDBRecordChangeRepository.getPendingChangeSets(zoneId)" should {
    "returns empty is no changes exist" in {
      val dynamoResponse = mock[QueryResult]
      when(dynamoResponse.getItems)
        .thenReturn(new java.util.ArrayList[java.util.Map[String, AttributeValue]]())
      when(dynamoDBHelper.queryAll(any[QueryRequest]))
        .thenReturn(IO.pure(List(dynamoResponse)))

      val store = new TestRepo
      val response = await[List[Zone]](store.getPendingChangeSets(zoneActive.id))

      verify(dynamoDBHelper).queryAll(any[QueryRequest])

      response shouldBe empty
    }
    "call dynamoDBHelper.query when retrieving an existing change set" in {
      val dynamoResponse = mock[QueryResult]
      val store = new TestRepo

      val resultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      resultList.add(store.toItem(pendingChangeSet, pendingCreateAAAA))
      resultList.add(store.toItem(pendingChangeSet, pendingCreateCNAME))

      when(dynamoDBHelper.queryAll(any[QueryRequest]))
        .thenReturn(IO.pure(List(dynamoResponse)))
      when(dynamoResponse.getItems).thenReturn(resultList)

      val response = await[List[ChangeSet]](store.getPendingChangeSets(zoneActive.id))

      verify(dynamoDBHelper).queryAll(any[QueryRequest])
      response should contain(pendingChangeSet)
    }
    "throw exception when query returns an unexpected response" in {
      when(dynamoDBHelper.queryAll(any[QueryRequest]))
        .thenThrow(new ResourceNotFoundException("failed"))
      val store = new TestRepo

      a[ResourceNotFoundException] should be thrownBy store.getPendingChangeSets(zoneActive.id)
    }

    "changeset are returned in sorted order by timestamp" in {
      val dynamoResponse = mock[QueryResult]
      val store = new TestRepo

      val resultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      resultList.add(store.toItem(pendingChangeSet, pendingCreateAAAA))
      resultList.add(store.toItem(pendingChangeSet, pendingCreateCNAME))
      resultList.add(store.toItem(pendingUpdateChangeSet, pendingUpdateAAAA))

      when(dynamoDBHelper.queryAll(any[QueryRequest]))
        .thenReturn(IO.pure(List(dynamoResponse)))
      when(dynamoResponse.getItems).thenReturn(resultList)

      val response = await[List[ChangeSet]](store.getPendingChangeSets(zoneActive.id))

      verify(dynamoDBHelper).queryAll(any[QueryRequest])
      response.size shouldBe 2
      response shouldBe List(pendingChangeSet, pendingUpdateChangeSet)
    }

  }

  "DynamoDBRecordChangeRepository.getAllPendingZones()" should {
    "returns empty is no pending zones exist" in {
      val dynamoResponse = mock[QueryResult]
      when(dynamoResponse.getItems)
        .thenReturn(new java.util.ArrayList[java.util.Map[String, AttributeValue]]())
      when(dynamoDBHelper.queryAll(any[QueryRequest]))
        .thenReturn(IO.pure(List(dynamoResponse)))

      val store = new TestRepo
      val response = await[List[String]](store.getAllPendingZoneIds())

      verify(dynamoDBHelper).queryAll(any[QueryRequest])

      response shouldBe empty
    }

    "call dynamoDBHelper.queryAll when retrieving an existing pending zoneids" in {
      val dynamoResponse = mock[QueryResult]
      val store = new TestRepo

      val resultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      resultList.add(store.toItem(pendingChangeSet, pendingCreateAAAA))
      resultList.add(store.toItem(pendingChangeSet, pendingCreateCNAME))

      when(dynamoDBHelper.queryAll(any[QueryRequest]))
        .thenReturn(IO.pure(List(dynamoResponse)))
      when(dynamoResponse.getItems).thenReturn(resultList)
      val response = await[List[String]](store.getAllPendingZoneIds())
      verify(dynamoDBHelper).queryAll(any[QueryRequest])
      response should contain(pendingChangeSet.zoneId)
      response shouldBe List(pendingChangeSet.zoneId)
    }

    "return no duplicate zoneids" in {
      val dynamoResponse = mock[QueryResult]
      val store = new TestRepo

      val resultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      resultList.add(store.toItem(pendingChangeSet, pendingCreateAAAA))
      resultList.add(store.toItem(pendingChangeSet, pendingCreateCNAME))

      when(dynamoDBHelper.queryAll(any[QueryRequest]))
        .thenReturn(IO.pure(List(dynamoResponse)))
      when(dynamoResponse.getItems).thenReturn(resultList)
      val response = await[List[String]](store.getAllPendingZoneIds())
      response should contain(pendingChangeSet.zoneId)
      response shouldBe List(pendingChangeSet.zoneId)
      response.size shouldBe 1
    }

    "throw exception when query returns an unexpected response" in {
      when(dynamoDBHelper.queryAll(any[QueryRequest]))
        .thenThrow(new ResourceNotFoundException("failed"))
      val store = new TestRepo

      a[ResourceNotFoundException] should be thrownBy store.getAllPendingZoneIds()
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

  "DynamoDBRecordChangeRepository.toChangeSet" should {
    "be able to decode the output of toItem" in {
      val store = new TestRepo
      val blob = store.toItem(pendingChangeSet, pendingCreateAAAA)
      val result = store.toChangeSet(blob)

      result shouldBe pendingChangeSet.copy(changes = Seq())
    }
    "throw an error when given bad input" in {
      val store = new TestRepo
      val blob = new java.util.HashMap[String, AttributeValue]()
      intercept[UnexpectedDynamoResponseException] {
        store.toChangeSet(blob)
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
