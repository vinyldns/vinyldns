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
import org.mockito.Matchers.any
import org.mockito.Mockito.doReturn
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import vinyldns.api.ResultHelpers
import vinyldns.api.domain.access.AccessValidations
import vinyldns.api.domain.record.RecordSetHelpers._
import vinyldns.api.domain.zone._
import vinyldns.api.route.ListRecordSetsResponse
import vinyldns.core.TestMembershipData._
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import vinyldns.core.domain.HighValueDomainError
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.{GroupRepository, ListUsersResults, UserRepository}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.{AccessLevel, ZoneRepository}
import vinyldns.core.queue.MessageQueue

class RecordSetServiceSpec
    extends WordSpec
    with EitherMatchers
    with Matchers
    with MockitoSugar
    with ResultHelpers
    with BeforeAndAfterEach {

  private val mockZoneRepo = mock[ZoneRepository]
  private val mockGroupRepo = mock[GroupRepository]
  private val mockRecordRepo = mock[RecordSetRepository]
  private val mockRecordChangeRepo = mock[RecordChangeRepository]
  private val mockUserRepo = mock[UserRepository]
  private val mockMessageQueue = mock[MessageQueue]

  doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(okZone.id)
  doReturn(IO.pure(Some(zoneNotAuthorized)))
    .when(mockZoneRepo)
    .getZone(zoneNotAuthorized.id)
  doReturn(IO.unit).when(mockMessageQueue).send(any[RecordSetChange])
  doReturn(IO.pure(Some(sharedZoneRecord.copy(status = RecordSetStatus.Active))))
    .when(mockRecordRepo)
    .getRecordSet(sharedZoneRecord.zoneId, sharedZoneRecord.id)

  val underTest = new RecordSetService(
    mockZoneRepo,
    mockGroupRepo,
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
    "fail if the zone is not found" in {
      val mockZone = okZone.copy(id = "fakeZone")
      doReturn(IO.pure(None)).when(mockZoneRepo).getZone(mockZone.id)

      val result = leftResultOf(underTest.getRecordSet(aaaa.id, mockZone.id, okAuth).value)
      result shouldBe a[ZoneNotFoundError]
    }

    "fail when the account is not authorized" in {
      doReturn(IO.pure(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(zoneNotAuthorized.id, aaaa.id)
      val result = leftResultOf(underTest.getRecordSet(aaaa.id, zoneNotAuthorized.id, okAuth).value)
      result shouldBe a[NotAuthorizedError]
    }
    "fail if the record already exists" in {
      val record = aaaa

      doReturn(IO.pure(List(aaaa)))
        .when(mockRecordRepo)
        .getRecordSets(okZone.id, record.name, record.typ)

      val result = leftResultOf(underTest.addRecordSet(aaaa, okAuth).value)
      result shouldBe a[RecordSetAlreadyExists]
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
    "succeed if user is in owner group" in {
      val record = aaaa.copy(ownerGroupId = Some(okGroup.id))

      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSets(okZone.id, record.name, record.typ)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, record.name)
      doReturn(IO.pure(Some(okGroup)))
        .when(mockGroupRepo)
        .getGroup(okGroup.id)

      val result: RecordSetChange = rightResultOf(
        underTest.addRecordSet(record, okAuth).map(_.asInstanceOf[RecordSetChange]).value)

      result.recordSet.ownerGroupId shouldBe Some(okGroup.id)
    }
    "fail if user is in not owner group" in {
      val record = aaaa.copy(ownerGroupId = Some(dummyGroup.id))

      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSets(okZone.id, record.name, record.typ)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, record.name)
      doReturn(IO.pure(Some(dummyGroup)))
        .when(mockGroupRepo)
        .getGroup(dummyGroup.id)

      val result = leftResultOf(underTest.addRecordSet(record, okAuth).value)

      result shouldBe a[InvalidRequest]
    }
    "fail if owner group is not found" in {
      val record = aaaa.copy(ownerGroupId = Some(dummyGroup.id))

      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSets(okZone.id, record.name, record.typ)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, record.name)
      doReturn(IO.pure(None))
        .when(mockGroupRepo)
        .getGroup(dummyGroup.id)

      val result = leftResultOf(underTest.addRecordSet(record, okAuth).value)

      result shouldBe a[InvalidGroupError]
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
      doReturn(IO.pure(Some(aaaa.copy(zoneId = zoneNotAuthorized.id))))
        .when(mockRecordRepo)
        .getRecordSet(zoneNotAuthorized.id, aaaa.id)
      val result = leftResultOf(
        underTest.updateRecordSet(aaaa.copy(zoneId = zoneNotAuthorized.id), okAuth).value)
      result shouldBe a[NotAuthorizedError]
    }
    "fail if the new record name is dotted" in {
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
    "fail if user is in owner group but zone is not shared" in {
      val auth = AuthPrincipal(listOfDummyUsers.head, Seq(oneUserDummyGroup.id))
      val oldRecord = aaaa.copy(
        name = "test-owner-group-failure",
        zoneId = okZone.id,
        status = RecordSetStatus.Active,
        ownerGroupId = Some(oneUserDummyGroup.id))

      val newRecord = oldRecord.copy(ttl = oldRecord.ttl + 1000)

      val result = leftResultOf(underTest.updateRecordSet(newRecord, auth).value)
      result shouldBe a[NotAuthorizedError]
    }
    "fail if new owner group does not exist" in {
      val zone = okZone.copy(shared = true, id = "test-owner-group")
      val auth = AuthPrincipal(listOfDummyUsers.head, Seq(oneUserDummyGroup.id))

      val oldRecord = aaaa.copy(
        name = "test-owner-group-failure",
        zoneId = zone.id,
        status = RecordSetStatus.Active,
        ownerGroupId = Some(oneUserDummyGroup.id))

      val newRecord = oldRecord.copy(ownerGroupId = Some("doesnt-exist"))

      doReturn(IO.pure(Some(zone)))
        .when(mockZoneRepo)
        .getZone(zone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(zone.id, oldRecord.id)
      doReturn(IO.pure(List(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSetsByName(zone.id, oldRecord.name)
      doReturn(IO.pure(None))
        .when(mockGroupRepo)
        .getGroup("doesnt-exist")

      val result = leftResultOf(underTest.updateRecordSet(newRecord, auth).value)
      result shouldBe a[InvalidGroupError]
    }
    "fail if user not in new owner group" in {
      val zone = okZone.copy(shared = true, id = "test-owner-group")
      val auth = AuthPrincipal(listOfDummyUsers.head, Seq(oneUserDummyGroup.id))

      val oldRecord = aaaa.copy(
        name = "test-owner-group-failure",
        zoneId = zone.id,
        status = RecordSetStatus.Active,
        ownerGroupId = Some(oneUserDummyGroup.id))

      val newRecord = oldRecord.copy(ownerGroupId = Some(okGroup.id))

      doReturn(IO.pure(Some(zone)))
        .when(mockZoneRepo)
        .getZone(zone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(zone.id, oldRecord.id)
      doReturn(IO.pure(List(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSetsByName(zone.id, oldRecord.name)
      doReturn(IO.pure(Some(okGroup)))
        .when(mockGroupRepo)
        .getGroup(okGroup.id)

      val result = leftResultOf(underTest.updateRecordSet(newRecord, auth).value)
      result shouldBe a[InvalidRequest]
    }
    "succeed if user is in owner group and zone is shared" in {
      val zone = okZone.copy(shared = true, id = "test-owner-group")
      val auth = AuthPrincipal(listOfDummyUsers.head, Seq(oneUserDummyGroup.id))
      val oldRecord = aaaa.copy(
        name = "test-owner-group-success",
        zoneId = zone.id,
        status = RecordSetStatus.Active,
        ownerGroupId = Some(oneUserDummyGroup.id))

      val newRecord = oldRecord.copy(ttl = oldRecord.ttl + 1000)

      doReturn(IO.pure(Some(zone)))
        .when(mockZoneRepo)
        .getZone(zone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(zone.id, newRecord.id)
      doReturn(IO.pure(List(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSetsByName(zone.id, newRecord.name)
      doReturn(IO.pure(Some(oneUserDummyGroup)))
        .when(mockGroupRepo)
        .getGroup(oneUserDummyGroup.id)

      val result = rightResultOf(
        underTest.updateRecordSet(newRecord, auth).map(_.asInstanceOf[RecordSetChange]).value)

      result.recordSet.ttl shouldBe newRecord.ttl
      result.recordSet.ownerGroupId shouldBe Some(oneUserDummyGroup.id)
    }
    "succeed if user is in owner group and zone is shared and new owner group is none" in {
      val zone = okZone.copy(shared = true, id = "test-owner-group")
      val auth = AuthPrincipal(listOfDummyUsers.head, Seq(oneUserDummyGroup.id))
      val oldRecord = aaaa.copy(
        name = "test-owner-group-success",
        zoneId = zone.id,
        status = RecordSetStatus.Active,
        ownerGroupId = Some(oneUserDummyGroup.id))

      val newRecord = oldRecord.copy(ownerGroupId = None)

      doReturn(IO.pure(Some(zone)))
        .when(mockZoneRepo)
        .getZone(zone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(zone.id, newRecord.id)
      doReturn(IO.pure(List(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSetsByName(zone.id, newRecord.name)

      val result = rightResultOf(
        underTest.updateRecordSet(newRecord, auth).map(_.asInstanceOf[RecordSetChange]).value)

      result.recordSet.ttl shouldBe newRecord.ttl
      result.recordSet.ownerGroupId shouldBe None
    }
    "fail if the retrieved recordSet's zoneId does not match the payload zoneId" in {
      val oldRecord = aaaa.copy(zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = aaaa.copy(zoneId = abcZone.id)

      val auth = okAuth.copy(memberGroupIds = okAuth.memberGroupIds :+ abcZone.adminGroupId)

      doReturn(IO.pure(Some(abcZone)))
        .when(mockZoneRepo)
        .getZone(newRecord.zoneId)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(newRecord.zoneId, newRecord.id)

      val result = leftResultOf(underTest.updateRecordSet(newRecord, auth).value)
      result shouldBe a[InvalidRequest]
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
    "fail for user who is not in record owner group in shared zone" in {
      val result =
        leftResultOf(
          underTest.deleteRecordSet(sharedZoneRecord.id, sharedZoneRecord.zoneId, dummyAuth).value)

      result shouldBe a[NotAuthorizedError]
    }
    "fail for user who is in record owner group in non-shared zone" in {
      doReturn(IO.pure(Some(sharedZone.copy(shared = false))))
        .when(mockZoneRepo)
        .getZone(sharedZone.id)

      val result =
        leftResultOf(
          underTest.deleteRecordSet(sharedZoneRecord.id, sharedZoneRecord.zoneId, okAuth).value)

      result shouldBe a[NotAuthorizedError]
    }
    "succeed for user in record owner group in shared zone" in {
      doReturn(IO.pure(Some(sharedZone)))
        .when(mockZoneRepo)
        .getZone(sharedZone.id)

      val result =
        underTest
          .deleteRecordSet(sharedZoneRecord.id, sharedZoneRecord.zoneId, okAuth)
          .value
          .unsafeRunSync()

      result should be(right)
    }
    "succeed for zone admin in shared zone" in {
      val result =
        underTest
          .deleteRecordSet(sharedZoneRecord.id, sharedZoneRecord.zoneId, sharedAuth)
          .value
          .unsafeRunSync()

      result should be(right)
    }
    "fail for super user if not zone admin" in {
      val result =
        underTest
          .deleteRecordSet(sharedZoneRecord.id, sharedZoneRecord.zoneId, superUserAuth)
          .value
          .unsafeRunSync()

      result should be(left)
    }
  }

  "getRecordSet" should {
    doReturn(IO.pure(Some(sharedZone))).when(mockZoneRepo).getZone(sharedZone.id)

    "return the record if user is a zone admin" in {
      doReturn(IO.pure(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(okZone.id, aaaa.id)
      val expectedRecordSetInfo = RecordSetInfo(aaaa, None)

      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(any[String])

      val result: RecordSetInfo =
        rightResultOf(underTest.getRecordSet(aaaa.id, okZone.id, okAuth).value)
      result shouldBe expectedRecordSetInfo
    }

    "fail if the record does not exist" in {
      val mockRecord = rsOk.copy(id = "faker")

      doReturn(IO.pure(None))
        .when(mockRecordRepo)
        .getRecordSet(okZone.id, mockRecord.id)

      val result = leftResultOf(underTest.getRecordSet(mockRecord.id, okZone.id, okAuth).value)

      result shouldBe a[RecordSetNotFoundError]
    }

    "return the record if the user is in the recordSet owner group in a shared zone" in {
      doReturn(IO.pure(Some(sharedZoneRecord)))
        .when(mockRecordRepo)
        .getRecordSet(sharedZone.id, sharedZoneRecord.id)

      doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(any[String])

      val expectedRecordSetInfo = RecordSetInfo(sharedZoneRecord, Some(okGroup.name))
      val result: RecordSetInfo =
        rightResultOf(underTest.getRecordSet(sharedZoneRecord.id, sharedZone.id, okAuth).value)
      result shouldBe expectedRecordSetInfo
    }

    "return the record if the recordSet owner group cannot be found but user is an admin" in {
      doReturn(IO.pure(Some(sharedZoneRecord)))
        .when(mockRecordRepo)
        .getRecordSet(sharedZone.id, sharedZoneRecord.id)

      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(any[String])

      val expectedRecordSetInfo = RecordSetInfo(sharedZoneRecord, None)

      val result: RecordSetInfo =
        rightResultOf(underTest.getRecordSet(sharedZoneRecord.id, sharedZone.id, sharedAuth).value)
      result shouldBe expectedRecordSetInfo
    }

    "fail when the account is not authorized to access the zone" in {
      doReturn(IO.pure(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(zoneNotAuthorized.id, aaaa.id)

      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(any[String])

      val result = leftResultOf(underTest.getRecordSet(aaaa.id, zoneNotAuthorized.id, okAuth).value)
      result shouldBe a[NotAuthorizedError]
    }

    "return the unowned record in a shared zone when the record has an approved record type" in {
      doReturn(IO.pure(Some(sharedZoneRecordNoOwnerGroup)))
        .when(mockRecordRepo)
        .getRecordSet(sharedZone.id, sharedZoneRecordNotFoundOwnerGroup.id)

      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(any[String])

      val expectedRecordSetInfo = RecordSetInfo(sharedZoneRecordNoOwnerGroup, None)

      val result: RecordSetInfo =
        rightResultOf(
          underTest.getRecordSet(sharedZoneRecordNoOwnerGroup.id, sharedZone.id, sharedAuth).value)
      result shouldBe expectedRecordSetInfo
    }

    "fail when the unowned record in a shared zone is not an approved record type and user is unassociated with it" in {
      doReturn(IO.pure(Some(sharedZoneRecordNotApprovedRecordType)))
        .when(mockRecordRepo)
        .getRecordSet(sharedZone.id, sharedZoneRecordNotApprovedRecordType.id)

      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(any[String])

      val result =
        leftResultOf(
          underTest
            .getRecordSet(sharedZoneRecordNotApprovedRecordType.id, sharedZone.id, okAuth)
            .value)
      result shouldBe a[NotAuthorizedError]
    }

    "succeed when a record in a shared zone has no owner group ID" in {
      doReturn(IO.pure(Some(sharedZoneRecordNoOwnerGroup)))
        .when(mockRecordRepo)
        .getRecordSet(sharedZone.id, sharedZoneRecordNoOwnerGroup.id)

      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(any[String])

      val result = underTest
        .getRecordSet(sharedZoneRecordNoOwnerGroup.id, sharedZone.id, okAuth)
        .value
        .unsafeRunSync()

      result should be(right)
    }

    "fail if the user is only in the recordSet owner group but the zone is not shared" in {
      doReturn(IO.pure(Some(notSharedZoneRecordWithOwnerGroup)))
        .when(mockRecordRepo)
        .getRecordSet(zoneNotAuthorized.id, notSharedZoneRecordWithOwnerGroup.id)

      doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(any[String])

      val result = leftResultOf(
        underTest
          .getRecordSet(notSharedZoneRecordWithOwnerGroup.id, zoneNotAuthorized.id, okAuth)
          .value)
      result shouldBe a[NotAuthorizedError]
    }
  }

  "getGroupName" should {
    "return the group name if a record owner group ID is present" in {
      doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(any[String])

      val result = rightResultOf(underTest.getGroupName(Some(okGroup.id)).value)
      result shouldBe Some("ok")
    }

    "return None if a record owner group ID is not present" in {
      val result = rightResultOf(underTest.getGroupName(None).value)
      result shouldBe None
    }
  }

  "listRecordSets" should {
    "return the recordSets" in {
      doReturn(IO.pure(Set(okGroup)))
        .when(mockGroupRepo)
        .getGroups(Set(okGroup.id, "not-in-backend"))

      doReturn(
        IO.pure(ListRecordSetResults(List(sharedZoneRecord, sharedZoneRecordNotFoundOwnerGroup))))
        .when(mockRecordRepo)
        .listRecordSets(
          zoneId = sharedZone.id,
          startFrom = None,
          maxItems = None,
          recordNameFilter = None)

      val result: ListRecordSetsResponse = rightResultOf(
        underTest
          .listRecordSets(
            sharedZone.id,
            startFrom = None,
            maxItems = None,
            recordNameFilter = None,
            authPrincipal = sharedAuth)
          .value)
      result.recordSets shouldBe
        List(
          RecordSetListInfo(
            RecordSetInfo(sharedZoneRecord, Some(okGroup.name)),
            AccessLevel.Delete),
          RecordSetListInfo(
            RecordSetInfo(sharedZoneRecordNotFoundOwnerGroup, None),
            AccessLevel.Delete)
        )
    }
    "return the recordSet for support admin" in {
      doReturn(IO.pure(Set()))
        .when(mockGroupRepo)
        .getGroups(Set())

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
      result.recordSets shouldBe List(
        RecordSetListInfo(RecordSetInfo(aaaa, None), AccessLevel.Read))
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

    "return the record set change if the user is in the record owner group in a shared zone" in {
      doReturn(IO.pure(Some(pendingCreateSharedRecord)))
        .when(mockRecordChangeRepo)
        .getRecordSetChange(sharedZone.id, pendingCreateSharedRecord.id)

      val actual: RecordSetChange =
        rightResultOf(
          underTest.getRecordSetChange(sharedZone.id, pendingCreateSharedRecord.id, okAuth).value)
      actual shouldBe pendingCreateSharedRecord
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
      doReturn(IO.pure(Some(zoneActive))).when(mockZoneRepo).getZone(zoneActive.id)
      doReturn(IO.pure(Some(pendingCreateAAAA)))
        .when(mockRecordChangeRepo)
        .getRecordSetChange(zoneActive.id, pendingCreateAAAA.id)

      val error = leftResultOf(
        underTest.getRecordSetChange(zoneActive.id, pendingCreateAAAA.id, dummyAuth).value)

      error shouldBe a[NotAuthorizedError]
    }

    "return a NotAuthorizedError if the user is in the record owner group but the zone is not shared" in {
      doReturn(IO.pure(Some(zoneNotAuthorized))).when(mockZoneRepo).getZone(zoneNotAuthorized.id)
      doReturn(IO.pure(Some(pendingCreateSharedRecordNotSharedZone)))
        .when(mockRecordChangeRepo)
        .getRecordSetChange(zoneNotAuthorized.id, pendingCreateSharedRecordNotSharedZone.id)

      val error = leftResultOf(underTest
        .getRecordSetChange(zoneNotAuthorized.id, pendingCreateSharedRecordNotSharedZone.id, okAuth)
        .value)

      error shouldBe a[NotAuthorizedError]
    }
  }

}
