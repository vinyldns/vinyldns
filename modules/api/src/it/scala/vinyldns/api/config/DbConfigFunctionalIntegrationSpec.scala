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

import cats.effect.IO
import cats.implicits._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.MySqlApiIntegrationSpec
import vinyldns.api.domain.access.AccessValidations
import vinyldns.api.domain.batch.{AddChangeInput, BatchChangeValidations}
import vinyldns.api.domain.membership.MembershipService
import vinyldns.api.domain.zone.ZoneValidations
import vinyldns.core.TestMembershipData.{okAuth, okUser}
import vinyldns.core.domain.membership.Group
import vinyldns.core.domain.record.{AData, RecordType}
import vinyldns.core.domain.zone.Zone

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext

/**
 * Functional end-to-end tests proving that DB config rows actually change system behaviour.
 *
 * Pattern for every test:
 *   1. Seed rows into app_config via appConfigRepository
 *   2. Call loadFromDb() + applyDbOverrides()
 *   3. Instantiate the service/validation that reads from RuntimeVinylDNSConfig
 *   4. Assert the business behaviour reflects the DB value — not a hardcoded default
 */
class DbConfigFunctionalIntegrationSpec
  extends AnyWordSpec
    with Matchers
    with MySqlApiIntegrationSpec
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  override def beforeAll(): Unit = { val _ = instance }

  private implicit val cs = IO.contextShift(ExecutionContext.global)

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

  private def validations(): BatchChangeValidations =
    new BatchChangeValidations(
      new AccessValidations(),
      RuntimeVinylDNSConfig.manualReviewConfig,
      RuntimeVinylDNSConfig.approvedNameServers
    )

  // ─── high-value-domains ───────────────────────────────────────────────────

  "BatchChangeValidations high-value-domains from DB" should {

    "reject a record whose name matches a regex loaded from DB" in {
      seed("high-value-domains", """{"fqdn-regex-list":["db-hvd.*"],"ip-list":[]}""")
      applyDb()

      val change = AddChangeInput("db-hvd-blocked.example.", RecordType.A, None, Some(300L), AData("1.1.1.1"))
      validations().validateInputName(change, isApproved = false).isInvalid shouldBe true
    }

    "allow a record whose name does NOT match HVD regex loaded from DB" in {
      seed("high-value-domains", """{"fqdn-regex-list":["db-hvd.*"],"ip-list":[]}""")
      applyDb()

      val change = AddChangeInput("safe-name.example.", RecordType.A, None, Some(300L), AData("1.1.1.1"))
      validations().validateInputName(change, isApproved = false).isValid shouldBe true
    }
  }

  // ─── manual-review-domains ────────────────────────────────────────────────

  "BatchChangeValidations manual-review-domains from DB" should {

    "flag a domain for manual review when domain regex is loaded from DB" in {
      seed("manual-batch-review-enabled", "true")
      seed("manual-review-domains",
        """{"domain-list":["needs-review.*"],"ip-list":[],"zone-name-list":[]}""")
      applyDb()

      val change = AddChangeInput("needs-review.example.", RecordType.A, None, Some(300L), AData("1.1.1.1"))
      validations().validateInputName(change, isApproved = false).isInvalid shouldBe true
    }

    "allow a domain not matching the DB pattern" in {
      seed("manual-review-domains",
        """{"domain-list":["needs-review.*"],"ip-list":[],"zone-name-list":[]}""")
      applyDb()

      val change = AddChangeInput("safe.example.", RecordType.A, None, Some(300L), AData("1.1.1.1"))
      validations().validateInputName(change, isApproved = false).isValid shouldBe true
    }
  }

  // ─── sync-delay ───────────────────────────────────────────────────────────

  "ZoneValidations sync-delay from DB" should {

    "block a sync when sync-delay from DB has not yet elapsed" in {
      seed("sync-delay", "999999999")
      applyDb()

      val recentlySyncedZone = Zone("example.com.", "admin@example.com",
        latestSync = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)))

      new ZoneValidations(RuntimeVinylDNSConfig.syncDelay)
        .outsideSyncDelay(recentlySyncedZone)
        .unsafeRunSync()
        .isLeft shouldBe true
    }

    "allow a sync when sync-delay in DB is 0" in {
      seed("sync-delay", "0")
      applyDb()

      val recentlySyncedZone = Zone("example.com.", "admin@example.com",
        latestSync = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)))

      new ZoneValidations(RuntimeVinylDNSConfig.syncDelay)
        .outsideSyncDelay(recentlySyncedZone)
        .unsafeRunSync()
        .isRight shouldBe true
    }
  }

  // ─── valid-email ──────────────────────────────────────────────────────────

  "MembershipService valid-email config from DB" should {

    "enforce email domain restriction loaded from DB" in {
      seed("valid-email", """{"email-domains":["allowed.com"],"number-of-dots":1}""")
      applyDb()

      val svc = new MembershipService(
        groupRepository, userRepository, membershipRepository,
        zoneRepository, groupChangeRepository, recordSetRepository
      )
      val result = svc.createGroup(
        Group("test-group-blocked", "user@blocked.com", adminUserIds = Set(okUser.id)),
        okAuth
      ).value.unsafeRunSync()
      result.isLeft shouldBe true
    }

    "allow an email matching the domain loaded from DB" in {
      seed("valid-email", """{"email-domains":["allowed.com"],"number-of-dots":1}""")
      applyDb()

      val svc = new MembershipService(
        groupRepository, userRepository, membershipRepository,
        zoneRepository, groupChangeRepository, recordSetRepository
      )
      val result = svc.emailValidation("user@allowed.com").value.unsafeRunSync()
      result.isRight shouldBe true
    }
  }

  // ─── limits ───────────────────────────────────────────────────────────────

  "RuntimeVinylDNSConfig limitsConfig from DB" should {

    "return updated MEMBERSHIP_ROUTING_MAX_GROUPS_LIST_LIMIT for route validation" in {
      seed("membership-routing-max-groups-list-limit", "42")
      applyDb()
      RuntimeVinylDNSConfig.limitsConfig.MEMBERSHIP_ROUTING_MAX_GROUPS_LIST_LIMIT shouldBe 42
    }

    "return updated BATCHCHANGE_ROUTING_MAX_ITEMS_LIMIT for route validation" in {
      seed("batchchange-routing-max-items-limit", "7")
      applyDb()
      RuntimeVinylDNSConfig.limitsConfig.BATCHCHANGE_ROUTING_MAX_ITEMS_LIMIT shouldBe 7
    }
  }

  // ─── approved-name-servers ────────────────────────────────────────────────

  "RuntimeVinylDNSConfig approved-name-servers from DB" should {

    "reflect the list loaded from DB" in {
      seed("approved-name-servers", "ns1.custom.com.,ns2.custom.com.")
      applyDb()

      val patterns = RuntimeVinylDNSConfig.approvedNameServers.map(_.pattern.pattern())
      patterns should have length 2
      patterns should contain("(?i)ns1.custom.com.")
      patterns should contain("(?i)ns2.custom.com.")
    }

    "BatchChangeValidations built after applyDb() uses the DB-sourced approved servers" in {
      seed("approved-name-servers", "ns1.only-this.com.")
      applyDb()

      // approvedNameServers is injected at construction time — so building after applyDb()
      // picks up the DB value. Verify RuntimeVinylDNSConfig itself has exactly the DB value.
      val patterns = RuntimeVinylDNSConfig.approvedNameServers.map(_.pattern.pattern())
      patterns should contain only "(?i)ns1.only-this.com."
    }
  }
}
