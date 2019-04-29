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

import cats.effect._
import org.joda.time.DateTime
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Seconds, Span}
import vinyldns.api.{DynamoDBApiIntegrationSpec, MySqlApiIntegrationSpec, ResultHelpers}
import vinyldns.api.domain.AccessValidations
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.api.engine.TestMessageQueue
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.{Group, GroupRepository, User, UserRepository}
import vinyldns.core.domain.record._
import vinyldns.dynamodb.repository.{DynamoDBRecordSetRepository, DynamoDBRepositorySettings}
import vinyldns.core.TestZoneData.testConnection
import vinyldns.core.domain.zone._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ZoneServiceIntegrationSpec
    extends DynamoDBApiIntegrationSpec
    with ResultHelpers
    with MockitoSugar
    with MySqlApiIntegrationSpec {

  private val recordSetTable = "recordSetTest"

  private val recordSetStoreConfig = DynamoDBRepositorySettings(s"$recordSetTable", 30, 30)

  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  private var recordSetRepo: RecordSetRepository = _
  private var zoneRepo: ZoneRepository = _

  private var testZoneService: ZoneServiceAlgebra = _

  private val user = User(s"live-test-user", "key", "secret")
  private val group = Group(s"test-group", "test@test.com", adminUserIds = Set(user.id))
  private val auth = AuthPrincipal(user, Seq(group.id))
  private val badAuth = AuthPrincipal(user, Seq())
  private val zone = Zone(
    s"live-test-zone.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection,
    adminGroupId = group.id)

  private val testRecordSOA = RecordSet(
    zoneId = zone.id,
    name = "vinyldns",
    typ = RecordType.SOA,
    ttl = 38400,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records =
      List(SOAData("172.17.42.1.", "admin.test.com.", 1439234395, 10800, 3600, 604800, 38400))
  )
  private val testRecordNS = RecordSet(
    zoneId = zone.id,
    name = "vinyldns",
    typ = RecordType.NS,
    ttl = 38400,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(NSData("172.17.42.1.")))
  private val testRecordA = RecordSet(
    zoneId = zone.id,
    name = "jenkins",
    typ = RecordType.A,
    ttl = 38400,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(AData("10.1.1.1")))

  private val changeSetSOA = ChangeSet(RecordSetChangeGenerator.forAdd(testRecordSOA, zone))
  private val changeSetNS = ChangeSet(RecordSetChangeGenerator.forAdd(testRecordNS, zone))
  private val changeSetA = ChangeSet(RecordSetChangeGenerator.forAdd(testRecordA, zone))

  def setup(): Unit = {
    recordSetRepo =
      DynamoDBRecordSetRepository(recordSetStoreConfig, dynamoIntegrationConfig).unsafeRunSync()
    zoneRepo = zoneRepository

    waitForSuccess(zoneRepo.save(zone))
    // Seeding records in DB
    waitForSuccess(recordSetRepo.apply(changeSetSOA))
    waitForSuccess(recordSetRepo.apply(changeSetNS))
    waitForSuccess(recordSetRepo.apply(changeSetA))

    testZoneService = new ZoneService(
      zoneRepo,
      mock[GroupRepository],
      mock[UserRepository],
      mock[ZoneChangeRepository],
      mock[ZoneConnectionValidator],
      TestMessageQueue,
      new ZoneValidations(1000),
      AccessValidations
    )
  }

  def tearDown(): Unit = ()

  "ZoneEntity" should {
    "reject a DeleteZone with bad auth" in {
      val result =
        testZoneService
          .deleteZone(zone.id, badAuth)
          .value
          .unsafeToFuture()
      whenReady(result) { out =>
        leftValue(out) shouldBe a[NotAuthorizedError]
      }
    }
    "accept a DeleteZone" in {
      val removeARecord = ChangeSet(RecordSetChangeGenerator.forDelete(testRecordA, zone))
      waitForSuccess(recordSetRepo.apply(removeARecord))

      val result =
        testZoneService
          .deleteZone(zone.id, auth)
          .value
          .unsafeToFuture()
          .mapTo[Either[Throwable, ZoneChange]]
      whenReady(result, timeout) { out =>
        out.isRight shouldBe true
        val change = out.toOption.get
        change.zone.id shouldBe zone.id
        change.changeType shouldBe ZoneChangeType.Delete
      }
    }
  }

  "getBackendIds" should {
    "return backend ids in config" in {
      testZoneService.getBackendIds().value.unsafeRunSync() shouldBe Right(
        List("func-test-backend"))
    }
  }

  private def waitForSuccess[T](f: => IO[T]): T = {
    val waiting = f.unsafeToFuture().recover { case _ => Thread.sleep(2000); waitForSuccess(f) }
    Await.result[T](waiting, 15.seconds)
  }
}
