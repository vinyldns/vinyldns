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
      // Value equality on the config fields we care about
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
      // currentIO and current must agree after concurrent reloads complete
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
