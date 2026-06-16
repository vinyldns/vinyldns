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

package vinyldns.api.domain.config

import cats.effect.IO
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.config.RuntimeVinylDNSConfig
import vinyldns.core.TestMembershipData.{okAuth, superUserAuth}
import vinyldns.core.domain.config.{AppConfigRepository, AppConfigResponse}

import java.time.Instant

/**
 * Unit tests for AppConfigService.
 *
 * Covers all changes made:
 *   1. CRUD operations (create/update/delete) do NOT call refresh() — appConfigRef unchanged
 *   2. reloadConfig returns "Config is already up to date." when no diff
 *   3. reloadConfig returns "Config reloaded successfully" when there are changes
 *   4. getEffectiveConfig returns effective/referenceDefaults/pending fields
 */
class AppConfigServiceSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  // ── In-memory stub AppConfigRepository ──────────────────────────────────────
  class StubAppConfigRepo(initial: Map[String, String] = Map.empty) extends AppConfigRepository {
    private var store: Map[String, String] = initial

    def setStore(m: Map[String, String]): Unit = store = m

    private def toResponse(key: String, value: String): AppConfigResponse =
      AppConfigResponse(key, value, Instant.now.toString, Instant.now.toString, "test", "test")

    def create(key: String, value: String, createdBy: String): IO[AppConfigResponse] = IO {
      store = store + (key -> value)
      toResponse(key, value)
    }

    def getByKey(key: String): IO[Option[AppConfigResponse]] =
      IO.pure(store.get(key).map(toResponse(key, _)))

    def getAll: IO[List[AppConfigResponse]] =
      IO.pure(store.map { case (k, v) => toResponse(k, v) }.toList)

    def update(key: String, value: String, updatedBy: String): IO[Option[AppConfigResponse]] = IO {
      if (store.contains(key)) {
        store = store.updated(key, value)
        Some(toResponse(key, value))
      } else None
    }

    def delete(key: String): IO[Boolean] = IO {
      if (store.contains(key)) { store = store - key; true } else false
    }
  }

  override def beforeEach(): Unit =
    RuntimeVinylDNSConfig.init().unsafeRunSync()

  private def serviceWith(repo: StubAppConfigRepo) = AppConfigService(repo)

  "AppConfigService.reloadConfig" should {

    "return 'Config is already up to date.' when DB equals in-memory snapshot" in {
      val repo = new StubAppConfigRepo(Map("sync-delay" -> "10000"))
      // Pre-seed memory so it matches the DB
      RuntimeVinylDNSConfig.loadFromDb(repo).unsafeRunSync()

      val result = serviceWith(repo).reloadConfig(superUserAuth).value.unsafeRunSync()
      result.isRight shouldBe true
      result.toOption.get.message shouldBe "Config is already up to date."
    }

    "return 'Config reloaded successfully' when DB has an updated value" in {
      val repo = new StubAppConfigRepo(Map("sync-delay" -> "10000"))
      // Memory starts empty (no loadFromDb yet)
      RuntimeVinylDNSConfig.init().unsafeRunSync()
      // DB now has a different value than what's in memory
      repo.setStore(Map("sync-delay" -> "20000"))

      val result = serviceWith(repo).reloadConfig(superUserAuth).value.unsafeRunSync()
      result.isRight shouldBe true
      result.toOption.get.message shouldBe "Config reloaded successfully"
    }

    "return 'Config reloaded successfully' when DB has a newly added key" in {
      val repo = new StubAppConfigRepo(Map.empty)
      RuntimeVinylDNSConfig.init().unsafeRunSync()
      // DB now has a new key not in memory
      repo.setStore(Map("max-zone-size" -> "50000"))

      val result = serviceWith(repo).reloadConfig(superUserAuth).value.unsafeRunSync()
      result.isRight shouldBe true
      result.toOption.get.message shouldBe "Config reloaded successfully"
    }

    "return 'Config reloaded successfully' when a key was removed from DB" in {
      val repo = new StubAppConfigRepo(Map("sync-delay" -> "10000"))
      RuntimeVinylDNSConfig.loadFromDb(repo).unsafeRunSync()
      // Remove the key from DB
      repo.setStore(Map.empty)

      val result = serviceWith(repo).reloadConfig(superUserAuth).value.unsafeRunSync()
      result.isRight shouldBe true
      result.toOption.get.message shouldBe "Config reloaded successfully"
    }

    "populate updated map with from/to values" in {
      val repo = new StubAppConfigRepo(Map("sync-delay" -> "10000"))
      RuntimeVinylDNSConfig.loadFromDb(repo).unsafeRunSync()
      repo.setStore(Map("sync-delay" -> "99999"))

      val result = serviceWith(repo).reloadConfig(superUserAuth).value.unsafeRunSync()
      val response = result.toOption.get
      response.updated should contain key "sync-delay"
      response.updated("sync-delay").from shouldBe Some("10000")
      response.updated("sync-delay").to   shouldBe Some("99999")
    }

    "populate added map for newly inserted DB keys" in {
      val repo = new StubAppConfigRepo(Map.empty)
      RuntimeVinylDNSConfig.init().unsafeRunSync()
      repo.setStore(Map("new-key" -> "new-value"))

      val result = serviceWith(repo).reloadConfig(superUserAuth).value.unsafeRunSync()
      val response = result.toOption.get
      response.added should contain("new-key" -> "new-value")
    }

    "populate removed list for keys deleted from DB" in {
      val repo = new StubAppConfigRepo(Map("old-key" -> "v"))
      RuntimeVinylDNSConfig.loadFromDb(repo).unsafeRunSync()
      repo.setStore(Map.empty)

      val result = serviceWith(repo).reloadConfig(superUserAuth).value.unsafeRunSync()
      val response = result.toOption.get
      response.removed should contain("old-key")
    }

    "return NotAuthorizedError for a non-super user" in {
      val repo = new StubAppConfigRepo()
      val result = serviceWith(repo).reloadConfig(okAuth).value.unsafeRunSync()
      result.isLeft shouldBe true
    }
  }

  // ── CRUD does NOT trigger refresh ────────────────────────────────────────────

  "AppConfigService.createAppConfig" should {

    "write to DB without updating in-memory appConfigRef" in {
      val repo = new StubAppConfigRepo()
      RuntimeVinylDNSConfig.init().unsafeRunSync()

      val before = RuntimeVinylDNSConfig.getAll.unsafeRunSync()
      serviceWith(repo).createAppConfig("k1", "v1", superUserAuth).value.unsafeRunSync()
      val after = RuntimeVinylDNSConfig.getAll.unsafeRunSync()

      // appConfigRef must NOT have changed — k1 absent in memory
      after shouldBe before
      after.contains("k1") shouldBe false
    }

    "return the created config" in {
      val repo = new StubAppConfigRepo()
      val result = serviceWith(repo).createAppConfig("k1", "v1", superUserAuth).value.unsafeRunSync()
      result.isRight shouldBe true
      result.toOption.get.key shouldBe "k1"
      result.toOption.get.value shouldBe "v1"
    }
  }

  "AppConfigService.updateAppConfig" should {

    "write updated value to DB without updating in-memory appConfigRef" in {
      val repo = new StubAppConfigRepo(Map("sync-delay" -> "10000"))
      // Pre-load so memory has sync-delay = 10000
      RuntimeVinylDNSConfig.loadFromDb(repo).unsafeRunSync()

      serviceWith(repo).updateAppConfig("sync-delay", "99999", superUserAuth).value.unsafeRunSync()

      // Memory should still have the OLD value — reload not called
      RuntimeVinylDNSConfig.get("sync-delay").unsafeRunSync() shouldBe Some("10000")
    }

    "return the updated config" in {
      val repo = new StubAppConfigRepo(Map("sync-delay" -> "10000"))
      val result = serviceWith(repo).updateAppConfig("sync-delay", "20000", superUserAuth).value.unsafeRunSync()
      result.isRight shouldBe true
      result.toOption.get.value shouldBe "20000"
    }
  }

  "AppConfigService.deleteAppConfig" should {

    "remove from DB without updating in-memory appConfigRef" in {
      val repo = new StubAppConfigRepo(Map("sync-delay" -> "10000"))
      RuntimeVinylDNSConfig.loadFromDb(repo).unsafeRunSync()

      serviceWith(repo).deleteAppConfig("sync-delay", superUserAuth).value.unsafeRunSync()

      // Memory should still have the key — reload not called
      RuntimeVinylDNSConfig.get("sync-delay").unsafeRunSync() shouldBe Some("10000")
    }
  }

  // ── getEffectiveConfig ────────────────────────────────────────────────────────

  "AppConfigService.getEffectiveConfig" should {

    "return effective map matching in-memory snapshot" in {
      val repo = new StubAppConfigRepo(Map("sync-delay" -> "10000"))
      RuntimeVinylDNSConfig.loadFromDb(repo).unsafeRunSync()

      val result = serviceWith(repo).getEffectiveConfig(superUserAuth).value.unsafeRunSync()
      result.isRight shouldBe true
      result.toOption.get.effective should contain("sync-delay" -> "10000")
    }

    "return pending with from/to when DB differs from memory" in {
      val repo = new StubAppConfigRepo(Map("sync-delay" -> "10000"))
      RuntimeVinylDNSConfig.loadFromDb(repo).unsafeRunSync()
      // Update DB but do not reload
      repo.setStore(Map("sync-delay" -> "99999"))

      val result = serviceWith(repo).getEffectiveConfig(superUserAuth).value.unsafeRunSync()
      val response = result.toOption.get
      response.pending should contain key "sync-delay"
      response.pending("sync-delay").from shouldBe Some("10000")
      response.pending("sync-delay").to   shouldBe Some("99999")
    }

    "return empty pending when DB matches memory" in {
      val repo = new StubAppConfigRepo(Map("sync-delay" -> "10000"))
      RuntimeVinylDNSConfig.loadFromDb(repo).unsafeRunSync()

      val result = serviceWith(repo).getEffectiveConfig(superUserAuth).value.unsafeRunSync()
      result.toOption.get.pending shouldBe empty
    }

    "return pending with null from when DB has a new key not in memory" in {
      val repo = new StubAppConfigRepo(Map.empty)
      RuntimeVinylDNSConfig.init().unsafeRunSync()
      repo.setStore(Map("brand-new-key" -> "val"))

      val result = serviceWith(repo).getEffectiveConfig(superUserAuth).value.unsafeRunSync()
      val response = result.toOption.get
      response.pending should contain key "brand-new-key"
      response.pending("brand-new-key").from shouldBe None
      response.pending("brand-new-key").to   shouldBe Some("val")
    }

    "return reference-defaults for keys in reference.conf not present in memory" in {
      val repo = new StubAppConfigRepo(Map.empty)
      RuntimeVinylDNSConfig.init().unsafeRunSync()

      val result = serviceWith(repo).getEffectiveConfig(superUserAuth).value.unsafeRunSync()
      // At least some reference keys should be listed
      result.toOption.get.referenceDefaults should not be empty
    }

    "return NotAuthorizedError for a non-super user" in {
      val repo = new StubAppConfigRepo()
      val result = serviceWith(repo).getEffectiveConfig(okAuth).value.unsafeRunSync()
      result.isLeft shouldBe true
    }
  }
}
