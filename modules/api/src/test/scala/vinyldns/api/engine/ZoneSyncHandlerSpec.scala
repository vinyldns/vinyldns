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
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{doReturn, reset, times, verify}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterEach
import scalikejdbc.{ConnectionPool, DB}
import vinyldns.api.VinylDNSTestHelpers
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.api.domain.zone.{DnsZoneViewLoader, VinylDNSZoneViewLoader, ZoneView}
import vinyldns.core.domain.{Encrypted, Fqdn}
import vinyldns.core.domain.backend.{Backend, BackendResolver}
import vinyldns.core.domain.record.NameSort.NameSort
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.ZoneRepository.DuplicateZoneError
import vinyldns.core.domain.zone._
import cats.syntax.all._
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.api.engine.ZoneSyncHandler.{monitor, time}
import vinyldns.core.domain.record.RecordTypeSort.RecordTypeSort
import vinyldns.mysql.TransactionProvider

class ZoneSyncHandlerSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with VinylDNSTestHelpers
    with TransactionProvider {

  private implicit val logger: Logger = LoggerFactory.getLogger("vinyldns.engine.ZoneSyncHandler")
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  // Copy of runSync to verify the working of transaction and rollback while exception occurs
  def testRunSyncFunc(
                       recordSetRepository: RecordSetRepository,
                       recordChangeRepository: RecordChangeRepository,
                       recordSetCacheRepository: RecordSetCacheRepository,
                       zoneChange: ZoneChange,
                       backendResolver: BackendResolver,
                       maxZoneSize: Int,
                       vinyldnsLoader: (Zone, RecordSetRepository,RecordSetCacheRepository) => VinylDNSZoneViewLoader =
     VinylDNSZoneViewLoader.apply
  ): IO[ZoneChange] =
    monitor("zone.sync") {
      time(s"zone.sync; zoneName='${zoneChange.zone.name}'") {
        val zone = zoneChange.zone
        val dnsLoader = DnsZoneViewLoader(zone, backendResolver.resolve(zone), maxZoneSize)
        val dnsView =
          time(
            s"zone.sync.loadDnsView; zoneName='${zone.name}'; zoneChange='${zoneChange.id}'"
          )(dnsLoader.load())
        val vinyldnsView = time(s"zone.sync.loadVinylDNSView; zoneName='${zone.name}'")(
          vinyldnsLoader(zone, recordSetRepository, recordSetCacheRepository).load()
        )
        val recordSetChanges = (dnsView, vinyldnsView).parTupled.map {
          case (dnsZoneView, vinylDnsZoneView) => vinylDnsZoneView.diff(dnsZoneView)
        }

        recordSetChanges.flatMap { allChanges =>
          val changesWithUserIds = allChanges.map(_.withUserId(zoneChange.userId))

          if (changesWithUserIds.isEmpty) {
            logger.info(
              s"zone.sync.changes; zoneName='${zone.name}'; changeCount=0; zoneChange='${zoneChange.id}'"
            )
            IO.pure(
              zoneChange.copy(
                zone.copy(status = ZoneStatus.Active, latestSync = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS))),
                status = ZoneChangeStatus.Synced
              )
            )
          } else {
            changesWithUserIds
              .filter { chg =>
                chg.recordSet.name != zone.name && chg.recordSet.name.contains(".") &&
                  chg.recordSet.typ != RecordType.SRV && chg.recordSet.typ != RecordType.TXT &&
                  chg.recordSet.typ != RecordType.NAPTR
              }
              .map(_.recordSet.name)
              .grouped(1000)
              .foreach { dottedGroup =>
                val dottedGroupString = dottedGroup.mkString(", ")
                logger.info(
                  s"Zone sync for zoneName='${zone.name}'; zoneId='${zone.id}'; " +
                    s"zoneChange='${zoneChange.id}' includes the following ${dottedGroup.length} " +
                    s"dotted host records: [$dottedGroupString]"
                )
              }

            logger.info(
              s"zone.sync.changes; zoneName='${zone.name}'; " +
                s"changeCount=${changesWithUserIds.size}; zoneChange='${zoneChange.id}'"
            )
            val changeSet = ChangeSet(changesWithUserIds).copy(status = ChangeSetStatus.Applied)

            executeWithinTransaction { db: DB =>
              // we want to make sure we write to both the change repo and record set repo
              // at the same time as this can take a while
              val saveRecordChanges = time(s"zone.sync.saveChanges; zoneName='${zone.name}'")(
                recordChangeRepository.save(db, changeSet)
              )
              val saveRecordSets = time(s"zone.sync.saveRecordSets; zoneName='${zone.name}'")(
                recordSetRepository.apply(db, changeSet)
              )

              val saveRecordSetDatas = time(s"zone.sync.saveRecordSetDatas; zoneName='${zone.name}'")(
                recordSetCacheRepository.save(db,changeSet)
              )
              // join together the results of saving both the record changes as well as the record sets
              for {
                _ <- saveRecordChanges
                _ <- saveRecordSets
                _ <- saveRecordSetDatas
              } yield zoneChange.copy(
                zone.copy(status = ZoneStatus.Active, latestSync = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS))),
                status = ZoneChangeStatus.Synced
              )
            }
          }
        }
      }.attempt.map {
        case Left(e: Throwable) =>
          logger.error(
            s"Encountered error syncing ; zoneName='${zoneChange.zone.name}'; zoneChange='${zoneChange.id}'",
            e
          )
          // We want to just move back to an active status, do not update latest sync
          zoneChange.copy(
            zone = zoneChange.zone.copy(status = ZoneStatus.Active),
            status = ZoneChangeStatus.Failed,
            systemMessage = Some("Changes Rolled back. " + e.getMessage)
          )
        case Right(ok) => ok
      }
    }


  private val mockBackend = mock[Backend]
  private val mockBackendResolver = mock[BackendResolver]
  private val mockDNSLoader = mock[DnsZoneViewLoader]
  private val mockVinylDNSLoader = mock[VinylDNSZoneViewLoader]
  private val recordSetRepo = mock[RecordSetRepository]
  private val recordChangeRepo = mock[RecordChangeRepository]
  private val recordSetCacheRepo = mock[RecordSetCacheRepository]
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
    connection = Some(ZoneConnection(zoneName, dnsKeyName, Encrypted(dnsTsig), dnsServerAddress)),
    transferConnection = Some(ZoneConnection(zoneName, dnsKeyName, Encrypted(dnsTsig), dnsServerAddress))
  )

  private val testReverseZone = Zone(
    reverseZoneName,
    "test@test.com",
    ZoneStatus.Active,
    connection = Some(ZoneConnection(zoneName, dnsKeyName, Encrypted(dnsTsig), dnsServerAddress)),
    transferConnection = Some(ZoneConnection(zoneName, dnsKeyName, Encrypted(dnsTsig), dnsServerAddress))
  )

  private val testRecord1 = RecordSet(
    zoneId = testZone.id,
    name = "abc",
    typ = RecordType.A,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    records = List(AData("1.1.1.1"))
  )
  private val testRecord2 = RecordSet(
    zoneId = testZone.id,
    name = "def",
    typ = RecordType.A,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    records = List(AData("2.2.2.2"))
  )
  private val testRecordDotted = RecordSet(
    zoneId = testZone.id,
    name = "gh.i.",
    typ = RecordType.A,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    records = List(AData("3.3.3.3"))
  )
  private val testRecordDottedOk = RecordSet(
    zoneId = testZone.id,
    name = s"ok-dotted.${testZone.name}",
    typ = RecordType.A,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    records = List(AData("4.4.4.4"))
  )

  private val testReversePTR = RecordSet(
    zoneId = testReverseZone.id,
    name = "33.33",
    typ = RecordType.PTR,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    records = List(PTRData(Fqdn("test.com.")))
  )
  private val testReverseSOA = RecordSet(
    zoneId = testReverseZone.id,
    name = "30.172.in-addr.arpa.",
    typ = RecordType.SOA,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    records = List(SOAData(Fqdn("mname"), "rname", 1439234395, 10800, 3600, 604800, 38400))
  )
  private val testReverseNS = RecordSet(
    zoneId = testReverseZone.id,
    name = "30.172.in-addr.arpa.",
    typ = RecordType.NS,
    ttl = 100,
    status = RecordSetStatus.Active,
    created = Instant.now.truncatedTo(ChronoUnit.MILLIS),
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
    recordSetCacheRepo,
    zoneChangeRepo,
    zoneRepo,
    mockBackendResolver,
    10000,
    (_, _, _) => mockVinylDNSLoader
  )

  private val testRunSync = testRunSyncFunc(
    recordSetRepo,
    recordChangeRepo,
    recordSetCacheRepo,
    testZoneChange,
    mockBackendResolver,
    10000,
    (_, _, _) => mockVinylDNSLoader
  )


  private val runSync = ZoneSyncHandler.runSync(
    recordSetRepo,
    recordChangeRepo,
    recordSetCacheRepo,
    testZoneChange,
    mockBackendResolver,
    10000,
    (_, _, _) => mockVinylDNSLoader
  )

  override def beforeEach(): Unit = {
    reset(
      recordSetRepo,
      recordChangeRepo,
      recordSetCacheRepo,
      zoneRepo,
      zoneChangeRepo,
      mockDNSLoader,
      mockVinylDNSLoader,
      mockBackend
    )

    doReturn(
      IO(ListRecordSetResults(List(testRecord1), None, None, None, None, None, None, NameSort.ASC, recordTypeSort = RecordTypeSort.NONE))
    ).when(recordSetRepo)
      .listRecordSets(
        any[Option[String]],
        any[Option[String]],
        any[Option[Int]],
        any[Option[String]],
        any[Option[Set[RecordType]]],
        any[Option[String]],
        any[NameSort],
        any[RecordTypeSort],
      )

    doReturn(IO(testChangeSet)).when(recordSetRepo).apply(any[DB], any[ChangeSet])
    doReturn(IO(testChangeSet)).when(recordChangeRepo).save(any[DB], any[ChangeSet])
    doReturn(IO(testChangeSet)).when(recordSetCacheRepo).save(any[DB], any[ChangeSet])

    doReturn(IO(testZoneChange)).when(zoneChangeRepo).save(any[ZoneChange])
    doReturn(IO(testZone)).when(zoneRepo).save(any[Zone])

    doReturn(() => IO(testDnsView)).when(mockDNSLoader).load
    doReturn(() => IO(testVinylDNSView)).when(mockVinylDNSLoader).load
    doReturn(IO.pure(List(testRecord1, testRecord2)))
      .when(mockBackend)
      .loadZone(any[Zone], any[Int])
  }

  // Add connection to run test and check transaction
  ConnectionPool.add('default, "jdbc:h2:mem:vinyldns;MODE=MYSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;IGNORECASE=TRUE;INIT=RUNSCRIPT FROM 'classpath:test/ddl.sql'","sa","")

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
          recordSetCacheRepo,
          testZoneChange,
          mockBackendResolver,
          10000,
          (_, _, _) => mockVinylDNSLoader
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
          recordSetCacheRepo,
          testZoneChange,
          mockBackendResolver,
          10000,
          (_, _, _) => mockVinylDNSLoader
        )
        .unsafeRunSync()

      verify(mockBackend, times(1)).loadZone(any[Zone], any[Int])
    }

    "Send the correct zone to the VinylDNSZoneViewLoader" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      val zoneCaptor = ArgumentCaptor.forClass(classOf[Zone])
      val repoCaptor = ArgumentCaptor.forClass(classOf[RecordSetRepository])
      val repoDataCaptor = ArgumentCaptor.forClass(classOf[RecordSetCacheRepository])

      val vinyldnsLoader =
        mock[(Zone, RecordSetRepository, RecordSetCacheRepository) => VinylDNSZoneViewLoader]
      doReturn(mockVinylDNSLoader)
        .when(vinyldnsLoader)
        .apply(any[Zone], any[RecordSetRepository], any[RecordSetCacheRepository])

      ZoneSyncHandler
        .runSync(
          recordSetRepo,
          recordChangeRepo,
          recordSetCacheRepo,
          testZoneChange,
          mockBackendResolver,
          10000,
          vinyldnsLoader
        )
        .unsafeRunSync()

      verify(vinyldnsLoader).apply(
        zoneCaptor.capture(),
        repoCaptor.capture(),
        repoDataCaptor.capture()
      )
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

      verify(recordChangeRepo).save(any[DB], captor.capture())
      val req = captor.getValue
      anonymize(req) shouldBe anonymize(testChangeSet)

    }
    "save the record changes to the recordSetCacheRepo" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      val captor = ArgumentCaptor.forClass(classOf[ChangeSet])
      runSync.unsafeRunSync()

      verify(recordSetCacheRepo).save(any[DB], captor.capture())
      val req = captor.getValue
      anonymize(req) shouldBe anonymize(testChangeSet)

    }

    "save the record sets to the recordSetRepo" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])
      val captor = ArgumentCaptor.forClass(classOf[ChangeSet])
      runSync.unsafeRunSync()

      verify(recordSetRepo).apply(any[DB], captor.capture())
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

      doReturn(IO(correctChangeSet)).when(recordSetRepo).apply(any[DB], captor.capture())
      doReturn(IO(correctChangeSet)).when(recordChangeRepo).save(any[DB], any[ChangeSet])
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

      doReturn(IO(correctChangeSet)).when(recordSetRepo).apply(any[DB], captor.capture())
      doReturn(IO(correctChangeSet)).when(recordChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])

      val zoneChange = ZoneChange(testReverseZone, testReverseZone.account, ZoneChangeType.Sync)

      ZoneSyncHandler
        .runSync(
          recordSetRepo,
          recordChangeRepo,
          recordSetCacheRepo,
          zoneChange,
          mockBackendResolver,
          10000,
          (_, _, _) => mockVinylDNSLoader
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

    "verify transaction by not saving changes to database when exception occurs while saving to RecordSetRepo" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])

      // Raise Error while saving changes to Record set repo
      doReturn(IO.raiseError(new RuntimeException("Save Recordset Repo Failed!")))
        .when(recordSetRepo)
        .apply(any[DB], any[ChangeSet])

      val result = testRunSync.unsafeRunSync()

      // Does not save changes to both recordChangeRepo and recordSetRepo as it rollbacks changes made when exception occurred in RecordSetRepo
      // Transaction saves either both record changes and record sets or saves none to database
      result.systemMessage.get shouldBe "Changes Rolled back. Save Recordset Repo Failed!"

      // ZoneChangeStatus Fails as exception occurred
      result.status shouldBe ZoneChangeStatus.Failed
      result.zone.status shouldBe ZoneStatus.Active
      result.zone.latestSync should not be defined
    }

    "verify transaction by not saving changes to database when exception occurs while saving to RecordChangeRepo" in {
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])

      // Raise Error while saving changes to Record change repo
      doReturn(IO.raiseError(new RuntimeException("Save Record change Repo Failed!")))
        .when(recordChangeRepo)
        .save(any[DB], any[ChangeSet])

      val result = testRunSync.unsafeRunSync()

      // Does not save changes to both recordChangeRepo and recordSetRepo as it rollbacks changes made when exception occurred in RecordChangeRepo
      // Transaction saves either both record changes and record sets or saves none to database
      result.systemMessage.get shouldBe "Changes Rolled back. Save Record change Repo Failed!"

      // ZoneChangeStatus Fails as exception occurred
      result.status shouldBe ZoneChangeStatus.Failed
      result.zone.status shouldBe ZoneStatus.Active
      result.zone.latestSync shouldBe testZoneChange.zone.latestSync
    }

  }
}
