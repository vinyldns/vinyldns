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
import org.mockito.Matchers.any
import org.mockito.Mockito.{doReturn, reset, times, verify}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterEach
import vinyldns.api.VinylDNSTestHelpers
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.api.domain.zone.{DnsZoneViewLoader, VinylDNSZoneViewLoader, ZoneView}
import vinyldns.core.domain.Fqdn
import vinyldns.core.domain.backend.{Backend, BackendResolver}
import vinyldns.core.domain.record.NameSort.NameSort
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.ZoneRepository.DuplicateZoneError
import vinyldns.core.domain.zone._

class ZoneSyncHandlerSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with VinylDNSTestHelpers {

  private val mockBackend = mock[Backend]
  private val mockBackendResolver = mock[BackendResolver]
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
    records = List(AData("1.1.1.1"))
  )
  private val testRecord2 = RecordSet(
    zoneId = testZone.id,
    name = "def",
    typ = RecordType.A,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(AData("2.2.2.2"))
  )
  private val testRecordDotted = RecordSet(
    zoneId = testZone.id,
    name = "gh.i.",
    typ = RecordType.A,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(AData("3.3.3.3"))
  )
  private val testRecordDottedOk = RecordSet(
    zoneId = testZone.id,
    name = s"ok-dotted.${testZone.name}",
    typ = RecordType.A,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(AData("4.4.4.4"))
  )

  private val testReversePTR = RecordSet(
    zoneId = testReverseZone.id,
    name = "33.33",
    typ = RecordType.PTR,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(PTRData(Fqdn("test.com.")))
  )
  private val testReverseSOA = RecordSet(
    zoneId = testReverseZone.id,
    name = "30.172.in-addr.arpa.",
    typ = RecordType.SOA,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(SOAData(Fqdn("mname"), "rname", 1439234395, 10800, 3600, 604800, 38400))
  )
  private val testReverseNS = RecordSet(
    zoneId = testReverseZone.id,
    name = "30.172.in-addr.arpa.",
    typ = RecordType.NS,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(NSData(Fqdn("172.17.42.1.")))
  )

  private val testRecordSetChange = RecordSetChangeGenerator.forZoneSyncAdd(testRecord2, testZone)
  private val testChangeSet =
    ChangeSet.apply(testRecordSetChange).copy(status = ChangeSetStatus.Applied)
  private val testZoneChange =
    ZoneChange(testZone.copy(status = ZoneStatus.Syncing), testZone.account, ZoneChangeType.Sync)
  private val testDnsView = ZoneView(testZone, List(testRecord1, testRecord2))
  private val testVinylDNSView = ZoneView(testZone, List(testRecord1))

  private val zoneSync = ZoneSyncHandler(
    recordSetRepo,
    recordChangeRepo,
    zoneChangeRepo,
    zoneRepo,
    mockBackendResolver,
    (_, _) => mockVinylDNSLoader
  )

  private val runSync = ZoneSyncHandler.runSync(
    recordSetRepo,
    recordChangeRepo,
    testZoneChange,
    mockBackendResolver,
    (_, _) => mockVinylDNSLoader
  )

  override def beforeEach(): Unit = {
    reset(
      recordSetRepo,
      recordChangeRepo,
      zoneRepo,
      zoneChangeRepo,
      mockDNSLoader,
      mockVinylDNSLoader,
      mockBackend
    )

    doReturn(
      IO(ListRecordSetResults(List(testRecord1), None, None, None, None, None, None, NameSort.ASC))
    ).when(recordSetRepo)
      .listRecordSets(
        any[Option[String]],
        any[Option[String]],
        any[Option[Int]],
        any[Option[String]],
        any[Option[Set[RecordType]]],
        any[Option[String]],
        any[NameSort]
      )
    doReturn(IO(testChangeSet)).when(recordSetRepo).apply(any[ChangeSet])
    doReturn(IO(testChangeSet)).when(recordChangeRepo).save(any[ChangeSet])
    doReturn(IO(testZoneChange)).when(zoneChangeRepo).save(any[ZoneChange])
    doReturn(IO(testZone)).when(zoneRepo).save(any[Zone])

    doReturn(() => IO(testDnsView)).when(mockDNSLoader).load
    doReturn(() => IO(testVinylDNSView)).when(mockVinylDNSLoader).load
    doReturn(IO.pure(List(testRecord1, testRecord2)))
      .when(mockBackend)
      .loadZone(any[Zone], any[Int])
  }

  "ZoneSyncHandler" should {
    "process successful zone sync" in {
      doReturn(IO.pure(Right(testZoneChange)))
        .when(zoneRepo)
        .save(any[Zone])
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])

      val result = zoneSync(testZoneChange).unsafeRunSync()

      val changeCaptor = ArgumentCaptor.forClass(classOf[ZoneChange])
      verify(zoneChangeRepo, times(2)).save(changeCaptor.capture())

      val savedChange = changeCaptor.getAllValues

      // first saveZoneAndChange
      savedChange.get(0).status shouldBe ZoneChangeStatus.Pending
      savedChange.get(0).zone.status shouldBe ZoneStatus.Syncing
      savedChange.get(0).zone.latestSync should not be defined

      // second saveZoneAndChange
      savedChange.get(1).status shouldBe ZoneChangeStatus.Synced
      savedChange.get(1).zone.status shouldBe ZoneStatus.Active
      savedChange.get(1).zone.latestSync shouldBe defined

      // returned result
      result.status shouldBe ZoneChangeStatus.Synced
      result.zone.status shouldBe ZoneStatus.Active
      result.zone.latestSync shouldBe defined
    }

    "process successful zone sync with no changes" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      doReturn(IO.pure(Right(testZoneChange)))
        .when(zoneRepo)
        .save(any[Zone])
      doReturn(() => IO(testDnsView)).when(mockDNSLoader).load
      doReturn(() => IO(testDnsView)).when(mockVinylDNSLoader).load

      val result = zoneSync(testZoneChange).unsafeRunSync()

      val changeCaptor = ArgumentCaptor.forClass(classOf[ZoneChange])
      verify(zoneChangeRepo, times(2)).save(changeCaptor.capture())

      val savedChange = changeCaptor.getAllValues

      // first saveZoneAndChange
      savedChange.get(0).status shouldBe ZoneChangeStatus.Pending
      savedChange.get(0).zone.status shouldBe ZoneStatus.Syncing
      savedChange.get(0).zone.latestSync should not be defined

      // second saveZoneAndChange
      savedChange.get(1).status shouldBe ZoneChangeStatus.Synced
      savedChange.get(1).zone.status shouldBe ZoneStatus.Active
      savedChange.get(1).zone.latestSync shouldBe defined

      // returned result
      result.status shouldBe ZoneChangeStatus.Synced
      result.zone.status shouldBe ZoneStatus.Active
      result.zone.latestSync shouldBe defined
    }

    "handle failed zone sync" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      doReturn(() => IO.raiseError(new RuntimeException("Dns Failed")))
        .when(mockVinylDNSLoader)
        .load
      doReturn(IO.pure(Right(testZoneChange)))
        .when(zoneRepo)
        .save(any[Zone])

      val result = zoneSync(testZoneChange).unsafeRunSync()

      val changeCaptor = ArgumentCaptor.forClass(classOf[ZoneChange])
      verify(zoneChangeRepo, times(2)).save(changeCaptor.capture())

      val savedChange = changeCaptor.getAllValues

      // first saveZoneAndChange
      savedChange.get(0).status shouldBe ZoneChangeStatus.Pending
      savedChange.get(0).zone.status shouldBe ZoneStatus.Syncing
      savedChange.get(0).zone.latestSync should not be defined

      // second saveZoneAndChange
      savedChange.get(1).status shouldBe ZoneChangeStatus.Failed
      savedChange.get(1).zone.status shouldBe ZoneStatus.Active
      savedChange.get(1).zone.latestSync should not be defined

      // final result
      result.status shouldBe ZoneChangeStatus.Failed
      result.zone.status shouldBe ZoneStatus.Active
      result.zone.latestSync should not be defined
    }
  }

  "saveZoneAndChange" should {
    "save zone and zoneChange with given statuses" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      doReturn(IO.pure(Right(testZoneChange))).when(zoneRepo).save(testZoneChange.zone)

      ZoneSyncHandler.saveZoneAndChange(zoneRepo, zoneChangeRepo, testZoneChange).unsafeRunSync()

      val changeCaptor = ArgumentCaptor.forClass(classOf[ZoneChange])
      verify(zoneChangeRepo).save(changeCaptor.capture())

      val savedChange = changeCaptor.getValue

      savedChange.status shouldBe ZoneChangeStatus.Pending
      savedChange.zone.status shouldBe ZoneStatus.Syncing
      savedChange.zone.latestSync shouldBe testZoneChange.zone.latestSync
    }

    "handle duplicateZoneError" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      doReturn(IO.pure(Left(DuplicateZoneError("error")))).when(zoneRepo).save(testZoneChange.zone)

      ZoneSyncHandler.saveZoneAndChange(zoneRepo, zoneChangeRepo, testZoneChange).unsafeRunSync()

      val changeCaptor = ArgumentCaptor.forClass(classOf[ZoneChange])
      verify(zoneChangeRepo).save(changeCaptor.capture())

      val savedChange = changeCaptor.getValue

      savedChange.status shouldBe ZoneChangeStatus.Failed
      savedChange.zone.status shouldBe ZoneStatus.Syncing
      savedChange.systemMessage shouldBe Some("Zone with name \"error\" already exists.")
    }
  }

  "runSync" should {
    "send the correct zone to the DNSZoneViewLoader" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      val captor = ArgumentCaptor.forClass(classOf[Zone])

      ZoneSyncHandler
        .runSync(
          recordSetRepo,
          recordChangeRepo,
          testZoneChange,
          mockBackendResolver,
          (_, _) => mockVinylDNSLoader
        )
        .unsafeRunSync()

      verify(mockBackend).loadZone(captor.capture(), any[Int])
      val req = captor.getValue
      req shouldBe testZone.copy(status = ZoneStatus.Syncing)
    }

    "load the dns zone from DNSZoneViewLoader" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      ZoneSyncHandler
        .runSync(
          recordSetRepo,
          recordChangeRepo,
          testZoneChange,
          mockBackendResolver,
          (_, _) => mockVinylDNSLoader
        )
        .unsafeRunSync()

      verify(mockBackend, times(1)).loadZone(any[Zone], any[Int])
    }

    "Send the correct zone to the VinylDNSZoneViewLoader" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      val zoneCaptor = ArgumentCaptor.forClass(classOf[Zone])
      val repoCaptor = ArgumentCaptor.forClass(classOf[RecordSetRepository])

      val vinyldnsLoader = mock[(Zone, RecordSetRepository) => VinylDNSZoneViewLoader]
      doReturn(mockVinylDNSLoader).when(vinyldnsLoader).apply(any[Zone], any[RecordSetRepository])

      ZoneSyncHandler
        .runSync(
          recordSetRepo,
          recordChangeRepo,
          testZoneChange,
          mockBackendResolver,
          vinyldnsLoader
        )
        .unsafeRunSync()

      verify(vinyldnsLoader).apply(zoneCaptor.capture(), repoCaptor.capture())
      val req = zoneCaptor.getValue
      req shouldBe testZone.copy(status = ZoneStatus.Syncing)
    }

    "load the dns zone from VinylDNSZoneViewLoader" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      runSync.unsafeRunSync()

      verify(mockVinylDNSLoader, times(1)).load
    }

    "save the record changes to the recordChangeRepo" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      val captor = ArgumentCaptor.forClass(classOf[ChangeSet])
      runSync.unsafeRunSync()

      verify(recordChangeRepo).save(captor.capture())
      val req = captor.getValue
      anonymize(req) shouldBe anonymize(testChangeSet)
    }

    "save the record sets to the recordSetRepo" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      val captor = ArgumentCaptor.forClass(classOf[ChangeSet])
      runSync.unsafeRunSync()

      verify(recordSetRepo).apply(captor.capture())
      val req = captor.getValue
      anonymize(req) shouldBe anonymize(testChangeSet)
    }

    "returns the zone as active and sets the latest sync" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      val testVinylDNSView = ZoneView(testZone, List(testRecord1, testRecord2))
      doReturn(() => IO(testVinylDNSView)).when(mockVinylDNSLoader).load
      val result = runSync.unsafeRunSync()

      result.zone.status shouldBe ZoneStatus.Active
      result.zone.latestSync shouldBe defined
    }

    "allow dotted hosts" in {
      val captor = ArgumentCaptor.forClass(classOf[ChangeSet])
      val testVinylDNSView = mock[ZoneView]

      val dottedChange = RecordSetChangeGenerator.forAdd(testRecordDotted, testZone)
      val okDottedChange = RecordSetChangeGenerator.forAdd(testRecordDottedOk, testZone)
      val expectedChanges = Seq(okDottedChange, dottedChange)
      val correctChangeSet = testChangeSet.copy(changes = expectedChanges)

      doReturn(List(dottedChange, okDottedChange))
        .when(testVinylDNSView)
        .diff(any[ZoneView])
      doReturn(() => IO(testVinylDNSView)).when(mockVinylDNSLoader).load
      doReturn(IO(correctChangeSet)).when(recordSetRepo).apply(captor.capture())
      doReturn(IO(correctChangeSet)).when(recordChangeRepo).save(any[ChangeSet])
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])

      runSync.unsafeRunSync()

      captor.getValue.changes should contain theSameElementsAs expectedChanges
    }

    "allow for dots in reverse zone PTR, SOA, NS records" in {
      val captor = ArgumentCaptor.forClass(classOf[ChangeSet])
      val testVinylDNSView = mock[ZoneView]
      val dottedChange = RecordSetChangeGenerator.forAdd(testRecordDotted, testZone)
      val ptrChange = RecordSetChangeGenerator.forAdd(testReversePTR, testReverseZone)
      val nsChange = RecordSetChangeGenerator.forAdd(testReverseNS, testReverseZone)
      val soaChange = RecordSetChangeGenerator.forAdd(testReverseSOA, testReverseZone)
      val changes = List(ptrChange, nsChange, soaChange, dottedChange)
      val expectedChanges = List(ptrChange, nsChange, soaChange, dottedChange)
      val correctChangeSet = testChangeSet.copy(changes = expectedChanges)

      doReturn(changes).when(testVinylDNSView).diff(any[ZoneView])
      doReturn(() => IO(testVinylDNSView)).when(mockVinylDNSLoader).load
      doReturn(IO(correctChangeSet)).when(recordSetRepo).apply(captor.capture())
      doReturn(IO(correctChangeSet)).when(recordChangeRepo).save(any[ChangeSet])
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])

      val zoneChange = ZoneChange(testReverseZone, testReverseZone.account, ZoneChangeType.Sync)

      ZoneSyncHandler
        .runSync(
          recordSetRepo,
          recordChangeRepo,
          zoneChange,
          mockBackendResolver,
          (_, _) => mockVinylDNSLoader
        )
        .unsafeRunSync()

      captor.getValue.changes should contain theSameElementsAs expectedChanges
    }

    "handles errors by moving the zone back to an active status and failing the zone change" in {
      doReturn(() => IO.raiseError(new RuntimeException("Dns Failed")))
        .when(mockVinylDNSLoader)
        .load
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      val result = runSync.unsafeRunSync()

      result.status shouldBe ZoneChangeStatus.Failed
      result.zone.status shouldBe ZoneStatus.Active
      result.zone.latestSync shouldBe testZoneChange.zone.latestSync
    }
  }
}
