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

package vinyldns.api.domain.record

import cats.effect._
import cats.implicits._
import cats.scalatest.EitherMatchers
import org.mockito.Matchers.any

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc.DB
import vinyldns.api._
import vinyldns.api.config.VinylDNSConfig
import vinyldns.api.domain.access.AccessValidations
import vinyldns.api.domain.zone._
import vinyldns.api.engine.TestMessageQueue
import vinyldns.mysql.TransactionProvider
import vinyldns.core.TestZoneData.testConnection
import vinyldns.core.domain.{Encrypted, Fqdn, HighValueDomainError}
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.backend.{Backend, BackendResolver}
import vinyldns.core.domain.membership.{Group, GroupRepository, User, UserRepository}
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._
import vinyldns.core.notifier.{AllNotifiers, Notification, Notifier}

import scala.concurrent.ExecutionContext

class RecordSetServiceIntegrationSpec
  extends AnyWordSpec
    with ResultHelpers
    with EitherMatchers
    with MockitoSugar
    with Matchers
    with MySqlApiIntegrationSpec
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with TransactionProvider {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  private val vinyldnsConfig = VinylDNSConfig.load().unsafeRunSync()

  private val recordSetRepo = recordSetRepository
  private val recordSetCacheRepo = recordSetCacheRepository
  private val mockNotifier = mock[Notifier]
  private val mockNotifiers = AllNotifiers(List(mockNotifier))

  private val zoneRepo: ZoneRepository = zoneRepository
  private val groupRepo: GroupRepository = groupRepository

  private var testRecordSetService: RecordSetServiceAlgebra = _

  private val user = User("live-test-user", "key", Encrypted("secret"))
  private val testUser = User("testuser", "key", Encrypted("secret"))
  private val user2 = User("shared-record-test-user", "key-shared", Encrypted("secret-shared"))

  private val group = Group(s"test-group", "test@test.com", adminUserIds = Set(user.id))
  private val dummyGroup = Group(s"dummy-group", "test@test.com", adminUserIds = Set(testUser.id))
  private val group2 = Group(s"test-group", "test@test.com", adminUserIds = Set(user.id, user2.id))
  private val sharedGroup =
    Group(s"test-shared-group", "test@test.com", adminUserIds = Set(user.id, user2.id))
  private val auth = AuthPrincipal(user, Seq(group.id, sharedGroup.id))
  private val auth2 = AuthPrincipal(user2, Seq(sharedGroup.id, group2.id))
  val dummyAuth: AuthPrincipal = AuthPrincipal(testUser, Seq(dummyGroup.id))

  private val dummyZone = Zone(
    s"dummy.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection,
    adminGroupId = dummyGroup.id
  )
  private val zone = Zone(
    s"live-zone-test.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection,
    adminGroupId = group.id
  )

  private val apexTestRecordA = RecordSet(
    zone.id,
    "live-zone-test",
    A,
    38400,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )
  private val apexTestRecordAAAA = RecordSet(
    zone.id,
    "live-zone-test",
    AAAA,
    38400,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AAAAData("fd69:27cc:fe91::60"))
  )
  private val dottedTestRecord = RecordSet(
    dummyZone.id,
    "test.dotted",
    AAAA,
    38400,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AAAAData("fd69:27cc:fe91::60"))
  )
  private val subTestRecordA = RecordSet(
    zone.id,
    "a-record",
    A,
    38400,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )
  private val subTestRecordAAAA = RecordSet(
    zone.id,
    "aaaa-record",
    AAAA,
    38400,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AAAAData("fd69:27cc:fe91::60"))
  )
  private val subTestRecordNS = RecordSet(
    zone.id,
    "ns-record",
    NS,
    38400,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(NSData(Fqdn("172.17.42.1.")))
  )

  private val zoneTestNameConflicts = Zone(
    s"zone-test-name-conflicts.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection,
    adminGroupId = group.id
  )
  private val apexTestRecordNameConflict = RecordSet(
    zoneTestNameConflicts.id,
    "zone-test-name-conflicts.",
    A,
    38400,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )
  private val subTestRecordNameConflict = RecordSet(
    zoneTestNameConflicts.id,
    "relative-name-conflict",
    A,
    38400,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )

  private val zoneTestAddRecords = Zone(
    s"zone-test-add-records.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection,
    adminGroupId = group.id
  )

  private val highValueDomainRecord = RecordSet(
    zone.id,
    "high-value-domain-existing",
    A,
    38400,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("1.1.1.1"))
  )

  private val sharedZone = Zone(
    s"shared-zone-test.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection,
    adminGroupId = group.id,
    shared = true
  )

  private val sharedTestRecord = RecordSet(
    sharedZone.id,
    "shared-record",
    A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("1.1.1.1")),
    ownerGroupId = Some(sharedGroup.id)
  )

  private val sharedTestRecordPendingReviewOwnerShip = RecordSet(
    sharedZone.id,
    "shared-record-ownerShip-pendingReview",
    A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("1.1.1.1")),
    ownerGroupId = Some(sharedGroup.id),
    recordSetGroupChange = Some(OwnerShipTransfer(
      ownerShipTransferStatus = OwnerShipTransferStatus.PendingReview,
      requestedOwnerGroupId = Some(group.id)))
  )

  private val sharedTestRecordCancelledOwnerShip = RecordSet(
    sharedZone.id,
    "shared-record-ownerShip-cancelled",
    A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("1.1.1.1")),
    ownerGroupId = Some(sharedGroup.id),
    recordSetGroupChange = Some(OwnerShipTransfer(
      ownerShipTransferStatus = OwnerShipTransferStatus.Cancelled,
      requestedOwnerGroupId = Some(group.id)))
  )

  private val sharedTestRecordBadOwnerGroup = RecordSet(
    sharedZone.id,
    "shared-record-bad-owner-group",
    A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("1.1.1.1")),
    ownerGroupId = Some("non-existent")
  )

  private val testOwnerGroupRecordInNormalZone = RecordSet(
    zone.id,
    "user-in-owner-group-but-zone-not-shared",
    A,
    38400,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1")),
    ownerGroupId = Some(sharedGroup.id)
  )

  private val mockBackendResolver = mock[BackendResolver]
  private val mockBackend = mock[Backend]

  override def afterAll(): Unit = {
    clearRecordSetRepo()
    clearZoneRepo()
    clearGroupRepo()
  }

  override def beforeEach(): Unit = {
    def makeAddChange(rs: RecordSet, zone: Zone): RecordSetChange =
      RecordSetChange(
        zone = zone,
        recordSet = rs,
        userId = "system",
        changeType = RecordSetChangeType.Create,
        status = RecordSetChangeStatus.Pending,
        singleBatchChangeIds = Nil
      )
    clearRecordSetRepo()
    clearZoneRepo()
    clearGroupRepo()

    def saveGroupData(
                       groupRepo: GroupRepository,
                       group: Group
                     ): IO[Group] =
      executeWithinTransaction { db: DB =>
        groupRepo.save(db, group)
      }

    List(group, group2, sharedGroup, dummyGroup).traverse(g => saveGroupData(groupRepo, g).void).unsafeRunSync()
    List(zone, dummyZone, zoneTestNameConflicts, zoneTestAddRecords, sharedZone)
      .traverse(
        z => zoneRepo.save(z)
      )
      .unsafeRunSync()

    // Seeding records in DB
    val sharedRecords = List(
      sharedTestRecord,
      sharedTestRecordBadOwnerGroup,
      sharedTestRecordPendingReviewOwnerShip,
      sharedTestRecordCancelledOwnerShip
    )
    val conflictRecords = List(
      subTestRecordNameConflict,
      apexTestRecordNameConflict
    )
    val zoneRecords = List(
      apexTestRecordA,
      apexTestRecordAAAA,
      dottedTestRecord,
      subTestRecordA,
      subTestRecordAAAA,
      subTestRecordNS,
      highValueDomainRecord,
      testOwnerGroupRecordInNormalZone
    )
    val changes = ChangeSet(
      sharedRecords.map(makeAddChange(_, sharedZone)) ++
        conflictRecords.map(makeAddChange(_, zoneTestNameConflicts)) ++
        zoneRecords.map(makeAddChange(_, zone))
    )
    executeWithinTransaction { db: DB =>
      recordSetRepo.apply(db, changes)
    }.unsafeRunSync()

    testRecordSetService = new RecordSetService(
      zoneRepo,
      groupRepo,
      recordSetRepo,
      recordSetCacheRepo,
      mock[RecordChangeRepository],
      mock[UserRepository],
      TestMessageQueue,
      new AccessValidations(),
      mockBackendResolver,
      false,
      vinyldnsConfig.highValueDomainConfig,
      vinyldnsConfig.dottedHostsConfig,
      vinyldnsConfig.serverConfig.approvedNameServers,
      useRecordSetCache = true,
      mockNotifiers
    )
  }

  "MySqlRecordSetRepository" should {
    "not alter record name when seeding database for tests" in {
      val originalRecord = testRecordSetService
        .getRecordSet(apexTestRecordA.id, auth)
        .value
        .unsafeRunSync()
      rightValue(originalRecord).name shouldBe "live-zone-test"
    }
  }

  "RecordSetService" should {
    "create apex record without trailing dot and save record name with trailing dot" in {
      val newRecord = RecordSet(
        zoneTestAddRecords.id,
        "zone-test-add-records",
        A,
        38400,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        None,
        List(AData("10.1.1.1"))
      )
      val result =
        testRecordSetService
          .addRecordSet(newRecord, auth)
          .value
          .unsafeRunSync()
      rightValue(result)
        .asInstanceOf[RecordSetChange]
        .recordSet
        .name shouldBe "zone-test-add-records."
    }

    "create dotted record fails if it doesn't satisfy dotted hosts config" in {
      val newRecord = RecordSet(
        zoneTestAddRecords.id,
        "test.dot",
        A,
        38400,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        None,
        List(AData("10.1.1.1"))
      )
      val result =
        testRecordSetService
          .addRecordSet(newRecord, auth)
          .value
          .unsafeRunSync()
      leftValue(result) shouldBe a[InvalidRequest]
    }

    "create dotted record succeeds if it satisfies all dotted hosts config" in {
      val newRecord = RecordSet(
        dummyZone.id,
        "testing.dotted",
        AAAA,
        38400,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        None,
        List(AAAAData("fd69:27cc:fe91::60"))
      )
      // succeeds as zone, user and record type is allowed as defined in application.conf
      val result =
        testRecordSetService
          .addRecordSet(newRecord, dummyAuth)
          .value
          .unsafeRunSync()

      rightValue(result)
        .asInstanceOf[RecordSetChange]
        .recordSet
        .name shouldBe "testing.dotted"
    }

    "fail creating dotted record if it satisfies all dotted hosts config except dots-limit for the zone" in {
      val newRecord = RecordSet(
        dummyZone.id,
        "test.dotted.more.dots.than.allowed",
        AAAA,
        38400,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        None,
        List(AAAAData("fd69:27cc:fe91::60"))
      )

      // The number of dots allowed in the record name for this zone as defined in the config is 3.
      // Creating with 4 dots results in an error
      val result =
        testRecordSetService
          .addRecordSet(newRecord, dummyAuth)
          .value
          .unsafeRunSync()
      leftValue(result) shouldBe a[InvalidRequest]
    }

    "auto-approve ownership transfer request, if user tried to update the ownership" in {
      val newRecord = sharedTestRecord.copy(recordSetGroupChange =
        Some(OwnerShipTransfer(ownerShipTransferStatus = OwnerShipTransferStatus.AutoApproved,
          requestedOwnerGroupId = Some(group.id))))

      val result = testRecordSetService
        .updateRecordSet(newRecord, auth2)
        .value
        .unsafeRunSync()

      val change = rightValue(result).asInstanceOf[RecordSetChange]
      change.recordSet.name shouldBe "shared-record"
      change.recordSet.ownerGroupId.get shouldBe group.id
      change.recordSet.recordSetGroupChange.get.ownerShipTransferStatus shouldBe OwnerShipTransferStatus.AutoApproved
      change.recordSet.recordSetGroupChange.get.requestedOwnerGroupId.get shouldBe group.id
    }

    "approve ownership transfer request, if user requested for ownership transfer" in {
      val newRecord = sharedTestRecordPendingReviewOwnerShip.copy(recordSetGroupChange =
        Some(OwnerShipTransfer(
          ownerShipTransferStatus = OwnerShipTransferStatus.ManuallyApproved)))

      doReturn(IO.unit).when(mockNotifier).notify(any[Notification[_]])

      val result = testRecordSetService
        .updateRecordSet(newRecord, auth2)
        .value
        .unsafeRunSync()

      val change = rightValue(result).asInstanceOf[RecordSetChange]
      change.recordSet.name shouldBe "shared-record-ownerShip-pendingReview"
      change.recordSet.ownerGroupId.get shouldBe group.id
      change.recordSet.recordSetGroupChange.get.ownerShipTransferStatus shouldBe OwnerShipTransferStatus.ManuallyApproved
      change.recordSet.recordSetGroupChange.get.requestedOwnerGroupId.get shouldBe group.id
    }

    "reject ownership transfer request, if user requested for ownership transfer" in {
      val newRecord = sharedTestRecordPendingReviewOwnerShip.copy(recordSetGroupChange =
        Some(OwnerShipTransfer(
          ownerShipTransferStatus = OwnerShipTransferStatus.ManuallyRejected)))

      doReturn(IO.unit).when(mockNotifier).notify(any[Notification[_]])

      val result = testRecordSetService
        .updateRecordSet(newRecord, auth2)
        .value
        .unsafeRunSync()

      val change = rightValue(result).asInstanceOf[RecordSetChange]
      change.recordSet.name shouldBe "shared-record-ownerShip-pendingReview"
      change.recordSet.ownerGroupId.get shouldBe sharedGroup.id
      change.recordSet.recordSetGroupChange.get.ownerShipTransferStatus shouldBe OwnerShipTransferStatus.ManuallyRejected
      change.recordSet.recordSetGroupChange.get.requestedOwnerGroupId.get shouldBe group.id
    }

    "request ownership transfer, if user not in the owner group and wants to own the record" in {
      val newRecord = sharedTestRecord.copy(recordSetGroupChange =
        Some(OwnerShipTransfer(
          ownerShipTransferStatus = OwnerShipTransferStatus.Requested,
          requestedOwnerGroupId = Some(dummyGroup.id))))

      doReturn(IO.unit).when(mockNotifier).notify(any[Notification[_]])

      val result = testRecordSetService
        .updateRecordSet(newRecord, dummyAuth)
        .value
        .unsafeRunSync()

      val change = rightValue(result).asInstanceOf[RecordSetChange]
      change.recordSet.name shouldBe "shared-record"
      change.recordSet.ownerGroupId.get shouldBe sharedGroup.id
      change.recordSet.recordSetGroupChange.get.ownerShipTransferStatus shouldBe OwnerShipTransferStatus.PendingReview
      change.recordSet.recordSetGroupChange.get.requestedOwnerGroupId.get shouldBe dummyGroup.id
    }

    "fail requesting ownership transfer if user is not in owner group and tried to update other fields in record set" in {
      val newRecord = sharedTestRecord.copy(
        ttl = 3000,
        recordSetGroupChange =
        Some(OwnerShipTransfer(
          ownerShipTransferStatus = OwnerShipTransferStatus.Requested,
          requestedOwnerGroupId = Some(dummyGroup.id))))

      val result = testRecordSetService
        .updateRecordSet(newRecord, dummyAuth)
        .value
        .unsafeRunSync()

      leftValue(result) shouldBe a[InvalidRequest]
    }

    "fail updating if user is not in owner group for ownership transfer approval" in {
      val newRecord = sharedTestRecordPendingReviewOwnerShip.copy(recordSetGroupChange =
        Some(OwnerShipTransfer(
          ownerShipTransferStatus = OwnerShipTransferStatus.ManuallyApproved)))

      val result = testRecordSetService
        .updateRecordSet(newRecord, dummyAuth)
        .value
        .unsafeRunSync()

      leftValue(result) shouldBe a[NotAuthorizedError]
    }

    "fail updating if user is not in owner group for ownership transfer reject" in {
      val newRecord = sharedTestRecordPendingReviewOwnerShip.copy(recordSetGroupChange =
        Some(OwnerShipTransfer(
          ownerShipTransferStatus = OwnerShipTransferStatus.ManuallyRejected)))

      val result = testRecordSetService
        .updateRecordSet(newRecord, dummyAuth)
        .value
        .unsafeRunSync()

      leftValue(result) shouldBe a[NotAuthorizedError]
    }

    "cancel the ownership transfer request, if user not require ownership transfer further" in {
      val newRecord = sharedTestRecordPendingReviewOwnerShip.copy(recordSetGroupChange =
        Some(OwnerShipTransfer(
          ownerShipTransferStatus = OwnerShipTransferStatus.Cancelled)))

      doReturn(IO.unit).when(mockNotifier).notify(any[Notification[_]])

      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeRunSync()

      val change = rightValue(result).asInstanceOf[RecordSetChange]
      change.recordSet.name shouldBe "shared-record-ownerShip-pendingReview"
      change.recordSet.ownerGroupId.get shouldBe sharedGroup.id
      change.recordSet.recordSetGroupChange.get.ownerShipTransferStatus shouldBe OwnerShipTransferStatus.Cancelled
      change.recordSet.recordSetGroupChange.get.requestedOwnerGroupId.get shouldBe group.id
    }

    "fail approving ownership transfer request, if user is cancelled" in {
      val newRecord = sharedTestRecordCancelledOwnerShip.copy(recordSetGroupChange =
        Some(OwnerShipTransfer(
          ownerShipTransferStatus = OwnerShipTransferStatus.ManuallyApproved)))
      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeRunSync()

      leftValue(result) shouldBe a[InvalidRequest]
    }

    "fail rejecting ownership transfer request, if user is cancelled" in {
      val newRecord = sharedTestRecordCancelledOwnerShip.copy(recordSetGroupChange =
        Some(OwnerShipTransfer(
          ownerShipTransferStatus = OwnerShipTransferStatus.ManuallyRejected)))

      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeRunSync()

      leftValue(result) shouldBe a[InvalidRequest]
    }

    "fail auto-approving ownership transfer request, if user is cancelled" in {
      val newRecord = sharedTestRecordCancelledOwnerShip.copy(recordSetGroupChange =
        Some(OwnerShipTransfer(
          ownerShipTransferStatus = OwnerShipTransferStatus.AutoApproved
        )))

      doReturn(IO.unit).when(mockNotifier).notify(any[Notification[_]])

      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeRunSync()

      leftValue(result) shouldBe a[InvalidRequest]
    }

    "fail auto-approving ownership transfer request, if zone is not shared" in {
      val newRecord = dottedTestRecord.copy(recordSetGroupChange =
        Some(OwnerShipTransfer(ownerShipTransferStatus = OwnerShipTransferStatus.AutoApproved,
          requestedOwnerGroupId = Some(group.id))))

      val result = testRecordSetService
        .updateRecordSet(newRecord, auth2)
        .value
        .unsafeRunSync()

      leftValue(result) shouldBe a[InvalidRequest]
    }

    "fail approving ownership transfer request, if zone is not shared" in {
      val newRecord = dottedTestRecord.copy(recordSetGroupChange =
        Some(OwnerShipTransfer(
          ownerShipTransferStatus = OwnerShipTransferStatus.ManuallyApproved
        )))

      val result = testRecordSetService
        .updateRecordSet(newRecord, auth2)
        .value
        .unsafeRunSync()

      leftValue(result) shouldBe a[InvalidRequest]
    }

    "fail requesting ownership transfer, if zone is not shared" in {
      val newRecord = dottedTestRecord.copy(recordSetGroupChange =
        Some(OwnerShipTransfer(
          ownerShipTransferStatus = OwnerShipTransferStatus.Requested,
          requestedOwnerGroupId = Some(dummyGroup.id)
        )))

      doReturn(IO.unit).when(mockNotifier).notify(any[Notification[_]])

      val result = testRecordSetService
        .updateRecordSet(newRecord, dummyAuth)
        .value
        .unsafeRunSync()

      leftValue(result) shouldBe a[InvalidRequest]
    }

    "update dotted record succeeds if it satisfies all dotted hosts config" in {
      val newRecord = dottedTestRecord.copy(ttl = 37000)

      val result = testRecordSetService
        .updateRecordSet(newRecord, dummyAuth)
        .value
        .unsafeRunSync()
      val change = rightValue(result).asInstanceOf[RecordSetChange]
      change.recordSet.name shouldBe "test.dotted"
      change.recordSet.ttl shouldBe 37000
    }

    "update dotted record name fails as updating a record name is not allowed" in {
      val newRecord = dottedTestRecord.copy(name = "trial.dotted")

      val result = testRecordSetService
        .updateRecordSet(newRecord, dummyAuth)
        .value
        .unsafeRunSync()
      // We get an "InvalidRequest: Cannot update RecordSet's name."
      leftValue(result) shouldBe a[InvalidRequest]
    }

    "update apex A record and add trailing dot" in {
      val newRecord = apexTestRecordA.copy(ttl = 200)
      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeRunSync()
      val change = rightValue(result).asInstanceOf[RecordSetChange]
      change.recordSet.name shouldBe "live-zone-test."
      change.recordSet.ttl shouldBe 200
    }

    "update apex AAAA record and add trailing dot" in {
      val newRecord = apexTestRecordAAAA.copy(ttl = 200)
      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeRunSync()
      val change = rightValue(result).asInstanceOf[RecordSetChange]
      change.recordSet.name shouldBe "live-zone-test."
      change.recordSet.ttl shouldBe 200
    }

    "update relative A record without adding trailing dot" in {
      val newRecord = subTestRecordA.copy(ttl = 200)
      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeRunSync()
      val change = rightValue(result).asInstanceOf[RecordSetChange]
      change.recordSet.name shouldBe "a-record"
      change.recordSet.ttl shouldBe 200
    }

    "update relative AAAA without adding trailing dot" in {
      val newRecord = subTestRecordAAAA.copy(ttl = 200)
      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeRunSync()
      val change = rightValue(result).asInstanceOf[RecordSetChange]
      change.recordSet.name shouldBe "aaaa-record"
      change.recordSet.ttl shouldBe 200
    }

    "update relative NS record without trailing dot" in {
      val newRecord = subTestRecordNS.copy(ttl = 200)
      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeRunSync()
      val change = rightValue(result).asInstanceOf[RecordSetChange]
      change.recordSet.name shouldBe "ns-record"
      change.recordSet.ttl shouldBe 200
    }

    "fail to add relative record if apex record with same name already exists" in {
      val newRecord = apexTestRecordNameConflict.copy(name = "zone-test-name-conflicts")

      doReturn(IO(List(newRecord)))
        .when(mockBackend)
        .resolve(
          zoneTestNameConflicts.name,
          zoneTestNameConflicts.name,
          newRecord.typ
        )

      val result =
        testRecordSetService
          .addRecordSet(newRecord, auth)
          .value
          .unsafeRunSync()
      leftValue(result) shouldBe a[RecordSetAlreadyExists]
    }

    "fail to add apex record if relative record with same name already exists" in {
      val newRecord = subTestRecordNameConflict.copy(name = "relative-name-conflict")

      doReturn(IO(List(newRecord)))
        .when(mockBackend)
        .resolve(newRecord.name, zoneTestNameConflicts.name, newRecord.typ)

      val result =
        testRecordSetService
          .addRecordSet(newRecord, auth)
          .value
          .unsafeRunSync()
      leftValue(result) shouldBe a[RecordSetAlreadyExists]
    }

    "fail to add a dns record whose name is a high value domain" in {
      val highValueRecord = highValueDomainRecord.copy(name = "high-value-domain-new")
      val result =
        testRecordSetService
          .addRecordSet(highValueRecord, auth)
          .value
          .unsafeRunSync()

      leftValue(result) shouldBe InvalidRequest(
        HighValueDomainError("high-value-domain-new.live-zone-test.").message
      )
    }

    "fail to update a record whose name is a high value domain" in {
      val newRecord = highValueDomainRecord.copy(ttl = highValueDomainRecord.ttl + 1000)

      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeRunSync()

      leftValue(result) shouldBe InvalidRequest(
        HighValueDomainError("high-value-domain-existing.live-zone-test.").message
      )
    }

    "fail to delete a record whose name is a high value domain" in {
      val result = testRecordSetService
        .deleteRecordSet(highValueDomainRecord.id, highValueDomainRecord.zoneId, auth)
        .value
        .unsafeRunSync()

      leftValue(result) shouldBe InvalidRequest(
        HighValueDomainError("high-value-domain-existing.live-zone-test.").message
      )
    }

    "get a shared record when user is in assigned ownerGroup" in {
      val result =
        testRecordSetService
          .getRecordSet(sharedTestRecord.id, auth2)
          .value
          .unsafeRunSync()

      rightValue(result).name shouldBe "shared-record"
      rightValue(result).ownerGroupName shouldBe Some(sharedGroup.name)
    }

    "get a shared record when owner group can't be found" in {
      val result =
        testRecordSetService
          .getRecordSet(sharedTestRecordBadOwnerGroup.id, auth)
          .value
          .unsafeRunSync()
      rightValue(result).name shouldBe "shared-record-bad-owner-group"
      rightValue(result).ownerGroupName shouldBe None
    }

    "fail updating if user is in owner group but zone is not shared" in {
      val result = testRecordSetService
        .updateRecordSet(testOwnerGroupRecordInNormalZone, auth2)
        .value
        .unsafeRunSync()

      leftValue(result) shouldBe a[NotAuthorizedError]
    }

    "fail updating if owner group does not exist" in {
      val newRecord = sharedTestRecord.copy(ownerGroupId = Some("no-existo"))

      val result = testRecordSetService
        .updateRecordSet(newRecord, auth2)
        .value
        .unsafeRunSync()

      leftValue(result) shouldBe a[InvalidGroupError]
    }

    "fail updating if user is not in new owner group" in {
      val newRecord = sharedTestRecord.copy(ownerGroupId = Some(group.id))

      val result = testRecordSetService
        .updateRecordSet(newRecord, auth2)
        .value
        .unsafeRunSync()

      leftValue(result) shouldBe a[InvalidRequest]
    }

    "update successfully if user is in owner group and zone is shared" in {
      val newRecord = sharedTestRecord.copy(ttl = sharedTestRecord.ttl + 100)
      val result = testRecordSetService
        .updateRecordSet(newRecord, auth2)
        .value
        .unsafeRunSync()

      rightValue(result).asInstanceOf[RecordSetChange].recordSet.ttl shouldBe newRecord.ttl
      rightValue(result).asInstanceOf[RecordSetChange].recordSet.ownerGroupId shouldBe
        sharedTestRecord.ownerGroupId
    }

    "update successfully if user is changing owner group to None" in {
      val newRecord = sharedTestRecord.copy(ownerGroupId = None)
      val result = testRecordSetService
        .updateRecordSet(newRecord, auth2)
        .value
        .unsafeRunSync()

      rightValue(result).asInstanceOf[RecordSetChange].recordSet.ownerGroupId shouldBe None
    }

    "update successfully if user is changing owner group to another group they are a part of" in {
      val newRecord = sharedTestRecord.copy(ownerGroupId = Some(group2.id))
      val result = testRecordSetService
        .updateRecordSet(newRecord, auth2)
        .value
        .unsafeRunSync()

      rightValue(result).asInstanceOf[RecordSetChange].recordSet.ownerGroupId shouldBe
        Some(group2.id)
    }

    "delete dotted host record successfully for user in record owner group" in {
      val result = testRecordSetService
        .deleteRecordSet(dottedTestRecord.id, dottedTestRecord.zoneId, dummyAuth)
        .value
        .unsafeRunSync()

      result should be(right)
    }

    "fail deleting for user not in record owner group in shared zone" in {
      val result =
        testRecordSetService
          .deleteRecordSet(sharedTestRecord.id, sharedTestRecord.zoneId, dummyAuth)
          .value.unsafeRunSync().swap.toOption.get

      result shouldBe a[NotAuthorizedError]
    }

    "fail deleting for user in record owner group in non-shared zone" in {
      val result =
        testRecordSetService
          .deleteRecordSet(
            testOwnerGroupRecordInNormalZone.id,
            testOwnerGroupRecordInNormalZone.zoneId,
            auth2
          )
          .value.unsafeRunSync().swap.toOption.get

      result shouldBe a[NotAuthorizedError]
    }

    "delete successfully for user in record owner group in shared zone" in {
      val result = testRecordSetService
        .deleteRecordSet(sharedTestRecord.id, sharedTestRecord.zoneId, auth2)
        .value
        .unsafeRunSync()

      result should be(right)
    }

    "delete successfully for zone admin in shared zone" in {
      val result = testRecordSetService
        .deleteRecordSet(sharedTestRecord.id, sharedTestRecord.zoneId, auth)
        .value
        .unsafeRunSync()

      result should be(right)
    }
  }
}
