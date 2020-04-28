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
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.TestMembershipData._

import scala.collection.JavaConverters._
import cats.effect._
import com.typesafe.config.ConfigFactory
import vinyldns.core.crypto.{CryptoAlgebra, NoOpCrypto}
import vinyldns.core.domain.membership.LockStatus
import vinyldns.dynamodb.DynamoTestConfig

class DynamoDBUserRepositorySpec
    extends AnyWordSpec
    with MockitoSugar
    with Matchers
    with ScalaFutures
    with BeforeAndAfterEach {

  private val dynamoDBHelper = mock[DynamoDBHelper]
  private val mockPutItemResult = mock[PutItemResult] // User repo is initialized with dummy users
  doReturn(IO.pure(mockPutItemResult)).when(dynamoDBHelper).putItem(any[PutItemRequest])
  private val usersStoreConfig = DynamoTestConfig.usersStoreConfig
  private val userTable = usersStoreConfig.tableName
  private val crypto = new NoOpCrypto(ConfigFactory.load())
  private val underTest = new DynamoDBUserRepository(
    userTable,
    dynamoDBHelper,
    DynamoDBUserRepository.toItem(crypto, _),
    DynamoDBUserRepository.fromItem
  )

  override def beforeEach(): Unit =
    reset(dynamoDBHelper)

  import DynamoDBUserRepository._

  "DynamoDBUserRepository.toItem" should {
    "set all values correctly" in {
      val crypt = new CryptoAlgebra {
        def encrypt(value: String): String = "encrypted"
        def decrypt(value: String): String = "decrypted"
      }
      val items = toItem(crypt, okUser)
      items.get(USER_ID).getS shouldBe okUser.id
      items.get(USER_NAME).getS shouldBe okUser.userName
      items.get(ACCESS_KEY).getS shouldBe okUser.accessKey
      items.get(SECRET_KEY).getS shouldBe "encrypted"
      items.get(FIRST_NAME).getS shouldBe okUser.firstName.get
      items.get(LAST_NAME).getS shouldBe okUser.lastName.get
      items.get(EMAIL).getS shouldBe okUser.email.get
      items.get(CREATED).getN shouldBe okUser.created.getMillis.toString
      items.get(LOCK_STATUS).getS shouldBe okUser.lockStatus.toString
    }
    "set the first name to null if it is not present" in {
      val emptyFirstName = okUser.copy(firstName = None)

      val items = toItem(crypto, emptyFirstName)
      Option(items.get(DynamoDBUserRepository.FIRST_NAME).getS) shouldBe None
      items.get(DynamoDBUserRepository.FIRST_NAME).getNULL shouldBe true
    }
    "set the last name to null if it is not present" in {
      val emptyLastName = okUser.copy(lastName = None)

      val items = toItem(crypto, emptyLastName)
      Option(items.get(LAST_NAME).getS) shouldBe None
      items.get(LAST_NAME).getNULL shouldBe true
    }
    "set the email to null if it is not present" in {
      val emptyEmail = okUser.copy(email = None)

      val items = toItem(crypto, emptyEmail)
      Option(items.get(EMAIL).getS) shouldBe None
      items.get(EMAIL).getNULL shouldBe true
    }
  }

  "DynamoDBUserRepository.fromItem" should {
    "set all the values correctly" in {
      val items = toItem(crypto, okUser)
      val user = fromItem(items).unsafeRunSync()

      user shouldBe okUser
    }
    "set all the values correctly if first name is not present" in {
      val emptyFirstName = okUser.copy(firstName = None)
      val items = toItem(crypto, emptyFirstName)
      val user = fromItem(items).unsafeRunSync()

      user shouldBe emptyFirstName
    }
    "set all the values correctly if last name is not present" in {
      val emptyLastName = okUser.copy(lastName = None)
      val items = toItem(crypto, emptyLastName)
      val user = fromItem(items).unsafeRunSync()

      user shouldBe emptyLastName
    }
    "set all the values correctly if email is not present" in {
      val emptyEmail = okUser.copy(email = None)
      val items = toItem(crypto, emptyEmail)
      val user = fromItem(items).unsafeRunSync()

      user shouldBe emptyEmail
    }
    "sets empty values correctly if key is not present in item" in {
      val item = new java.util.HashMap[String, AttributeValue]()
      item.put(USER_ID, new AttributeValue("ok"))
      item.put(USER_NAME, new AttributeValue("ok"))
      item.put(CREATED, new AttributeValue().withN("0"))
      item.put(ACCESS_KEY, new AttributeValue("accessKey"))
      item.put(SECRET_KEY, new AttributeValue("secretkey"))
      item.put(LOCK_STATUS, new AttributeValue("lockstatus"))
      val user = fromItem(item).unsafeRunSync()

      user.firstName shouldBe None
      user.lastName shouldBe None
      user.email shouldBe None
    }
    "sets the isSuper flag correctly" in {
      val superUser = okUser.copy(isSuper = true)
      val items = toItem(crypto, superUser)
      val user = fromItem(items).unsafeRunSync()

      user shouldBe superUser
    }
    "sets the isSuper flag correctly if the key is not present in the item" in {
      val item = new java.util.HashMap[String, AttributeValue]()
      item.put(USER_ID, new AttributeValue("ok"))
      item.put(USER_NAME, new AttributeValue("ok"))
      item.put(CREATED, new AttributeValue().withN("0"))
      item.put(ACCESS_KEY, new AttributeValue("accesskey"))
      item.put(SECRET_KEY, new AttributeValue("secretkey"))
      item.put(LOCK_STATUS, new AttributeValue("Locked"))
      val user = fromItem(item).unsafeRunSync()

      user.isSuper shouldBe false
    }

    "sets the lockStatus to Unlocked if the given value is invalid" in {
      val item = new java.util.HashMap[String, AttributeValue]()
      item.put(USER_ID, new AttributeValue("ok"))
      item.put(USER_NAME, new AttributeValue("ok"))
      item.put(CREATED, new AttributeValue().withN("0"))
      item.put(ACCESS_KEY, new AttributeValue("accesskey"))
      item.put(SECRET_KEY, new AttributeValue("secretkey"))
      item.put(LOCK_STATUS, new AttributeValue("lock_status"))
      val user = fromItem(item).unsafeRunSync()

      user.lockStatus shouldBe LockStatus.Unlocked
    }
    "sets the lockStatus to Unlocked if the given value is null" in {
      val item = new java.util.HashMap[String, AttributeValue]()
      item.put(USER_ID, new AttributeValue("ok"))
      item.put(USER_NAME, new AttributeValue("ok"))
      item.put(CREATED, new AttributeValue().withN("0"))
      item.put(ACCESS_KEY, new AttributeValue("accesskey"))
      item.put(SECRET_KEY, new AttributeValue("secretkey"))
      val user = fromItem(item).unsafeRunSync()

      user.lockStatus shouldBe LockStatus.Unlocked
    }
  }

  "DynamoDBUserRepository.getUser" should {
    "return the user if the id is found" in {
      val dynamoResponse = mock[GetItemResult]

      val expected = toItem(crypto, okUser)
      doReturn(expected).when(dynamoResponse).getItem
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).getItem(any[GetItemRequest])

      val response = underTest.getUser(okUser.id).unsafeRunSync()

      verify(dynamoDBHelper).getItem(any[GetItemRequest])

      response shouldBe Some(okUser)
    }
    "throw exception when get returns an unexpected response" in {
      doReturn(IO.raiseError(new ResourceNotFoundException("bar does not exist")))
        .when(dynamoDBHelper)
        .getItem(any[GetItemRequest])

      val result = underTest.getUser(okUser.id)
      a[ResourceNotFoundException] shouldBe thrownBy(result.unsafeRunSync())
    }
    "return None if not found" in {
      val dynamoResponse = mock[GetItemResult]
      doReturn(null).when(dynamoResponse).getItem
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).getItem(any[GetItemRequest])

      val response = underTest.getUser(okUser.id).unsafeRunSync()

      verify(dynamoDBHelper).getItem(any[GetItemRequest])

      response shouldBe None
    }
  }

  "DynamoDBUserRepository.getUsers" should {
    "return the users if the id is found" in {
      val firstResponse = mock[BatchGetItemResult]
      val firstPage =
        Map(userTable -> listOfDummyUsers.slice(0, 100).map(toItem(crypto, _)).asJava).asJava
      doReturn(firstPage).when(firstResponse).getResponses

      val secondResponse = mock[BatchGetItemResult]
      val secondPage = Map(
        userTable -> listOfDummyUsers
          .slice(100, 200)
          .map(toItem(crypto, _))
          .asJava
      ).asJava
      doReturn(secondPage).when(secondResponse).getResponses

      doReturn(IO.pure(firstResponse))
        .doReturn(IO.pure(secondResponse))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val response =
        underTest.getUsers(listOfDummyUsers.map(_.id).toSet, None, None).unsafeRunSync()

      verify(dynamoDBHelper, times(2)).batchGetItem(any[BatchGetItemRequest])

      response.users should contain theSameElementsAs listOfDummyUsers
      response.lastEvaluatedId shouldBe None
    }
    "return None if no users found" in {
      val firstResponse = mock[BatchGetItemResult]
      val firstPage = Map(userTable -> List().asJava).asJava
      doReturn(firstPage).when(firstResponse).getResponses

      doReturn(IO.pure(firstResponse))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val response = underTest.getUsers(Set("notFound"), None, None).unsafeRunSync()

      verify(dynamoDBHelper).batchGetItem(any[BatchGetItemRequest])

      response.users should contain theSameElementsAs Set()
      response.lastEvaluatedId shouldBe None
    }
    "return None if table is missing" in {
      val firstResponse = mock[BatchGetItemResult]
      val firstPage = Map().asJava
      doReturn(firstPage).when(firstResponse).getResponses

      doReturn(IO.pure(firstResponse))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val response = underTest.getUsers(Set("notFound"), None, None).unsafeRunSync()

      verify(dynamoDBHelper).batchGetItem(any[BatchGetItemRequest])

      response.users should contain theSameElementsAs Set()
      response.lastEvaluatedId shouldBe None
    }
    "returns is starting at the exclusiveStartKey" in {
      def toBatchGetItemRequest(userIds: List[String]): BatchGetItemRequest = {
        val allKeys = new util.ArrayList[util.Map[String, AttributeValue]]()

        for { userId <- userIds } {
          val key = new util.HashMap[String, AttributeValue]()
          key.put(USER_ID, new AttributeValue(userId))
          allKeys.add(key)
        }

        val keysAndAttributes = new KeysAndAttributes().withKeys(allKeys)

        val request = new util.HashMap[String, KeysAndAttributes]()
        request.put(userTable, keysAndAttributes)

        new BatchGetItemRequest().withRequestItems(request)
      }

      val firstResponse = mock[BatchGetItemResult]
      val firstPage = Map(
        userTable -> listOfDummyUsers
          .slice(151, 200)
          .map(toItem(crypto, _))
          .asJava
      ).asJava
      doReturn(firstPage).when(firstResponse).getResponses

      doReturn(IO.pure(firstResponse))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val batchGetCaptor = ArgumentCaptor.forClass(classOf[BatchGetItemRequest])

      val response =
        underTest.getUsers(listOfDummyUsers.map(_.id).toSet, Some("dummy150"), None).unsafeRunSync()

      response.users should contain theSameElementsAs listOfDummyUsers.slice(151, 200)
      response.lastEvaluatedId shouldBe None

      verify(dynamoDBHelper).batchGetItem(batchGetCaptor.capture())

      val batchGet = batchGetCaptor.getValue

      val expected = toBatchGetItemRequest(listOfDummyUsers.slice(151, 200).map(_.id))
      batchGet shouldBe expected
    }
    "truncates the response to only return limit items" in {
      val firstResponse = mock[BatchGetItemResult]
      val firstPage =
        Map(userTable -> listOfDummyUsers.slice(0, 50).map(toItem(crypto, _)).asJava).asJava
      doReturn(firstPage).when(firstResponse).getResponses

      doReturn(IO.pure(firstResponse))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      val response =
        underTest.getUsers(listOfDummyUsers.map(_.id).toSet, None, Some(50)).unsafeRunSync()

      verify(dynamoDBHelper).batchGetItem(any[BatchGetItemRequest])

      response.users should contain theSameElementsAs listOfDummyUsers.take(50)
      response.lastEvaluatedId shouldBe Some(listOfDummyUsers(49).id)
    }
    "throw exception when get returns an unexpected response" in {
      doReturn(IO.raiseError(new ResourceNotFoundException("bar does not exist")))
        .when(dynamoDBHelper)
        .batchGetItem(any[BatchGetItemRequest])

      a[ResourceNotFoundException] shouldBe thrownBy(
        underTest.getUsers(listOfDummyUsers.map(_.id).toSet, None, None).unsafeRunSync()
      )
    }
  }
  "DynamoDBUserRepository.getAllUsers" should {
    "throw an UnsupportedDynamoDBRepoFunction error" in {
      assertThrows[UnsupportedDynamoDBRepoFunction](underTest.getAllUsers.unsafeRunSync())
    }
  }
  "DynamoDBUserRepository.getUserByAccessKey" should {
    "return the user if the access key is found" in {
      val dynamoResponse = mock[QueryResult]

      val expected = List(toItem(crypto, okUser)).asJava
      doReturn(expected).when(dynamoResponse).getItems
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val response = underTest.getUserByAccessKey(okUser.accessKey).unsafeRunSync()

      verify(dynamoDBHelper).query(any[QueryRequest])

      response shouldBe Some(okUser)
    }
    "throw exception when get returns an unexpected response" in {
      doReturn(IO.raiseError(new ResourceNotFoundException("bar does not exist")))
        .when(dynamoDBHelper)
        .query(any[QueryRequest])

      val result = underTest.getUserByAccessKey(okUser.accessKey)
      a[ResourceNotFoundException] shouldBe thrownBy(result.unsafeRunSync())
    }
    "return None if not found" in {
      val dynamoResponse = mock[QueryResult]
      doReturn(List().asJava).when(dynamoResponse).getItems
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val response = underTest.getUserByAccessKey(okUser.accessKey).unsafeRunSync()

      verify(dynamoDBHelper).query(any[QueryRequest])

      response shouldBe None
    }
  }
  "DynamoDBUserRepository.save" should {
    "return the user when saved" in {
      val mockPutItemResult = mock[PutItemResult]

      doReturn(IO.pure(mockPutItemResult))
        .when(dynamoDBHelper)
        .putItem(any[PutItemRequest])

      val response = underTest.save(okUser).unsafeRunSync()

      response shouldBe okUser
    }
    "throw an UnsupportedDynamoDBRepoFunction error when batch save is invoked" in {
      assertThrows[UnsupportedDynamoDBRepoFunction](underTest.save(List(okUser)).unsafeRunSync())
    }
  }
}
