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

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.typesafe.config.Config
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

class DynamoDBClientSpec extends WordSpec with Matchers with MockitoSugar {

  "DynamoDBClient" should {
    "create an AmazonDynamoDBClient" in {
      val mockConfig = mock[Config]
      doReturn("theKey").when(mockConfig).getString("key")
      doReturn("theSecret").when(mockConfig).getString("secret")
      doReturn("http://www.endpoint.com").when(mockConfig).getString("endpoint")

      val client = DynamoDBClient(mockConfig)
      client shouldBe a[AmazonDynamoDBClient]

      verify(mockConfig).getString("key")
      verify(mockConfig).getString("secret")
      verify(mockConfig).getString("endpoint")
    }
  }
}
