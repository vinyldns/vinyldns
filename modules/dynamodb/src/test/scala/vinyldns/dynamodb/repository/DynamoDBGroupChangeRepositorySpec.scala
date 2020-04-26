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

class DynamoDBGroupChangeRepositorySpec
    extends AnyWordSpec
    with MockitoSugar
    with Matchers
    with ScalaFutures
    with BeforeAndAfterEach {

  private val dynamoDBHelper = mock[DynamoDBHelper]
  private val groupChangeStoreConfig = DynamoTestConfig.groupChangesStoreConfig
  private val groupChangeTable = groupChangeStoreConfig.tableName
  class TestDynamoDBGroupChangeRepository
      extends DynamoDBGroupChangeRepository(groupChangeTable, dynamoDBHelper)

  private val underTest = new DynamoDBGroupChangeRepository(groupChangeTable, dynamoDBHelper)

  override def beforeEach(): Unit =
    reset(dynamoDBHelper)

  "DynamoDBGroupChangeRepository.toItem and fromItem" should {
    "work with all values set" in {
      val roundRobin = underTest.fromItem(underTest.toItem(okGroupChangeUpdate))
      roundRobin shouldBe okGroupChangeUpdate
    }

    "work with oldGroup = None" in {
      val roundRobin = underTest.fromItem(underTest.toItem(okGroupChange))
      roundRobin shouldBe okGroupChange
    }
  }

  "DynamoDBGroupChangeRepository.save" should {
    "return the group change when saved" in {
      val mockPutItemResult = mock[PutItemResult]

      doReturn(IO.pure(mockPutItemResult))
        .when(dynamoDBHelper)
        .putItem(any[PutItemRequest])

      val response = underTest.save(okGroupChange).unsafeRunSync()

      response shouldBe okGroupChange
    }
    "throw exception when save returns an unexpected response" in {
      doReturn(IO.raiseError(new ResourceNotFoundException("bar does not exist")))
        .when(dynamoDBHelper)
        .putItem(any[PutItemRequest])

      val result = underTest.save(okGroupChange)
      a[ResourceNotFoundException] shouldBe thrownBy(result.unsafeRunSync())
    }
  }

  "DynamoDBGroupChangeRepository.getGroupChange" should {
    "return the group change if the id is found" in {
      val dynamoResponse = mock[GetItemResult]

      val expected = underTest.toItem(okGroupChange)
      doReturn(expected).when(dynamoResponse).getItem
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).getItem(any[GetItemRequest])

      val response = underTest.getGroupChange(okGroupChange.id).unsafeRunSync()

      verify(dynamoDBHelper).getItem(any[GetItemRequest])

      response shouldBe Some(okGroupChange)
    }
    "throw exception when get returns an unexpected response" in {
      doReturn(IO.raiseError(new ResourceNotFoundException("bar does not exist")))
        .when(dynamoDBHelper)
        .getItem(any[GetItemRequest])

      val result = underTest.getGroupChange(okGroupChange.id)
      a[ResourceNotFoundException] shouldBe thrownBy(result.unsafeRunSync())
    }
    "return None if not found" in {
      val dynamoResponse = mock[GetItemResult]
      doReturn(null).when(dynamoResponse).getItem
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).getItem(any[GetItemRequest])

      val response = underTest.getGroupChange(okGroupChange.id).unsafeRunSync()

      verify(dynamoDBHelper).getItem(any[GetItemRequest])

      response shouldBe None
    }
  }
  "DynamoDBGroupChangeRepository.getGroupChanges" should {
    "returns all matching GroupChanges and the correct nextId" in {
      val dynamoResponse = mock[QueryResult]

      val expected = listOfDummyGroupChanges.slice(0, 100).map(underTest.toItem).asJava
      doReturn(expected).when(dynamoResponse).getItems()

      val lastEvaluatedKey = underTest.toItem(listOfDummyGroupChanges(99))
      doReturn(lastEvaluatedKey).when(dynamoResponse).getLastEvaluatedKey

      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val response = underTest.getGroupChanges(oneUserDummyGroup.id, None, 100).unsafeRunSync()

      response.changes should contain theSameElementsAs listOfDummyGroupChanges.take(100)
      response.lastEvaluatedTimeStamp shouldBe Some(
        listOfDummyGroupChanges(99).created.getMillis.toString
      )
    }
    "returns an empty list when no matching changes are found" in {
      val dynamoResponse = mock[QueryResult]

      val expected = List().asJava
      doReturn(expected).when(dynamoResponse).getItems()

      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val response = underTest.getGroupChanges(oneUserDummyGroup.id, None, 100).unsafeRunSync()

      response.changes shouldBe Seq()
      response.lastEvaluatedTimeStamp shouldBe None
    }
    "starts from the correct change" in {
      val dynamoGetResponse = mock[GetItemResult]

      doReturn(underTest.toItem(listOfDummyGroupChanges(50))).when(dynamoGetResponse).getItem
      doReturn(IO.pure(dynamoGetResponse))
        .when(dynamoDBHelper)
        .getItem(any[GetItemRequest])

      val dynamoQueryResponse = mock[QueryResult]

      val expected = listOfDummyGroupChanges.slice(51, 151).map(underTest.toItem).asJava
      doReturn(expected).when(dynamoQueryResponse).getItems()

      val lastEvaluatedKey = underTest.toItem(listOfDummyGroupChanges(150))
      doReturn(lastEvaluatedKey).when(dynamoQueryResponse).getLastEvaluatedKey

      doReturn(IO.pure(dynamoQueryResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val response = underTest
        .getGroupChanges(
          oneUserDummyGroup.id,
          Some(listOfDummyGroupChanges(50).created.getMillis.toString),
          100
        )
        .unsafeRunSync()

      response.changes should contain theSameElementsAs listOfDummyGroupChanges.slice(51, 151)
      response.lastEvaluatedTimeStamp shouldBe Some(
        listOfDummyGroupChanges(150).created.getMillis.toString
      )
    }
    "returns `maxItems` items" in {
      val dynamoResponse = mock[QueryResult]

      val expected = listOfDummyGroupChanges.slice(0, 50).map(underTest.toItem).asJava
      doReturn(expected).when(dynamoResponse).getItems()

      val lastEvaluatedKey = underTest.toItem(listOfDummyGroupChanges(49))
      doReturn(lastEvaluatedKey).when(dynamoResponse).getLastEvaluatedKey

      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val response = underTest.getGroupChanges(oneUserDummyGroup.id, None, 50).unsafeRunSync()

      response.changes should contain theSameElementsAs listOfDummyGroupChanges.take(50)
      response.lastEvaluatedTimeStamp shouldBe Some(
        listOfDummyGroupChanges(49).created.getMillis.toString
      )
    }

    "returns entire page and nextId = None if there are less than maxItems left" in {
      val dynamoGetResponse = mock[GetItemResult]

      doReturn(underTest.toItem(listOfDummyGroupChanges(99))).when(dynamoGetResponse).getItem
      doReturn(IO.pure(dynamoGetResponse))
        .when(dynamoDBHelper)
        .getItem(any[GetItemRequest])

      val dynamoQueryResponse = mock[QueryResult]

      val expected = listOfDummyGroupChanges.slice(100, 200).map(underTest.toItem).asJava
      doReturn(expected).when(dynamoQueryResponse).getItems()

      doReturn(IO.pure(dynamoQueryResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val response =
        underTest
          .getGroupChanges(oneUserDummyGroup.id, Some(listOfDummyGroupChanges(99).id), 100)
          .unsafeRunSync()

      response.changes should contain theSameElementsAs (listOfDummyGroupChanges.slice(100, 200))
      response.lastEvaluatedTimeStamp shouldBe None
    }
  }
}
