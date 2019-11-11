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

package vinyldns.api.domain.zone

import org.scalatest.{Matchers, WordSpec}
import org.xbill.DNS.ZoneTransferException
import vinyldns.core.domain.zone.{Zone, ZoneConnection}

class ZoneViewLoaderIntegrationSpec extends WordSpec with Matchers {
  "ZoneViewLoader" should {
    "return a ZoneView upon success" in {
      DnsZoneViewLoader(Zone("vinyldns.", "test@test.com"))
        .load()
        .unsafeRunSync() shouldBe a[ZoneView]
    }

    "return a failure if the transfer connection is bad" in {
      assertThrows[IllegalArgumentException](
        DnsZoneViewLoader(
          Zone("vinyldns.", "bad@transfer.connection")
            .copy(
              transferConnection =
                Some(ZoneConnection("invalid-connection.", "bad-key", "invalid-key", "10.1.1.1"))
            )
        ).load()
          .unsafeRunSync()
      )
    }

    "return a failure if the zone doesn't exist in the DNS backend" in {
      assertThrows[ZoneTransferException](
        DnsZoneViewLoader(Zone("non-existent-zone", "bad@zone.test"))
          .load()
          .unsafeRunSync()
      )
    }

    "return a failure if the zone is larger than the max zone size" in {
      assertThrows[ZoneTooLargeError](
        DnsZoneViewLoader(Zone("vinyldns.", "test@test.com"), DnsZoneViewLoader.dnsZoneTransfer, 1)
          .load()
          .unsafeRunSync()
      )
    }
  }
}
