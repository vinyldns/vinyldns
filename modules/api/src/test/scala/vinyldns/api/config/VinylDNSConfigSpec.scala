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

import cats.effect.{ContextShift, IO}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.backend.dns.DnsBackendProviderConfig
import vinyldns.core.domain.Encrypted
import vinyldns.core.domain.zone.ZoneConnection
import vinyldns.core.repository.RepositoryName._

class VinylDNSConfigSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val underTest: VinylDNSConfig = VinylDNSConfig.load().unsafeRunSync()
  private implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

  "VinylDNSConfig" should {
    "load the rest config" in {
      underTest.httpConfig.port shouldBe 9000
    }

    "properly load the datastore configs" in {
      (underTest.dataStoreConfigs should have).length(1L)
    }
    "assign the correct mysql repositories" in {
      val mysqlConfig =
        underTest.dataStoreConfigs
          .find(_.className == "vinyldns.mysql.repository.MySqlDataStoreProvider")
          .get

      mysqlConfig.repositories.keys should contain theSameElementsAs Set(
        zone,
        batchChange,
        user,
        recordSet,
        group,
        membership,
        groupChange,
        generateZone,
        zoneChange,
        recordChange,
        recordSetCache
      )
    }

    "properly load the notifier configs" in {
      val notifierConfigs = underTest.notifierConfigs
      notifierConfigs.length shouldBe 1

      notifierConfigs.head.className shouldBe "someclass"

      notifierConfigs.head.settings.getString("value").shouldBe("test")
    }

    "load specified backends" in {
      val zc = ZoneConnection("vinyldns.", "vinyldns.", Encrypted("nzisn+4G2ldMn0q1CV3vsg=="), sys.env.getOrElse("DEFAULT_DNS_ADDRESS", "127.0.0.1:19001"))
      val tc = zc.copy()

      val backends = underTest.backendConfigs.backendProviders
      backends.length shouldBe 1

      val config = DnsBackendProviderConfig.load(backends.head.settings).unsafeRunSync()
      config.backends.length shouldBe 2

      config.backends.head.id shouldBe "default"
      config.backends.head.zoneConnection.decrypted(underTest.crypto) shouldBe zc
      config.backends.head.transferConnection.get.decrypted(underTest.crypto) shouldBe tc
    }
  }
}
