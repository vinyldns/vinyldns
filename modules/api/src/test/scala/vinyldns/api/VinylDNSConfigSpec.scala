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

package vinyldns.api

import org.scalatest.{Matchers, WordSpec}

class VinylDNSConfigSpec extends WordSpec with Matchers {

  "VinylDNSConfig" should {
    "load the rest config" in {
      val restConfig = VinylDNSConfig.restConfig
      restConfig.getInt("port") shouldBe 9000
    }

    "load the dynamo config" in {
      val dynamoConfig = VinylDNSConfig.dynamoConfig
      dynamoConfig.getString("key") shouldBe "dynamoKey"
      dynamoConfig.getString("secret") shouldBe "dynamoSecret"
      dynamoConfig.getString("endpoint") shouldBe "dynamoEndpoint"
    }

    "load the zone change repository config" in {
      val config = VinylDNSConfig.zoneChangeStoreConfig
      config.getString("dynamo.tableName") shouldBe "zoneChanges"
      config.getInt("dynamo.provisionedReads") shouldBe 30
      config.getInt("dynamo.provisionedWrites") shouldBe 30
    }

    "load the record change repository config" in {
      val config = VinylDNSConfig.recordChangeStoreConfig
      config.getString("dynamo.tableName") shouldBe "recordChange"
      config.getInt("dynamo.provisionedReads") shouldBe 30
      config.getInt("dynamo.provisionedWrites") shouldBe 30
    }

    "load the membership repository config" in {
      val config = VinylDNSConfig.membershipStoreConfig
      config.getString("dynamo.tableName") shouldBe "membership"
      config.getInt("dynamo.provisionedReads") shouldBe 30
      config.getInt("dynamo.provisionedWrites") shouldBe 30
    }

    "load the record set repository config" in {
      val config = VinylDNSConfig.recordSetStoreConfig
      config.getString("dynamo.tableName") shouldBe "recordSet"
      config.getInt("dynamo.provisionedReads") shouldBe 30
      config.getInt("dynamo.provisionedWrites") shouldBe 30
    }

    "load the account store config" in {
      val config = VinylDNSConfig.accountStoreConfig
      config.getString("dynamo.key") shouldBe "dynamoKey"
      config.getString("dynamo.secret") shouldBe "dynamoSecret"
      config.getString("dynamo.endpoint") shouldBe "dynamoEndpoint"
    }

    "load the group repository config" in {
      val config = VinylDNSConfig.groupsStoreConfig
      config.getString("dynamo.tableName") shouldBe "groups"
      config.getInt("dynamo.provisionedReads") shouldBe 30
      config.getInt("dynamo.provisionedWrites") shouldBe 30
    }
  }
}
