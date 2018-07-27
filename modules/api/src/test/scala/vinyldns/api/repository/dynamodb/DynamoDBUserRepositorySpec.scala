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

import com.amazonaws.services.dynamodbv2.model._
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import vinyldns.api.domain.membership.{ListUsersResults, User}
import vinyldns.api.{GroupTestData, ResultHelpers, VinylDNSConfig}

import scala.collection.JavaConverters._
import scala.concurrent.Future

class DynamoDBUserRepositorySpec
    extends WordSpec
    with MockitoSugar
    with Matchers
    with GroupTestData
    with ResultHelpers
    with ScalaFutures
    with BeforeAndAfterEach {

  private implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))
  private val dynamoDBHelper = mock[DynamoDBHelper]
  private val mockPutItemResult = mock[PutItemResult] // User repo is initialized with dummy users
  doReturn(Future.successful(mockPutItemResult)).when(dynamoDBHelper).putItem(any[PutItemRequest])
  private val usersStoreConfig = VinylDNSConfig.usersStoreConfig
  private val userTable = VinylDNSConfig.usersStoreConfig.getString("dynamo.tableName")

  class TestDynamoDBUserRepository extends DynamoDBUserRepository(usersStoreConfig, dynamoDBHelper)

  private val underTest = new DynamoDBUserRepository(usersStoreConfig, dynamoDBHelper)

  override def beforeEach(): Unit = {
    reset(dynamoDBHelper)
    doNothing().when(dynamoDBHelper).setupTable(any[CreateTableRequest])
  }

  "DynamoDBUserRepository constructor" should {
    "call setuptable when it is built" in {
      val mockPutItemResult = mock[PutItemResult] // User repo is initialized with dummy users
      doReturn(Future.successful(mockPutItemResult))
        .when(dynamoDBHelper)
        .putItem(any[PutItemRequest])

      val setupTableCaptor = ArgumentCaptor.forClass(classOf[CreateTableRequest])

      new TestDynamoDBUserRepository
      verify(dynamoDBHelper).setupTable(setupTableCaptor.capture())

      val createTable = setupTableCaptor.getValue

      createTable.getTableName shouldBe userTable
      (createTable.getAttributeDefinitions should contain).only(underTest.tableAttributes: _*)
      createTable.getKeySchema.get(0).getAttributeName shouldBe underTest.USER_ID
      createTable.getKeySchema.get(0).getKeyType shouldBe KeyType.HASH.toString
      createTable.getGlobalSecondaryIndexes.toArray() shouldBe underTest.secondaryIndexes.toArray
      createTable.getProvisionedThroughput.getReadCapacityUnits shouldBe 30L
      createTable.getProvisionedThroughput.getWriteCapacityUnits shouldBe 30L
    }

    "fail when an exception is thrown setting up the table" in {

      doThrow(new RuntimeException("fail")).when(dynamoDBHelper).setupTable(any[CreateTableRequest])

      a[RuntimeException] should be thrownBy new TestDynamoDBUserRepository
    }
  }

  "DynamoDBUserRepository.toItem" should {
    "set all values correctly" in {
      val items = underTest.toItem(okUser)
      items.get(underTest.USER_ID).getS shouldBe okUser.id
      items.get(underTest.USER_NAME).getS shouldBe okUser.userName
      items.get(underTest.ACCESS_KEY).getS shouldBe okUser.accessKey
      items.get(underTest.SECRET_KEY).getS shouldBe okUser.secretKey
      items.get(underTest.FIRST_NAME).getS shouldBe okUser.firstName.get
      items.get(underTest.LAST_NAME).getS shouldBe okUser.lastName.get
      items.get(underTest.EMAIL).getS shouldBe okUser.email.get
      items.get(underTest.CREATED).getN shouldBe okUser.created.getMillis.toString
    }
    "set the first name to null if it is not present" in {
      val emptyFirstName = okUser.copy(firstName = None)

      val items = underTest.toItem(emptyFirstName)
      Option(items.get(underTest.FIRST_NAME).getS) shouldBe None
      items.get(underTest.FIRST_NAME).getNULL shouldBe true
    }
    "set the last name to null if it is not present" in {
      val emptyLastName = okUser.copy(lastName = None)

      val items = underTest.toItem(emptyLastName)
      Option(items.get(underTest.LAST_NAME).getS) shouldBe None
      items.get(underTest.LAST_NAME).getNULL shouldBe true
    }
    "set the email to null if it is not present" in {
      val emptyEmail = okUser.copy(email = None)

      val items = underTest.toItem(emptyEmail)
      Option(items.get(underTest.EMAIL).getS) shouldBe None
      items.get(underTest.EMAIL).getNULL shouldBe true
    }
  }

  "DynamoDBUserRepository.fromItem" should {
    "set all the values correctly" in {
      val items = underTest.toItem(okUser)
      val user = underTest.fromItem(items)

      user shouldBe okUser
    }
    "set all the values correctly if first name is not present" in {
      val emptyFirstName = okUser.copy(firstName = None)
      val items = underTest.toItem(emptyFirstName)
      val user = underTest.fromItem(items)

      user shouldBe emptyFirstName
    }
    "set all the values correctly if last name is not present" in {
      val emptyLastName = okUser.copy(lastName = None)
      val items = underTest.toItem(emptyLastName)
      val user = underTest.fromItem(items)

      user shouldBe emptyLastName
    }
    "set all the values correctly if email is not present" in {
      val emptyEmail = okUser.copy(email = None)
      val items = underTest.toItem(emptyEmail)
      val user = underTest.fromItem(items)

      user shouldBe emptyEmail
    }
    "sets empty values correctly if key is not present in item" in {
      val item = new java.util.HashMap[String, AttributeValue]()
      item.put(underTest.USER_ID, new AttributeValue("ok"))
      item.put(underTest.USER_NAME, new AttributeValue("ok"))
      item.put(underTest.CREATED, new AttributeValue().withN("0"))
      item.put(underTest.ACCESS_KEY, new AttributeValue("accessKey"))
      item.put(underTest.SECRET_KEY, new AttributeValue("secretkey"))
      val user = underTest.fromItem(item)

      user.firstName shouldBe None
      user.lastName shouldBe None
      user.email shouldBe None
    }
    "sets the isSuper flag correctly" in {
      val superUser = okUser.copy(isSuper = true)
      val items = underTest.toItem(superUser)
      val user = underTest.fromItem(items)

      user shouldBe superUser
    }
    "sets the isSuper flag correctly if the key is not present in the item" in {
      val item = new java.util.HashMap[String, AttributeValue]()
      item.put(underTest.USER_ID, new AttributeValue("ok"))
      item.put(underTest.USER_NAME, new AttributeValue("ok"))
      item.put(underTest.CREATED, new AttributeValue().withN("0"))
      item.put(underTest.ACCESS_KEY, new AttributeValue("accesskey"))
      item.put(underTest.SECRET_KEY, new AttributeValue("secretkey"))
      val user = underTest.fromItem(item)

      user.isSuper shouldBe false
    }
  }

  "DynamoDBUserRepository.getUser" should {
    "return the user if the id is found" in {
      val dynamoResponse = mock[GetItemResult]

      val expected = underTest.toItem(okUser)
      doReturn(expected).when(dynamoResponse).getItem
      doReturn(Future.successful(dynamoResponse)).when(dynamoDBHelper).getItem(any[GetItemRequest])

      val response = await[Option[User]](underTest.getUser(okUser.id))

      verify(dynamoDBHelper).getItem(any[GetItemRequest])

      response shouldBe Some(okUser)
    }
    "throw exception when get returns an unexpected response" in {
      doReturn(Future.failed(new ResourceNotFoundException("bar does not exist")))
        .when(dynamoDBHelper)
        .getItem(any[GetItemRequest])

      val result = underTest.getUser(okUser.id)
      whenReady(result.failed) { failed =>
        failed shouldBe a[ResourceNotFoundException]
      }
    }
    "return None if not found" in {
      val dynamoResponse = mock[GetItemResult]
      doReturn(null).when(dynamoResponse).getItem
      doReturn(Future.successful(dynamoResponse)).when(dynamoDBHelper).getItem(any[GetItemRequest])

      val response = await[Option[User]](underTest.getUser(okUser.id))

      verify(dynamoDBHelper).getItem(any[GetItemRequest])

      response shouldBe None
    }
  }

  "DynamoDBUserRepository.getUsers" should {
    "return the users if the id is found" in {
      val firstResponse = mock[BatchGetItemResult]
      val firstPage = Map(
        underTest.USER_TABLE -> listOfDummyUsers.slice(0, 100).map(underTest.toItem).asJava).asJava
      doReturn(firstPage).when(firstResponse).getResponses

      val secondResponse = mock[BatchGetItemResult]
      val secondPage = Map(
        underTest.USER_TABLE -> listOfDummyUsers
          .slice(100, 200)
          .map(underTest.toItem)
          .asJava).asJava
      doReturn(secondPage).when(secondResponse).getResponses

      doReturn(Future.successful(firstResponse))
        .doReturn(Future.successful(secondResponse))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val response =
        await[ListUsersResults](underTest.getUsers(listOfDummyUsers.map(_.id).toSet, None, None))

      verify(dynamoDBHelper, times(2)).batchGetItem(any[BatchGetItemRequest])

      response.users should contain theSameElementsAs listOfDummyUsers
      response.lastEvaluatedId shouldBe None
    }
    "return None if no users found" in {
      val firstResponse = mock[BatchGetItemResult]
      val firstPage = Map(underTest.USER_TABLE -> List().asJava).asJava
      doReturn(firstPage).when(firstResponse).getResponses

      doReturn(Future.successful(firstResponse))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val response = await[ListUsersResults](underTest.getUsers(Set("notFound"), None, None))

      verify(dynamoDBHelper).batchGetItem(any[BatchGetItemRequest])

      response.users should contain theSameElementsAs Set()
      response.lastEvaluatedId shouldBe None
    }
    "return None if table is missing" in {
      val firstResponse = mock[BatchGetItemResult]
      val firstPage = Map().asJava
      doReturn(firstPage).when(firstResponse).getResponses

      doReturn(Future.successful(firstResponse))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val response = await[ListUsersResults](underTest.getUsers(Set("notFound"), None, None))

      verify(dynamoDBHelper).batchGetItem(any[BatchGetItemRequest])

      response.users should contain theSameElementsAs Set()
      response.lastEvaluatedId shouldBe None
    }
    "returns is starting at the exclusiveStartKey" in {
      def toBatchGetItemRequest(userIds: List[String]): BatchGetItemRequest = {
        val allKeys = new util.ArrayList[util.Map[String, AttributeValue]]()

        for { userId <- userIds } {
          val key = new util.HashMap[String, AttributeValue]()
          key.put(underTest.USER_ID, new AttributeValue(userId))
          allKeys.add(key)
        }

        val keysAndAttributes = new KeysAndAttributes().withKeys(allKeys)

        val request = new util.HashMap[String, KeysAndAttributes]()
        request.put(underTest.USER_TABLE, keysAndAttributes)

        new BatchGetItemRequest().withRequestItems(request)
      }

      val firstResponse = mock[BatchGetItemResult]
      val firstPage = Map(
        underTest.USER_TABLE -> listOfDummyUsers
          .slice(151, 200)
          .map(underTest.toItem)
          .asJava).asJava
      doReturn(firstPage).when(firstResponse).getResponses

      doReturn(Future.successful(firstResponse))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val batchGetCaptor = ArgumentCaptor.forClass(classOf[BatchGetItemRequest])

      val response = await[ListUsersResults](
        underTest.getUsers(listOfDummyUsers.map(_.id).toSet, Some("dummy150"), None))

      response.users should contain theSameElementsAs listOfDummyUsers.slice(151, 200)
      response.lastEvaluatedId shouldBe None

      verify(dynamoDBHelper).batchGetItem(batchGetCaptor.capture())

      val batchGet = batchGetCaptor.getValue

      val expected = toBatchGetItemRequest(listOfDummyUsers.slice(151, 200).map(_.id))
      batchGet shouldBe expected
    }
    "truncates the response to only return limit items" in {
      val firstResponse = mock[BatchGetItemResult]
      val firstPage = Map(
        underTest.USER_TABLE -> listOfDummyUsers.slice(0, 50).map(underTest.toItem).asJava).asJava
      doReturn(firstPage).when(firstResponse).getResponses

      doReturn(Future.successful(firstResponse))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val response = await[ListUsersResults](
        underTest.getUsers(listOfDummyUsers.map(_.id).toSet, None, Some(50)))

      verify(dynamoDBHelper).batchGetItem(any[BatchGetItemRequest])

      response.users should contain theSameElementsAs listOfDummyUsers.take(50)
      response.lastEvaluatedId shouldBe Some(listOfDummyUsers(49).id)
    }
    "throw exception when get returns an unexpected response" in {
      doReturn(Future.failed(new ResourceNotFoundException("bar does not exist")))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val result = underTest.getUsers(listOfDummyUsers.map(_.id).toSet, None, None)
      whenReady(result.failed) { failed =>
        failed shouldBe a[ResourceNotFoundException]
      }
    }
  }
  "DynamoDBUserRepository.getUserByAccessKey" should {
    "return the user if the access key is found" in {
      val dynamoResponse = mock[QueryResult]

      val expected = List(underTest.toItem(okUser)).asJava
      doReturn(expected).when(dynamoResponse).getItems
      doReturn(Future.successful(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val response = await[Option[User]](underTest.getUserByAccessKey(okUser.accessKey))

      verify(dynamoDBHelper).query(any[QueryRequest])

      response shouldBe Some(okUser)
    }
    "throw exception when get returns an unexpected response" in {
      doReturn(Future.failed(new ResourceNotFoundException("bar does not exist")))
        .when(dynamoDBHelper)
        .query(any[QueryRequest])

      val result = underTest.getUserByAccessKey(okUser.accessKey)
      whenReady(result.failed) { failed =>
        failed shouldBe a[ResourceNotFoundException]
      }
    }
    "return None if not found" in {
      val dynamoResponse = mock[QueryResult]
      doReturn(List().asJava).when(dynamoResponse).getItems
      doReturn(Future.successful(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val response = await[Option[User]](underTest.getUserByAccessKey(okUser.accessKey))

      verify(dynamoDBHelper).query(any[QueryRequest])

      response shouldBe None
    }
  }
  "DynamoDBUserRepository.save" should {
    "return the user when saved" in {
      val mockPutItemResult = mock[PutItemResult]

      doReturn(Future.successful(mockPutItemResult))
        .when(dynamoDBHelper)
        .putItem(any[PutItemRequest])

      val response = await[User](underTest.save(okUser))

      response shouldBe okUser
    }
  }
}
