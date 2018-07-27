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

import org.mockito.Matchers.any
import org.mockito.Mockito.doReturn
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import scalaz.std.scalaFuture._
import vinyldns.api.domain.AccessValidations
import vinyldns.api.domain.membership.{ListUsersResults, UserRepository}
import vinyldns.api.domain.zone._
import vinyldns.api.engine.sqs.TestSqsService
import vinyldns.api.route.ListRecordSetsResponse
import vinyldns.api.{GroupTestData, ResultHelpers, VinylDNSTestData}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RecordSetServiceSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with VinylDNSTestData
    with Eventually
    with ResultHelpers
    with BeforeAndAfterEach
    with GroupTestData {

  private val mockZoneRepo = mock[ZoneRepository]
  private val mockRecordRepo = mock[RecordSetRepository]
  private val mockRecordChangeRepo = mock[RecordChangeRepository]
  private val mockUserRepo = mock[UserRepository]

  doReturn(Future.successful(Some(zoneAuthorized))).when(mockZoneRepo).getZone(zoneAuthorized.id)
  doReturn(Future.successful(Some(zoneNotAuthorized)))
    .when(mockZoneRepo)
    .getZone(zoneNotAuthorized.id)

  val underTest = new RecordSetService(
    mockZoneRepo,
    mockRecordRepo,
    mockRecordChangeRepo,
    mockUserRepo,
    TestSqsService,
    new AccessValidations())

  "addRecordSet" should {
    "return the recordSet change as the result" in {
      val record = aaaa.copy(zoneId = zoneAuthorized.id)

      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSets(zoneAuthorized.id, record.name, record.typ)
      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(zoneAuthorized.id, record.name)

      val result: RecordSetChange = rightResultOf(
        underTest.addRecordSet(record, okAuth).map(_.asInstanceOf[RecordSetChange]).run)

      result.recordSet.matches(record, zoneAuthorized.name) shouldBe true
      result.changeType shouldBe RecordSetChangeType.Create
      result.status shouldBe RecordSetChangeStatus.Pending
    }
    "fail when the account is not authorized" in {
      doReturn(Future.successful(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(zoneNotAuthorized.id, aaaa.id)
      val result = leftResultOf(underTest.getRecordSet(aaaa.id, zoneNotAuthorized.id, okAuth).run)
      result shouldBe a[NotAuthorizedError]
    }
    "fail if the record is dotted" in {
      val record =
        aaaa.copy(name = "new.name", zoneId = zoneAuthorized.id, status = RecordSetStatus.Active)

      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSets(zoneAuthorized.id, record.name, record.typ)
      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(zoneAuthorized.id, record.name)

      val result = leftResultOf(underTest.addRecordSet(record, okAuth).run)
      result shouldBe a[InvalidRequest]
    }
    "fail if the record is relative with trailing dot" in {
      val record =
        aaaa.copy(name = "new.", zoneId = zoneAuthorized.id, status = RecordSetStatus.Active)

      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSets(zoneAuthorized.id, record.name, record.typ)
      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(zoneAuthorized.id, record.name)

      val result = leftResultOf(underTest.addRecordSet(record, okAuth).run)
      result shouldBe a[InvalidRequest]
    }
    "succeed if record is apex with dot" in {
      val name = zoneAuthorized.name
      val record =
        aaaa.copy(name = name, zoneId = zoneAuthorized.id, status = RecordSetStatus.Active)

      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSets(zoneAuthorized.id, record.name, record.typ)
      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(zoneAuthorized.id, record.name)

      val result: RecordSetChange = rightResultOf(
        underTest.addRecordSet(record, okAuth).map(_.asInstanceOf[RecordSetChange]).run)

      result.recordSet.name shouldBe zoneAuthorized.name
    }
    "succeed if record is apex as '@'" in {
      val name = "@"
      val record =
        aaaa.copy(name = name, zoneId = zoneAuthorized.id, status = RecordSetStatus.Active)

      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSets(zoneAuthorized.id, record.name, record.typ)
      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(zoneAuthorized.id, record.name)

      val result: RecordSetChange = rightResultOf(
        underTest.addRecordSet(record, okAuth).map(_.asInstanceOf[RecordSetChange]).run)

      result.recordSet.name shouldBe zoneAuthorized.name
    }
    "succeed if record is apex without dot" in {
      val name = zoneAuthorized.name.substring(0, zoneAuthorized.name.length - 1)
      val record =
        aaaa.copy(name = name, zoneId = zoneAuthorized.id, status = RecordSetStatus.Active)

      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSets(zoneAuthorized.id, record.name, record.typ)
      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(zoneAuthorized.id, record.name)

      val result: RecordSetChange = rightResultOf(
        underTest.addRecordSet(record, okAuth).map(_.asInstanceOf[RecordSetChange]).run)

      result.recordSet.name shouldBe zoneAuthorized.name
    }
  }

  "updateRecordSet" should {
    "return the recordSet change as the result" in {
      val oldRecord = aaaa.copy(zoneId = zoneAuthorized.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(name = "newName")

      doReturn(Future.successful(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(zoneAuthorized.id, newRecord.id)
      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(zoneAuthorized.id, newRecord.name)

      val result: RecordSetChange = rightResultOf(
        underTest.updateRecordSet(newRecord, okAuth).map(_.asInstanceOf[RecordSetChange]).run)

      result.recordSet.matches(newRecord, zoneAuthorized.name) shouldBe true
      result.updates.get.matches(oldRecord, zoneAuthorized.name) shouldBe true
      result.changeType shouldBe RecordSetChangeType.Update
      result.status shouldBe RecordSetChangeStatus.Pending
    }
    "fail when the account is not authorized" in {
      doReturn(Future.successful(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(zoneNotAuthorized.id, aaaa.id)
      val result = leftResultOf(
        underTest.updateRecordSet(aaaa.copy(zoneId = zoneNotAuthorized.id), okAuth).run)
      result shouldBe a[NotAuthorizedError]
    }
    "fail if the record is dotted" in {
      val oldRecord = aaaa.copy(zoneId = zoneAuthorized.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(name = "new.name")

      doReturn(Future.successful(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(zoneAuthorized.id, newRecord.id)
      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(zoneAuthorized.id, newRecord.name)

      val result = leftResultOf(underTest.updateRecordSet(newRecord, okAuth).run)
      result shouldBe a[InvalidRequest]
    }
    "fail if the record is relative with trailing dot" in {
      val oldRecord = aaaa.copy(zoneId = zoneAuthorized.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(name = "new.")

      doReturn(Future.successful(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(zoneAuthorized.id, newRecord.id)
      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(zoneAuthorized.id, newRecord.name)

      val result = leftResultOf(underTest.updateRecordSet(newRecord, okAuth).run)
      result shouldBe a[InvalidRequest]
    }
    "succeed if record is apex with dot" in {
      val name = zoneAuthorized.name
      val oldRecord = aaaa.copy(zoneId = zoneAuthorized.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(name = name)

      doReturn(Future.successful(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(zoneAuthorized.id, newRecord.id)
      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(zoneAuthorized.id, newRecord.name)

      val result: RecordSetChange = rightResultOf(
        underTest.updateRecordSet(newRecord, okAuth).map(_.asInstanceOf[RecordSetChange]).run)

      result.recordSet.name shouldBe zoneAuthorized.name
    }
    "succeed if record is apex as '@'" in {
      val name = "@"
      val oldRecord = aaaa.copy(zoneId = zoneAuthorized.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(name = name)

      doReturn(Future.successful(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(zoneAuthorized.id, newRecord.id)
      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(zoneAuthorized.id, newRecord.name)

      val result: RecordSetChange = rightResultOf(
        underTest.updateRecordSet(newRecord, okAuth).map(_.asInstanceOf[RecordSetChange]).run)

      result.recordSet.name shouldBe zoneAuthorized.name
    }
    "succeed if record is apex without dot" in {
      val name = zoneAuthorized.name.substring(0, zoneAuthorized.name.length - 1)
      val oldRecord = aaaa.copy(zoneId = zoneAuthorized.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(name = name)

      doReturn(Future.successful(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(zoneAuthorized.id, newRecord.id)
      doReturn(Future.successful(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(zoneAuthorized.id, newRecord.name)

      val result: RecordSetChange = rightResultOf(
        underTest.updateRecordSet(newRecord, okAuth).map(_.asInstanceOf[RecordSetChange]).run)

      result.recordSet.name shouldBe zoneAuthorized.name
    }
  }

  "deleteRecordSet" should {
    "return the recordSet change as the result" in {
      val record = aaaa.copy(status = RecordSetStatus.Active)
      doReturn(Future.successful(Some(record)))
        .when(mockRecordRepo)
        .getRecordSet(zoneAuthorized.id, record.id)

      val result: RecordSetChange = rightResultOf(
        underTest
          .deleteRecordSet(record.id, zoneAuthorized.id, okAuth)
          .map(_.asInstanceOf[RecordSetChange])
          .run)

      result.recordSet.matches(record, zoneAuthorized.name) shouldBe true
      result.changeType shouldBe RecordSetChangeType.Delete
      result.status shouldBe RecordSetChangeStatus.Pending
    }
    "fails when the account is not authorized" in {
      doReturn(Future.successful(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(zoneNotAuthorized.id, aaaa.id)
      val result =
        leftResultOf(underTest.deleteRecordSet(aaaa.id, zoneNotAuthorized.id, okAuth).run)
      result shouldBe a[NotAuthorizedError]
    }
  }

  "getRecordSet" should {
    "return the recordSet" in {
      doReturn(Future.successful(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(zoneAuthorized.id, aaaa.id)
      val result: RecordSet =
        rightResultOf(underTest.getRecordSet(aaaa.id, zoneAuthorized.id, okAuth).run)
      result shouldBe aaaa
    }
    "fail when the account is not authorized" in {
      doReturn(Future.successful(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(zoneNotAuthorized.id, aaaa.id)
      val result = leftResultOf(underTest.getRecordSet(aaaa.id, zoneNotAuthorized.id, okAuth).run)
      result shouldBe a[NotAuthorizedError]
    }
  }

  "listRecordSets" should {
    "return the recordSets" in {
      doReturn(Future.successful(ListRecordSetResults(List(aaaa))))
        .when(mockRecordRepo)
        .listRecordSets(
          zoneId = zoneAuthorized.id,
          startFrom = None,
          maxItems = None,
          recordNameFilter = None)

      val result: ListRecordSetsResponse = rightResultOf(
        underTest
          .listRecordSets(
            zoneAuthorized.id,
            startFrom = None,
            maxItems = None,
            recordNameFilter = None,
            authPrincipal = okAuth)
          .run)
      result.recordSets shouldBe List(RecordSetInfo(aaaa, AccessLevel.Delete))
    }
    "fails when the account is not authorized" in {
      val result = leftResultOf(
        underTest
          .listRecordSets(
            zoneNotAuthorized.id,
            startFrom = None,
            maxItems = None,
            recordNameFilter = None,
            authPrincipal = okAuth)
          .run)
      result shouldBe a[NotAuthorizedError]
    }
  }

  "listRecordSetChanges" should {
    "retrieve the recordset changes" in {
      doReturn(Future.successful(ListRecordSetChangesResults(completeRecordSetChanges)))
        .when(mockRecordChangeRepo)
        .listRecordSetChanges(zoneId = zoneAuthorized.id, startFrom = None, maxItems = 100)
      doReturn(Future.successful(ListUsersResults(Seq(okUser), None)))
        .when(mockUserRepo)
        .getUsers(any[Set[String]], any[Option[String]], any[Option[Int]])

      val result: ListRecordSetChangesResponse =
        rightResultOf(underTest.listRecordSetChanges(zoneAuthorized.id, authPrincipal = okAuth).run)
      val changesWithName =
        completeRecordSetChanges.map(change => RecordSetChangeInfo(change, Some("ok")))
      val expectedResults = ListRecordSetChangesResponse(
        zoneId = zoneAuthorized.id,
        recordSetChanges = changesWithName,
        nextId = None,
        startFrom = None,
        maxItems = 100)
      result shouldBe expectedResults
    }

    "return a zone with no changes if no changes exist" in {
      doReturn(Future.successful(ListRecordSetChangesResults(items = Nil)))
        .when(mockRecordChangeRepo)
        .listRecordSetChanges(zoneId = zoneAuthorized.id, startFrom = None, maxItems = 100)

      val result: ListRecordSetChangesResponse =
        rightResultOf(underTest.listRecordSetChanges(zoneAuthorized.id, authPrincipal = okAuth).run)
      val expectedResults = ListRecordSetChangesResponse(
        zoneId = zoneAuthorized.id,
        recordSetChanges = List(),
        nextId = None,
        startFrom = None,
        maxItems = 100)
      result shouldBe expectedResults
    }

    "return a NotAuthorizedError" in {
      val error = leftResultOf(
        underTest.listRecordSetChanges(zoneNotAuthorized.id, authPrincipal = okAuth).run)
      error shouldBe a[NotAuthorizedError]
    }

    "return the record set changes sorted by created date desc" in {
      val rsChange1 = pendingCreateAAAA
      val rsChange2 = pendingCreateCNAME.copy(created = rsChange1.created.plus(10000))

      doReturn(Future.successful(ListRecordSetChangesResults(List(rsChange2, rsChange1))))
        .when(mockRecordChangeRepo)
        .listRecordSetChanges(zoneId = zoneAuthorized.id, startFrom = None, maxItems = 100)

      val result: ListRecordSetChangesResponse =
        rightResultOf(underTest.listRecordSetChanges(zoneAuthorized.id, authPrincipal = okAuth).run)
      val changesWithName =
        List(RecordSetChangeInfo(rsChange2, Some("ok")), RecordSetChangeInfo(rsChange1, Some("ok")))
      val expectedResults = ListRecordSetChangesResponse(
        zoneId = zoneAuthorized.id,
        recordSetChanges = changesWithName,
        nextId = None,
        startFrom = None,
        maxItems = 100)
      result shouldBe expectedResults
    }
  }

  "getRecordSetChange" should {
    "return the record set change if it is found" in {
      doReturn(Future.successful(Some(pendingCreateAAAA)))
        .when(mockRecordChangeRepo)
        .getRecordSetChange(pendingCreateAAAA.zoneId, pendingCreateAAAA.id)
      val actual: RecordSetChange = rightResultOf(
        underTest.getRecordSetChange(zoneAuthorized.id, pendingCreateAAAA.id, okAuth).run)
      actual shouldBe pendingCreateAAAA
    }

    "return a RecordSetChangeNotFoundError if it is not found" in {
      doReturn(Future.successful(None))
        .when(mockRecordChangeRepo)
        .getRecordSetChange(pendingCreateAAAA.zoneId, pendingCreateAAAA.id)
      val error = leftResultOf(
        underTest.getRecordSetChange(zoneAuthorized.id, pendingCreateAAAA.id, okAuth).run)
      error shouldBe a[RecordSetChangeNotFoundError]
    }

    "return a NotAuthorizedError if the user is not authorized to access the zone" in {
      val error = leftResultOf(
        underTest.getRecordSetChange(zoneNotAuthorized.id, pendingCreateAAAA.id, notAuth).run)

      error shouldBe a[NotAuthorizedError]
    }
  }

}
