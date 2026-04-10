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

import cats.implicits._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.MySqlApiIntegrationSpec

/**
 * Integration tests for RuntimeVinylDNSConfig.applyDbOverrides().
 *
 * Each test seeds specific rows into the real app_config MySQL table,
 * calls loadFromDb() + applyDbOverrides(), then asserts the volatile
 * vars reflect the DB values — not hardcoded defaults or reference.conf.
 */
class RuntimeVinylDNSConfigIntegrationSpec
  extends AnyWordSpec
    with Matchers
    with MySqlApiIntegrationSpec
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  override def beforeAll(): Unit = { val _ = instance }

  override def beforeEach(): Unit = {
    clearAppConfigRepo()
    RuntimeVinylDNSConfig.init().unsafeRunSync()
  }

  override def afterEach(): Unit =
    clearAppConfigRepo()

  private def seed(key: String, value: String): Unit =
    appConfigRepository.create(key, value).unsafeRunSync()

  private def applyDb(): Unit =
    (RuntimeVinylDNSConfig.loadFromDb(appConfigRepository) >>
      RuntimeVinylDNSConfig.applyDbOverrides()).unsafeRunSync()

  // ─── limits ──────────────────────────────────────────────────────────────────

  "RuntimeVinylDNSConfig DB overrides" should {

    "override limitsConfig from DB keys" in {
      seed("batchchange-routing-max-items-limit",      "55")
      seed("membership-routing-default-max-items",     "22")
      seed("membership-routing-max-items-limit",       "444")
      seed("membership-routing-max-groups-list-limit", "999")
      seed("recordset-routing-default-max-items",      "33")
      seed("zone-routing-default-max-items",           "44")
      seed("zone-routing-max-items-limit",             "77")
      applyDb()

      val limits = RuntimeVinylDNSConfig.limitsConfig
      limits.BATCHCHANGE_ROUTING_MAX_ITEMS_LIMIT       shouldBe 55
      limits.MEMBERSHIP_ROUTING_DEFAULT_MAX_ITEMS      shouldBe 22
      limits.MEMBERSHIP_ROUTING_MAX_ITEMS_LIMIT        shouldBe 444
      limits.MEMBERSHIP_ROUTING_MAX_GROUPS_LIST_LIMIT  shouldBe 999
      limits.RECORDSET_ROUTING_DEFAULT_MAX_ITEMS       shouldBe 33
      limits.ZONE_ROUTING_DEFAULT_MAX_ITEMS            shouldBe 44
      limits.ZONE_ROUTING_MAX_ITEMS_LIMIT              shouldBe 77
    }

    "keep reference.conf limitsConfig when DB has no limit keys" in {
      applyDb()
      val limits = RuntimeVinylDNSConfig.limitsConfig
      limits.BATCHCHANGE_ROUTING_MAX_ITEMS_LIMIT      shouldBe 100
      limits.MEMBERSHIP_ROUTING_MAX_GROUPS_LIST_LIMIT shouldBe 3000
      limits.ZONE_ROUTING_MAX_ITEMS_LIMIT             shouldBe 100
    }

    // ─── shared-approved-types ────────────────────────────────────────────────

    "override sharedApprovedTypes from DB" in {
      seed("shared-approved-types", "A,AAAA")
      applyDb()

      val types = RuntimeVinylDNSConfig.sharedApprovedTypes.map(_.toString)
      types should contain theSameElementsAs List("A", "AAAA")
    }

    "keep init() sharedApprovedTypes when DB has no shared-approved-types key" in {
      applyDb()
      // reference.conf has A,AAAA,CNAME,PTR,TXT
      val types = RuntimeVinylDNSConfig.sharedApprovedTypes.map(_.toString)
      types should contain theSameElementsAs List("A", "AAAA", "CNAME", "PTR", "TXT")
    }

    // ─── high-value-domains ───────────────────────────────────────────────────

    "override highValueDomainConfig from DB" in {
      seed("high-value-domains",
        """{"fqdn-regex-list":["hvd-test.*"],"ip-list":["192.0.2.1"]}""")
      applyDb()

      val hvd = RuntimeVinylDNSConfig.highValueDomainConfig
      hvd.fqdnRegexes      should have length 1
      hvd.fqdnRegexes.head.pattern.pattern() shouldBe "(?i)hvd-test.*"
      hvd.ipList           should have length 1
    }

    "keep empty highValueDomainConfig when DB has no high-value-domains key" in {
      applyDb()
      val hvd = RuntimeVinylDNSConfig.highValueDomainConfig
      hvd.fqdnRegexes shouldBe empty
      hvd.ipList      shouldBe empty
    }

    // ─── dotted-hosts ─────────────────────────────────────────────────────────

    "override dottedHostsConfig from DB" in {
      seed("dotted-hosts",
        """{"allowed-settings":[{"zone":"test.","user-list":["alice"],"group-list":[],"record-types":["A"],"dots-limit":2}]}""")
      applyDb()

      val dh = RuntimeVinylDNSConfig.dottedHostsConfig
      dh.zoneAuthConfigs should have length 1
      dh.zoneAuthConfigs.head.zone     shouldBe "test."
      dh.zoneAuthConfigs.head.userList shouldBe List("alice")
    }

    "keep reference.conf dottedHostsConfig when DB has no dotted-hosts key" in {
      applyDb()
      // reference.conf has 2 allowed-settings entries
      RuntimeVinylDNSConfig.dottedHostsConfig.zoneAuthConfigs should have length 2
    }

    // ─── approved-name-servers ────────────────────────────────────────────────

    "override approvedNameServers from DB" in {
      seed("approved-name-servers", "ns1.custom.com.,ns2.custom.com.")
      applyDb()

      val ns = RuntimeVinylDNSConfig.approvedNameServers.map(_.pattern.pattern())
      ns should have length 2
      ns should contain("(?i)ns1.custom.com.")
    }

    "keep reference.conf approvedNameServers when DB has no approved-name-servers key" in {
      applyDb()
      // reference.conf has 2 entries
      RuntimeVinylDNSConfig.approvedNameServers should have length 2
    }

    // ─── manual-review ────────────────────────────────────────────────────────

    "override manualReviewConfig enabled flag from DB" in {
      seed("manual-batch-review-enabled", "false")
      applyDb()
      RuntimeVinylDNSConfig.manualReviewConfig.enabled shouldBe false
    }

    "override manualReviewConfig domain/ip/zone lists from DB" in {
      seed("manual-batch-review-enabled", "true")
      seed("manual-review-domains",
        """{"domain-list":["needs-review\\\\."],"ip-list":["192.0.2.254"],"zone-name-list":["restricted."]}""")
      applyDb()

      val mr = RuntimeVinylDNSConfig.manualReviewConfig
      mr.enabled    shouldBe true
      mr.domainList should have length 1
      mr.ipList     should have length 1
      mr.zoneList   should contain("restricted.")
    }

    // ─── valid-email ──────────────────────────────────────────────────────────

    "override validEmailConfig from DB" in {
      seed("valid-email",
        """{"email-domains":["example.com","*.corp.com"],"number-of-dots":3}""")
      applyDb()

      val ve = RuntimeVinylDNSConfig.validEmailConfig
      ve.valid_domains    should contain theSameElementsAs List("example.com", "*.corp.com")
      ve.number_of_dots   shouldBe 3
    }

    "keep reference.conf validEmailConfig when DB has no valid-email key" in {
      applyDb()
      val ve = RuntimeVinylDNSConfig.validEmailConfig
      ve.valid_domains should not be empty
    }

    // ─── global-acl-rules ─────────────────────────────────────────────────────

    "override globalAcls from DB" in {
      seed("global-acl-rules",
        """[{"group-ids":["acl-group"],"fqdn-regex-list":[".*test-zone.*"]}]""")
      applyDb()

      RuntimeVinylDNSConfig.globalAcls.acls should have length 1
    }

    "keep empty globalAcls when DB has no global-acl-rules key" in {
      applyDb()
      RuntimeVinylDNSConfig.globalAcls.acls shouldBe empty
    }

    // ─── scalar IO methods ────────────────────────────────────────────────────

    "read syncDelay from DB" in {
      seed("sync-delay", "12345")
      applyDb()
      RuntimeVinylDNSConfig.syncDelay.unsafeRunSync() shouldBe 12345
    }

    "read maxZoneSize from DB" in {
      seed("max-zone-size", "9999")
      applyDb()
      RuntimeVinylDNSConfig.maxZoneSize.unsafeRunSync() shouldBe 9999
    }

    "read batchChangeLimit from DB" in {
      seed("batch-change-limit", "250")
      applyDb()
      RuntimeVinylDNSConfig.batchChangeLimit.unsafeRunSync() shouldBe 250
    }

    "read scheduledChangesEnabled from DB" in {
      seed("scheduled-changes-enabled", "false")
      applyDb()
      RuntimeVinylDNSConfig.scheduledChangesEnabled.unsafeRunSync() shouldBe false
    }

    "read processingDisabled from DB" in {
      seed("processing-disabled", "true")
      applyDb()
      RuntimeVinylDNSConfig.processingDisabled.unsafeRunSync() shouldBe true
    }

    "read useRecordSetCache from DB" in {
      seed("use-recordset-cache", "true")
      applyDb()
      RuntimeVinylDNSConfig.useRecordSetCache.unsafeRunSync() shouldBe true
    }

    "fall back to reference.conf for syncDelay when DB key is absent" in {
      applyDb()
      // reference.conf: sync-delay = 600000
      RuntimeVinylDNSConfig.syncDelay.unsafeRunSync() shouldBe 600000
    }
  }
}
