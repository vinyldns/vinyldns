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

package vinyldns.api.backend.dns

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.backend.dns.DnsProtocol.NoError
import vinyldns.core.crypto.NoOpCrypto
import vinyldns.core.domain.record.{
  AData,
  RecordSet,
  RecordSetChange,
  RecordSetChangeType,
  RecordSetStatus,
  RecordType
}
import vinyldns.core.domain.zone.{Algorithm, Zone, ZoneConnection}

class DnsBackendIntegrationSpec extends AnyWordSpec with Matchers {

  private val testConnection = ZoneConnection(
    "vinyldns.",
    "vinyldns.",
    "nzisn+4G2ldMn0q1CV3vsg==",
    sys.env.getOrElse("DEFAULT_DNS_ADDRESS", "127.0.0.1:19001"),
    Algorithm.HMAC_MD5
  )

  "DNSBackend" should {
    "connect to a zone without a tsig key for transfer or update" in {

      val config =
        DnsBackendConfig(
          "test",
          testConnection,
          Some(testConnection),
          DnsTsigUsage.Never
        )

      val backend = DnsBackend(
        "test",
        config.zoneConnection,
        config.transferConnection,
        NoOpCrypto.instance,
        config.tsigUsage
      )
      val testZone = Zone(
        "open1.",
        "test@test.com",
        connection = Some(testConnection)
      )

      val records = backend.loadZone(testZone, 100).unsafeRunSync()
      records should not be empty

      val testRs = RecordSet(
        testZone.id,
        "ok",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        None,
        List(AData("10.1.1.1"))
      )
      val update = backend
        .addRecord(
          RecordSetChange.apply(
            testZone,
            testRs,
            "user",
            RecordSetChangeType.Create
          )
        )
        .unsafeRunSync()

      update shouldBe a[NoError]
    }
  }
}
