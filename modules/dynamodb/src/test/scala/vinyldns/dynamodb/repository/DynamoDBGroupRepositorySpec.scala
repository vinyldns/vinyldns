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

import com.amazonaws.services.dynamodbv2.model.{GetItemRequest, ResourceNotFoundException, _}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.TestMembershipData._

import scala.collection.JavaConverters._
import cats.effect._
import vinyldns.dynamodb.DynamoTestConfig

class DynamoDBGroupRepositorySpec
    extends AnyWordSpec
    with MockitoSugar
    with Matchers
    with ScalaFutures
    with BeforeAndAfterEach {

  private val dynamoDBHelper = mock[DynamoDBHelper]
  private val groupsStoreConfig = DynamoTestConfig.groupsStoreConfig
  private val groupsTable = groupsStoreConfig.tableName

  private val underTest = new DynamoDBGroupRepository(groupsTable, dynamoDBHelper)

  override def beforeEach(): Unit =
    reset(dynamoDBHelper)

  "DynamoDBGroupRepository.toItem" should {
    "set all values correctly" in {
      val items = underTest.toItem(okGroup)
      items.get("group_id").getS shouldBe okGroup.id
      items.get("name").getS shouldBe okGroup.name
      items.get("email").getS shouldBe okGroup.email
      items.get("created").getN shouldBe okGroup.created.getMillis.toString
      items.get("status").getS shouldBe okGroup.status.toString
      items.get("member_ids").getSS should contain theSameElementsAs okGroup.memberIds
      items.get("admin_ids").getSS should contain theSameElementsAs okGroup.adminUserIds
      items.get("desc").getS shouldBe okGroup.description.get
    }

    "set the description to null if it is not present" in {
      val emptyDesc = okGroup.copy(description = None)

      val items = underTest.toItem(emptyDesc)
      items.get("desc").getS shouldBe null
      items.get("desc").getNULL shouldBe true
    }
  }

  "DynamoDBGroupRepository.fromItem" should {
    "set all the values correctly" in {
      val items = underTest.toItem(okGroup)
      val group = underTest.fromItem(items)

      group shouldBe okGroup
    }

    "set all the values correctly if description is not present" in {
      val emptyDesc = okGroup.copy(description = None)
      val items = underTest.toItem(emptyDesc)
      val group = underTest.fromItem(items)

      group shouldBe emptyDesc
    }
  }

  "DynamoDBGroupRepository.save" should {
    "return the group when saved" in {
      val mockPutItemResult = mock[PutItemResult]

      doReturn(IO.pure(mockPutItemResult))
        .when(dynamoDBHelper)
        .putItem(any[PutItemRequest])

      val response = underTest.save(okGroup).unsafeRunSync()

      response shouldBe okGroup
    }
  }

  "DynamoDBGroupRepository.getGroupByName" should {
    "return a group if the name is found" in {
      val mockQueryResult = mock[QueryResult]

      val expected = underTest.toItem(okGroup)
      doReturn(List(expected).asJava).when(mockQueryResult).getItems
      doReturn(IO.pure(mockQueryResult)).when(dynamoDBHelper).query(any[QueryRequest])

      val response = underTest.getGroupByName(okGroup.id).unsafeRunSync()

      response shouldBe Some(okGroup)
    }

    "return None if the group is not found" in {
      val mockQueryResult = mock[QueryResult]

      doReturn(new java.util.ArrayList[java.util.Map[String, AttributeValue]]())
        .when(mockQueryResult)
        .getItems
      doReturn(IO.pure(mockQueryResult)).when(dynamoDBHelper).query(any[QueryRequest])

      val response = underTest.getGroupByName(okGroup.id).unsafeRunSync()

      response shouldBe None
    }

    "return None if the group is deleted" in {
      val mockQueryResult = mock[QueryResult]

      val expected = underTest.toItem(deletedGroup)
      doReturn(List(expected).asJava).when(mockQueryResult).getItems
      doReturn(IO.pure(mockQueryResult)).when(dynamoDBHelper).query(any[QueryRequest])

      val response = underTest.getGroupByName(deletedGroup.id).unsafeRunSync()

      response shouldBe None
    }
  }

  "DynamoDBGroupRepository.getGroup" should {
    "return the group if the id is found" in {
      val dynamoResponse = mock[GetItemResult]

      val expected = underTest.toItem(okGroup)
      doReturn(expected).when(dynamoResponse).getItem
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).getItem(any[GetItemRequest])

      val response = underTest.getGroup(okGroup.id).unsafeRunSync()

      verify(dynamoDBHelper).getItem(any[GetItemRequest])

      response shouldBe Some(okGroup)
    }
    "throw exception when get returns an unexpected response" in {
      doReturn(IO.raiseError(new ResourceNotFoundException("bar does not exist")))
        .when(dynamoDBHelper)
        .getItem(any[GetItemRequest])

      val result = underTest.getGroup(okGroup.id)
      a[ResourceNotFoundException] shouldBe thrownBy(result.unsafeRunSync())
    }
    "return None if not found" in {
      val dynamoResponse = mock[GetItemResult]
      doReturn(null).when(dynamoResponse).getItem
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).getItem(any[GetItemRequest])

      val response = underTest.getGroup(okGroup.id).unsafeRunSync()

      verify(dynamoDBHelper).getItem(any[GetItemRequest])

      response shouldBe None
    }
    "not return a group if it is deleted" in {
      val dynamoResponse = mock[GetItemResult]

      val expected = underTest.toItem(deletedGroup)
      doReturn(expected).when(dynamoResponse).getItem
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).getItem(any[GetItemRequest])

      val response = underTest.getGroup(deletedGroup.id).unsafeRunSync()

      verify(dynamoDBHelper).getItem(any[GetItemRequest])

      response shouldBe None
    }
  }

  "DynamoDBGroupRepository.getGroups" should {
    "return the groups if the id is found" in {
      val firstResponse = mock[BatchGetItemResult]
      val firstPage = Map(
        groupsTable -> listOfDummyGroups
          .slice(0, 100)
          .map(underTest.toItem)
          .asJava
      ).asJava
      doReturn(firstPage).when(firstResponse).getResponses

      val secondResponse = mock[BatchGetItemResult]
      val secondPage = Map(
        groupsTable -> listOfDummyGroups
          .slice(100, 200)
          .map(underTest.toItem)
          .asJava
      ).asJava
      doReturn(secondPage).when(secondResponse).getResponses

      doReturn(IO.pure(firstResponse))
        .doReturn(IO.pure(secondResponse))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val response = underTest.getGroups(listOfDummyGroups.map(_.id).toSet).unsafeRunSync()

      verify(dynamoDBHelper, times(2)).batchGetItem(any[BatchGetItemRequest])

      response should contain theSameElementsAs listOfDummyGroups
    }

    "not return a group if it is deleted" in {
      val dynamoResponse = mock[BatchGetItemResult]
      val expected = underTest.toItem(deletedGroup)
      val firstPage = Map(groupsTable -> List(expected).asJava).asJava

      doReturn(firstPage).when(dynamoResponse).getResponses
      doReturn(IO.pure(dynamoResponse))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val response = underTest.getGroups(Set(deletedGroup.id)).unsafeRunSync()

      response shouldBe empty
    }

    "return None if no groups found" in {
      val firstResponse = mock[BatchGetItemResult]
      val firstPage = Map(groupsTable -> List().asJava).asJava
      doReturn(firstPage).when(firstResponse).getResponses

      doReturn(IO.pure(firstResponse))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val response = underTest.getGroups(Set("notFound")).unsafeRunSync()

      verify(dynamoDBHelper).batchGetItem(any[BatchGetItemRequest])

      response should contain theSameElementsAs Set()
    }
    "return None if table is missing" in {
      val firstResponse = mock[BatchGetItemResult]
      val firstPage = Map().asJava
      doReturn(firstPage).when(firstResponse).getResponses

      doReturn(IO.pure(firstResponse))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val response = underTest.getGroups(Set("notFound")).unsafeRunSync()

      verify(dynamoDBHelper).batchGetItem(any[BatchGetItemRequest])

      response should contain theSameElementsAs Set()
    }
    "throw exception when get returns an unexpected response" in {
      doReturn(IO.raiseError(new ResourceNotFoundException("bar does not exist")))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val result = underTest.getGroups(listOfDummyGroups.map(_.id).toSet)
      a[ResourceNotFoundException] shouldBe thrownBy(result.unsafeRunSync())
    }
  }

  "DynamoDBGroupRepository.delete" should {
    "return a deleted group on delete" in {
      val mockDeleteItemRequest = mock[DeleteItemResult]

      doReturn(IO.pure(mockDeleteItemRequest))
        .when(dynamoDBHelper)
        .deleteItem(any[DeleteItemRequest])

      val response = underTest.delete(okGroup).unsafeRunSync()
      response shouldBe okGroup
    }
  }
}
