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

import java.net.InetAddress
import java.time.temporal.ChronoUnit
import java.time.Instant
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import org.scalatestplus.mockito.MockitoSugar
import org.xbill.DNS
import vinyldns.api.backend.dns.DnsProtocol._
import vinyldns.core.TestRecordSetData.ds
import vinyldns.core.domain.Fqdn
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.Zone

import scala.collection.JavaConverters._

class DnsConversionsSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with DnsConversions
    with EitherValues {

  private val testZoneName = "vinyldns."
  private val testZone = Zone(testZoneName, "test@test.com")
  private val testZoneDnsName = new DNS.Name(testZoneName)
  private val testA = RecordSet(
    testZone.id,
    "a-record",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )
  private val testAMultiple = RecordSet(
    testZone.id,
    "a-record",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("1.1.1.1"), AData("2.2.2.2"))
  )
  private val testAAAA = RecordSet(
    testZone.id,
    "aaaa-record",
    RecordType.AAAA,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AAAAData("2001:db8:0:0:0:0:0:3"))
  )
  private val testCNAME = RecordSet(
    testZone.id,
    "cname-record",
    RecordType.CNAME,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(CNAMEData(Fqdn("cname.foo.vinyldns.")))
  )
  private val testMX = RecordSet(
    testZone.id,
    "mx-record",
    RecordType.MX,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(MXData(100, Fqdn("exchange.vinyldns.")))
  )
  private val testNS = RecordSet(
    testZone.id,
    "ns-record",
    RecordType.NS,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(NSData(Fqdn("nsdname.vinyldns.")))
  )
  private val testPTR = RecordSet(
    testZone.id,
    "ptr-record",
    RecordType.PTR,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(PTRData(Fqdn("ptr.vinyldns.")))
  )
  private val testSOA = RecordSet(
    testZone.id,
    "soa-record",
    RecordType.SOA,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(SOAData(Fqdn("mname.vinyldns."), "rname.vinyldns.", 1L, 2L, 3L, 4L, 5L))
  )
  private val testSPF = RecordSet(
    testZone.id,
    "spf-record",
    RecordType.SPF,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(SPFData("spf"))
  )
  private val testLongSPF = RecordSet(
    testZone.id,
    "long-spf-record",
    RecordType.SPF,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(SPFData("s" * 256))
  )
  private val testSRV = RecordSet(
    testZone.id,
    "srv-record",
    RecordType.SRV,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(SRVData(1, 2, 3, Fqdn("target.vinyldns.")))
  )
  private val testNAPTR = RecordSet(
    testZone.id,
    "naptr-record",
    RecordType.NAPTR,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(NAPTRData(1, 2, "U", "E2U+sip", "!.*!test.!", Fqdn("target.vinyldns.")))
  )
  private val testSSHFP = RecordSet(
    testZone.id,
    "sshfp-record",
    RecordType.SSHFP,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(SSHFPData(2, 1, "123456789ABCDEF67890123456789ABCDEF67890"))
  )
  private val testTXT = RecordSet(
    testZone.id,
    "txt-record",
    RecordType.TXT,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(TXTData("text"))
  )
  private val testLongTXT = RecordSet(
    testZone.id,
    "long-txt-record",
    RecordType.TXT,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(TXTData("a" * 64763))
  )
  private val testAt = RecordSet(
    testZone.id,
    "vinyldns.",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )
  private val testDS = ds.copy(zoneId = testZone.id)

  private val testDnsUnknown = DNS.Record.newRecord(
    DNS.Name.fromString("unknown-record."),
    DNS.Type.AFSDB,
    DNS.DClass.IN,
    200L
  )
  private val testDnsA = new DNS.ARecord(
    DNS.Name.fromString("a-record."),
    DNS.DClass.IN,
    200L,
    InetAddress.getByName("10.1.1.1")
  )
  private val testDnsA1 = new DNS.ARecord(
    DNS.Name.fromString("a-record."),
    DNS.DClass.IN,
    200L,
    InetAddress.getByName("1.1.1.1")
  )
  private val testDnsA2 = new DNS.ARecord(
    DNS.Name.fromString("a-record."),
    DNS.DClass.IN,
    200L,
    InetAddress.getByName("2.2.2.2")
  )
  private val testDnsAReplace = new DNS.ARecord(
    DNS.Name.fromString("a-record-2."),
    DNS.DClass.IN,
    200L,
    InetAddress.getByName("2.2.2.2")
  )
  private val testDnsAAAA = new DNS.AAAARecord(
    DNS.Name.fromString("aaaa-record."),
    DNS.DClass.IN,
    200L,
    InetAddress.getByName("2001:db8::3")
  )

  private val testDnsAt = new DNS.ARecord(
    DNS.Name.fromString("@", DNS.Name.fromString("vinyldns.")),
    DNS.DClass.IN,
    200L,
    InetAddress.getByName("10.1.1.1")
  )

  private val testDnsARRset = new DNS.RRset()
  testDnsARRset.addRR(testDnsA1)
  testDnsARRset.addRR(testDnsA2)

  private val mockMessage = mock[DNS.Message]

  private def rrset(record: DNS.Record): DNS.RRset = new DNS.RRset(record)

  private def verifyMatch(expected: RecordSet, actual: RecordSet) = {
    actual.name shouldBe expected.name
    actual.ttl shouldBe expected.ttl
    actual.typ shouldBe expected.typ
    actual.records should contain theSameElementsAs expected.records
  }

  private def verifyMatch(rrset: DNS.RRset, rs: RecordSet): Unit = {
    rrset.getName shouldBe recordDnsName(rs.name, testZoneName)
    rrset.getTTL shouldBe rs.ttl
    rrset.getType shouldBe toDnsRecordType(rs.typ)

    val records = rrset.rrs().asScala.map(_.asInstanceOf[DNS.Record]).toList
    val record = records.head
    record.getName shouldBe recordDnsName(rs.name, testZoneName)
    record.getTTL shouldBe rs.ttl
    record.getType shouldBe toDnsRecordType(rs.typ)

    // Convert the DNS records to their appropriate VinylDNS RecordData
    val convertedRecords = records.flatMap(toRecordSet(_, testZoneDnsName, "id").records)
    convertedRecords should contain theSameElementsAs rs.records
  }

  private def roundTrip(rs: RecordSet): RecordSet = {
    val recordList = toDnsRecords(rs, testZoneName).right.value.map(toRecordSet(_, testZoneDnsName))
    recordList.head.copy(records = recordList.flatMap(_.records))
  }

  override protected def beforeEach(): Unit = {
    doReturn(mockMessage).when(mockMessage).clone()
    doReturn(new java.util.ArrayList[DNS.Record]()).when(mockMessage).getSection(DNS.Section.ADDITIONAL)
  }

  "Collapsing multiple records to record sets" should {
    "combine records for the same dns record" in {
      val result = toFlattenedRecordSets(List(testDnsA1, testDnsA2), testZoneDnsName)

      result.length shouldBe 1
      result.head should have(
        'name ("a-record."),
        'typ (RecordType.A),
        'ttl (200L),
        'records (List(AData("1.1.1.1"), AData("2.2.2.2")))
      )
    }

    "combine records for different dns records" in {
      val result = toFlattenedRecordSets(List(testDnsA1, testDnsA2, testDnsAAAA), testZoneDnsName)

      result.length shouldBe 2

      val convertedA = result.find(_.typ == RecordType.A).get
      convertedA.name shouldBe "a-record."
      convertedA.typ shouldBe RecordType.A
      convertedA.ttl shouldBe 200L

      val convertedAAAA = result.find(_.typ == RecordType.AAAA).get
      convertedAAAA.name shouldBe "aaaa-record."
      convertedAAAA.typ shouldBe RecordType.AAAA
      convertedAAAA.ttl shouldBe 200L
    }
  }

  "Converting to a DNS RRset" should {
    "convert A record set" in {
      val result = toDnsRRset(testA, testZoneName).right.value

      verifyMatch(result, testA)
    }

    "convert multiple record set" in {
      val result = toDnsRRset(testAMultiple, testZoneName).right.value
      verifyMatch(result, testAMultiple)
    }

    "convert AAAA record set" in {
      val result = toDnsRRset(testAAAA, testZoneName).right.value
      verifyMatch(result, testAAAA)
    }

    "convert CNAME record set" in {
      val result = toDnsRRset(testCNAME, testZoneName).right.value
      verifyMatch(result, testCNAME)
    }

    "convert DS record set" in {
      val result = toDnsRRset(testDS, testZoneName).right.value
      verifyMatch(result, testDS)
    }

    "convert MX record set" in {
      val result = toDnsRRset(testMX, testZoneName).right.value
      verifyMatch(result, testMX)
    }

    "convert NS record set" in {
      val result = toDnsRRset(testNS, testZoneName).right.value
      verifyMatch(result, testNS)
    }

    "convert PTR record set" in {
      val result = toDnsRRset(testPTR, testZoneName).right.value
      verifyMatch(result, testPTR)
    }

    "convert SOA record set" in {
      val result = toDnsRRset(testSOA, testZoneName).right.value
      verifyMatch(result, testSOA)
    }

    "convert SPF record set" in {
      val result = toDnsRRset(testSPF, testZoneName).right.value
      verifyMatch(result, testSPF)
    }

    "convert SSHFP record set" in {
      val result = toDnsRRset(testSSHFP, testZoneName).right.value
      verifyMatch(result, testSSHFP)
    }

    "convert SRV record set" in {
      val result = toDnsRRset(testSRV, testZoneName).right.value
      verifyMatch(result, testSRV)
    }

    "convert NAPTR record set" in {
      val result = toDnsRRset(testNAPTR, testZoneName).right.value
      verifyMatch(result, testNAPTR)
    }

    "convert TXT record set" in {
      val result = toDnsRRset(testTXT, testZoneName).right.value
      verifyMatch(result, testTXT)
    }

    "convert long TXT record set" in {
      val result = toDnsRRset(testLongTXT, testZoneName).right.value
      verifyMatch(result, testLongTXT)
    }

    "convert long SPF record set" in {
      val result = toDnsRRset(testLongSPF, testZoneName).right.value
      verifyMatch(result, testLongSPF)
    }
  }

  "Converting to a Dns Response" should {
    "return the message when NoError" in {
      doReturn(DNS.Rcode.NOERROR).when(mockMessage).getRcode
      toDnsResponse(mockMessage).right.value shouldBe NoError(mockMessage)
    }
    "return a BadKey" in {
      doReturn(DNS.Rcode.BADKEY).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[BadKey]
    }
    "return a BadMode" in {
      doReturn(DNS.Rcode.BADMODE).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[BadMode]
    }
    "return a BadSig" in {
      doReturn(DNS.Rcode.BADSIG).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[BadSig]
    }
    "return a BadTime" in {
      doReturn(DNS.Rcode.BADTIME).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[BadTime]
    }
    "return a FormatError" in {
      doReturn(DNS.Rcode.FORMERR).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[FormatError]
    }
    "return a NotAuthorized" in {
      doReturn(DNS.Rcode.NOTAUTH).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[NotAuthorized]
    }
    "return a NotImplemented" in {
      doReturn(DNS.Rcode.NOTIMP).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[NotImplemented]
    }
    "return a NotZone" in {
      doReturn(DNS.Rcode.NOTZONE).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[NotZone]
    }
    "return a NameNotFound" in {
      doReturn(DNS.Rcode.NXDOMAIN).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[NameNotFound]
    }
    "return a RecordSetNotFound" in {
      doReturn(DNS.Rcode.NXRRSET).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[RecordSetNotFound]
    }
    "return a Refused" in {
      doReturn(DNS.Rcode.REFUSED).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[Refused]
    }
    "return a ServerFailure" in {
      doReturn(DNS.Rcode.SERVFAIL).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[ServerFailure]
    }
    "return a NameExists" in {
      doReturn(DNS.Rcode.YXDOMAIN).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[NameExists]
    }
    "return a RecordSetExists" in {
      doReturn(DNS.Rcode.YXRRSET).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[RecordSetExists]
    }
    "return a UnrecognizedResponse" in {
      doReturn(999).when(mockMessage).getRcode
      toDnsResponse(mockMessage).left.value shouldBe a[UnrecognizedResponse]
    }
  }

  "Converting to/from a recordset" should {
    "convert to/from RecordType A" in {
      verifyMatch(testA, roundTrip(testA))
    }
    "convert to/from RecordType AAAA" in {
      verifyMatch(testAAAA, roundTrip(testAAAA))
    }
    "convert to/from RecordType CNAME" in {
      verifyMatch(testCNAME, roundTrip(testCNAME))
    }
    "convert to/from RecordType DS" in {
      verifyMatch(testDS, roundTrip(testDS))
    }
    "convert to/from RecordType MX" in {
      verifyMatch(testMX, roundTrip(testMX))
    }
    "convert to/from RecordType NS" in {
      verifyMatch(testNS, roundTrip(testNS))
    }
    "convert to/from RecordType PTR" in {
      verifyMatch(testPTR, roundTrip(testPTR))
    }
    "convert to/from RecordType SOA" in {
      verifyMatch(testSOA, roundTrip(testSOA))
    }
    "convert to/from RecordType SPF" in {
      verifyMatch(testSPF, roundTrip(testSPF))
    }
    "convert to/from RecordType SRV" in {
      verifyMatch(testSRV, roundTrip(testSRV))
    }
    "convert to/from RecordType NAPTR" in {
      verifyMatch(testNAPTR, roundTrip(testNAPTR))
    }
    "convert to/from RecordType SSHFP" in {
      verifyMatch(testSSHFP, roundTrip(testSSHFP))
    }
    "convert to/from RecordType TXT" in {
      verifyMatch(testTXT, roundTrip(testTXT))
    }
    "convert to/from RecordType TXT long TXT record data" in {
      verifyMatch(testLongTXT, roundTrip(testLongTXT))
    }
    "convert to/from RecordType SPF long SPF record data" in {
      verifyMatch(testLongSPF, roundTrip(testLongSPF))
    }
  }

  "Converting to DNS RecordType" should {
    "support A" in {
      toDnsRecordType(RecordType.A) shouldBe DNS.Type.A
    }
    "support AAAA" in {
      toDnsRecordType(RecordType.AAAA) shouldBe DNS.Type.AAAA
    }
    "support CNAME" in {
      toDnsRecordType(RecordType.CNAME) shouldBe DNS.Type.CNAME
    }
    "support DS" in {
      toDnsRecordType(RecordType.DS) shouldBe DNS.Type.DS
    }
    "support MX" in {
      toDnsRecordType(RecordType.MX) shouldBe DNS.Type.MX
    }
    "support NS" in {
      toDnsRecordType(RecordType.NS) shouldBe DNS.Type.NS
    }
    "support PTR" in {
      toDnsRecordType(RecordType.PTR) shouldBe DNS.Type.PTR
    }
    "support SOA" in {
      toDnsRecordType(RecordType.SOA) shouldBe DNS.Type.SOA
    }
    "support SPF" in {
      toDnsRecordType(RecordType.SPF) shouldBe DNS.Type.SPF
    }
    "support SSHFP" in {
      toDnsRecordType(RecordType.SSHFP) shouldBe DNS.Type.SSHFP
    }
    "support SRV" in {
      toDnsRecordType(RecordType.SRV) shouldBe DNS.Type.SRV
    }
    "support NAPTR" in {
      toDnsRecordType(RecordType.NAPTR) shouldBe DNS.Type.NAPTR
    }
    "support TXT" in {
      toDnsRecordType(RecordType.TXT) shouldBe DNS.Type.TXT
    }
  }

  "Converting from a DNS record" should {
    "convert from an unknown record type" in {
      val result = toRecordSet(testDnsUnknown, testZoneDnsName)
      result.name shouldBe testDnsUnknown.getName.toString
      result.ttl shouldBe testDnsUnknown.getTTL
      result.typ shouldBe RecordType.UNKNOWN
      result.records shouldBe empty
      result.zoneId shouldBe "unknown"
    }
  }

  "Converting to an update message" should {
    "work for an Add message" in {
      val dnsMessage = toAddRecordMessage(rrset(testDnsA), testZoneName).right.value
      val dnsRecord = dnsMessage.getSection(DNS.Section.UPDATE).asScala.head
      dnsRecord.getName.toString shouldBe "a-record."
      dnsRecord.getTTL shouldBe testA.ttl
      dnsRecord.getType shouldBe DNS.Type.A
      dnsRecord shouldBe a[DNS.ARecord]
      dnsRecord.asInstanceOf[DNS.ARecord].getAddress.getHostAddress shouldBe "10.1.1.1"

      val zoneRRset = dnsMessage.getSectionRRsets(DNS.Section.ZONE).asScala.head
      zoneRRset.getName.toString shouldBe "vinyldns."
    }
    "work for an Update message" in {
      val dnsMessage =
        toUpdateRecordMessage(rrset(testDnsA), rrset(testDnsAReplace), testZoneName).right.value
      // Update record issues a replace, the first section is an EmptyRecord containing the name and type to replace
      val emptyRecord = dnsMessage.getSection(DNS.Section.UPDATE).asScala.head
      emptyRecord.getName.toString shouldBe "a-record-2."
      emptyRecord.getType shouldBe DNS.Type.A
      emptyRecord.getDClass shouldBe DNS.DClass.ANY

      // The second section in the replace is the data that is being passed in, this is different than an add
      val dnsRecord = dnsMessage.getSection(DNS.Section.UPDATE).asScala(1)
      dnsRecord.getName.toString shouldBe "a-record."
      dnsRecord.getTTL shouldBe testA.ttl
      dnsRecord.getType shouldBe DNS.Type.A
      dnsRecord shouldBe a[DNS.ARecord]
      dnsRecord.asInstanceOf[DNS.ARecord].getAddress.getHostAddress shouldBe "10.1.1.1"

      val zoneRRset = dnsMessage.getSectionRRsets(DNS.Section.ZONE).asScala.head
      zoneRRset.getName.toString shouldBe "vinyldns."
    }
    "work for a Delete message" in {
      val dnsMessage = toDeleteRecordMessage(rrset(testDnsA), testZoneName).right.value

      val dnsRecord = dnsMessage.getSection(DNS.Section.UPDATE).asScala.head
      dnsRecord.getName.toString shouldBe "a-record."
      dnsRecord.getType shouldBe DNS.Type.A
      dnsRecord.getTTL shouldBe 0
      dnsRecord.getDClass shouldBe DNS.DClass.ANY
      dnsRecord should not be a[DNS.ARecord]

      val zoneRRset = dnsMessage.getSectionRRsets(DNS.Section.ZONE).asScala.head
      zoneRRset.getName.toString shouldBe "vinyldns."
    }
  }

  "Converting a record set with @" should {
    "round trip to the same item" in {
      verifyMatch(testAt, roundTrip(testAt))
    }
    "convert @ to zone name" in {
      val actual = toRecordSet(testDnsAt, testZoneDnsName)
      actual.name shouldBe "vinyldns."
    }
    "convert zone name to @" in {
      val actual = toDnsRecords(testAt, testZoneName)
      val omitFinalDot = false
      actual.right.value.head.getName.toString(omitFinalDot) shouldBe testZoneName
    }
  }

  "removeStartingDomainNameLabel" should {
    "remove the first label from multi-label FQDN" in {
      getZoneFromNonApexFqdn("start.domain.name.") shouldBe "domain.name."
    }

    "return an empty string for a single-label FQDN" in {
      getZoneFromNonApexFqdn("a.") shouldBe ""
    }
  }

  "Relativize record name" should {
    "remove ending zone name from FQDN if relative" in {
      relativize("relative.zone.name.", "zone.name.") shouldBe "relative"
    }

    "return the FQDN if it matches the zone name" in {
      val input = "fully.qualified.match."
      relativize(input, input) shouldBe input
    }

    "returns the FQDN with trailing dot if it matches the zone name" in {
      relativize("almost.qualified.match", "almost.qualified.match.") shouldBe "almost.qualified.match."
    }

    "return name if fully relative" in {
      relativize("relativize", "zone.name.") shouldBe "relativize"
    }
  }

  "getIPv4FullReverseName" should {
    "convert a valid ip" in {
      getIPv4FullReverseName("1.2.3.4") shouldBe Some("4.3.2.1.in-addr.arpa.")
    }
    "return None with an invalid IP" in {
      getIPv4FullReverseName("999.2.3.4") shouldBe None
    }
  }
  "getIPv4NonDelegatedZoneName" should {
    "remove the 1st octet for a valid ip" in {
      getIPv4NonDelegatedZoneName("1.2.3.4") shouldBe Some("3.2.1.in-addr.arpa.")
    }
    "return None with an invalid IP" in {
      getIPv4NonDelegatedZoneName("999.2.3.4") shouldBe None
    }
  }
  "getIPv6FullReverseName" should {
    "convert a valid ip" in {
      val expectedName = "9.2.3.8.2.4.0.0.0.0.f.f.0.0.0.0.0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa."

      // these are all different forms of the same IP
      getIPv6FullReverseName("2001:0db8:0000:0000:0000:ff00:0042:8329") shouldBe Some(expectedName)
      getIPv6FullReverseName("2001:db8:0:0:0:ff00:42:8329") shouldBe Some(expectedName)
      getIPv6FullReverseName("2001:db8::ff00:42:8329") shouldBe Some(expectedName)
    }
    "return None with an invalid IP" in {
      getIPv6FullReverseName("2001::42::8329") shouldBe None
    }
  }
  "recordDnsName" should {
    "return just zone name if record name is @" in {
      val recordName = "@"
      val zoneName = "foo.bar."
      recordDnsName(recordName, zoneName).toString shouldBe zoneName
    }

    "return just zone name if record name is same as zone name" in {
      val nameWithDot = "foo.bar."
      val nameWithoutDot = "foo.bar"

      recordDnsName(nameWithDot, nameWithDot).toString() shouldBe nameWithDot
      recordDnsName(nameWithoutDot, nameWithoutDot).toString() shouldBe nameWithDot
      recordDnsName(nameWithDot, nameWithoutDot).toString() shouldBe nameWithDot
      recordDnsName(nameWithoutDot, nameWithDot).toString() shouldBe nameWithDot
    }
  }
  "getAllPossibleZones" should {
    "return the ordered zone options for a fqdn" in {
      val nameWithDot = "test.example.com."
      val nameWithoutDot = "test.example.com"
      val expected = List("test.example.com.", "example.com.", "com.")

      getAllPossibleZones(nameWithDot) shouldBe expected
      getAllPossibleZones(nameWithoutDot) shouldBe expected
    }
  }
}
