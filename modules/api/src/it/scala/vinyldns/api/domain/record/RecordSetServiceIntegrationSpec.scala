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

import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.scalatest.Matchers
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Seconds, Span}

import vinyldns.api.domain.AccessValidations
import vinyldns.api.domain.auth.AuthPrincipal
import vinyldns.api.domain.membership.{Group, User, UserRepository}
import vinyldns.api.domain.record.RecordType._
import vinyldns.api.domain.zone.{RecordSetAlreadyExists, Zone, ZoneRepository, ZoneStatus}
import vinyldns.api.engine.sqs.TestSqsService
import vinyldns.api.repository.dynamodb.{DynamoDBIntegrationSpec, DynamoDBRecordSetRepository}
import vinyldns.api.repository.mysql.VinylDNSJDBC

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class RecordSetServiceIntegrationSpec
    extends DynamoDBIntegrationSpec
    with MockitoSugar
    with Matchers {

  private val recordSetTable = "recordSetTest"

  private val liveTestConfig = ConfigFactory.parseString(s"""
       |  recordSet {
       |    # use the dummy store, this should only be used local
       |    dummy = true
       |
       |    dynamo {
       |      tableName = "$recordSetTable"
       |      provisionedReads=30
       |      provisionedWrites=30
       |    }
       |  }
    """.stripMargin)

  private val recordSetStoreConfig = liveTestConfig.getConfig("recordSet")

  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  private var recordSetRepo: DynamoDBRecordSetRepository = _
  private var zoneRepo: ZoneRepository = _

  private var testRecordSetService: RecordSetServiceAlgebra = _

  private val user = User("live-test-user", "key", "secret")
  private val group = Group(s"test-group", "test@test.com", adminUserIds = Set(user.id))
  private val auth = AuthPrincipal(user, Seq(group.id))

  private val zone = Zone(
    s"live-zone-test.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection,
    adminGroupId = group.id)
  private val apexTestRecordA = RecordSet(
    zone.id,
    "live-zone-test",
    A,
    38400,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AData("10.1.1.1")))
  private val apexTestRecordAAAA = RecordSet(
    zone.id,
    "live-zone-test",
    AAAA,
    38400,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AAAAData("fd69:27cc:fe91::60")))
  private val subTestRecordA = RecordSet(
    zone.id,
    "a-record",
    A,
    38400,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AData("10.1.1.1")))
  private val subTestRecordAAAA = RecordSet(
    zone.id,
    "aaaa-record",
    AAAA,
    38400,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AAAAData("fd69:27cc:fe91::60")))
  private val subTestRecordNS = RecordSet(
    zone.id,
    "ns-record",
    NS,
    38400,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(NSData("172.17.42.1.")))

  private val zoneTestNameConflicts = Zone(
    s"zone-test-name-conflicts.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection,
    adminGroupId = group.id)
  private val apexTestRecordNameConflict = RecordSet(
    zoneTestNameConflicts.id,
    "zone-test-name-conflicts.",
    A,
    38400,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AData("10.1.1.1")))
  private val subTestRecordNameConflict = RecordSet(
    zoneTestNameConflicts.id,
    "relative-name-conflict",
    A,
    38400,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AData("10.1.1.1")))

  private val zoneTestAddRecords = Zone(
    s"zone-test-add-records.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection,
    adminGroupId = group.id)

  def setup(): Unit = {
    recordSetRepo = new DynamoDBRecordSetRepository(recordSetStoreConfig, dynamoDBHelper)
    zoneRepo = VinylDNSJDBC.instance.zoneRepository

    List(zone, zoneTestNameConflicts, zoneTestAddRecords).map(z => waitForSuccess(zoneRepo.save(z)))

    // Seeding records in DB
    val records = List(
      apexTestRecordA,
      apexTestRecordAAAA,
      subTestRecordA,
      subTestRecordAAAA,
      subTestRecordNS,
      apexTestRecordNameConflict,
      subTestRecordNameConflict)
    records.map(record => waitForSuccess(recordSetRepo.putRecordSet(record)))

    testRecordSetService = new RecordSetService(
      zoneRepo,
      recordSetRepo,
      mock[RecordChangeRepository],
      mock[UserRepository],
      TestSqsService,
      new AccessValidations())
  }

  def tearDown(): Unit = ()

  "DynamoDBRecordSetRepository" should {
    "not alter record name when seeding database for tests" in {
      val originalRecord = testRecordSetService
        .getRecordSet(apexTestRecordA.id, apexTestRecordA.zoneId, auth)
        .value
        .mapTo[Either[Throwable, RecordSet]]
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
        List(AData("10.1.1.1")))
      val result =
        testRecordSetService
          .addRecordSet(newRecord, auth)
          .value
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
        .mapTo[Either[Throwable, RecordSetChange]]
      whenReady(result, timeout) { out =>
        val change = rightValue(out)
        change.recordSet.name shouldBe "aaaa-record"
        change.recordSet.ttl shouldBe 200
      }
    }

    "update relative NS record without trailing dot" in {
      val newRecord = subTestRecordNS.copy(ttl = 200)
      val superAuth = AuthPrincipal(okGroupAuth.signedInUser.copy(isSuper = true), Seq.empty)
      val result = testRecordSetService
        .updateRecordSet(newRecord, superAuth)
        .value
        .mapTo[Either[Throwable, RecordSetChange]]
      whenReady(result, timeout) { out =>
        val change = rightValue(out)
        change.recordSet.name shouldBe "ns-record"
        change.recordSet.ttl shouldBe 200
      }
    }

    "fail to add relative record if apex record with same name already exists" in {
      val newRecord = apexTestRecordNameConflict.copy(name = "zone-test-name-conflicts")
      val result =
        testRecordSetService
          .addRecordSet(newRecord, auth)
          .value
          .mapTo[Either[Throwable, RecordSetChange]]
      whenReady(result, timeout) { out =>
        leftValue(out) shouldBe a[RecordSetAlreadyExists]
      }
    }

    "fail to add apex record if relative record with same name already exists" in {
      val newRecord = subTestRecordNameConflict.copy(name = "relative-name-conflict.")
      val result =
        testRecordSetService
          .addRecordSet(newRecord, auth)
          .value
          .mapTo[Either[Throwable, RecordSetChange]]
      whenReady(result, timeout) { out =>
        leftValue(out) shouldBe a[RecordSetAlreadyExists]
      }
    }
  }

  private def waitForSuccess[T](f: => Future[T]): T = {
    val waiting = f.recover { case _ => Thread.sleep(2000); waitForSuccess(f) }
    Await.result[T](waiting, 15.seconds)
  }
}
