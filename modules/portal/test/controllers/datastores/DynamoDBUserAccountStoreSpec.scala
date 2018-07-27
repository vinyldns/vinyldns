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

package controllers.datastores

import java.util

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model._
import models.UserAccount
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.{Configuration, Environment}
import vinyldns.core.crypto.CryptoAlgebra

class DynamoDBUserAccountStoreSpec extends Specification with Mockito {

  val testCrypto = new CryptoAlgebra {
    def encrypt(value: String): String = "encrypted!"
    def decrypt(value: String): String = "decrypted!"
  }

  "DynamoDBUserAccountStore" should {
    "Store a new user when email, first name, and last name are None" in {
      val (client, config) = buildMocks()
      val user = UserAccount("fbaggins", None, None, None)
      val item = DynamoDBUserAccountStore.toItem(user, testCrypto)
      val mockResult = mock[PutItemResult]
      mockResult.getAttributes.returns(item)
      client.putItem(any[PutItemRequest]).returns(mockResult)

      val underTest = new DynamoDBUserAccountStore(client, config, testCrypto)

      val result = underTest.storeUser(user)
      result must beASuccessfulTry
      compareUserAccounts(result.get, user)
    }

    "Store a new user when everything is ok" in {
      val (client, config) = buildMocks()
      val user =
        UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fbaggins@hobbitmail.me"))
      val item = DynamoDBUserAccountStore.toItem(user, testCrypto)
      val mockResult = mock[PutItemResult]
      mockResult.getAttributes.returns(item)
      client.putItem(any[PutItemRequest]).returns(mockResult)

      val underTest = new DynamoDBUserAccountStore(client, config, testCrypto)

      val result = underTest.storeUser(user)
      result must beASuccessfulTry
      compareUserAccounts(result.get, user)
    }

    "Store a user over an existing user returning the new user" in {
      val (client, config) = buildMocks()
      val oldUser = UserAccount("old", Some("Old"), Some("User"), Some("oldman@mail.me"))
      val newUser = oldUser.copy(username = "new")
      val mockResult = mock[PutItemResult]
      mockResult.getAttributes.returns(DynamoDBUserAccountStore.toItem(newUser, testCrypto))
      client.putItem(any[PutItemRequest]).returns(mockResult)

      val underTest = new DynamoDBUserAccountStore(client, config, testCrypto)

      underTest.storeUser(oldUser)
      val result = underTest.storeUser(newUser)
      result must beASuccessfulTry
      compareUserAccounts(result.get, newUser)
    }

    "Retrieve a given user based on user-id" in {
      val user = UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fb@hobbitmail.me"))
      val (client, config) = buildMocks()
      val getResult = mock[GetItemResult]
      val resultItem = DynamoDBUserAccountStore.toItem(user, testCrypto)
      getResult.getItem.returns(resultItem)
      client.getItem(any[GetItemRequest]).returns(getResult)

      val underTest = new DynamoDBUserAccountStore(client, config, testCrypto)

      val result = underTest.getUserById(user.userId)
      result must beASuccessfulTry
      result.get must beSome
      compareUserAccounts(result.get.get, user)
    }

    "Retrieve a given user based on username" in {
      val user = UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fb@hobbitmail.me"))
      val (client, config) = buildMocks()
      val queryResult = mock[QueryResult]
      val resultList = new util.ArrayList[util.Map[String, AttributeValue]]()
      resultList.add(DynamoDBUserAccountStore.toItem(user, testCrypto))
      queryResult.getItems.returns(resultList)
      queryResult.getCount.returns(1)
      client.query(any[QueryRequest]).returns(queryResult)

      val underTest = new DynamoDBUserAccountStore(client, config, testCrypto)

      val result = underTest.getUserByName(user.username)
      result must beASuccessfulTry
      result.get must beSome
      compareUserAccounts(result.get.get, user)
    }

    "Return a successful none if the user is not found by id (empty item)" in {
      val user = UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fb@hobbitmail.me"))
      val (client, config) = buildMocks()
      val getResult = mock[GetItemResult]
      val resultItem = new util.HashMap[String, AttributeValue]()
      getResult.getItem.returns(resultItem)
      client.getItem(any[GetItemRequest]).returns(getResult)

      val underTest = new DynamoDBUserAccountStore(client, config, testCrypto)

      val result = underTest.getUserById(user.userId)
      result must beASuccessfulTry[Option[UserAccount]](None)
    }

    "Return a successful none if the user is not found by id (null)" in {
      val user = UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fb@hobbitmail.me"))
      val (client, config) = buildMocks()
      val getResult = null
      client.getItem(any[GetItemRequest]).returns(getResult)

      val underTest = new DynamoDBUserAccountStore(client, config, testCrypto)

      val result = underTest.getUserById(user.userId)
      result must beASuccessfulTry[Option[UserAccount]](None)
    }

    "Return a successful none if the user is not found by name" in {
      val user = UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fb@hobbitmail.me"))
      val (client, config) = buildMocks()
      val queryResult = mock[QueryResult]
      val resultList = new util.ArrayList[util.Map[String, AttributeValue]]()
      queryResult.getItems.returns(resultList)
      queryResult.getCount.returns(0)
      client.query(any[QueryRequest]).returns(queryResult)

      val underTest = new DynamoDBUserAccountStore(client, config, testCrypto)

      val result = underTest.getUserByName(user.username)
      result must beASuccessfulTry(None)
    }

    "Return a user based on username when more than one is found" in {
      val user = UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fb@hobbitmail.me"))
      val secondUser =
        UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fb@hobbitmail.me"))
      val thirdUser =
        UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fb@hobbitmail.me"))
      val (client, config) = buildMocks()
      val queryResult = mock[QueryResult]
      val resultList = new util.ArrayList[util.Map[String, AttributeValue]]()
      resultList.add(DynamoDBUserAccountStore.toItem(user, testCrypto))
      resultList.add(DynamoDBUserAccountStore.toItem(secondUser, testCrypto))
      resultList.add(DynamoDBUserAccountStore.toItem(thirdUser, testCrypto))
      queryResult.getItems.returns(resultList)
      queryResult.getCount.returns(3)
      client.query(any[QueryRequest]).returns(queryResult)

      val underTest = new DynamoDBUserAccountStore(client, config, testCrypto)

      val result = underTest.getUserByName(user.username)
      result must beASuccessfulTry
      result.get must beSome
      compareUserAccounts(result.get.get, user)
    }

    "Encrypt the user secret" in {
      val user = UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fb@hobbitmail.me"))
      val mockCrypto = mock[CryptoAlgebra]
      mockCrypto.encrypt(user.accessSecret).returns("hello")

      val item = DynamoDBUserAccountStore.toItem(user, mockCrypto)
      item.get("secretkey").getS must beEqualTo("hello")

      there.was(one(mockCrypto).encrypt(user.accessSecret))
    }

    "Decrypt the user secret" in {
      val user = UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fb@hobbitmail.me"))
      val mockCrypto = mock[CryptoAlgebra]
      mockCrypto.encrypt(user.accessSecret).returns("encrypt")
      mockCrypto.decrypt("encrypt").returns("decrypt")

      val item = DynamoDBUserAccountStore.toItem(user, mockCrypto)
      val u = DynamoDBUserAccountStore.fromItem(item, mockCrypto)
      u.accessSecret must beEqualTo("decrypt")

      there.was(one(mockCrypto).decrypt(item.get("secretkey").getS))
    }
  }

  def buildMocks(): (AmazonDynamoDBClient, Configuration) = {
    val client = mock[AmazonDynamoDBClient]
    val config = Configuration.load(Environment.simple())
    (client, config)
  }

  def buildTestStore(
      client: AmazonDynamoDBClient = mock[AmazonDynamoDBClient],
      config: Configuration = mock[Configuration]): DynamoDBUserAccountStore =
    new DynamoDBUserAccountStore(client, config, testCrypto)

  def compareUserAccounts(actual: UserAccount, expected: UserAccount) = {
    actual.userId must beEqualTo(expected.userId)
    actual.created.compareTo(expected.created) must beEqualTo(0)
    actual.username must beEqualTo(expected.username)
    actual.firstName must beEqualTo(expected.firstName)
    actual.lastName must beEqualTo(expected.lastName)
    actual.accessKey must beEqualTo(expected.accessKey)
    testCrypto.decrypt(actual.accessSecret) must beEqualTo(
      testCrypto.decrypt(expected.accessSecret))
  }
}
