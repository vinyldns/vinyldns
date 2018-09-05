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
import vinyldns.core.domain.zone.{ListZoneChangesResults, ZoneChange}
import vinyldns.api.{ResultHelpers, VinylDNSConfig, VinylDNSTestData}

import cats.effect._

class DynamoDBZoneChangeRepositorySpec
    extends WordSpec
    with MockitoSugar
    with Matchers
    with VinylDNSTestData
    with ResultHelpers
    with ScalaFutures
    with BeforeAndAfterEach {

  private val dynamoDBHelper = mock[TestDynamoDBHelper]
  private val zoneChangeStoreConfig = VinylDNSConfig.zoneChangeStoreConfig
  private val zoneChangeTable = zoneChangeStoreConfig.getString("dynamo.tableName")

  class TestDynamoDBZoneChangeRepository
      extends DynamoDBZoneChangeRepository(zoneChangeStoreConfig, dynamoDBHelper)

  private val underTest = new TestDynamoDBZoneChangeRepository

  override def beforeEach(): Unit = reset(dynamoDBHelper)

  "DynamoDBZoneChangeRepository.apply" should {
    "call setuptable when it is built" in {
      val setupTableCaptor = ArgumentCaptor.forClass(classOf[CreateTableRequest])

      val store = new TestDynamoDBZoneChangeRepository
      verify(dynamoDBHelper).setupTable(setupTableCaptor.capture())

      val createTable = setupTableCaptor.getValue
      createTable.getTableName shouldBe zoneChangeTable

      (createTable.getAttributeDefinitions should contain).only(store.tableAttributes: _*)
      createTable.getKeySchema.get(0).getAttributeName shouldBe store.ZONE_ID
      createTable.getKeySchema.get(0).getKeyType shouldBe KeyType.HASH.toString
      createTable.getKeySchema.get(1).getAttributeName shouldBe store.CHANGE_ID
      createTable.getKeySchema.get(1).getKeyType shouldBe KeyType.RANGE.toString
      createTable.getGlobalSecondaryIndexes.toArray() shouldBe store.secondaryIndexes.toArray
      createTable.getProvisionedThroughput.getReadCapacityUnits shouldBe 30L
      createTable.getProvisionedThroughput.getWriteCapacityUnits shouldBe 30L
    }

    "fail when an exception is thrown setting up the table" in {
      doThrow(new RuntimeException("fail")).when(dynamoDBHelper).setupTable(any[CreateTableRequest])
      a[RuntimeException] should be thrownBy new TestDynamoDBZoneChangeRepository
    }
  }

  "DynamoDBZoneChangeRepository.save" should {
    "call DynamoDBClient.putItem when creating a zone change" in {
      val putItemResult = mock[PutItemResult]

      when(dynamoDBHelper.putItem(any[PutItemRequest])).thenReturn(IO.pure(putItemResult))
      val actual = await[ZoneChange](underTest.save(zoneChangeComplete))

      verify(dynamoDBHelper).putItem(any[PutItemRequest])
      actual shouldBe zoneChangeComplete
    }

    "throw an exception when anything goes wrong" in {
      when(dynamoDBHelper.putItem(any[PutItemRequest]))
        .thenThrow(new InternalServerErrorException("foobar"))

      a[RuntimeException] should be thrownBy underTest.save(zoneChangeComplete)
    }
  }

  "DynamoDBZoneChangeRepository.getChanges" should {
    "call dynamo client when no changes exist" in {
      val dynamoResponse = mock[QueryResult]
      val dynamoResponses = List(dynamoResponse)

      when(dynamoResponse.getItems)
        .thenReturn(new java.util.ArrayList[java.util.Map[String, AttributeValue]]())
      doReturn(IO.pure(dynamoResponses)).when(dynamoDBHelper).queryAll(any[QueryRequest])

      val response = await[ListZoneChangesResults](underTest.listZoneChanges(okZone.id))

      verify(dynamoDBHelper).queryAll(any[QueryRequest])

      response.items shouldBe empty
    }
    "call dynamoDBHelper.query when retrieving an existing zone" in {
      val dynamoResponse = mock[QueryResult]
      val dynamoResponses = List(dynamoResponse)

      val resultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      resultList.add(underTest.toItem(zoneChangePending))
      resultList.add(underTest.toItem(zoneChangeSynced))

      doReturn(IO.pure(dynamoResponses)).when(dynamoDBHelper).queryAll(any[QueryRequest])
      when(dynamoResponse.getItems).thenReturn(resultList)

      val response = await[ListZoneChangesResults](underTest.listZoneChanges(okZone.id))

      verify(dynamoDBHelper).queryAll(any[QueryRequest])
      response.items should contain theSameElementsAs List(zoneChangePending, zoneChangeSynced)
    }
    "not return duplicate changes " in {
      val dynamoResponse = mock[QueryResult]
      val dynamoResponses = List(dynamoResponse)

      val resultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      resultList.add(underTest.toItem(zoneChangeComplete))
      resultList.add(underTest.toItem(zoneChangeComplete))

      doReturn(IO.pure(dynamoResponses)).when(dynamoDBHelper).queryAll(any[QueryRequest])
      when(dynamoResponse.getItems).thenReturn(resultList)

      val response = await[ListZoneChangesResults](underTest.listZoneChanges(okZone.id))

      verify(dynamoDBHelper).queryAll(any[QueryRequest])
      response.items shouldBe List(zoneChangeComplete)
    }
    "throw exception when query returns an unexpected response" in {
      when(dynamoDBHelper.queryAll(any[QueryRequest]))
        .thenThrow(new InternalServerErrorException("foo"))

      a[InternalServerErrorException] should be thrownBy underTest.listZoneChanges(okZone.id)
    }
  }

  "DynamoDBZoneChangeRepository.getAllPendingZones" should {
    "call dynamo client when no changes exist" in {
      val dynamoResponse = mock[QueryResult]
      when(dynamoResponse.getItems)
        .thenReturn(new java.util.ArrayList[java.util.Map[String, AttributeValue]]())
      when(dynamoDBHelper.queryAll(any[QueryRequest]))
        .thenReturn(IO.pure(List(dynamoResponse)))

      val response = await[List[String]](underTest.getAllPendingZoneIds())

      verify(dynamoDBHelper, times(1)).queryAll(any[QueryRequest])

      response shouldBe empty
    }

    "call dynamoDBHelper.query when retrieving an existing zone" in {
      val pendingDynamoResponse = mock[QueryResult]
      val pendingResultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      pendingResultList.add(underTest.toItem(zoneChangePending))

      when(dynamoDBHelper.queryAll(any[QueryRequest]))
        .thenReturn(IO.pure(List(pendingDynamoResponse)))
      when(pendingDynamoResponse.getItems).thenReturn(pendingResultList)
      val response = await[List[String]](underTest.getAllPendingZoneIds())

      verify(dynamoDBHelper, times(1)).queryAll(any[QueryRequest])
      response should contain(zoneChangePending.zoneId)
    }

    "not return duplicate changes" in {
      val pendingDynamoResponse = mock[QueryResult]
      val pendingResultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      pendingResultList.add(underTest.toItem(zoneChangePending))
      pendingResultList.add(underTest.toItem(zoneChangePending))

      when(dynamoDBHelper.queryAll(any[QueryRequest]))
        .thenReturn(IO.pure(List(pendingDynamoResponse)))
      when(pendingDynamoResponse.getItems).thenReturn(pendingResultList)
      val response = await[List[String]](underTest.getAllPendingZoneIds())
      response.size shouldBe 1
      response shouldBe List(zoneChangePending.zoneId)
    }

    "throw exception when query returns an unexpected response" in {
      when(dynamoDBHelper.queryAll(any[QueryRequest]))
        .thenThrow(new InternalServerErrorException("foo"))

      a[InternalServerErrorException] should be thrownBy underTest.getAllPendingZoneIds
    }
  }

  "DynamoDBZoneChangeRepository.getPending" should {
    "call dynamo client when no changes exist" in {
      val dynamoResponse = mock[QueryResult]
      when(dynamoResponse.getItems)
        .thenReturn(new java.util.ArrayList[java.util.Map[String, AttributeValue]]())
      when(dynamoDBHelper.query(any[QueryRequest])).thenReturn(IO.pure(dynamoResponse))

      val response = await[List[ZoneChange]](underTest.getPending(okZone.id))

      verify(dynamoDBHelper, times(2)).query(any[QueryRequest])

      response shouldBe empty
    }
    "call dynamoDBHelper.query when retrieving an existing zone" in {
      val pendingDynamoResponse = mock[QueryResult]
      val pendingResultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      pendingResultList.add(underTest.toItem(zoneChangePending))

      val completedDynamoResponse = mock[QueryResult]
      val completedResultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      completedResultList.add(underTest.toItem(zoneChangeComplete))

      when(dynamoDBHelper.query(any[QueryRequest]))
        .thenReturn(IO.pure(pendingDynamoResponse))
        .thenReturn(IO.pure(completedDynamoResponse))
      when(pendingDynamoResponse.getItems).thenReturn(pendingResultList)
      when(completedDynamoResponse.getItems).thenReturn(completedResultList)

      val response = await[List[ZoneChange]](underTest.getPending(okZone.id))

      verify(dynamoDBHelper, times(2)).query(any[QueryRequest])
      response shouldBe List(zoneChangePending, zoneChangeComplete)
    }
    "not return duplicate changes" in {
      val pendingDynamoResponse = mock[QueryResult]
      val pendingResultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      pendingResultList.add(underTest.toItem(zoneChangePending))
      pendingResultList.add(underTest.toItem(zoneChangePending))

      val completedDynamoResponse = mock[QueryResult]
      val completedResultList = new java.util.ArrayList[java.util.Map[String, AttributeValue]]()
      completedResultList.add(underTest.toItem(zoneChangeComplete))
      completedResultList.add(underTest.toItem(zoneChangeComplete))

      when(dynamoDBHelper.query(any[QueryRequest]))
        .thenReturn(IO.pure(pendingDynamoResponse))
        .thenReturn(IO.pure(completedDynamoResponse))
      when(pendingDynamoResponse.getItems).thenReturn(pendingResultList)
      when(completedDynamoResponse.getItems).thenReturn(completedResultList)

      val response = await[List[ZoneChange]](underTest.getPending(okZone.id))

      verify(dynamoDBHelper, times(2)).query(any[QueryRequest])
      response should contain theSameElementsAs List(zoneChangePending, zoneChangeComplete)
    }
    "throw exception when query returns an unexpected response" in {
      when(dynamoDBHelper.query(any[QueryRequest]))
        .thenThrow(new InternalServerErrorException("foo"))

      a[InternalServerErrorException] should be thrownBy underTest.getPending(okZone.id)
    }
  }
}
