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
import vinyldns.core.repository.RepositoryName._

class VinylDNSConfigSpec extends WordSpec with Matchers {

  "VinylDNSConfig" should {
    "load the rest config" in {
      val restConfig = VinylDNSConfig.restConfig
      restConfig.getInt("port") shouldBe 9000
    }

    "properly load the datastore configs" in {

      VinylDNSConfig.dataStoreConfigs.unsafeRunSync.length shouldBe 2
    }
    "assign the correct mysql repositories" in {
      val mysqlConfig =
        VinylDNSConfig.dataStoreConfigs.unsafeRunSync
          .find(_.className == "vinyldns.mysql.repository.MySqlDataStoreProvider")
          .get

      mysqlConfig.repositories.keys should contain theSameElementsAs Set(zone, batchChange, user, recordSet)
    }
    "assign the correct dynamodb repositories" in {
      val dynamodbConfig =
        VinylDNSConfig.dataStoreConfigs.unsafeRunSync
          .find(_.className == "vinyldns.dynamodb.repository.DynamoDBDataStoreProvider")
          .get

      dynamodbConfig.repositories.keys should contain theSameElementsAs
        Set(group, membership, groupChange, recordChange, zoneChange)
    }

    "load string list for key that exists" in {
      VinylDNSConfig.getOptionalStringList("string-list-test").length shouldBe 1
    }

    "load empty string list that does not exist" in {
      VinylDNSConfig.getOptionalStringList("no-existo").length shouldBe 0
    }
  }
}
