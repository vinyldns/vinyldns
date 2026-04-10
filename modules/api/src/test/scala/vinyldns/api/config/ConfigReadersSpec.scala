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

package vinyldns.api.config

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pureconfig._
import pureconfig.generic.auto._
import scala.reflect.ClassTag

class ConfigReadersSpec extends AnyWordSpec with Matchers {

  private def load[A: ClassTag: ConfigReader](hocon: String): A =
    ConfigSource.fromConfig(ConfigFactory.parseString(hocon)).loadOrThrow[A]


  "ServerConfig configReader" should {

    "load all defaults when the block is empty" in {
      val cfg = load[ServerConfig]("{}")
      cfg.healthCheckTimeout          shouldBe 10
      cfg.defaultTtl                  shouldBe 7200
      cfg.maxZoneSize                 shouldBe 60000
      cfg.syncDelay                   shouldBe 10000
      cfg.validateRecordLookupAgainstDnsBackend shouldBe false
      cfg.processingDisabled          shouldBe false
      cfg.useRecordSetCache           shouldBe false
      cfg.approvedNameServers         shouldBe empty
      cfg.color                       shouldBe "blue"
      cfg.version                     shouldBe ""
      cfg.keyName                     shouldBe ""
      cfg.loadTestData                shouldBe false
      cfg.isZoneSyncScheduleAllowed   shouldBe true
    }

    "load explicit values when provided" in {
      val hocon =
        """{
          |  health-check-timeout = 30
          |  default-ttl = 3600
          |  max-zone-size = 1000
          |  sync-delay = 5000
          |  validate-record-lookup-against-dns-backend = true
          |  processing-disabled = true
          |  use-recordset-cache = true
          |  approved-name-servers = ["ns1.example.com.", "ns2.example.com."]
          |  color = "green"
          |  version = "1.2.3"
          |  load-test-data = true
          |  is-zone-sync-schedule-allowed = false
          |}""".stripMargin
      val cfg = load[ServerConfig](hocon)
      cfg.healthCheckTimeout          shouldBe 30
      cfg.defaultTtl                  shouldBe 3600
      cfg.maxZoneSize                 shouldBe 1000
      cfg.syncDelay                   shouldBe 5000
      cfg.validateRecordLookupAgainstDnsBackend shouldBe true
      cfg.processingDisabled          shouldBe true
      cfg.useRecordSetCache           shouldBe true
      cfg.approvedNameServers         should have length 2
      cfg.color                       shouldBe "green"
      cfg.version                     shouldBe "1.2.3"
      cfg.loadTestData                shouldBe true
      cfg.isZoneSyncScheduleAllowed   shouldBe false
    }

    "read keyName from defaultZoneConnection when present" in {
      val hocon =
        """{
          |  defaultZoneConnection {
          |    keyName = "vinyldns."
          |    name = "vinyldns."
          |    key  = "somekey=="
          |    primaryServer = "127.0.0.1"
          |  }
          |}""".stripMargin
      val cfg = load[ServerConfig](hocon)
      cfg.keyName shouldBe "vinyldns."
    }

    "default keyName to empty string when defaultZoneConnection is absent" in {
      val cfg = load[ServerConfig]("{}")
      cfg.keyName shouldBe ""
    }
  }

  "LimitsConfig configReader" should {

    "load all defaults when the block is empty" in {
      val cfg = load[LimitsConfig]("{}")
      cfg.BATCHCHANGE_ROUTING_MAX_ITEMS_LIMIT       shouldBe 100
      cfg.MEMBERSHIP_ROUTING_DEFAULT_MAX_ITEMS      shouldBe 100
      cfg.MEMBERSHIP_ROUTING_MAX_ITEMS_LIMIT        shouldBe 1000
      cfg.MEMBERSHIP_ROUTING_MAX_GROUPS_LIST_LIMIT  shouldBe 3000
      cfg.RECORDSET_ROUTING_DEFAULT_MAX_ITEMS       shouldBe 100
      cfg.ZONE_ROUTING_DEFAULT_MAX_ITEMS            shouldBe 100
      cfg.ZONE_ROUTING_MAX_ITEMS_LIMIT              shouldBe 100
    }

    "load explicit values when provided" in {
      val hocon =
        """{
          |  batchchange-routing-max-items-limit      = 50
          |  membership-routing-default-max-items     = 25
          |  membership-routing-max-items-limit       = 500
          |  membership-routing-max-groups-list-limit = 1500
          |  recordset-routing-default-max-items      = 75
          |  zone-routing-default-max-items           = 60
          |  zone-routing-max-items-limit             = 200
          |}""".stripMargin
      val cfg = load[LimitsConfig](hocon)
      cfg.BATCHCHANGE_ROUTING_MAX_ITEMS_LIMIT       shouldBe 50
      cfg.MEMBERSHIP_ROUTING_DEFAULT_MAX_ITEMS      shouldBe 25
      cfg.MEMBERSHIP_ROUTING_MAX_ITEMS_LIMIT        shouldBe 500
      cfg.MEMBERSHIP_ROUTING_MAX_GROUPS_LIST_LIMIT  shouldBe 1500
      cfg.RECORDSET_ROUTING_DEFAULT_MAX_ITEMS       shouldBe 75
      cfg.ZONE_ROUTING_DEFAULT_MAX_ITEMS            shouldBe 60
      cfg.ZONE_ROUTING_MAX_ITEMS_LIMIT              shouldBe 200
    }

    "load partial values and default the rest" in {
      val cfg = load[LimitsConfig]("{ batchchange-routing-max-items-limit = 42 }")
      cfg.BATCHCHANGE_ROUTING_MAX_ITEMS_LIMIT  shouldBe 42
      cfg.MEMBERSHIP_ROUTING_DEFAULT_MAX_ITEMS shouldBe 100   // default
      cfg.ZONE_ROUTING_MAX_ITEMS_LIMIT         shouldBe 100   // default
    }
  }

  "ManualReviewConfig configReader" should {

    "default to enabled=true with empty lists when manual-review-domains is absent" in {
      val cfg = load[ManualReviewConfig]("{}")
      cfg.enabled    shouldBe true
      cfg.domainList shouldBe empty
      cfg.ipList     shouldBe empty
      cfg.zoneList   shouldBe empty
    }

    "respect manual-batch-review-enabled = false" in {
      val cfg = load[ManualReviewConfig]("{ manual-batch-review-enabled = false }")
      cfg.enabled shouldBe false
    }

    "load domain/ip/zone lists from manual-review-domains when present" in {
      val hocon =
        """{
          |  manual-review-domains {
          |    domain-list    = ["test\\\\.example\\\\.com\\\\."]
          |    ip-list        = ["192.168.1.1"]
          |    zone-name-list = ["restricted.zone."]
          |  }
          |}""".stripMargin
      val cfg = load[ManualReviewConfig](hocon)
      cfg.domainList should have length 1
      cfg.ipList     should have length 1
      cfg.zoneList   should contain("restricted.zone.")
    }
  }
}
