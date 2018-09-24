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

import cats.effect.IO
import cats.scalatest.{EitherMatchers, EitherValues}
import org.joda.time.DateTime
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import vinyldns.api.domain.AccessValidations
import vinyldns.api.domain.record.{RecordSetService, RecordSetServiceAlgebra}
import vinyldns.api.engine.sqs.TestSqsService
import vinyldns.api.repository.mysql.TestMySqlInstance
import vinyldns.api.{DynamoDBApiIntegrationSpec, VinylDNSTestData}
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership._
import vinyldns.core.domain.record.RecordType.{A, CNAME}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._
import vinyldns.dynamodb.repository.{DynamoDBRecordSetRepository, DynamoDBRepositorySettings}

class RecordSetChangeHandlerIntegrationSpec
    extends DynamoDBApiIntegrationSpec
    with VinylDNSTestData
    with MockitoSugar
    with Eventually
    with EitherMatchers
    with EitherValues {

  private val recordSetTable = "recordSetTest"

  private val recordSetStoreConfig = DynamoDBRepositorySettings(s"$recordSetTable", 30, 30)

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

  private val wildcardTestRecordCNAME = RecordSet(
    zone.id,
    "*",
    CNAME,
    38400,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(CNAMEData("example.com")))

  private val testRecordA = RecordSet(
    zone.id,
    "a-record",
    A,
    38400,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AData("10.1.1.1")))

  override def anonymize(recordSet: RecordSet): RecordSet = {
    val fakeTime = new DateTime(2010, 1, 1, 0, 0)
    recordSet.copy(id = "a", created = fakeTime, updated = None)
  }

  def setup(): Unit = {
    recordSetRepo =
      DynamoDBRecordSetRepository(recordSetStoreConfig, dynamoIntegrationConfig).unsafeRunSync()
    zoneRepo = TestMySqlInstance.zoneRepository

    zoneRepo.save(zone).unsafeRunSync()

    // Seeding records in DB
    val records = List(
      wildcardTestRecordCNAME,
      testRecordA
    )

    records.map(record => recordSetRepo.putRecordSet(record).unsafeRunSync())

    testRecordSetService = new RecordSetService(
      zoneRepo,
      recordSetRepo,
      mock[RecordChangeRepository],
      mock[UserRepository],
      TestSqsService,
      AccessValidations)
  }

  def tearDown(): Unit = ()

  "RecordSetChangeHandler" should {
    "add record if CNAME wildcard exists" in {
      val newRecord = testRecordA.copy(name = "any-name")
      val result =
        testRecordSetService
          .addRecordSet(newRecord, auth)
          .value
          .unsafeRunSync()
          .map(_.asInstanceOf[RecordSetChange])

      result.value.recordSet.name shouldBe "any-name"
    }
  }
}
