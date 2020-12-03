package vinyldns.api.backend.dns

import org.joda.time.DateTime
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
    "test",
    "test",
    "nzisn+4G2ldMn0q1CV3vsg==",
    "127.0.0.1:19001",
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
        "open.",
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
        DateTime.now,
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
