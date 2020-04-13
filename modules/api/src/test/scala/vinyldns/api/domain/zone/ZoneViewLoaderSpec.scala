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

import java.net.InetAddress

import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures.whenReady
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import org.xbill.DNS
import org.xbill.DNS.{Name, ZoneTransferIn}
import vinyldns.api.domain.dns.DnsConversions
import vinyldns.core.domain.record._

import scala.collection.JavaConverters._
import scala.collection.mutable
import cats.effect._
import vinyldns.core.domain.Fqdn
import vinyldns.core.domain.record.NameSort.NameSort
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.zone.{Zone, ZoneConnection, ZoneStatus}

class ZoneViewLoaderSpec extends WordSpec with Matchers with MockitoSugar with DnsConversions {
  val testZoneName = "vinyldns."

  val testZoneConnection: Option[ZoneConnection] = Some(
    ZoneConnection(testZoneName, testZoneName, "nzisn+4G2ldMn0q1CV3vsg==", "127.0.0.1:19001")
  )

  private val testZone = Zone("vinyldns.", "test@test.com")
  private val records = List(
    RecordSet(
      zoneId = testZone.id,
      name = "abc",
      typ = RecordType.A,
      ttl = 100,
      status = RecordSetStatus.Active,
      created = DateTime.now,
      records = List(AData("1.1.1.1"))
    ),
    RecordSet(
      zoneId = testZone.id,
      name = "abc",
      typ = RecordType.A,
      ttl = 100,
      status = RecordSetStatus.Active,
      created = DateTime.now,
      records = List(AData("2.2.2.2"))
    ),
    RecordSet(
      zoneId = testZone.id,
      name = "abc",
      typ = RecordType.AAAA,
      ttl = 100,
      status = RecordSetStatus.Active,
      created = DateTime.now,
      records = List(AAAAData("2001:db8:a0b:12f0::1"))
    ),
    RecordSet(
      zoneId = testZone.id,
      name = "def",
      typ = RecordType.A,
      ttl = 100,
      status = RecordSetStatus.Active,
      created = DateTime.now,
      records = List(AData("3.3.3.3"))
    )
  )

  "VinylDNSZoneViewLoader" should {
    "load the DNS Zones" in {
      val mockRecordSetRepo = mock[RecordSetRepository]

      doReturn(IO(ListRecordSetResults(records, None, None, None, None, None, None, NameSort.ASC)))
        .when(mockRecordSetRepo)
        .listRecordSets(
          any[Option[String]],
          any[Option[String]],
          any[Option[Int]],
          any[Option[String]],
          any[Option[Set[RecordType]]],
          any[Option[String]],
          any[NameSort]
        )

      val underTest = VinylDNSZoneViewLoader(testZone, mockRecordSetRepo)

      val expected = ZoneView(testZone, records)

      val actual = underTest.load().unsafeRunSync()

      actual shouldBe expected
    }
  }

