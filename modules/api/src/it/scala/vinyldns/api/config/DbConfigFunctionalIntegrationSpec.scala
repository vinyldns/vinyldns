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
import vinyldns.api.domain.config.AppConfigService
import vinyldns.core.TestMembershipData.{okAuth, okUser, superUserAuth}
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
 *   4. Assert the business behaviour reflects the DB value
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

  private def seed(key: String, value: String, createdBy: String): Unit =
    appConfigRepository.create(key, value, createdBy).unsafeRunSync()

  private def applyDb(): Unit =
    (RuntimeVinylDNSConfig.loadFromDb(appConfigRepository) >>
      RuntimeVinylDNSConfig.applyDbOverrides()).unsafeRunSync()

  private def validations(): BatchChangeValidations =
    new BatchChangeValidations(
      new AccessValidations(),
      RuntimeVinylDNSConfig.manualReviewConfig,
      RuntimeVinylDNSConfig.approvedNameServers
    )

  "BatchChangeValidations high-value-domains from DB" should {

    "reject a record whose name matches a regex loaded from DB" in {
      seed("high-value-domains", """{"fqdn-regex-list":["db-hvd.*"],"ip-list":[]}""", "bob")
      applyDb()

      val change = AddChangeInput("db-hvd-blocked.example.", RecordType.A, None, Some(300L), AData("1.1.1.1"))
      validations().validateInputName(change, isApproved = false).isInvalid shouldBe true
    }

    "allow a record whose name does NOT match HVD regex loaded from DB" in {
      seed("high-value-domains", """{"fqdn-regex-list":["db-hvd.*"],"ip-list":[]}""", "bob")
      applyDb()

      val change = AddChangeInput("safe-name.example.", RecordType.A, None, Some(300L), AData("1.1.1.1"))
      validations().validateInputName(change, isApproved = false).isValid shouldBe true
    }
  }

  "BatchChangeValidations manual-review-domains from DB" should {

    "flag a domain for manual review when domain regex is loaded from DB" in {
      seed("manual-batch-review-enabled", "true", "system")
      seed("manual-review-domains",
        """{"domain-list":["needs-review.*"],"ip-list":[],"zone-name-list":[]}""", "bob")
      applyDb()

      val change = AddChangeInput("needs-review.example.", RecordType.A, None, Some(300L), AData("1.1.1.1"))
      validations().validateInputName(change, isApproved = false).isInvalid shouldBe true
    }

    "allow a domain not matching the DB pattern" in {
      seed("manual-review-domains",
        """{"domain-list":["needs-review.*"],"ip-list":[],"zone-name-list":[]}""", "bob")
      applyDb()

      val change = AddChangeInput("safe.example.", RecordType.A, None, Some(300L), AData("1.1.1.1"))
      validations().validateInputName(change, isApproved = false).isValid shouldBe true
    }
  }

  "ZoneValidations sync-delay from DB" should {

    "block a sync when sync-delay from DB has not yet elapsed" in {
      seed("sync-delay", "999999999", "bob")
      applyDb()

      val recentlySyncedZone = Zone("example.com.", "admin@example.com",
        latestSync = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)))

      new ZoneValidations(RuntimeVinylDNSConfig.syncDelay)
        .outsideSyncDelay(recentlySyncedZone)
        .unsafeRunSync()
        .isLeft shouldBe true
    }

    "allow a sync when sync-delay in DB is 0" in {
      seed("sync-delay", "0" , "bob")
      applyDb()

      val recentlySyncedZone = Zone("example.com.", "admin@example.com",
        latestSync = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)))

      new ZoneValidations(RuntimeVinylDNSConfig.syncDelay)
        .outsideSyncDelay(recentlySyncedZone)
        .unsafeRunSync()
        .isRight shouldBe true
    }
  }

  "MembershipService valid-email config from DB" should {

    "enforce email domain restriction loaded from DB" in {
      seed("valid-email", """{"email-domains":["allowed.com"],"number-of-dots":1}""", "bob")
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
      seed("valid-email", """{"email-domains":["allowed.com"],"number-of-dots":1}""", "bob")
      applyDb()

      val svc = new MembershipService(
        groupRepository, userRepository, membershipRepository,
        zoneRepository, groupChangeRepository, recordSetRepository
      )
      val result = svc.emailValidation("user@allowed.com").value.unsafeRunSync()
      result.isRight shouldBe true
    }
  }

  "RuntimeVinylDNSConfig limitsConfig from DB" should {

    "return updated MEMBERSHIP_ROUTING_MAX_GROUPS_LIST_LIMIT for route validation" in {
      seed("membership-routing-max-groups-list-limit", "42" , "bob")
      applyDb()
      RuntimeVinylDNSConfig.limitsConfig.MEMBERSHIP_ROUTING_MAX_GROUPS_LIST_LIMIT shouldBe 42
    }

    "return updated BATCHCHANGE_ROUTING_MAX_ITEMS_LIMIT for route validation" in {
      seed("batchchange-routing-max-items-limit", "7", "bob")
      applyDb()
      RuntimeVinylDNSConfig.limitsConfig.BATCHCHANGE_ROUTING_MAX_ITEMS_LIMIT shouldBe 7
    }
  }

  "RuntimeVinylDNSConfig approved-name-servers from DB" should {

    "reflect the list loaded from DB" in {
      seed("approved-name-servers", "ns1.custom.com.,ns2.custom.com.", "bob")
      applyDb()

      val patterns = RuntimeVinylDNSConfig.approvedNameServers.map(_.pattern.pattern())
      patterns should have length 2
      patterns should contain("(?i)ns1.custom.com.")
      patterns should contain("(?i)ns2.custom.com.")
    }

    "BatchChangeValidations built after applyDb() uses the DB-sourced approved servers" in {
      seed("approved-name-servers", "ns1.only-this.com.", "bob")
      applyDb()
      val patterns = RuntimeVinylDNSConfig.approvedNameServers.map(_.pattern.pattern())
      patterns should contain only "(?i)ns1.only-this.com."
    }
  }

  // ── AppConfigService.reloadConfig end-to-end ──────────────────────────────────

  "AppConfigService.reloadConfig" should {

    "return 'Config is already up to date.' when DB and memory are in sync" in {
      seed("sync-delay", "10000", "system")
      RuntimeVinylDNSConfig.loadFromDb(appConfigRepository).unsafeRunSync()

      val svc    = AppConfigService(appConfigRepository)
      val result = svc.reloadConfig(superUserAuth).value.unsafeRunSync()
      result.isRight shouldBe true
      result.toOption.get.message shouldBe "Config is already up to date."
      result.toOption.get.updated shouldBe empty
      result.toOption.get.added   shouldBe empty
      result.toOption.get.removed shouldBe empty
    }

    "return 'Config reloaded successfully' and populate updated when a value changed in DB" in {
      seed("sync-delay", "10000", "system")
      RuntimeVinylDNSConfig.loadFromDb(appConfigRepository).unsafeRunSync()

      // Change the DB value — do NOT reload memory yet
      appConfigRepository.update("sync-delay", "20000", "system").unsafeRunSync()

      val svc    = AppConfigService(appConfigRepository)
      val result = svc.reloadConfig(superUserAuth).value.unsafeRunSync()
      result.isRight shouldBe true
      val resp = result.toOption.get
      resp.message shouldBe "Config reloaded successfully"
      resp.updated should contain key "sync-delay"
      resp.updated("sync-delay").from shouldBe Some("10000")
      resp.updated("sync-delay").to   shouldBe Some("20000")
    }

    "return 'Config reloaded successfully' and populate added when a new DB row is inserted" in {
      val svc    = AppConfigService(appConfigRepository)
      seed("brand-new-key", "val", "system")

      val result = svc.reloadConfig(superUserAuth).value.unsafeRunSync()
      result.isRight shouldBe true
      val resp = result.toOption.get
      resp.message shouldBe "Config reloaded successfully"
      resp.added should contain("brand-new-key" -> "val")
    }

    "return 'Config reloaded successfully' and populate removed when a DB row is deleted" in {
      seed("to-be-deleted", "v", "system")
      RuntimeVinylDNSConfig.loadFromDb(appConfigRepository).unsafeRunSync()

      appConfigRepository.delete("to-be-deleted").unsafeRunSync()

      val svc    = AppConfigService(appConfigRepository)
      val result = svc.reloadConfig(superUserAuth).value.unsafeRunSync()
      result.isRight shouldBe true
      val resp = result.toOption.get
      resp.message  shouldBe "Config reloaded successfully"
      resp.removed should contain("to-be-deleted")
    }

    "apply DB values to runtime after reload (sync-delay actually changes)" in {
      seed("sync-delay", "10000", "system")
      RuntimeVinylDNSConfig.loadFromDb(appConfigRepository).unsafeRunSync()
      appConfigRepository.update("sync-delay", "55555", "system").unsafeRunSync()

      val svc = AppConfigService(appConfigRepository)
      svc.reloadConfig(superUserAuth).value.unsafeRunSync()

      RuntimeVinylDNSConfig.syncDelay.unsafeRunSync() shouldBe 55555
    }

    "return NotAuthorizedError for a non-super user" in {
      val svc    = AppConfigService(appConfigRepository)
      val result = svc.reloadConfig(okAuth).value.unsafeRunSync()
      result.isLeft shouldBe true
    }
  }

  // ── AppConfigService.getEffectiveConfig end-to-end ───────────────────────────

  "AppConfigService.getEffectiveConfig" should {

    "return the current in-memory effective snapshot" in {
      seed("sync-delay", "10000", "system")
      RuntimeVinylDNSConfig.loadFromDb(appConfigRepository).unsafeRunSync()

      val svc    = AppConfigService(appConfigRepository)
      val result = svc.getEffectiveConfig(superUserAuth).value.unsafeRunSync()
      result.isRight shouldBe true
      result.toOption.get.effective should contain("sync-delay" -> "10000")
    }

    "return non-empty pending when DB updated but not reloaded" in {
      seed("sync-delay", "10000", "system")
      RuntimeVinylDNSConfig.loadFromDb(appConfigRepository).unsafeRunSync()
      appConfigRepository.update("sync-delay", "99999", "system").unsafeRunSync()

      val svc    = AppConfigService(appConfigRepository)
      val result = svc.getEffectiveConfig(superUserAuth).value.unsafeRunSync()
      val resp   = result.toOption.get
      resp.pending should contain key "sync-delay"
      resp.pending("sync-delay").from shouldBe Some("10000")
      resp.pending("sync-delay").to   shouldBe Some("99999")
    }

    "return empty pending after reload is called" in {
      seed("sync-delay", "10000", "system")
      RuntimeVinylDNSConfig.loadFromDb(appConfigRepository).unsafeRunSync()
      appConfigRepository.update("sync-delay", "99999", "system").unsafeRunSync()

      val svc = AppConfigService(appConfigRepository)
      svc.reloadConfig(superUserAuth).value.unsafeRunSync()

      val result = svc.getEffectiveConfig(superUserAuth).value.unsafeRunSync()
      result.toOption.get.pending shouldBe empty
    }

    "return NotAuthorizedError for a non-super user" in {
      val svc    = AppConfigService(appConfigRepository)
      val result = svc.getEffectiveConfig(okAuth).value.unsafeRunSync()
      result.isLeft shouldBe true
    }
  }

  // ── CRUD does NOT refresh in-memory snapshot ──────────────────────────────────

  "AppConfigService CRUD operations" should {

    "not update in-memory snapshot after create" in {
      val svc    = AppConfigService(appConfigRepository)
      val before = RuntimeVinylDNSConfig.getAll.unsafeRunSync()

      svc.createAppConfig("crud-key", "v", superUserAuth).value.unsafeRunSync()

      RuntimeVinylDNSConfig.getAll.unsafeRunSync() shouldBe before
      RuntimeVinylDNSConfig.get("crud-key").unsafeRunSync() shouldBe None
    }

    "not update in-memory snapshot after update" in {
      seed("sync-delay", "10000", "system")
      RuntimeVinylDNSConfig.loadFromDb(appConfigRepository).unsafeRunSync()

      val svc = AppConfigService(appConfigRepository)
      svc.updateAppConfig("sync-delay", "99999", superUserAuth).value.unsafeRunSync()

      // Memory still has old value
      RuntimeVinylDNSConfig.get("sync-delay").unsafeRunSync() shouldBe Some("10000")
    }

    "not update in-memory snapshot after delete" in {
      seed("sync-delay", "10000", "system")
      RuntimeVinylDNSConfig.loadFromDb(appConfigRepository).unsafeRunSync()

      val svc = AppConfigService(appConfigRepository)
      svc.deleteAppConfig("sync-delay", superUserAuth).value.unsafeRunSync()

      // Memory still has the key — only reload removes it
      RuntimeVinylDNSConfig.get("sync-delay").unsafeRunSync() shouldBe Some("10000")
    }

    "show the CRUD change as pending in getEffectiveConfig until reload" in {
      seed("sync-delay", "10000", "system")
      RuntimeVinylDNSConfig.loadFromDb(appConfigRepository).unsafeRunSync()

      val svc = AppConfigService(appConfigRepository)
      svc.updateAppConfig("sync-delay", "77777", superUserAuth).value.unsafeRunSync()

      val effective = svc.getEffectiveConfig(superUserAuth).value.unsafeRunSync()
      effective.toOption.get.pending should contain key "sync-delay"
      effective.toOption.get.pending("sync-delay").to shouldBe Some("77777")
    }
  }
}
