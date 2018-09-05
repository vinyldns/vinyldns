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

package vinyldns.api.domain.dns

import java.net.InetAddress

import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.xbill.DNS
import vinyldns.api.ResultHelpers
import vinyldns.api.domain.dns.DnsProtocol._
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.record._
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.zone.{Zone, ZoneConnection}

import scala.collection.JavaConverters._

class DnsConnectionSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with ResultHelpers
    with BeforeAndAfterEach {

  private val zoneConnection =
    ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1")
  private val testZone = Zone("vinyldns", "test@test.com")
  private val testA = RecordSet(
    testZone.id,
    "a-record",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AData("10.1.1.1")))

  private val testAMultiple = RecordSet(
    testZone.id,
    "a-record",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AData("1.1.1.1"), AData("2.2.2.2")))

  private val testDnsA = new DNS.ARecord(
    DNS.Name.fromString("a-record."),
    DNS.DClass.IN,
    200L,
    InetAddress.getByName("10.1.1.1"))

  private val mockResolver = mock[DNS.Resolver]
  private val mockMessage = mock[DNS.Message]
  private val messageCaptor = ArgumentCaptor.forClass(classOf[DNS.Message])
  private val mockDnsQuery = mock[DnsQuery]
  private val underTest = new DnsConnection(mockResolver) {
    override def toQuery(
        name: String,
        zoneName: String,
        typ: RecordType): Either[Throwable, DnsQuery] =
      Right(mockDnsQuery)
  }
  private val dnsQueryTest = new DnsConnection(mockResolver)

  override def beforeEach(): Unit = {
    doReturn(mockMessage).when(mockMessage).clone()
    doReturn(new Array[DNS.Record](0)).when(mockMessage).getSectionArray(DNS.Section.ADDITIONAL)
    doReturn(DNS.Rcode.NOERROR).when(mockMessage).getRcode
    doReturn(mockMessage).when(mockResolver).send(messageCaptor.capture())
    doReturn(DNS.Lookup.SUCCESSFUL).when(mockDnsQuery).result
    doReturn(List(testDnsA)).when(mockDnsQuery).run()
    doReturn(DNS.Name.fromString(s"${testZone.name}.")).when(mockDnsQuery).zoneName
  }

  private def addRsChange(zone: Zone = testZone, rs: RecordSet = testA) =
    RecordSetChange(zone, rs, "system", RecordSetChangeType.Create)

  private def deleteRsChange(zone: Zone = testZone, rs: RecordSet = testA) =
    RecordSetChange(zone, rs, "system", RecordSetChangeType.Delete)

  private def updateRsChange(zone: Zone = testZone, rs: RecordSet = testA) =
    RecordSetChange(zone, rs, "system", RecordSetChangeType.Update, updates = Some(rs))

  "DnsQuery" should {
    "be created in the origin dns server" in {
      val query = dnsQueryTest.toQuery("www", "vinyldns.", RecordType.A).toOption.get
      Option(query.lookup) shouldBe defined
    }
  }

  "Creating a Dns Connection" should {
    "decrypt the zone connection" in {
      val conn = spy(zoneConnection)
      DnsConnection(conn)

      verify(conn).decrypted(any[CryptoAlgebra])
    }

    "parse the port when specified on the primary server" in {
      val conn = zoneConnection.copy(primaryServer = "dns.comcast.net:19001")

      val dnsConn = DnsConnection(conn)
      val simpleResolver = dnsConn.resolver.asInstanceOf[DNS.SimpleResolver]

      val address = simpleResolver.getAddress

      address.getHostName shouldBe "dns.comcast.net"
      address.getPort shouldBe 19001
    }

    "use default port of 53 when not specified" in {
      val conn = zoneConnection.copy(primaryServer = "dns.comcast.net")

      val dnsConn = DnsConnection(conn)
      val simpleResolver = dnsConn.resolver.asInstanceOf[DNS.SimpleResolver]

      val address = simpleResolver.getAddress

      address.getHostName shouldBe "dns.comcast.net"
      address.getPort shouldBe 53
    }
  }

  "Resolving records" should {
    "return a single record when only one DNS record is returned" in {
      val records: List[RecordSet] =
        rightResultOf(underTest.resolve("www", "vinyldns.", RecordType.A).value)
      records.head should have(
        'name ("a-record."),
        'typ (RecordType.A),
        'ttl (200L),
        'records (List(AData("10.1.1.1")))
      )
    }
    "return multiple records when multiple DNS records are returned" in {
      val a1 = new DNS.ARecord(
        DNS.Name.fromString("a-record."),
        DNS.DClass.IN,
        200L,
        InetAddress.getByName("1.1.1.1"))
      val a2 = new DNS.ARecord(
        DNS.Name.fromString("a-record."),
        DNS.DClass.IN,
        200L,
        InetAddress.getByName("2.2.2.2"))
      doReturn(List(a1, a2)).when(mockDnsQuery).run()

      val records: List[RecordSet] =
        rightResultOf(underTest.resolve("www", "vinyldns.", RecordType.A).value)
      records.head should have(
        'name ("a-record."),
        'typ (RecordType.A),
        'ttl (200L),
        'records (List(AData("1.1.1.1"), AData("2.2.2.2")))
      )
    }
    "return an empty list when HOST NOT FOUND" in {
      doReturn(DNS.Lookup.HOST_NOT_FOUND).when(mockDnsQuery).result

      val records: List[RecordSet] =
        rightResultOf(underTest.resolve("www", "vinyldns.", RecordType.A).value)

      records shouldBe empty
    }
    "return an Uncrecoverable error" in {
      doReturn(DNS.Lookup.UNRECOVERABLE).when(mockDnsQuery).result

      val error = leftResultOf(underTest.resolve("www", "vinyldns.", RecordType.A).value)

      error shouldBe a[Unrecoverable]
    }
    "return a TryAgain error" in {
      doReturn("this is bad").when(mockDnsQuery).error
      doReturn(DNS.Lookup.TRY_AGAIN).when(mockDnsQuery).result

      val error = leftResultOf(underTest.resolve("www", "vinyldns.", RecordType.A).value)

      error shouldBe a[TryAgain]
    }
    "TypeNotFound should return an empty list" in {
      doReturn(DNS.Lookup.TYPE_NOT_FOUND).when(mockDnsQuery).result

      val result: List[RecordSet] =
        rightResultOf(underTest.resolve("www", "vinyldns.", RecordType.A).value)

      result shouldBe List()
    }
  }

  "Adding a recordset" should {
    "return an InvalidRecord error if there are no records present" in {
      val noRecords = testA.copy(records = Nil)

      val result = leftResultOf(underTest.addRecord(addRsChange(testZone, noRecords)).value)

      result shouldBe a[InvalidRecord]
    }
    "send an appropriate update message to the resolver" in {
      val change = addRsChange()

      val result: DnsResponse = rightResultOf(underTest.addRecord(change).value)

      val sentMessage = messageCaptor.getValue

      val dnsRecord = sentMessage.getSectionArray(DNS.Section.UPDATE)(0)
      dnsRecord.getName.toString shouldBe "a-record.vinyldns."
      dnsRecord.getTTL shouldBe testA.ttl
      dnsRecord.getType shouldBe DNS.Type.A
      dnsRecord shouldBe a[DNS.ARecord]
      dnsRecord.asInstanceOf[DNS.ARecord].getAddress.getHostAddress shouldBe "10.1.1.1"

      val zoneRRset = sentMessage.getSectionRRsets(DNS.Section.ZONE)(0)
      zoneRRset.getName.toString shouldBe "vinyldns."

      result shouldBe a[NoError]
    }
    "send an appropriate update message to the resolver when multiple record sets are present" in {
      val change = addRsChange(testZone, testAMultiple)

      val result: DnsResponse = rightResultOf(underTest.addRecord(change).value)

      val sentMessage = messageCaptor.getValue

      val rrset = sentMessage.getSectionRRsets(DNS.Section.UPDATE)(0)
      rrset.getName.toString shouldBe "a-record.vinyldns."
      rrset.getTTL shouldBe testA.ttl
      rrset.getType shouldBe DNS.Type.A

      // we should have 2 records for add
      val records =
        rrset.rrs().asScala.toList.map(_.asInstanceOf[DNS.ARecord].getAddress.getHostAddress)
      val expected = testAMultiple.records.map(_.asInstanceOf[AData].address)

      records should contain theSameElementsAs expected

      val zoneRRset = sentMessage.getSectionRRsets(DNS.Section.ZONE)(0)
      zoneRRset.getName.toString shouldBe "vinyldns."

      result shouldBe a[NoError]
    }
  }

  "Updating a recordset with a name or TTL change" should {
    "return an InvalidRecord error if there are no records present" in {
      val noRecords = testA.copy(records = Nil)

      val result = leftResultOf(underTest.updateRecord(updateRsChange(testZone, noRecords)).value)

      result shouldBe a[InvalidRecord]
    }
    "send an appropriate replace message to the resolver for a name change" in {
      val change = updateRsChange().copy(updates = Some(testA.copy(name = "updated-a-record")))

      val result: DnsResponse = rightResultOf(underTest.updateRecord(change).value)

      val sentMessage = messageCaptor.getValue

      // Update record issues a replace, the first section is an EmptyRecord containing the name and type to replace
      val emptyRecord = sentMessage.getSectionArray(DNS.Section.UPDATE)(0)
      emptyRecord.getName.toString shouldBe "updated-a-record.vinyldns."
      emptyRecord.getType shouldBe DNS.Type.A
      emptyRecord.getDClass shouldBe DNS.DClass.ANY

      // The second section in the replace is the data that is being passed in, this is different than an add
      val dnsRecord = sentMessage.getSectionArray(DNS.Section.UPDATE)(1)
      dnsRecord.getName.toString shouldBe "a-record.vinyldns."
      dnsRecord.getTTL shouldBe testA.ttl
      dnsRecord.getType shouldBe DNS.Type.A
      dnsRecord shouldBe a[DNS.ARecord]
      dnsRecord.asInstanceOf[DNS.ARecord].getAddress.getHostAddress shouldBe "10.1.1.1"

      val zoneRRset = sentMessage.getSectionRRsets(DNS.Section.ZONE)(0)
      zoneRRset.getName.toString shouldBe "vinyldns."

      result shouldBe a[NoError]
    }
    "send an appropriate replace message to the resolver for a TTL change" in {
      val change = updateRsChange(rs = testA.copy(ttl = 300)).copy(updates = Some(testA))

      val result: DnsResponse = rightResultOf(underTest.updateRecord(change).value)

      val sentMessage = messageCaptor.getValue

      // Update record issues a replace, the first section is an EmptyRecord containing the name and type to replace
      val emptyRecord = sentMessage.getSectionArray(DNS.Section.UPDATE)(0)
      emptyRecord.getName.toString shouldBe "a-record.vinyldns."
      emptyRecord.getType shouldBe DNS.Type.A
      emptyRecord.getDClass shouldBe DNS.DClass.ANY

      // The second section in the replace is the data that is being passed in, this is different than an add
      val dnsRecord = sentMessage.getSectionArray(DNS.Section.UPDATE)(1)
      dnsRecord.getName.toString shouldBe "a-record.vinyldns."
      dnsRecord.getTTL shouldBe 300
      dnsRecord.getType shouldBe DNS.Type.A
      dnsRecord shouldBe a[DNS.ARecord]
      dnsRecord.asInstanceOf[DNS.ARecord].getAddress.getHostAddress shouldBe "10.1.1.1"

      val zoneRRset = sentMessage.getSectionRRsets(DNS.Section.ZONE)(0)
      zoneRRset.getName.toString shouldBe "vinyldns."

      result shouldBe a[NoError]
    }
    "send an appropriate replace message in the event that the record being replaced is None" in {
      val change = updateRsChange().copy(updates = None)

      val result: DnsResponse = rightResultOf(underTest.updateRecord(change).value)

      val sentMessage = messageCaptor.getValue

      val emptyRecord = sentMessage.getSectionArray(DNS.Section.UPDATE)

      emptyRecord shouldBe empty
      result shouldBe a[NoError]
    }
    "send an appropriate replace message to the resolver for multiple records" in {
      val change = updateRsChange(testZone, testAMultiple).copy(
        updates = Some(testAMultiple.copy(name = "updated-a-record")))

      val result: DnsResponse = rightResultOf(underTest.updateRecord(change).value)

      val sentMessage = messageCaptor.getValue

      // Update record issues a replace, the first section is an EmptyRecord containing the name and type to replace
      val emptyRecord = sentMessage.getSectionArray(DNS.Section.UPDATE)(0)
      emptyRecord.getName.toString shouldBe "updated-a-record.vinyldns."
      emptyRecord.getType shouldBe DNS.Type.A
      emptyRecord.getDClass shouldBe DNS.DClass.ANY

      // The second section in the replace is the data that is being passed in, this is different than an add
      val dnsRecord1 = sentMessage.getSectionArray(DNS.Section.UPDATE)(1)
      dnsRecord1.getName.toString shouldBe "a-record.vinyldns."
      dnsRecord1.getTTL shouldBe testA.ttl
      dnsRecord1.getType shouldBe DNS.Type.A
      dnsRecord1 shouldBe a[DNS.ARecord]
      val dnsRecord1Data = dnsRecord1.asInstanceOf[DNS.ARecord].getAddress.getHostAddress
      List("1.1.1.1", "2.2.2.2") should contain(dnsRecord1Data)

      val dnsRecord2 = sentMessage.getSectionArray(DNS.Section.UPDATE)(2)
      dnsRecord2.getName.toString shouldBe "a-record.vinyldns."
      dnsRecord2.getTTL shouldBe testA.ttl
      dnsRecord2.getType shouldBe DNS.Type.A
      dnsRecord2 shouldBe a[DNS.ARecord]
      val dnsRecord2Data = dnsRecord1.asInstanceOf[DNS.ARecord].getAddress.getHostAddress
      List("1.1.1.1", "2.2.2.2") should contain(dnsRecord2Data)

      val zoneRRset = sentMessage.getSectionRRsets(DNS.Section.ZONE)(0)
      zoneRRset.getName.toString shouldBe "vinyldns."

      result shouldBe a[NoError]
    }
  }

  "Updating a recordset without a name change" should {
    "return an InvalidRecord error if there are no records present" in {
      val noRecords = testA.copy(records = Nil)

      val result = leftResultOf(underTest.updateRecord(updateRsChange(testZone, noRecords)).value)

      result shouldBe a[InvalidRecord]
    }
    "send a message with an empty body to the resolver when no changes have occurred" in {
      val change = updateRsChange().copy(updates = Some(testA))

      val result: DnsResponse = rightResultOf(underTest.updateRecord(change).value)

      val sentMessage = messageCaptor.getValue

      val emptyRecord = sentMessage.getSectionArray(DNS.Section.UPDATE)

      emptyRecord shouldBe empty
      result shouldBe a[NoError]
    }
    "send an appropriate replace message to the resolver" in {
      val change =
        updateRsChange().copy(updates = Some(testA.copy(records = List(AData("127.0.0.1")))))

      val result: DnsResponse = rightResultOf(underTest.updateRecord(change).value)

      val sentMessage = messageCaptor.getValue

      // A NONE update is sent for each DNS record that is getting deleted
      val emptyRecord = sentMessage.getSectionArray(DNS.Section.UPDATE)(0)
      emptyRecord.getName.toString shouldBe "a-record.vinyldns."
      emptyRecord.getType shouldBe DNS.Type.A
      emptyRecord.getDClass shouldBe DNS.DClass.NONE

      // The second section in the replace is the data that is being passed in, this is different than an add
      val dnsRecord = sentMessage.getSectionArray(DNS.Section.UPDATE)(1)
      dnsRecord.getName.toString shouldBe "a-record.vinyldns."
      dnsRecord.getTTL shouldBe testA.ttl
      dnsRecord.getType shouldBe DNS.Type.A
      dnsRecord shouldBe a[DNS.ARecord]
      dnsRecord.asInstanceOf[DNS.ARecord].getAddress.getHostAddress shouldBe "10.1.1.1"

      val zoneRRset = sentMessage.getSectionRRsets(DNS.Section.ZONE)(0)
      zoneRRset.getName.toString shouldBe "vinyldns."

      result shouldBe a[NoError]
    }
    "send an appropriate replace message in the event that the record being replaced is None" in {
      val change = updateRsChange().copy(updates = None)

      val result: DnsResponse = rightResultOf(underTest.updateRecord(change).value)

      val sentMessage = messageCaptor.getValue

      val emptyRecord = sentMessage.getSectionArray(DNS.Section.UPDATE)

      emptyRecord shouldBe empty
      result shouldBe a[NoError]
    }
    "send an appropriate replace message to the resolver for multiple records" in {
      val change = updateRsChange(testZone, testAMultiple).copy(
        updates = Some(testAMultiple.copy(records = List(AData("4.4.4.4"), AData("3.3.3.3")))))

      val result: DnsResponse = rightResultOf(underTest.updateRecord(change).value)

      val sentMessage = messageCaptor.getValue

      // A NONE update is sent for each DNS record that is getting deleted
      val deleteRecord1 = sentMessage.getSectionArray(DNS.Section.UPDATE)(0)
      deleteRecord1.getName.toString shouldBe "a-record.vinyldns."
      deleteRecord1.getType shouldBe DNS.Type.A
      deleteRecord1.getDClass shouldBe DNS.DClass.NONE
      val deleteRecord1Data = deleteRecord1.asInstanceOf[DNS.ARecord].getAddress.getHostAddress
      List("4.4.4.4", "3.3.3.3") should contain(deleteRecord1Data)

      val deleteRecord2 = sentMessage.getSectionArray(DNS.Section.UPDATE)(1)
      deleteRecord2.getName.toString shouldBe "a-record.vinyldns."
      deleteRecord2.getType shouldBe DNS.Type.A
      deleteRecord2.getDClass shouldBe DNS.DClass.NONE
      val deleteRecord2Data = deleteRecord1.asInstanceOf[DNS.ARecord].getAddress.getHostAddress
      List("4.4.4.4", "3.3.3.3") should contain(deleteRecord2Data)

      // The second section in the replace is the data that is being passed in, this is different than an add
      val dnsRecord1 = sentMessage.getSectionArray(DNS.Section.UPDATE)(2)
      dnsRecord1.getName.toString shouldBe "a-record.vinyldns."
      dnsRecord1.getTTL shouldBe testA.ttl
      dnsRecord1.getType shouldBe DNS.Type.A
      dnsRecord1 shouldBe a[DNS.ARecord]
      val dnsRecord1Data = dnsRecord1.asInstanceOf[DNS.ARecord].getAddress.getHostAddress
      List("1.1.1.1", "2.2.2.2") should contain(dnsRecord1Data)

      val dnsRecord2 = sentMessage.getSectionArray(DNS.Section.UPDATE)(3)
      dnsRecord2.getName.toString shouldBe "a-record.vinyldns."
      dnsRecord2.getTTL shouldBe testA.ttl
      dnsRecord2.getType shouldBe DNS.Type.A
      dnsRecord2 shouldBe a[DNS.ARecord]
      val dnsRecord2Data = dnsRecord1.asInstanceOf[DNS.ARecord].getAddress.getHostAddress
      List("1.1.1.1", "2.2.2.2") should contain(dnsRecord2Data)

      val zoneRRset = sentMessage.getSectionRRsets(DNS.Section.ZONE)(0)
      zoneRRset.getName.toString shouldBe "vinyldns."

      result shouldBe a[NoError]
    }
  }

  "Deleting a recordset" should {
    "return an InvalidRecord error if there are no records present in the delete" in {
      val noRecords = testA.copy(records = Nil)

      val result = leftResultOf(underTest.updateRecord(deleteRsChange(testZone, noRecords)).value)

      result shouldBe a[InvalidRecord]
    }
    "send an appropriate delete message to the resolver" in {
      val change = deleteRsChange()

      val result: DnsResponse = rightResultOf(underTest.deleteRecord(change).value)

      val sentMessage = messageCaptor.getValue

      val dnsRecord = sentMessage.getSectionArray(DNS.Section.UPDATE)(0)
      dnsRecord.getName.toString shouldBe "a-record.vinyldns."
      dnsRecord.getType shouldBe DNS.Type.A
      dnsRecord.getTTL shouldBe 0
      dnsRecord.getDClass shouldBe DNS.DClass.ANY
      dnsRecord should not be a[DNS.ARecord]

      val zoneRRset = sentMessage.getSectionRRsets(DNS.Section.ZONE)(0)
      zoneRRset.getName.toString shouldBe "vinyldns."

      result shouldBe a[NoError]
    }
    "send an appropriate delete message to the resolver for multiple records" in {
      val change = deleteRsChange(testZone, testAMultiple)

      val result: DnsResponse = rightResultOf(underTest.deleteRecord(change).value)

      val sentMessage = messageCaptor.getValue

      val dnsRecord1 = sentMessage.getSectionArray(DNS.Section.UPDATE)(0)
      dnsRecord1.getName.toString shouldBe "a-record.vinyldns."
      dnsRecord1.getType shouldBe DNS.Type.A
      dnsRecord1.getTTL shouldBe 0
      dnsRecord1.getDClass shouldBe DNS.DClass.ANY
      dnsRecord1 should not be a[DNS.ARecord]

      val zoneRRset = sentMessage.getSectionRRsets(DNS.Section.ZONE)(0)
      zoneRRset.getName.toString shouldBe "vinyldns."

      result shouldBe a[NoError]
    }
  }
}