  "DnsZoneViewLoader" should {
    "load the DNS zones" in {
      val testZone = Zone(
        testZoneName,
        "test@test.com",
        ZoneStatus.Active,
        connection = testZoneConnection,
        transferConnection = testZoneConnection
      )

      val mockTransfer = mock[ZoneTransferIn]

      val expectedRecords = List(
        RecordSet(
          zoneId = testZone.id,
          name = "abc",
          typ = RecordType.A,
          ttl = 38400,
          status = RecordSetStatus.Active,
          created = DateTime.now,
          records = List(AData("1.1.1.1"))
        ),
        RecordSet(
          zoneId = testZone.id,
          name = "abc",
          typ = RecordType.A,
          ttl = 38400,
          status = RecordSetStatus.Active,
          created = DateTime.now,
          records = List(AData("2.2.2.2"))
        ),
        RecordSet(
          zoneId = testZone.id,
          name = "abc",
          typ = RecordType.AAAA,
          ttl = 38400,
          status = RecordSetStatus.Active,
          created = DateTime.now,
          records = List(AAAAData(InetAddress.getByName("2001:db8:a0b:12f0::1").getHostAddress))
        ),
        RecordSet(
          zoneId = testZone.id,
          name = "def",
          typ = RecordType.A,
          ttl = 38400,
          status = RecordSetStatus.Active,
          created = DateTime.now,
          records = List(AData("3.3.3.3"))
        )
      )

      val dnsRecords = new mutable.ArrayBuffer[DNS.Record]()
      dnsRecords.append(
        new DNS.ARecord(
          new Name("abc.vinyldns."),
          DNS.DClass.IN,
          38400,
          InetAddress.getByName("1.1.1.1")
        )
      )
      dnsRecords.append(
        new DNS.ARecord(
          new Name("abc.vinyldns."),
          DNS.DClass.IN,
          38400,
          InetAddress.getByName("2.2.2.2")
        )
      )
      dnsRecords.append(
        new DNS.AAAARecord(
          new Name("abc.vinyldns."),
          DNS.DClass.IN,
          38400,
          InetAddress.getByName("2001:db8:a0b:12f0::1")
        )
      )
      dnsRecords.append(
        new DNS.ARecord(
          new Name("def.vinyldns."),
          DNS.DClass.IN,
          38400,
          InetAddress.getByName("3.3.3.3")
        )
      )

      doReturn(dnsRecords.asJava).when(mockTransfer).getAXFR

      val mockTransferFunc = mock[Zone => ZoneTransferIn]
      doReturn(mockTransfer).when(mockTransferFunc).apply(testZone)

      val underTest = DnsZoneViewLoader(testZone, mockTransferFunc)

      val actual = underTest.load().unsafeToFuture()

      val expected = ZoneView(testZone, expectedRecords)

      whenReady(actual) { result =>
        result.zone shouldBe testZone
        result.recordSetsMap.size shouldBe expected.recordSetsMap.size
        result.recordSetsMap.keySet.foreach { key =>
          val resultRecordSet = result.recordSetsMap(key)
          val expectedRecordSet = expected.recordSetsMap(key)

          resultRecordSet should have(
            'zoneId (expectedRecordSet.zoneId),
            'name (expectedRecordSet.name),
            'ttl (expectedRecordSet.ttl),
            'status (expectedRecordSet.status),
            'records (expectedRecordSet.records),
            'typ (expectedRecordSet.typ),
            'account (expectedRecordSet.account)
          )
        }
      }
    }
    "return distinct entries and drop unsupported record types" in {
      val testZone = Zone(
        testZoneName,
        "test@test.com",
        ZoneStatus.Active,
        connection = testZoneConnection,
        transferConnection = testZoneConnection
      )

      val mockTransfer = mock[ZoneTransferIn]

      val expectedRecords = List(
        RecordSet(
          zoneId = testZone.id,
          name = "abc",
          typ = RecordType.A,
          ttl = 38400,
          status = RecordSetStatus.Active,
          created = DateTime.now,
          records = List(AData("1.1.1.1"), AData("2.2.2.2"))
        ),
        RecordSet(
          zoneId = testZone.id,
          name = "vinyldns.",
          typ = RecordType.SOA,
          ttl = 38400,
          status = RecordSetStatus.Active,
          created = DateTime.now,
          records = List(
            SOAData(
              Fqdn("172.17.42.1."),
              "admin.vinyldns.com.",
              1439234395,
              10800,
              3600,
              604800,
              38400
            )
          )
        )
      )

      val dnsRecords = new mutable.ArrayBuffer[DNS.Record]()
      dnsRecords.append(
        new DNS.SOARecord(
          new Name("vinyldns."),
          DNS.DClass.IN,
          38400,
          new Name("172.17.42.1."),
          new Name("admin.vinyldns.com."),
          1439234395,
          10800,
          3600,
          604800,
          38400
        )
      )
      dnsRecords.append(
        new DNS.ARecord(
          new Name("abc.vinyldns."),
          DNS.DClass.IN,
          38400,
          InetAddress.getByName("1.1.1.1")
        )
      )
      dnsRecords.append(
        new DNS.ARecord(
          new Name("abc.vinyldns."),
          DNS.DClass.IN,
          38400,
          InetAddress.getByName("1.1.1.1")
        )
      )
      dnsRecords.append(
        new DNS.ARecord(
          new Name("abc.vinyldns."),
          DNS.DClass.IN,
          38400,
          InetAddress.getByName("1.1.1.1")
        )
      )
      dnsRecords.append(
        new DNS.ARecord(
          new Name("abc.vinyldns."),
          DNS.DClass.IN,
          38400,
          InetAddress.getByName("1.1.1.1")
        )
      )
      dnsRecords.append(
        new DNS.ARecord(
          new Name("abc.vinyldns."),
          DNS.DClass.IN,
          38400,
          InetAddress.getByName("2.2.2.2")
        )
      )
      dnsRecords.append(
        new DNS.SOARecord(
          new Name("vinyldns."),
          DNS.DClass.IN,
          38400,
          new Name("172.17.42.1."),
          new Name("admin.vinyldns.com."),
          1439234395,
          10800,
          3600,
          604800,
          38400
        )
      )
      dnsRecords.append(
        new DNS.NULLRecord(
          new Name("some.unsupported.record.type."),
          DNS.DClass.IN,
          38400,
          "some data".getBytes
        )
      )

      doReturn(dnsRecords.asJava).when(mockTransfer).getAXFR

      val mockTransferFunc = mock[Zone => ZoneTransferIn]
      doReturn(mockTransfer).when(mockTransferFunc).apply(testZone)

      val underTest = DnsZoneViewLoader(testZone, mockTransferFunc)

      val actual = underTest.load().unsafeToFuture()

      val expected = ZoneView(testZone, expectedRecords)

      whenReady(actual) { result =>
        result.zone shouldBe testZone
        result.recordSetsMap.size shouldBe expected.recordSetsMap.size
        result.recordSetsMap.keySet.foreach { key =>
          val resultRecordSet = result.recordSetsMap(key)
          val expectedRecordSet = expected.recordSetsMap(key)

          resultRecordSet should have(
            'zoneId (expectedRecordSet.zoneId),
            'name (expectedRecordSet.name),
            'ttl (expectedRecordSet.ttl),
            'status (expectedRecordSet.status),
            'records (expectedRecordSet.records),
            'typ (expectedRecordSet.typ),
            'account (expectedRecordSet.account)
          )
        }
      }
    }
  }
}
