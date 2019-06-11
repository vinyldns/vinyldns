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
import vinyldns.api.crypto.Crypto
import vinyldns.core.domain.zone.ZoneConnection
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

      mysqlConfig.repositories.keys should contain theSameElementsAs Set(
        zone,
        batchChange,
        user,
        recordSet)
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

    "properly load the notifier configs" in {
      val notifierConfigs = VinylDNSConfig.notifierConfigs.unsafeRunSync

      notifierConfigs.length shouldBe 1

      notifierConfigs.head.className shouldBe "someclass"

      notifierConfigs.head.settings.getString("value").shouldBe("test")
    }

    "load default keys" in {
      val defaultConn =
        ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "127.0.0.1:19001")

      VinylDNSConfig.configuredDnsConnections.defaultZoneConnection
        .decrypted(Crypto.instance) shouldBe
        defaultConn
      VinylDNSConfig.configuredDnsConnections.defaultTransferConnection
        .decrypted(Crypto.instance) shouldBe
        defaultConn
    }
    "load specified backends" in {
      val zc = ZoneConnection("zoneconn.", "vinyldns.", "test-key", "127.0.0.1:19001")
      val tc = zc.copy(name = "transferconn.")

      val backends = VinylDNSConfig.configuredDnsConnections.dnsBackends
      backends.length shouldBe 1

      backends.head.id shouldBe "test"
      backends.head.zoneConnection.decrypted(Crypto.instance) shouldBe zc
      backends.head.transferConnection.decrypted(Crypto.instance) shouldBe tc
    }
    "load the sync blacklist as lowercase, trailing dot" in {
      VinylDNSConfig.syncBannedZones shouldBe List("can.not.sync.", "to.lower.")
    }
  }
}
