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

package vinyldns.core.domain.zone

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ConfiguredDnsConnectionsSpec extends AnyWordSpec with Matchers {

  private val cryptoConfig = ConfigFactory.parseString(
    """{ type = "vinyldns.core.crypto.NoOpCrypto" }"""
  )

  "ConfiguredDnsConnections.load" should {

    "load defaultZoneConnection when provided" in {
      val config = ConfiguredDnsConnections.load(
        ConfigFactory.parseString(
          """{
            |  vinyldns.defaultZoneConnection {
            |    name          = "vinyldns."
            |    keyName       = "vinyldns."
            |    key           = "nzisn+4G2ldMn0q1CV3vsg=="
            |    primaryServer = "127.0.0.1:19001"
            |  }
            |  vinyldns.defaultTransferConnection {
            |    name          = "vinyldns."
            |    keyName       = "vinyldns."
            |    key           = "nzisn+4G2ldMn0q1CV3vsg=="
            |    primaryServer = "127.0.0.1:19001"
            |  }
            |}""".stripMargin
        ),
        cryptoConfig
      ).unsafeRunSync()

      config.defaultZoneConnection.name     shouldBe "vinyldns."
      config.defaultZoneConnection.keyName  shouldBe "vinyldns."
      config.defaultZoneConnection.primaryServer shouldBe "127.0.0.1:19001"
    }

    "default to empty ZoneConnection when defaultZoneConnection is absent" in {
      val config = ConfiguredDnsConnections.load(
        ConfigFactory.empty(),
        cryptoConfig
      ).unsafeRunSync()

      config.defaultZoneConnection.name         shouldBe ""
      config.defaultZoneConnection.keyName      shouldBe ""
      config.defaultZoneConnection.primaryServer shouldBe ""
    }

    "default to empty ZoneConnection when defaultTransferConnection is absent" in {
      val config = ConfiguredDnsConnections.load(
        ConfigFactory.empty(),
        cryptoConfig
      ).unsafeRunSync()

      config.defaultTransferConnection.name         shouldBe ""
      config.defaultTransferConnection.keyName      shouldBe ""
      config.defaultTransferConnection.primaryServer shouldBe ""
    }

    "load an empty dnsBackends list when vinyldns.backends is absent" in {
      val config = ConfiguredDnsConnections.load(
        ConfigFactory.empty(),
        cryptoConfig
      ).unsafeRunSync()

      config.dnsBackends shouldBe empty
    }
  }

  "RepositoriesConfig.forAll" should {
    import vinyldns.core.repository.{RepositoriesConfig, RepositoryName}

    "create a config that owns all given repo names" in {
      val names = List(RepositoryName.user, RepositoryName.group, RepositoryName.zone)
      val cfg   = RepositoriesConfig.forAll(names)

      cfg.hasKey(RepositoryName.user)  shouldBe true
      cfg.hasKey(RepositoryName.group) shouldBe true
      cfg.hasKey(RepositoryName.zone)  shouldBe true
      cfg.nonEmpty                     shouldBe true
    }

    "not include repos not in the given list" in {
      val cfg = RepositoriesConfig.forAll(List(RepositoryName.user))
      cfg.hasKey(RepositoryName.group)  shouldBe false
      cfg.hasKey(RepositoryName.zone)   shouldBe false
    }

    "return an empty configMap for an empty names list" in {
      val cfg = RepositoriesConfig.forAll(Nil)
      cfg.nonEmpty shouldBe false
    }
  }
}
