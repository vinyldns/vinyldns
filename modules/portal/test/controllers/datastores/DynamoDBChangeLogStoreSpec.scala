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

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import controllers.{ChangeLogMessage, Create, UserChangeMessage}
import models.UserAccount
import org.joda.time.DateTime
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.{Configuration, Environment}
import vinyldns.core.crypto.CryptoAlgebra

class DynamoDBChangeLogStoreSpec extends Specification with Mockito {
  private val testCrypto = new CryptoAlgebra {
    def encrypt(value: String): String = "encrypted!"
    def decrypt(value: String): String = "decrypted!"
  }

  private val testUserAcc = UserAccount("foo", Some("bar"), Some("baz"), Some("qux"))
  private val testMessage =
    UserChangeMessage("foo", "bar", DateTime.now, Create, testUserAcc, Some(testUserAcc))
  "DynamoDbChangeLogStore" should {
    "accept a message and return it upon success" in {
      val (client, config) = buildMocks()
      val underTest = new DynamoDBChangeLogStore(client, config, testCrypto)
      val result = underTest.log(testMessage)
      result must beSuccessfulTry[ChangeLogMessage](testMessage)
    }

    "accept a message and return it upon success when email, last name, and first name are none" in {
      val (client, config) = buildMocks()
      val underTest = new DynamoDBChangeLogStore(client, config, testCrypto)
      val user = testUserAcc.copy(firstName = None, lastName = None, email = None)
      val message = testMessage.copy(previousUser = None)

      val result = underTest.log(message)
      result must beSuccessfulTry[ChangeLogMessage](message)
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
}
