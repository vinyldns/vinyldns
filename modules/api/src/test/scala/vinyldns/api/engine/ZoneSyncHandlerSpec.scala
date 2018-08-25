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

package vinyldns.api.engine

import cats.effect._
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{any, anyString}
import org.mockito.Mockito.{doReturn, reset, times, verify}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import vinyldns.api.VinylDNSTestData
import vinyldns.api.domain.record._
import vinyldns.api.domain.zone._

class ZoneSyncHandlerSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with VinylDNSTestData {

  private val mockDNSLoader = mock[DnsZoneViewLoader]
  private val mockVinylDNSLoader = mock[VinylDNSZoneViewLoader]
  private val recordSetRepo = mock[RecordSetRepository]
  private val recordChangeRepo = mock[RecordChangeRepository]
  private val zoneRepo = mock[ZoneRepository]
  private val zoneChangeRepo = mock[ZoneChangeRepository]

  private val dnsServer = "127.0.0.1"
  private val dnsPort = "19001"
  private val zoneName = "vinyldns."
  private val reverseZoneName = "30.172.in-addr.arpa."
  private val dnsKeyName = "vinyldns."
  private val dnsTsig = "nzisn+4G2ldMn0q1CV3vsg=="
  private val dnsServerAddress = s"$dnsServer:$dnsPort"

  private val testZone = Zone(
    zoneName,
    "test@test.com",
    ZoneStatus.Active,
    connection = Some(ZoneConnection(zoneName, dnsKeyName, dnsTsig, dnsServerAddress)),
    transferConnection = Some(ZoneConnection(zoneName, dnsKeyName, dnsTsig, dnsServerAddress))
  )

  private val testReverseZone = Zone(
    reverseZoneName,
    "test@test.com",
    ZoneStatus.Active,
    connection = Some(ZoneConnection(zoneName, dnsKeyName, dnsTsig, dnsServerAddress)),
    transferConnection = Some(ZoneConnection(zoneName, dnsKeyName, dnsTsig, dnsServerAddress))
  )

  private val testRecord1 = RecordSet(
    zoneId = testZone.id,
    name = "abc",
    typ = RecordType.A,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(AData("1.1.1.1")))
  private val testRecord2 = RecordSet(
    zoneId = testZone.id,
    name = "def",
    typ = RecordType.A,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(AData("2.2.2.2")))
  private val testRecordDotted = RecordSet(
    zoneId = testZone.id,
    name = "gh.i.",
    typ = RecordType.A,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(AData("3.3.3.3")))
  private val testRecordDottedOk = RecordSet(
    zoneId = testZone.id,
    name = s"ok-dotted.${testZone.name}",
    typ = RecordType.A,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(AData("4.4.4.4"))
  )
  private val testRecordUnknown = RecordSet(
    zoneId = testZone.id,
    name = "jkl",
    typ = RecordType.UNKNOWN,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List())

  private val testReversePTR = RecordSet(
    zoneId = testReverseZone.id,
    name = "33.33",
    typ = RecordType.PTR,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(PTRData("test.com."))
  )
  private val testReverseSOA = RecordSet(
    zoneId = testReverseZone.id,
    name = "30.172.in-addr.arpa.",
    typ = RecordType.SOA,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(SOAData("mname", "rname", 1439234395, 10800, 3600, 604800, 38400))
  )
  private val testReverseNS = RecordSet(
    zoneId = testReverseZone.id,
    name = "30.172.in-addr.arpa.",
    typ = RecordType.NS,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(NSData("172.17.42.1."))
  )

  private val testRecordSetChange = RecordSetChange.forSyncAdd(testRecord2, testZone)
  private val testChangeSet =
    ChangeSet.apply(testRecordSetChange).copy(status = ChangeSetStatus.Applied)
  private val testZoneChange = ZoneChange(testZone, testZone.account, ZoneChangeType.Sync)
  private val testDnsView = ZoneView(testZone, List(testRecord1, testRecord2))
  private val testVinylDNSView = ZoneView(testZone, List(testRecord1))

