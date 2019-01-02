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
import vinyldns.api.domain.{AccessValidations, HighValueDomainError}
import vinyldns.api.domain.record.RecordSetHelpers._
import vinyldns.api.domain.zone._
import vinyldns.api.route.ListRecordSetsResponse
import vinyldns.api.ResultHelpers
import cats.effect._
import vinyldns.core.TestMembershipData._
import vinyldns.core.TestZoneData._
import vinyldns.core.TestRecordSetData._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.{ListUsersResults, UserRepository}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.{AccessLevel, ZoneRepository}
import vinyldns.core.queue.MessageQueue

class RecordSetServiceSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with Eventually
    with ResultHelpers
    with BeforeAndAfterEach {

  private val mockZoneRepo = mock[ZoneRepository]
  private val mockRecordRepo = mock[RecordSetRepository]
  private val mockRecordChangeRepo = mock[RecordChangeRepository]
  private val mockUserRepo = mock[UserRepository]
  private val mockMessageQueue = mock[MessageQueue]

  doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(okZone.id)
  doReturn(IO.pure(Some(zoneNotAuthorized)))
    .when(mockZoneRepo)
    .getZone(zoneNotAuthorized.id)
  doReturn(IO.unit).when(mockMessageQueue).send(any[RecordSetChange])

  val underTest = new RecordSetService(
    mockZoneRepo,
    mockRecordRepo,
    mockRecordChangeRepo,
    mockUserRepo,
    mockMessageQueue,
    AccessValidations)

  "addRecordSet" should {
    "return the recordSet change as the result" in {
      val record = aaaa.copy(zoneId = okZone.id)

      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSets(okZone.id, record.name, record.typ)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, record.name)

      val result: RecordSetChange =
        rightResultOf(
          underTest.addRecordSet(record, okAuth).map(_.asInstanceOf[RecordSetChange]).value)

      matches(result.recordSet, record, okZone.name) shouldBe true
      result.changeType shouldBe RecordSetChangeType.Create
      result.status shouldBe RecordSetChangeStatus.Pending
    }
    "fail when the account is not authorized" in {
      doReturn(IO.pure(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(zoneNotAuthorized.id, aaaa.id)
      val result = leftResultOf(underTest.getRecordSet(aaaa.id, zoneNotAuthorized.id, okAuth).value)
      result shouldBe a[NotAuthorizedError]
    }
    "fail if the record is dotted" in {
      val record =
        aaaa.copy(name = "new.name", zoneId = okZone.id, status = RecordSetStatus.Active)

      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSets(okZone.id, record.name, record.typ)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, record.name)

      val result = leftResultOf(underTest.addRecordSet(record, okAuth).value)
      result shouldBe a[InvalidRequest]
    }
    "fail if the record is relative with trailing dot" in {
      val record =
        aaaa.copy(name = "new.", zoneId = okZone.id, status = RecordSetStatus.Active)

      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSets(okZone.id, record.name, record.typ)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, record.name)

      val result = leftResultOf(underTest.addRecordSet(record, okAuth).value)
      result shouldBe a[InvalidRequest]
    }
    "fail if the record is a high value domain" in {
      val record =
        aaaa.copy(name = "high-value-domain", zoneId = okZone.id, status = RecordSetStatus.Active)

      val result = leftResultOf(underTest.addRecordSet(record, okAuth).value)
      result shouldBe InvalidRequest(
        HighValueDomainError(s"high-value-domain.${okZone.name}").message)
    }
    "succeed if record is apex with dot" in {
      val name = okZone.name
      val record =
        aaaa.copy(name = name, zoneId = okZone.id, status = RecordSetStatus.Active)

      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSets(okZone.id, record.name, record.typ)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, record.name)

      val result: RecordSetChange = rightResultOf(
        underTest.addRecordSet(record, okAuth).map(_.asInstanceOf[RecordSetChange]).value)

      result.recordSet.name shouldBe okZone.name
    }
    "succeed if record is apex as '@'" in {
      val name = "@"
      val record =
        aaaa.copy(name = name, zoneId = okZone.id, status = RecordSetStatus.Active)

      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSets(okZone.id, record.name, record.typ)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, record.name)

      val result: RecordSetChange = rightResultOf(
        underTest.addRecordSet(record, okAuth).map(_.asInstanceOf[RecordSetChange]).value)

      result.recordSet.name shouldBe okZone.name
    }
    "succeed if record is apex without dot" in {
      val name = okZone.name.substring(0, okZone.name.length - 1)
      val record =
        aaaa.copy(name = name, zoneId = okZone.id, status = RecordSetStatus.Active)

      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSets(okZone.id, record.name, record.typ)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, record.name)

      val result: RecordSetChange = rightResultOf(
        underTest.addRecordSet(record, okAuth).map(_.asInstanceOf[RecordSetChange]).value)

      result.recordSet.name shouldBe okZone.name
    }
  }

  "updateRecordSet" should {
    "return the recordSet change as the result" in {
      val oldRecord = aaaa.copy(zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(name = "newName")

      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(okZone.id, newRecord.id)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)

      val result: RecordSetChange = rightResultOf(
        underTest.updateRecordSet(newRecord, okAuth).map(_.asInstanceOf[RecordSetChange]).value)

      matches(result.recordSet, newRecord, okZone.name) shouldBe true
      matches(result.updates.get, oldRecord, okZone.name) shouldBe true
      result.changeType shouldBe RecordSetChangeType.Update
      result.status shouldBe RecordSetChangeStatus.Pending
    }
    "fail when the account is not authorized" in {
      doReturn(IO.pure(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(zoneNotAuthorized.id, aaaa.id)
      val result = leftResultOf(
        underTest.updateRecordSet(aaaa.copy(zoneId = zoneNotAuthorized.id), okAuth).value)
      result shouldBe a[NotAuthorizedError]
    }
    "fail if the record is dotted" in {
      val oldRecord = aaaa.copy(zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(name = "new.name")

      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(okZone.id, newRecord.id)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)

      val result = leftResultOf(underTest.updateRecordSet(newRecord, okAuth).value)
      result shouldBe a[InvalidRequest]
    }
    "fail if the record is relative with trailing dot" in {
      val oldRecord = aaaa.copy(zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(name = "new.")

      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(okZone.id, newRecord.id)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)

      val result = leftResultOf(underTest.updateRecordSet(newRecord, okAuth).value)
      result shouldBe a[InvalidRequest]
    }
    "succeed if record is apex with dot" in {
      val name = okZone.name
      val oldRecord = aaaa.copy(zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(name = name)

      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(okZone.id, newRecord.id)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)

      val result: RecordSetChange = rightResultOf(
        underTest.updateRecordSet(newRecord, okAuth).map(_.asInstanceOf[RecordSetChange]).value)

      result.recordSet.name shouldBe okZone.name
    }
    "succeed if record is apex as '@'" in {
      val name = "@"
      val oldRecord = aaaa.copy(zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(name = name)

      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(okZone.id, newRecord.id)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)

      val result: RecordSetChange = rightResultOf(
        underTest.updateRecordSet(newRecord, okAuth).map(_.asInstanceOf[RecordSetChange]).value)

      result.recordSet.name shouldBe okZone.name
    }
    "succeed if record is apex without dot" in {
      val name = okZone.name.substring(0, okZone.name.length - 1)
      val oldRecord = aaaa.copy(zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(name = name)

      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(okZone.id, newRecord.id)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)

      val result: RecordSetChange = rightResultOf(
        underTest.updateRecordSet(newRecord, okAuth).map(_.asInstanceOf[RecordSetChange]).value)

      result.recordSet.name shouldBe okZone.name
    }
    "fail if the record is a high value domain" in {
      val oldRecord =
        aaaa.copy(name = "high-value-domain", zoneId = okZone.id, status = RecordSetStatus.Active)

      val newRecord = oldRecord.copy(ttl = oldRecord.ttl + 1000)

      val result = leftResultOf(underTest.updateRecordSet(newRecord, okAuth).value)
      result shouldBe InvalidRequest(
        HighValueDomainError(s"high-value-domain.${okZone.name}").message)
    }
  }

  "deleteRecordSet" should {
    "return the recordSet change as the result" in {
      val record = aaaa.copy(status = RecordSetStatus.Active)
      doReturn(IO.pure(Some(record)))
        .when(mockRecordRepo)
        .getRecordSet(okZone.id, record.id)

      val result: RecordSetChange = rightResultOf(
        underTest
          .deleteRecordSet(record.id, okZone.id, okAuth)
          .map(_.asInstanceOf[RecordSetChange])
          .value)

      matches(result.recordSet, record, okZone.name) shouldBe true
      result.changeType shouldBe RecordSetChangeType.Delete
      result.status shouldBe RecordSetChangeStatus.Pending
    }
    "fails when the account is not authorized" in {
      doReturn(IO.pure(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(zoneNotAuthorized.id, aaaa.id)
      val result =
        leftResultOf(underTest.deleteRecordSet(aaaa.id, zoneNotAuthorized.id, okAuth).value)
      result shouldBe a[NotAuthorizedError]
    }
    "fail if the record is a high value domain" in {
      val record =
        aaaa.copy(name = "high-value-domain", zoneId = okZone.id, status = RecordSetStatus.Active)

      doReturn(IO.pure(Some(record)))
        .when(mockRecordRepo)
        .getRecordSet(okZone.id, record.id)

      val result =
        leftResultOf(underTest.deleteRecordSet(record.id, okZone.id, okAuth).value)
      result shouldBe InvalidRequest(
        HighValueDomainError(s"high-value-domain.${okZone.name}").message)
    }
  }

  "getRecordSet" should {
    "return the recordSet" in {
      doReturn(IO.pure(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(okZone.id, aaaa.id)
      val result: RecordSet =
        rightResultOf(underTest.getRecordSet(aaaa.id, okZone.id, okAuth).value)
      result shouldBe aaaa
    }
    "fail when the account is not authorized" in {
      doReturn(IO.pure(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(zoneNotAuthorized.id, aaaa.id)
      val result = leftResultOf(underTest.getRecordSet(aaaa.id, zoneNotAuthorized.id, okAuth).value)
      result shouldBe a[NotAuthorizedError]
    }
  }

  "listRecordSets" should {
    "return the recordSets" in {
      doReturn(IO.pure(ListRecordSetResults(List(aaaa))))
        .when(mockRecordRepo)
        .listRecordSets(
          zoneId = okZone.id,
          startFrom = None,
          maxItems = None,
          recordNameFilter = None)

      val result: ListRecordSetsResponse = rightResultOf(
        underTest
          .listRecordSets(
            okZone.id,
            startFrom = None,
            maxItems = None,
            recordNameFilter = None,
            authPrincipal = okAuth)
          .value)
      result.recordSets shouldBe List(RecordSetInfo(aaaa, AccessLevel.Delete))
    }
    "return the recordSet for support admin" in {
      doReturn(IO.pure(ListRecordSetResults(List(aaaa))))
        .when(mockRecordRepo)
        .listRecordSets(
          zoneId = okZone.id,
          startFrom = None,
          maxItems = None,
          recordNameFilter = None)

      val result: ListRecordSetsResponse = rightResultOf(
        underTest
          .listRecordSets(
            okZone.id,
            startFrom = None,
            maxItems = None,
            recordNameFilter = None,
            authPrincipal = AuthPrincipal(okAuth.signedInUser.copy(isSupport = true), Seq.empty)
          )
          .value)
      result.recordSets shouldBe List(RecordSetInfo(aaaa, AccessLevel.Read))
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
          .value)
      result shouldBe a[NotAuthorizedError]
    }
  }

  "listRecordSetChanges" should {
    "retrieve the recordset changes" in {
      val completeRecordSetChanges: List[RecordSetChange] =
        List(pendingCreateAAAA, pendingCreateCNAME, completeCreateAAAA, completeCreateCNAME)

      doReturn(IO.pure(ListRecordSetChangesResults(completeRecordSetChanges)))
        .when(mockRecordChangeRepo)
        .listRecordSetChanges(zoneId = okZone.id, startFrom = None, maxItems = 100)
      doReturn(IO.pure(ListUsersResults(Seq(okUser), None)))
        .when(mockUserRepo)
        .getUsers(any[Set[String]], any[Option[String]], any[Option[Int]])

      val result: ListRecordSetChangesResponse =
        rightResultOf(underTest.listRecordSetChanges(okZone.id, authPrincipal = okAuth).value)
      val changesWithName =
        completeRecordSetChanges.map(change => RecordSetChangeInfo(change, Some("ok")))
      val expectedResults = ListRecordSetChangesResponse(
        zoneId = okZone.id,
        recordSetChanges = changesWithName,
        nextId = None,
        startFrom = None,
        maxItems = 100)
      result shouldBe expectedResults
    }

    "return a zone with no changes if no changes exist" in {
      doReturn(IO.pure(ListRecordSetChangesResults(items = Nil)))
        .when(mockRecordChangeRepo)
        .listRecordSetChanges(zoneId = okZone.id, startFrom = None, maxItems = 100)

      val result: ListRecordSetChangesResponse =
        rightResultOf(underTest.listRecordSetChanges(okZone.id, authPrincipal = okAuth).value)
      val expectedResults = ListRecordSetChangesResponse(
        zoneId = okZone.id,
        recordSetChanges = List(),
        nextId = None,
        startFrom = None,
        maxItems = 100)
      result shouldBe expectedResults
    }

    "return a NotAuthorizedError" in {
      val error = leftResultOf(
        underTest.listRecordSetChanges(zoneNotAuthorized.id, authPrincipal = okAuth).value)
      error shouldBe a[NotAuthorizedError]
    }

    "return the record set changes sorted by created date desc" in {
      val rsChange1 = pendingCreateAAAA
      val rsChange2 = pendingCreateCNAME.copy(created = rsChange1.created.plus(10000))

      doReturn(IO.pure(ListRecordSetChangesResults(List(rsChange2, rsChange1))))
        .when(mockRecordChangeRepo)
        .listRecordSetChanges(zoneId = okZone.id, startFrom = None, maxItems = 100)

      val result: ListRecordSetChangesResponse =
        rightResultOf(underTest.listRecordSetChanges(okZone.id, authPrincipal = okAuth).value)
      val changesWithName =
        List(RecordSetChangeInfo(rsChange2, Some("ok")), RecordSetChangeInfo(rsChange1, Some("ok")))
      val expectedResults = ListRecordSetChangesResponse(
        zoneId = okZone.id,
        recordSetChanges = changesWithName,
        nextId = None,
        startFrom = None,
        maxItems = 100)
      result shouldBe expectedResults
    }
  }

  "getRecordSetChange" should {
    "return the record set change if it is found" in {
      doReturn(IO.pure(Some(pendingCreateAAAA)))
        .when(mockRecordChangeRepo)
        .getRecordSetChange(okZone.id, pendingCreateAAAA.id)

      val actual: RecordSetChange =
        rightResultOf(underTest.getRecordSetChange(okZone.id, pendingCreateAAAA.id, okAuth).value)
      actual shouldBe pendingCreateAAAA
    }

    "return a RecordSetChangeNotFoundError if it is not found" in {
      doReturn(IO.pure(None))
        .when(mockRecordChangeRepo)
        .getRecordSetChange(okZone.id, pendingCreateAAAA.id)
      val error =
        leftResultOf(underTest.getRecordSetChange(okZone.id, pendingCreateAAAA.id, okAuth).value)
      error shouldBe a[RecordSetChangeNotFoundError]
    }

    "return a NotAuthorizedError if the user is not authorized to access the zone" in {
      val error = leftResultOf(
        underTest.getRecordSetChange(zoneNotAuthorized.id, pendingCreateAAAA.id, dummyAuth).value)

      error shouldBe a[NotAuthorizedError]
    }
  }

}
