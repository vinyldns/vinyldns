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

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.actor.ActorSystem
import scala.concurrent.Await
import scala.concurrent.duration._

class RuntimeVinylDNSConfigSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    // Initialise once for the whole spec; safe to call multiple times
    RuntimeVinylDNSConfig.init().unsafeRunSync()
  }

  "RuntimeVinylDNSConfig.init" should {

    "populate current synchronously after init" in {
      RuntimeVinylDNSConfig.current should not be null
    }

    "return a valid VinylDNSConfig from current after init" in {
      val cfg = RuntimeVinylDNSConfig.current
      cfg.serverConfig should not be null
      cfg.validEmailConfig should not be null
      cfg.manualReviewConfig should not be null
    }

    "make currentIO return the same value as current after init" in {
      val fromIO  = RuntimeVinylDNSConfig.currentIO.unsafeRunSync()
      val fromSync = RuntimeVinylDNSConfig.current
      fromIO.serverConfig.color  shouldBe fromSync.serverConfig.color
      fromIO.serverConfig.keyName shouldBe fromSync.serverConfig.keyName
      fromIO.validEmailConfig    shouldBe fromSync.validEmailConfig
    }
  }

  "RuntimeVinylDNSConfig.reload" should {

    "complete without error when the config file has not changed" in {
      noException should be thrownBy RuntimeVinylDNSConfig.reload().unsafeRunSync()
    }

    "keep current non-null after reload" in {
      RuntimeVinylDNSConfig.reload().unsafeRunSync()
      RuntimeVinylDNSConfig.current should not be null
    }

    "keep currentIO consistent with current after reload" in {
      RuntimeVinylDNSConfig.reload().unsafeRunSync()
      val fromIO   = RuntimeVinylDNSConfig.currentIO.unsafeRunSync()
      val fromSync = RuntimeVinylDNSConfig.current
      fromIO.serverConfig.color   shouldBe fromSync.serverConfig.color
      fromIO.validEmailConfig     shouldBe fromSync.validEmailConfig
      fromIO.manualReviewConfig.enabled shouldBe fromSync.manualReviewConfig.enabled
    }

    "preserve valid email config across reload" in {
      val before = RuntimeVinylDNSConfig.current.validEmailConfig
      RuntimeVinylDNSConfig.reload().unsafeRunSync()
      val after = RuntimeVinylDNSConfig.current.validEmailConfig
      after.valid_domains      shouldBe before.valid_domains
      after.number_of_dots     shouldBe before.number_of_dots
    }

    "be safe when called multiple times in succession" in {
      noException should be thrownBy {
        (1 to 5).foreach(_ => RuntimeVinylDNSConfig.reload().unsafeRunSync())
      }
      RuntimeVinylDNSConfig.current should not be null
    }

    "be safe when called concurrently" in {
      import scala.concurrent.Await
      import scala.concurrent.duration._
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

      val futures = (1 to 10).map(_ => Future(RuntimeVinylDNSConfig.reload().unsafeRunSync()))
      Await.result(Future.sequence(futures), 30.seconds)

      RuntimeVinylDNSConfig.current should not be null
      val fromIO   = RuntimeVinylDNSConfig.currentIO.unsafeRunSync()
      val fromSync = RuntimeVinylDNSConfig.current
      fromIO.serverConfig.color shouldBe fromSync.serverConfig.color
    }
  }

  "RuntimeVinylDNSConfig.getRaw" should {

    "return the underlying typesafe Config" in {
      RuntimeVinylDNSConfig.getRaw should not be null
    }

    "contain the vinyldns namespace" in {
      RuntimeVinylDNSConfig.getRaw.hasPath("vinyldns") shouldBe true
    }
  }

  "RuntimeVinylDNSConfig.currentIO (Boot: vinyldnsConfig <- RuntimeVinylDNSConfig.currentIO)" should {

    "return a non-null config" in {
      val cfg = RuntimeVinylDNSConfig.currentIO.unsafeRunSync()
      cfg should not be null
    }

    "contain non-empty dataStoreConfigs (used by DataStoreLoader.loadAll)" in {
      val cfg = RuntimeVinylDNSConfig.currentIO.unsafeRunSync()
      cfg.dataStoreConfigs should not be empty
    }

    "have a non-null crypto instance (used by DataStoreLoader.loadAll)" in {
      val cfg = RuntimeVinylDNSConfig.currentIO.unsafeRunSync()
      cfg.crypto should not be null
    }

    "have dataStoreConfigs with a non-empty className for each entry" in {
      val cfg = RuntimeVinylDNSConfig.currentIO.unsafeRunSync()
      cfg.dataStoreConfigs.foreach { dsc =>
        dsc.className should not be empty
      }
    }

    "have a non-null messageQueueConfig (used in subsequent Boot steps)" in {
      val cfg = RuntimeVinylDNSConfig.currentIO.unsafeRunSync()
      cfg.messageQueueConfig should not be null
    }
  }

  // ── getEffectiveDetailed ──────────────────────────────────────────────────────

  "RuntimeVinylDNSConfig.getEffectiveDetailed" should {

    // Inline stub repo for unit-level tests (no MySQL needed)
    import cats.effect.IO
    import vinyldns.core.domain.config.{AppConfigRepository, AppConfigResponse}
    import java.time.Instant

    def stubRepo(dbStore: Map[String, String]): AppConfigRepository = new AppConfigRepository {
      private def r(k: String, v: String) =
        AppConfigResponse(k, v, Instant.now.toString, Instant.now.toString, "test", "test")
      def create(k: String, v: String, by: String)           = IO.pure(r(k, v))
      def getByKey(k: String)                                = IO.pure(dbStore.get(k).map(r(k, _)))
      def getAll                                             = IO.pure(dbStore.map { case (k, v) => r(k, v) }.toList)
      def update(k: String, v: String, by: String)           = IO.pure(dbStore.get(k).map(_ => r(k, v)))
      def delete(k: String)                                  = IO.pure(dbStore.contains(k))
    }

    "return effective map equal to the current in-memory snapshot" in {
      RuntimeVinylDNSConfig.loadFromDb(stubRepo(Map.empty)).unsafeRunSync()
      val repo = stubRepo(Map.empty)
      val resp = RuntimeVinylDNSConfig.getEffectiveDetailed(repo).unsafeRunSync()
      resp.effective shouldBe RuntimeVinylDNSConfig.getAll.unsafeRunSync()
    }

    "return empty pending when DB matches memory" in {
      val kvs  = Map("sync-delay" -> "10000")
      RuntimeVinylDNSConfig.loadFromDb(stubRepo(Map.empty)).unsafeRunSync()
      RuntimeVinylDNSConfig.loadFromDb(stubRepo(kvs)).unsafeRunSync()

      val resp = RuntimeVinylDNSConfig.getEffectiveDetailed(stubRepo(kvs)).unsafeRunSync()
      resp.pending shouldBe empty
    }

    "return pending entry when DB value differs from memory" in {
      val initialKvs = Map("sync-delay" -> "10000")
      RuntimeVinylDNSConfig.loadFromDb(stubRepo(Map.empty)).unsafeRunSync()
      RuntimeVinylDNSConfig.loadFromDb(stubRepo(initialKvs)).unsafeRunSync()

      // DB updated but memory not reloaded
      val updatedRepo = stubRepo(Map("sync-delay" -> "99999"))
      val resp = RuntimeVinylDNSConfig.getEffectiveDetailed(updatedRepo).unsafeRunSync()
      resp.pending should contain key "sync-delay"
      resp.pending("sync-delay").from shouldBe Some("10000")
      resp.pending("sync-delay").to   shouldBe Some("99999")
    }

    "return pending with None from when DB has a key not yet in memory" in {
      RuntimeVinylDNSConfig.loadFromDb(stubRepo(Map.empty)).unsafeRunSync()
      val resp = RuntimeVinylDNSConfig.getEffectiveDetailed(stubRepo(Map("brand-new" -> "val"))).unsafeRunSync()
      resp.pending should contain key "brand-new"
      resp.pending("brand-new").from shouldBe None
      resp.pending("brand-new").to   shouldBe Some("val")
    }

    "return pending with None to when memory has a key absent from DB" in {
      RuntimeVinylDNSConfig.loadFromDb(stubRepo(Map.empty)).unsafeRunSync()
      RuntimeVinylDNSConfig.loadFromDb(stubRepo(Map("old-key" -> "v"))).unsafeRunSync()

      val resp = RuntimeVinylDNSConfig.getEffectiveDetailed(stubRepo(Map.empty)).unsafeRunSync()
      resp.pending should contain key "old-key"
      resp.pending("old-key").from shouldBe Some("v")
      resp.pending("old-key").to   shouldBe None
    }

    "return non-empty reference-defaults when no DB keys are loaded" in {
      RuntimeVinylDNSConfig.loadFromDb(stubRepo(Map.empty)).unsafeRunSync()
      val resp = RuntimeVinylDNSConfig.getEffectiveDetailed(stubRepo(Map.empty)).unsafeRunSync()
      resp.referenceDefaults should not be empty
    }

    "exclude a key from reference-defaults once it is loaded into memory" in {
      RuntimeVinylDNSConfig.loadFromDb(stubRepo(Map.empty)).unsafeRunSync()
      val before = RuntimeVinylDNSConfig.getEffectiveDetailed(stubRepo(Map.empty)).unsafeRunSync()
      val someRefKey = before.referenceDefaults.head

      // Seed that key into memory
      RuntimeVinylDNSConfig.loadFromDb(stubRepo(Map(someRefKey -> "x"))).unsafeRunSync()
      val after = RuntimeVinylDNSConfig.getEffectiveDetailed(stubRepo(Map(someRefKey -> "x"))).unsafeRunSync()
      after.referenceDefaults should not contain someRefKey
    }
  }

  // ── reloadWithDiff ────────────────────────────────────────────────────────────

  "RuntimeVinylDNSConfig.reloadWithDiff" should {

    import cats.effect.IO
    import vinyldns.core.domain.config.{AppConfigRepository, AppConfigResponse}
    import java.time.Instant

    def stubRepo(dbStore: Map[String, String]): AppConfigRepository = new AppConfigRepository {
      private def r(k: String, v: String) =
        AppConfigResponse(k, v, Instant.now.toString, Instant.now.toString, "test", "test")
      def create(k: String, v: String, by: String)  = IO.pure(r(k, v))
      def getByKey(k: String)                       = IO.pure(dbStore.get(k).map(r(k, _)))
      def getAll                                    = IO.pure(dbStore.map { case (k, v) => r(k, v) }.toList)
      def update(k: String, v: String, by: String)  = IO.pure(dbStore.get(k).map(_ => r(k, v)))
      def delete(k: String)                         = IO.pure(dbStore.contains(k))
    }

    "return an empty diff when DB and memory are already in sync" in {
      val repo = stubRepo(Map("sync-delay" -> "10000"))
      RuntimeVinylDNSConfig.loadFromDb(repo).unsafeRunSync()

      val diff = RuntimeVinylDNSConfig.reloadWithDiff(repo).unsafeRunSync()
      diff shouldBe empty
    }

    "return a diff entry when DB has an updated value" in {
      val repo = stubRepo(Map("sync-delay" -> "10000"))
      RuntimeVinylDNSConfig.loadFromDb(repo).unsafeRunSync()

      val updatedRepo = stubRepo(Map("sync-delay" -> "99999"))
      val diff = RuntimeVinylDNSConfig.reloadWithDiff(updatedRepo).unsafeRunSync()
      diff should contain key "sync-delay"
      diff("sync-delay") shouldBe (Some("10000"), Some("99999"))
    }

    "return a diff entry when a new key is added to DB" in {
      RuntimeVinylDNSConfig.loadFromDb(stubRepo(Map.empty)).unsafeRunSync()
      val repo = stubRepo(Map("brand-new" -> "val"))
      val diff = RuntimeVinylDNSConfig.reloadWithDiff(repo).unsafeRunSync()
      diff should contain key "brand-new"
      diff("brand-new") shouldBe (None, Some("val"))
    }

    "return a diff entry when a key is removed from DB" in {
      val repo = stubRepo(Map("old-key" -> "v"))
      RuntimeVinylDNSConfig.loadFromDb(repo).unsafeRunSync()

      val diff = RuntimeVinylDNSConfig.reloadWithDiff(stubRepo(Map.empty)).unsafeRunSync()
      diff should contain key "old-key"
      diff("old-key") shouldBe (Some("v"), None)
    }

    "update appConfigRef after reload so a second diff is empty" in {
      RuntimeVinylDNSConfig.loadFromDb(stubRepo(Map.empty)).unsafeRunSync()
      val repo = stubRepo(Map("sync-delay" -> "20000"))
      RuntimeVinylDNSConfig.reloadWithDiff(repo).unsafeRunSync()

      // Same repo again — memory now matches DB
      val diff2 = RuntimeVinylDNSConfig.reloadWithDiff(repo).unsafeRunSync()
      diff2 shouldBe empty
    }
  }

  "Boot: system <- IO(ActorSystem(VinylDNS, RuntimeVinylDNSConfig.getRaw))" should {

    "create an ActorSystem from getRaw without throwing" in {
      val system = ActorSystem("VinylDNSTest", RuntimeVinylDNSConfig.getRaw)
      try {
        system should not be null
        system.name shouldBe "VinylDNSTest"
      } finally {
        Await.result(system.terminate(), 10.seconds)
      }
    }

    "produce an ActorSystem whose config reflects the vinyldns namespace" in {
      val system = ActorSystem("VinylDNSConfigCheck", RuntimeVinylDNSConfig.getRaw)
      try {
        system.settings.config.hasPath("vinyldns") shouldBe true
      } finally {
        Await.result(system.terminate(), 10.seconds)
      }
    }
  }
}