  override def beforeEach(): Unit = {
    reset(recordSetRepo)
    reset(recordChangeRepo)
    reset(zoneRepo)
    reset(zoneChangeRepo)
    reset(mockDNSLoader)
    reset(mockVinylDNSLoader)

    doReturn(IO(ListRecordSetResults(List(testRecord1))))
      .when(recordSetRepo)
      .listRecordSets(anyString(), any[Option[String]], any[Option[Int]], any[Option[String]])
    doReturn(IO(testChangeSet)).when(recordSetRepo).apply(any[ChangeSet])
    doReturn(IO(testChangeSet)).when(recordChangeRepo).save(any[ChangeSet])
    doReturn(IO(testZoneChange)).when(zoneChangeRepo).save(any[ZoneChange])
    doReturn(IO(testZone)).when(zoneRepo).save(any[Zone])

    doReturn(() => IO(testDnsView)).when(mockDNSLoader).load
    doReturn(() => IO(testVinylDNSView)).when(mockVinylDNSLoader).load
  }

  "ZoneSyncer" should {
    "Send the correct zone to the DNSZoneViewLoader" in {
      val captor = ArgumentCaptor.forClass(classOf[Zone])

      val dnsLoader = mock[Zone => DnsZoneViewLoader]
      doReturn(mockDNSLoader).when(dnsLoader).apply(any[Zone])

      val syncer =
        ZoneSyncHandler(recordSetRepo, recordChangeRepo, dnsLoader, (_, _) => mockVinylDNSLoader)

      syncer(testZoneChange).unsafeRunSync()

      verify(dnsLoader).apply(captor.capture())
      val req = captor.getValue
      req shouldBe testZone

    }

    "load the dns zone from DNSZoneViewLoader" in {
      val syncer = ZoneSyncHandler(
        recordSetRepo,
        recordChangeRepo,
        _ => mockDNSLoader,
        (_, _) => mockVinylDNSLoader)
      syncer(testZoneChange).unsafeRunSync()

      verify(mockDNSLoader, times(1)).load
    }

    "Send the correct zone to the VinylDNSZoneViewLoader" in {
      val zoneCaptor = ArgumentCaptor.forClass(classOf[Zone])
      val repoCaptor = ArgumentCaptor.forClass(classOf[RecordSetRepository])

      val vinyldnsLoader = mock[(Zone, RecordSetRepository) => VinylDNSZoneViewLoader]
      doReturn(mockVinylDNSLoader).when(vinyldnsLoader).apply(any[Zone], any[RecordSetRepository])

      val syncer =
        ZoneSyncHandler(recordSetRepo, recordChangeRepo, _ => mockDNSLoader, vinyldnsLoader)
      syncer(testZoneChange).unsafeRunSync()

      verify(vinyldnsLoader).apply(zoneCaptor.capture(), repoCaptor.capture())
      val req = zoneCaptor.getValue
      req shouldBe testZone
    }

    "load the dns zone from VinylDNSZoneViewLoader" in {
      val syncer = ZoneSyncHandler(
        recordSetRepo,
        recordChangeRepo,
        _ => mockDNSLoader,
        (_, _) => mockVinylDNSLoader)
      syncer(testZoneChange).unsafeRunSync()

      verify(mockVinylDNSLoader, times(1)).load
    }

    "compute the diff correctly" in {
      val captor = ArgumentCaptor.forClass(classOf[ZoneView])

      val testVinylDNSView = mock[ZoneView]
      doReturn(List(testRecordSetChange)).when(testVinylDNSView).diff(any[ZoneView])
      doReturn(() => IO(testVinylDNSView)).when(mockVinylDNSLoader).load

      val syncer = ZoneSyncHandler(
        recordSetRepo,
        recordChangeRepo,
        _ => mockDNSLoader,
        (_, _) => mockVinylDNSLoader)
      syncer(testZoneChange).unsafeRunSync()

      verify(testVinylDNSView).diff(captor.capture())
      val req = captor.getValue
      req shouldBe testDnsView
    }

    "save the record changes to the recordChangeRepo" in {
      val captor = ArgumentCaptor.forClass(classOf[ChangeSet])

      val syncer = ZoneSyncHandler(
        recordSetRepo,
        recordChangeRepo,
        _ => mockDNSLoader,
        (_, _) => mockVinylDNSLoader)
      syncer(testZoneChange).unsafeRunSync()

      verify(recordChangeRepo).save(captor.capture())
      val req = captor.getValue
      anonymize(req) shouldBe anonymize(testChangeSet)
    }

    "save the record sets to the recordSetRepo" in {
      val captor = ArgumentCaptor.forClass(classOf[ChangeSet])
      val syncer = ZoneSyncHandler(
        recordSetRepo,
        recordChangeRepo,
        _ => mockDNSLoader,
        (_, _) => mockVinylDNSLoader)
      syncer(testZoneChange).unsafeRunSync()

      verify(recordSetRepo).apply(captor.capture())
      val req = captor.getValue
      anonymize(req) shouldBe anonymize(testChangeSet)
    }

    "returns the zone as active and sets the latest sync" in {
      val testVinylDNSView = ZoneView(testZone, List(testRecord1, testRecord2))
      doReturn(() => IO(testVinylDNSView)).when(mockVinylDNSLoader).load

      val syncer = ZoneSyncHandler(
        recordSetRepo,
        recordChangeRepo,
        _ => mockDNSLoader,
        (_, _) => mockVinylDNSLoader)
      val result = syncer(testZoneChange).unsafeRunSync()

      result.zone.status shouldBe ZoneStatus.Active
      result.zone.latestSync shouldBe defined
    }

    "filters out unknown record types but allow dotted hosts" in {
      val captor = ArgumentCaptor.forClass(classOf[ChangeSet])
      val testVinylDNSView = mock[ZoneView]

      val unknownChange = RecordSetChange.forAdd(testRecordUnknown, testZone)
      val dottedChange = RecordSetChange.forAdd(testRecordDotted, testZone)
      val okDottedChange = RecordSetChange.forAdd(testRecordDottedOk, testZone)
      val expectedChanges = Seq(okDottedChange, dottedChange)
      val correctChangeSet = testChangeSet.copy(changes = expectedChanges)

      doReturn(List(unknownChange, dottedChange, okDottedChange))
        .when(testVinylDNSView)
        .diff(any[ZoneView])
      doReturn(() => IO(testVinylDNSView)).when(mockVinylDNSLoader).load
      doReturn(IO(correctChangeSet)).when(recordSetRepo).apply(captor.capture())
      doReturn(IO(correctChangeSet)).when(recordChangeRepo).save(any[ChangeSet])

      val syncer = ZoneSyncHandler(
        recordSetRepo,
        recordChangeRepo,
        _ => mockDNSLoader,
        (_, _) => mockVinylDNSLoader)
      syncer(testZoneChange).unsafeRunSync()

      captor.getValue.changes should contain theSameElementsAs expectedChanges
    }

    "allow for dots in reverse zone PTR, SOA, NS records" in {
      val captor = ArgumentCaptor.forClass(classOf[ChangeSet])
      val testVinylDNSView = mock[ZoneView]
      val dottedChange = RecordSetChange.forAdd(testRecordDotted, testZone)
      val ptrChange = RecordSetChange.forAdd(testReversePTR, testReverseZone)
      val nsChange = RecordSetChange.forAdd(testReverseNS, testReverseZone)
      val soaChange = RecordSetChange.forAdd(testReverseSOA, testReverseZone)
      val changes = List(ptrChange, nsChange, soaChange, dottedChange)
      val expectedChanges = List(ptrChange, nsChange, soaChange, dottedChange)
      val correctChangeSet = testChangeSet.copy(changes = expectedChanges)

      doReturn(changes).when(testVinylDNSView).diff(any[ZoneView])
      doReturn(() => IO(testVinylDNSView)).when(mockVinylDNSLoader).load
      doReturn(IO(correctChangeSet)).when(recordSetRepo).apply(captor.capture())
      doReturn(IO(correctChangeSet)).when(recordChangeRepo).save(any[ChangeSet])

      val zoneChange = ZoneChange(testReverseZone, testReverseZone.account, ZoneChangeType.Sync)

      val syncer = ZoneSyncHandler(
        recordSetRepo,
        recordChangeRepo,
        _ => mockDNSLoader,
        (_, _) => mockVinylDNSLoader)
      syncer(zoneChange).unsafeRunSync()

      captor.getValue.changes should contain theSameElementsAs expectedChanges
    }

    "handles errors by moving the zone back to an active status and failing the zone change" in {
      doReturn(() => IO.raiseError(new RuntimeException("Dns Failed")))
        .when(mockVinylDNSLoader)
        .load
      val syncer = ZoneSyncHandler(
        recordSetRepo,
        recordChangeRepo,
        _ => mockDNSLoader,
        (_, _) => mockVinylDNSLoader)
      val result = syncer(testZoneChange).unsafeRunSync()

      result.status shouldBe ZoneChangeStatus.Failed
      result.zone.status shouldBe ZoneStatus.Active
      result.zone.latestSync shouldBe testZoneChange.zone.latestSync
    }
  }
}
