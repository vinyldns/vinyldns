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
import cats.scalatest.EitherMatchers
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.time.{Seconds, Span}
import vinyldns.api._
import vinyldns.api.domain.access.AccessValidations
import vinyldns.api.domain.zone._
import vinyldns.api.engine.TestMessageQueue
import vinyldns.core.TestMembershipData._
import vinyldns.core.TestZoneData.testConnection
import vinyldns.core.domain.{Fqdn, HighValueDomainError}
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.{Group, GroupRepository, User, UserRepository}
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._
import vinyldns.dns.DnsConnection
import vinyldns.dynamodb.repository.{DynamoDBRecordSetRepository, DynamoDBRepositorySettings}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class RecordSetServiceIntegrationSpec
    extends DynamoDBApiIntegrationSpec
    with ResultHelpers
    with EitherMatchers
    with MockitoSugar
    with Matchers
    with MySqlApiIntegrationSpec {

  private val recordSetTable = "recordSetTest"

  private val recordSetStoreConfig = DynamoDBRepositorySettings(s"$recordSetTable", 30, 30)

  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  private var recordSetRepo: DynamoDBRecordSetRepository = _
  private var zoneRepo: ZoneRepository = _
  private var groupRepo: GroupRepository = _

  private var testRecordSetService: RecordSetServiceAlgebra = _

  private val user = User("live-test-user", "key", "secret")
  private val user2 = User("shared-record-test-user", "key-shared", "secret-shared")
  private val group = Group(s"test-group", "test@test.com", adminUserIds = Set(user.id))
  private val group2 = Group(s"test-group", "test@test.com", adminUserIds = Set(user.id, user2.id))
  private val sharedGroup =
    Group(s"test-shared-group", "test@test.com", adminUserIds = Set(user.id, user2.id))
  private val auth = AuthPrincipal(user, Seq(group.id, sharedGroup.id))
  private val auth2 = AuthPrincipal(user2, Seq(sharedGroup.id, group2.id))

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
    DateTime.now,
    None,
    List(AData("10.1.1.1"))
  )
  private val apexTestRecordAAAA = RecordSet(
    zone.id,
    "live-zone-test",
    AAAA,
    38400,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AAAAData("fd69:27cc:fe91::60"))
  )
  private val subTestRecordA = RecordSet(
    zone.id,
    "a-record",
    A,
    38400,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AData("10.1.1.1"))
  )
  private val subTestRecordAAAA = RecordSet(
    zone.id,
    "aaaa-record",
    AAAA,
    38400,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AAAAData("fd69:27cc:fe91::60"))
  )
  private val subTestRecordNS = RecordSet(
    zone.id,
    "ns-record",
    NS,
    38400,
    RecordSetStatus.Active,
    DateTime.now,
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
    DateTime.now,
    None,
    List(AData("10.1.1.1"))
  )
  private val subTestRecordNameConflict = RecordSet(
    zoneTestNameConflicts.id,
    "relative-name-conflict",
    A,
    38400,
    RecordSetStatus.Active,
    DateTime.now,
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
    DateTime.now,
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
    DateTime.now,
    None,
    List(AData("1.1.1.1")),
    ownerGroupId = Some(sharedGroup.id)
  )

  private val sharedTestRecordBadOwnerGroup = RecordSet(
    sharedZone.id,
    "shared-record-bad-owner-group",
    A,
    200,
    RecordSetStatus.Active,
    DateTime.now,
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
    DateTime.now,
    None,
    List(AData("10.1.1.1")),
    ownerGroupId = Some(sharedGroup.id)
  )

  private val zoneConnection =
    ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1")

  private val configuredConnections =
    ConfiguredDnsConnections(zoneConnection, zoneConnection, List())

  private val mockDnsConnection = mock[DnsConnection]

  def setup(): Unit = {
    recordSetRepo =
      DynamoDBRecordSetRepository(recordSetStoreConfig, dynamoIntegrationConfig).unsafeRunSync()
    zoneRepo = zoneRepository
    groupRepo = groupRepository
    List(group, group2, sharedGroup).map(g => waitForSuccess(groupRepo.save(g)))
    List(zone, zoneTestNameConflicts, zoneTestAddRecords, sharedZone).map(
      z => waitForSuccess(zoneRepo.save(z))
    )

    // Seeding records in DB
    val records = List(
      apexTestRecordA,
      apexTestRecordAAAA,
      subTestRecordA,
      subTestRecordAAAA,
      subTestRecordNS,
      apexTestRecordNameConflict,
      subTestRecordNameConflict,
      highValueDomainRecord,
      sharedTestRecord,
      sharedTestRecordBadOwnerGroup,
      testOwnerGroupRecordInNormalZone
    )
    records.map(record => waitForSuccess(recordSetRepo.putRecordSet(record)))

    testRecordSetService = new RecordSetService(
      zoneRepo,
      groupRepo,
      recordSetRepo,
      mock[RecordChangeRepository],
      mock[UserRepository],
      TestMessageQueue,
      new AccessValidations(),
      (_, _) => mockDnsConnection,
      configuredConnections,
      false
    )
  }

  def tearDown(): Unit = ()

  "DynamoDBRecordSetRepository" should {
    "not alter record name when seeding database for tests" in {
      val originalRecord = testRecordSetService
        .getRecordSet(apexTestRecordA.id, auth)
        .value
        .unsafeToFuture()
        .mapTo[Either[Throwable, RecordSetInfo]]
      whenReady(originalRecord, timeout) { out =>
        rightValue(out).name shouldBe "live-zone-test"
      }
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
        DateTime.now,
        None,
        List(AData("10.1.1.1"))
      )
      val result =
        testRecordSetService
          .addRecordSet(newRecord, auth)
          .value
          .unsafeToFuture()
          .mapTo[Either[Throwable, RecordSetChange]]
      whenReady(result, timeout) { out =>
        rightValue(out).recordSet.name shouldBe "zone-test-add-records."
      }
    }

    "update apex A record and add trailing dot" in {
      val newRecord = apexTestRecordA.copy(ttl = 200)
      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeToFuture()
        .mapTo[Either[Throwable, RecordSetChange]]
      whenReady(result, timeout) { out =>
        val change = rightValue(out)
        change.recordSet.name shouldBe "live-zone-test."
        change.recordSet.ttl shouldBe 200
      }
    }

    "update apex AAAA record and add trailing dot" in {
      val newRecord = apexTestRecordAAAA.copy(ttl = 200)
      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeToFuture()
        .mapTo[Either[Throwable, RecordSetChange]]
      whenReady(result, timeout) { out =>
        val change = rightValue(out)
        change.recordSet.name shouldBe "live-zone-test."
        change.recordSet.ttl shouldBe 200
      }
    }

    "update relative A record without adding trailing dot" in {
      val newRecord = subTestRecordA.copy(ttl = 200)
      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeToFuture()
        .mapTo[Either[Throwable, RecordSetChange]]
      whenReady(result, timeout) { out =>
        val change = rightValue(out)
        change.recordSet.name shouldBe "a-record"
        change.recordSet.ttl shouldBe 200
      }
    }

    "update relative AAAA without adding trailing dot" in {
      val newRecord = subTestRecordAAAA.copy(ttl = 200)
      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeToFuture()
        .mapTo[Either[Throwable, RecordSetChange]]
      whenReady(result, timeout) { out =>
        val change = rightValue(out)
        change.recordSet.name shouldBe "aaaa-record"
        change.recordSet.ttl shouldBe 200
      }
    }

    "update relative NS record without trailing dot" in {
      val newRecord = subTestRecordNS.copy(ttl = 200)
      val result = testRecordSetService
        .updateRecordSet(newRecord, auth)
        .value
        .unsafeToFuture()
        .mapTo[Either[Throwable, RecordSetChange]]
      whenReady(result, timeout) { out =>
        val change = rightValue(out)
        change.recordSet.name shouldBe "ns-record"
        change.recordSet.ttl shouldBe 200
      }
    }

    "fail to add relative record if apex record with same name already exists" in {
      val newRecord = apexTestRecordNameConflict.copy(name = "zone-test-name-conflicts")

      doReturn(IO(List(newRecord)))
        .when(mockDnsConnection)
        .resolve(
          zoneTestNameConflicts.name,
          zoneTestNameConflicts.name,
          newRecord.typ
        )

      val result =
        testRecordSetService
          .addRecordSet(newRecord, auth)
          .value
          .unsafeToFuture()
          .mapTo[Either[Throwable, RecordSetChange]]
      whenReady(result, timeout) { out =>
        leftValue(out) shouldBe a[RecordSetAlreadyExists]
      }
    }

    "fail to add apex record if relative record with same name already exists" in {
      val newRecord = subTestRecordNameConflict.copy(name = "relative-name-conflict.")

      doReturn(IO(List(newRecord)))
        .when(mockDnsConnection)
        .resolve(newRecord.name, zoneTestNameConflicts.name, newRecord.typ)

      val result =
        testRecordSetService
          .addRecordSet(newRecord, auth)
          .value
          .unsafeToFuture()
          .mapTo[Either[Throwable, RecordSetChange]]
      whenReady(result, timeout) { out =>
        leftValue(out) shouldBe a[RecordSetAlreadyExists]
      }
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
          .unsafeToFuture()
          .mapTo[Either[Throwable, RecordSetInfo]]
      whenReady(result, timeout) { out =>
        rightValue(out).name shouldBe "shared-record"
        rightValue(out).ownerGroupName shouldBe Some(sharedGroup.name)
      }
    }

    "get a shared record when owner group can't be found" in {
      val result =
        testRecordSetService
          .getRecordSet(sharedTestRecordBadOwnerGroup.id, auth)
          .value
          .unsafeToFuture()
          .mapTo[Either[Throwable, RecordSetInfo]]
      whenReady(result, timeout) { out =>
        rightValue(out).name shouldBe "shared-record-bad-owner-group"
        rightValue(out).ownerGroupName shouldBe None
      }
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

    "fail deleting for user not in record owner group in shared zone" in {
      val result = leftResultOf(
        testRecordSetService
          .deleteRecordSet(sharedTestRecord.id, sharedTestRecord.zoneId, dummyAuth)
          .value
      )

      result shouldBe a[NotAuthorizedError]
    }

    "fail deleting for user in record owner group in non-shared zone" in {
      val result = leftResultOf(
        testRecordSetService
          .deleteRecordSet(
            testOwnerGroupRecordInNormalZone.id,
            testOwnerGroupRecordInNormalZone.zoneId,
            auth2
          )
          .value
      )

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

  private def waitForSuccess[T](f: => IO[T]): T = {
    val waiting = f.unsafeToFuture().recover { case _ => Thread.sleep(2000); waitForSuccess(f) }
    Await.result[T](waiting, 15.seconds)
  }
}
