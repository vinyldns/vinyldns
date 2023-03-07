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
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterEach
import vinyldns.api.{ResultHelpers, VinylDNSTestHelpers}
import vinyldns.api.domain.access.AccessValidations
import vinyldns.api.domain.record.RecordSetHelpers._
import vinyldns.api.domain.zone._
import vinyldns.api.route.{ListGlobalRecordSetsResponse, ListRecordSetsByZoneResponse}
import vinyldns.core.TestMembershipData._
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import vinyldns.core.domain.HighValueDomainError
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.backend.{Backend, BackendResolver}
import vinyldns.core.domain.membership.{GroupRepository, ListUsersResults, UserRepository}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._
import vinyldns.core.queue.MessageQueue

class RecordSetServiceSpec
  extends AnyWordSpec
    with EitherMatchers
    with Matchers
    with MockitoSugar
    with ResultHelpers
    with BeforeAndAfterEach {

  private val mockZoneRepo = mock[ZoneRepository]
  private val mockGroupRepo = mock[GroupRepository]
  private val mockRecordRepo = mock[RecordSetRepository]
  private val mockRecordDataRepo = mock[RecordSetCacheRepository]
  private val mockRecordChangeRepo = mock[RecordChangeRepository]
  private val mockUserRepo = mock[UserRepository]
  private val mockMessageQueue = mock[MessageQueue]
  private val mockBackend =
    mock[Backend]
  private val mockBackendResolver = mock[BackendResolver]

  doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(okZone.id)
  doReturn(IO.pure(Some(zoneNotAuthorized)))
    .when(mockZoneRepo)
    .getZone(zoneNotAuthorized.id)
  doReturn(IO.unit).when(mockMessageQueue).send(any[RecordSetChange])
  doReturn(IO.pure(Some(sharedZoneRecord.copy(status = RecordSetStatus.Active))))
    .when(mockRecordRepo)
    .getRecordSet(sharedZoneRecord.id)
  doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])

  val underTest = new RecordSetService(
    mockZoneRepo,
    mockGroupRepo,
    mockRecordRepo,
    mockRecordDataRepo,
    mockRecordChangeRepo,
    mockUserRepo,
    mockMessageQueue,
    new AccessValidations(
      sharedApprovedTypes = VinylDNSTestHelpers.sharedApprovedTypes
    ),
    mockBackendResolver,
    false,
    VinylDNSTestHelpers.highValueDomainConfig,
    VinylDNSTestHelpers.approvedNameServers,
    true
  )

  val underTestWithDnsBackendValidations = new RecordSetService(
    mockZoneRepo,
    mockGroupRepo,
    mockRecordRepo,
    mockRecordDataRepo,
    mockRecordChangeRepo,
    mockUserRepo,
    mockMessageQueue,
    new AccessValidations(
      sharedApprovedTypes = VinylDNSTestHelpers.sharedApprovedTypes
    ),
    mockBackendResolver,
    true,
    VinylDNSTestHelpers.highValueDomainConfig,
    VinylDNSTestHelpers.approvedNameServers,
    true
  )

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
          underTest.addRecordSet(record, okAuth).map(_.asInstanceOf[RecordSetChange]).value
        )

      matches(result.recordSet, record, okZone.name) shouldBe true
      result.changeType shouldBe RecordSetChangeType.Create
      result.status shouldBe RecordSetChangeStatus.Pending
    }
    "fail if the zone is not found" in {
      val mockZone = okZone.copy(id = "fakeZone")
      doReturn(IO.pure(None)).when(mockZoneRepo).getZone(mockZone.id)

      val result = leftResultOf(underTest.getRecordSetByZone(aaaa.id, mockZone.id, okAuth).value)
      result shouldBe a[ZoneNotFoundError]
    }

    "fail when the account is not authorized" in {
      doReturn(IO.pure(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(aaaa.id)
      val result =
        leftResultOf(underTest.getRecordSetByZone(aaaa.id, zoneNotAuthorized.id, okAuth).value)
      result shouldBe a[NotAuthorizedError]
    }
    "fail if the record already exists" in {
      val record = aaaa

      doReturn(IO.pure(List(aaaa)))
        .when(mockRecordRepo)
        .getRecordSets(okZone.id, record.name, record.typ)

      doReturn(IO(List(aaaa)))
        .when(mockBackend)
        .resolve(record.name, okZone.name, record.typ)

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
      result shouldBe an[InvalidRequest]
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

      val result =
        leftResultOf(underTestWithDnsBackendValidations.addRecordSet(record, okAuth).value)
      result shouldBe an[InvalidRequest]
    }
    "fail if the record is a high value domain" in {
      val record =
        aaaa.copy(name = "high-value-domain", zoneId = okZone.id, status = RecordSetStatus.Active)

      val result = leftResultOf(underTest.addRecordSet(record, okAuth).value)
      result shouldBe InvalidRequest(
        HighValueDomainError(s"high-value-domain.${okZone.name}").message
      )
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
        underTest.addRecordSet(record, okAuth).map(_.asInstanceOf[RecordSetChange]).value
      )

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
        underTest.addRecordSet(record, okAuth).map(_.asInstanceOf[RecordSetChange]).value
      )

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
        underTest.addRecordSet(record, okAuth).map(_.asInstanceOf[RecordSetChange]).value
      )

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
        underTest.addRecordSet(record, okAuth).map(_.asInstanceOf[RecordSetChange]).value
      )

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

      result shouldBe an[InvalidRequest]
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

      result shouldBe an[InvalidGroupError]
    }
    "succeed if record exists in database but not in DNS backend" in {
      val record = aaaa.copy(zoneId = okZone.id)

      doReturn(IO.pure(List(record)))
        .when(mockRecordRepo)
        .getRecordSets(okZone.id, record.name, record.typ)
      doReturn(IO(List()))
        .when(mockBackend)
        .resolve(record.name, okZone.name, record.typ)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, record.name)

      val result: RecordSetChange =
        rightResultOf(
          underTestWithDnsBackendValidations
            .addRecordSet(record, okAuth)
            .map(_.asInstanceOf[RecordSetChange])
            .value
        )

      matches(result.recordSet, record, okZone.name) shouldBe true
      result.changeType shouldBe RecordSetChangeType.Create
      result.status shouldBe RecordSetChangeStatus.Pending
    }
  }

  "updateRecordSet" should {
    "return the recordSet change as the result" in {
      val oldRecord = aaaa.copy(zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(ttl = oldRecord.ttl + 1000)

      doReturn(IO.pure(Some(okZone)))
        .when(mockZoneRepo)
        .getZone(okZone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(newRecord.id)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)

      val result: RecordSetChange = rightResultOf(
        underTest.updateRecordSet(newRecord, okAuth).map(_.asInstanceOf[RecordSetChange]).value
      )

      matches(result.recordSet, newRecord, okZone.name) shouldBe true
      matches(result.updates.get, oldRecord, okZone.name) shouldBe true
      result.changeType shouldBe RecordSetChangeType.Update
      result.status shouldBe RecordSetChangeStatus.Pending
    }
    "fail when the account is not authorized" in {
      doReturn(IO.pure(Some(zoneNotAuthorized)))
        .when(mockZoneRepo)
        .getZone(zoneNotAuthorized.id)
      doReturn(IO.pure(Some(aaaa.copy(zoneId = zoneNotAuthorized.id))))
        .when(mockRecordRepo)
        .getRecordSet(aaaa.id)
      val result = leftResultOf(
        underTest.updateRecordSet(aaaa.copy(zoneId = zoneNotAuthorized.id), okAuth).value
      )
      result shouldBe a[NotAuthorizedError]
    }
    "succeed if the dotted record name is unchanged" in {
      val oldRecord =
        aaaa.copy(name = "new.name", zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(ttl = oldRecord.ttl + 1000)

      doReturn(IO.pure(Some(okZone)))
        .when(mockZoneRepo)
        .getZone(okZone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(newRecord.id)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)

      val result: RecordSetChange = rightResultOf(
        underTest.updateRecordSet(newRecord, okAuth).map(_.asInstanceOf[RecordSetChange]).value
      )

      result.recordSet.name shouldBe oldRecord.name
      result.recordSet.ttl shouldBe oldRecord.ttl + 1000
    }
    "fail if the record is relative with trailing dot" in {
      val oldRecord = aaaa.copy(zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(name = "new.")

      doReturn(IO.pure(Some(okZone)))
        .when(mockZoneRepo)
        .getZone(okZone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(newRecord.id)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)

      val result = leftResultOf(underTest.updateRecordSet(newRecord, okAuth).value)
      result shouldBe an[InvalidRequest]
    }
    "succeed if record is apex with dot" in {
      val name = okZone.name
      val oldRecord = aaaa.copy(name = name, zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(ttl = oldRecord.ttl + 1000)

      doReturn(IO.pure(Some(okZone)))
        .when(mockZoneRepo)
        .getZone(okZone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(newRecord.id)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)

      val result: RecordSetChange = rightResultOf(
        underTest.updateRecordSet(newRecord, okAuth).map(_.asInstanceOf[RecordSetChange]).value
      )

      result.recordSet.name shouldBe okZone.name
      result.recordSet.ttl shouldBe oldRecord.ttl + 1000
    }
    "succeed if record is apex as '@'" in {
      val name = "@"
      val oldRecord = aaaa.copy(name = name, zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(ttl = oldRecord.ttl + 1000)

      doReturn(IO.pure(Some(okZone)))
        .when(mockZoneRepo)
        .getZone(okZone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(newRecord.id)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)

      val result: RecordSetChange = rightResultOf(
        underTest.updateRecordSet(newRecord, okAuth).map(_.asInstanceOf[RecordSetChange]).value
      )

      result.recordSet.name shouldBe okZone.name
      result.recordSet.ttl shouldBe oldRecord.ttl + 1000
    }
    "succeed if record is apex without dot" in {
      val name = okZone.name.substring(0, okZone.name.length - 1)
      val oldRecord = aaaa.copy(name = name, zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(ttl = oldRecord.ttl + 1000)

      doReturn(IO.pure(Some(okZone)))
        .when(mockZoneRepo)
        .getZone(okZone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(newRecord.id)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)

      val result: RecordSetChange = rightResultOf(
        underTest.updateRecordSet(newRecord, okAuth).map(_.asInstanceOf[RecordSetChange]).value
      )

      result.recordSet.name shouldBe okZone.name
      result.recordSet.ttl shouldBe oldRecord.ttl + 1000
    }
    "fail if the record is a high value domain" in {
      val oldRecord =
        aaaa.copy(name = "high-value-domain", zoneId = okZone.id, status = RecordSetStatus.Active)

      val newRecord = oldRecord.copy(ttl = oldRecord.ttl + 1000)

      doReturn(IO.pure(Some(okZone)))
        .when(mockZoneRepo)
        .getZone(okZone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(newRecord.id)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)

      val result = leftResultOf(underTest.updateRecordSet(newRecord, okAuth).value)
      result shouldBe InvalidRequest(
        HighValueDomainError(s"high-value-domain.${okZone.name}").message
      )
    }
    "fail if user is in owner group but zone is not shared" in {
      val auth = AuthPrincipal(listOfDummyUsers.head, Seq(oneUserDummyGroup.id))
      val oldRecord = aaaa.copy(
        name = "test-owner-group-failure",
        zoneId = okZone.id,
        status = RecordSetStatus.Active,
        ownerGroupId = Some(oneUserDummyGroup.id)
      )

      val newRecord = oldRecord.copy(ttl = oldRecord.ttl + 1000)

      doReturn(IO.pure(Some(okZone)))
        .when(mockZoneRepo)
        .getZone(okZone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(newRecord.id)
      doReturn(IO.pure(List()))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)

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
        ownerGroupId = Some(oneUserDummyGroup.id)
      )

      val newRecord = oldRecord.copy(ownerGroupId = Some("doesnt-exist"))

      doReturn(IO.pure(Some(zone)))
        .when(mockZoneRepo)
        .getZone(zone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(oldRecord.id)
      doReturn(IO.pure(List(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSetsByName(zone.id, oldRecord.name)
      doReturn(IO.pure(None))
        .when(mockGroupRepo)
        .getGroup("doesnt-exist")

      val result = leftResultOf(underTest.updateRecordSet(newRecord, auth).value)
      result shouldBe an[InvalidGroupError]
    }
    "fail if user not in new owner group" in {
      val zone = okZone.copy(shared = true, id = "test-owner-group")
      val auth = AuthPrincipal(listOfDummyUsers.head, Seq(oneUserDummyGroup.id))

      val oldRecord = aaaa.copy(
        name = "test-owner-group-failure",
        zoneId = zone.id,
        status = RecordSetStatus.Active,
        ownerGroupId = Some(oneUserDummyGroup.id)
      )

      val newRecord = oldRecord.copy(ownerGroupId = Some(okGroup.id))

      doReturn(IO.pure(Some(zone)))
        .when(mockZoneRepo)
        .getZone(zone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(oldRecord.id)
      doReturn(IO.pure(List(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSetsByName(zone.id, oldRecord.name)
      doReturn(IO.pure(Some(okGroup)))
        .when(mockGroupRepo)
        .getGroup(okGroup.id)

      val result = leftResultOf(underTest.updateRecordSet(newRecord, auth).value)
      result shouldBe an[InvalidRequest]
    }
    "succeed if user is in owner group and zone is shared" in {
      val zone = okZone.copy(shared = true, id = "test-owner-group")
      val auth = AuthPrincipal(listOfDummyUsers.head, Seq(oneUserDummyGroup.id))
      val oldRecord = aaaa.copy(
        name = "test-owner-group-success",
        zoneId = zone.id,
        status = RecordSetStatus.Active,
        ownerGroupId = Some(oneUserDummyGroup.id)
      )

      val newRecord = oldRecord.copy(ttl = oldRecord.ttl + 1000)

      doReturn(IO.pure(Some(zone)))
        .when(mockZoneRepo)
        .getZone(zone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(newRecord.id)
      doReturn(IO.pure(List(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSetsByName(zone.id, newRecord.name)
      doReturn(IO.pure(Some(oneUserDummyGroup)))
        .when(mockGroupRepo)
        .getGroup(oneUserDummyGroup.id)

      val result = rightResultOf(
        underTest.updateRecordSet(newRecord, auth).map(_.asInstanceOf[RecordSetChange]).value
      )

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
        ownerGroupId = Some(oneUserDummyGroup.id)
      )

      val newRecord = oldRecord.copy(ownerGroupId = None)

      doReturn(IO.pure(Some(zone)))
        .when(mockZoneRepo)
        .getZone(zone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(newRecord.id)
      doReturn(IO.pure(List(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSetsByName(zone.id, newRecord.name)

      val result = rightResultOf(
         underTest.updateRecordSet(newRecord, auth).map(_.asInstanceOf[RecordSetChange]).value
      )

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
        .getRecordSet(newRecord.id)

      val result = leftResultOf(underTest.updateRecordSet(newRecord, auth).value)
      result shouldBe an[InvalidRequest]
    }
    "succeed if new record exists in database but not in DNS backend" in {
      val oldRecord = aaaa.copy(zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(ttl = 3600)

      doReturn(IO.pure(Some(okZone)))
        .when(mockZoneRepo)
        .getZone(okZone.id)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(newRecord.id)
      doReturn(IO.pure(List(newRecord)))
        .when(mockRecordRepo)
        .getRecordSetsByName(okZone.id, newRecord.name)
      doReturn(IO(List()))
        .when(mockBackend)
        .resolve(newRecord.name, okZone.name, newRecord.typ)

      val result: RecordSetChange = rightResultOf(
        underTestWithDnsBackendValidations
          .updateRecordSet(newRecord, okAuth)
          .map(_.asInstanceOf[RecordSetChange])
          .value
      )

      matches(result.recordSet, newRecord, okZone.name) shouldBe true
      matches(result.updates.get, oldRecord, okZone.name) shouldBe true
      result.changeType shouldBe RecordSetChangeType.Update
      result.status shouldBe RecordSetChangeStatus.Pending
    }
    "fail if the retrieved recordSet's name does not match the payload name" in {
      val oldRecord =
        aaaa.copy(name = "oldRecordName", zoneId = okZone.id, status = RecordSetStatus.Active)
      val newRecord = oldRecord.copy(name = "newRecordName")

      val auth = okAuth.copy(memberGroupIds = okAuth.memberGroupIds :+ abcZone.adminGroupId)

      doReturn(IO.pure(Some(okZone)))
        .when(mockZoneRepo)
        .getZone(newRecord.zoneId)
      doReturn(IO.pure(Some(oldRecord)))
        .when(mockRecordRepo)
        .getRecordSet(newRecord.id)

      val result = leftResultOf(underTest.updateRecordSet(newRecord, auth).value)
      result shouldBe a[InvalidRequest]
    }
  }

  "deleteRecordSet" should {
    "return the recordSet change as the result" in {
      val record = aaaa.copy(status = RecordSetStatus.Active)
      doReturn(IO.pure(Some(record)))
        .when(mockRecordRepo)
        .getRecordSet(record.id)

      val result: RecordSetChange = rightResultOf(
        underTest
          .deleteRecordSet(record.id, okZone.id, okAuth)
          .map(_.asInstanceOf[RecordSetChange])
          .value
      )

      matches(result.recordSet, record, okZone.name) shouldBe true
      result.changeType shouldBe RecordSetChangeType.Delete
      result.status shouldBe RecordSetChangeStatus.Pending
    }
    "fails when the account is not authorized" in {
      doReturn(IO.pure(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(aaaa.id)
      val result =
        leftResultOf(underTest.deleteRecordSet(aaaa.id, zoneNotAuthorized.id, okAuth).value)
      result shouldBe a[NotAuthorizedError]
    }
    "fail if the record is a high value domain" in {
      val record =
        aaaa.copy(name = "high-value-domain", zoneId = okZone.id, status = RecordSetStatus.Active)

      doReturn(IO.pure(Some(record)))
        .when(mockRecordRepo)
        .getRecordSet(record.id)

      val result =
        leftResultOf(underTest.deleteRecordSet(record.id, okZone.id, okAuth).value)
      result shouldBe InvalidRequest(
        HighValueDomainError(s"high-value-domain.${okZone.name}").message
      )
    }
    "fail for user who is not in record owner group in shared zone" in {
      val result =
        leftResultOf(
          underTest.deleteRecordSet(sharedZoneRecord.id, sharedZoneRecord.zoneId, dummyAuth).value
        )

      result shouldBe a[NotAuthorizedError]
    }
    "fail for user who is in record owner group in non-shared zone" in {
      doReturn(IO.pure(Some(sharedZone.copy(shared = false))))
        .when(mockZoneRepo)
        .getZone(sharedZone.id)

      val result =
        leftResultOf(
          underTest.deleteRecordSet(sharedZoneRecord.id, sharedZoneRecord.zoneId, okAuth).value
        )

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

    "return the record if it exists" in {
      doReturn(IO.pure(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(aaaa.id)
      val expectedRecordSetInfo = RecordSetInfo(aaaa, None)

      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(any[String])

      val result: RecordSetInfo =
        rightResultOf(underTest.getRecordSet(aaaa.id, okAuth).value)
      result shouldBe expectedRecordSetInfo
    }

    "fail if the record does not exist" in {
      val mockRecord = rsOk.copy(id = "faker")

      doReturn(IO.pure(None))
        .when(mockRecordRepo)
        .getRecordSet(mockRecord.id)

      val result = leftResultOf(underTest.getRecordSet(mockRecord.id, okAuth).value)

      result shouldBe a[RecordSetNotFoundError]
    }
  }

  "getRecordSetByZone" should {
    doReturn(IO.pure(Some(sharedZone))).when(mockZoneRepo).getZone(sharedZone.id)

    "return the record if user is a zone admin" in {
      doReturn(IO.pure(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(aaaa.id)
      val expectedRecordSetInfo = RecordSetInfo(aaaa, None)

      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(any[String])

      val result: RecordSetInfo =
        rightResultOf(underTest.getRecordSetByZone(aaaa.id, okZone.id, okAuth).value)
      result shouldBe expectedRecordSetInfo
    }

    "fail if the record does not exist" in {
      val mockRecord = rsOk.copy(id = "faker")

      doReturn(IO.pure(None))
        .when(mockRecordRepo)
        .getRecordSet(mockRecord.id)

      val result =
        leftResultOf(underTest.getRecordSetByZone(mockRecord.id, okZone.id, okAuth).value)

      result shouldBe a[RecordSetNotFoundError]
    }

    "return the record if the user is in the recordSet owner group in a shared zone" in {
      doReturn(IO.pure(Some(sharedZoneRecord)))
        .when(mockRecordRepo)
        .getRecordSet(sharedZoneRecord.id)

      doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(any[String])

      val expectedRecordSetInfo = RecordSetInfo(sharedZoneRecord, Some(okGroup.name))
      val result: RecordSetInfo =
        rightResultOf(
          underTest.getRecordSetByZone(sharedZoneRecord.id, sharedZone.id, okAuth).value
        )
      result shouldBe expectedRecordSetInfo
    }

    "return the record if the recordSet owner group cannot be found but user is an admin" in {
      doReturn(IO.pure(Some(sharedZoneRecord)))
        .when(mockRecordRepo)
        .getRecordSet(sharedZoneRecord.id)

      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(any[String])

      val expectedRecordSetInfo = RecordSetInfo(sharedZoneRecord, None)

      val result: RecordSetInfo =
        rightResultOf(
          underTest.getRecordSetByZone(sharedZoneRecord.id, sharedZone.id, sharedAuth).value
        )
      result shouldBe expectedRecordSetInfo
    }

    "fail when the account is not authorized to access the zone" in {
      doReturn(IO.pure(Some(aaaa)))
        .when(mockRecordRepo)
        .getRecordSet(aaaa.id)

      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(any[String])

      val result =
        leftResultOf(underTest.getRecordSetByZone(aaaa.id, zoneNotAuthorized.id, okAuth).value)
      result shouldBe a[NotAuthorizedError]
    }

    "return the unowned record in a shared zone when the record has an approved record type" in {
      doReturn(IO.pure(Some(sharedZoneRecordNoOwnerGroup)))
        .when(mockRecordRepo)
        .getRecordSet(sharedZoneRecordNotFoundOwnerGroup.id)

      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(any[String])

      val expectedRecordSetInfo = RecordSetInfo(sharedZoneRecordNoOwnerGroup, None)

      val result: RecordSetInfo =
        rightResultOf(
          underTest
            .getRecordSetByZone(sharedZoneRecordNoOwnerGroup.id, sharedZone.id, sharedAuth)
            .value
        )
      result shouldBe expectedRecordSetInfo
    }

    "fail when the unowned record in a shared zone is not an approved record type and user is unassociated with it" in {
      doReturn(IO.pure(Some(sharedZoneRecordNotApprovedRecordType)))
        .when(mockRecordRepo)
        .getRecordSet(sharedZoneRecordNotApprovedRecordType.id)

      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(any[String])

      val result =
        leftResultOf(
          underTest
            .getRecordSetByZone(sharedZoneRecordNotApprovedRecordType.id, sharedZone.id, okAuth)
            .value
        )
      result shouldBe a[NotAuthorizedError]
    }

    "succeed when a record in a shared zone has no owner group ID" in {
      doReturn(IO.pure(Some(sharedZoneRecordNoOwnerGroup)))
        .when(mockRecordRepo)
        .getRecordSet(sharedZoneRecordNoOwnerGroup.id)

      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(any[String])

      val result = underTest
        .getRecordSetByZone(sharedZoneRecordNoOwnerGroup.id, sharedZone.id, okAuth)
        .value
        .unsafeRunSync()

      result should be(right)
    }

    "fail if the user is only in the recordSet owner group but the zone is not shared" in {
      doReturn(IO.pure(Some(notSharedZoneRecordWithOwnerGroup)))
        .when(mockRecordRepo)
        .getRecordSet(notSharedZoneRecordWithOwnerGroup.id)

      doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(any[String])

      val result = leftResultOf(
        underTest
          .getRecordSetByZone(notSharedZoneRecordWithOwnerGroup.id, zoneNotAuthorized.id, okAuth)
          .value
      )
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
        .getGroups(any[Set[String]])

      doReturn(IO.pure(Set(sharedZone)))
        .when(mockZoneRepo)
        .getZones(Set(sharedZone.id))

      doReturn(
        IO.pure(
          ListRecordSetResults(
            List(sharedZoneRecord),
            recordNameFilter = Some("aaaa*"),
            nameSort = NameSort.ASC,
            recordOwnerGroupFilter = Some("owner group id")
          )
        )
      ).when(mockRecordRepo)
        .listRecordSets(
          zoneId = any[Option[String]],
          startFrom = any[Option[String]],
          maxItems = any[Option[Int]],
          recordNameFilter = any[Option[String]],
          recordTypeFilter = any[Option[Set[RecordType.RecordType]]],
          recordOwnerGroupFilter = any[Option[String]],
          nameSort = any[NameSort.NameSort]
        )

      val result: ListGlobalRecordSetsResponse = rightResultOf(
        underTest
          .listRecordSets(
            startFrom = None,
            maxItems = None,
            recordNameFilter = "aaaa*",
            recordTypeFilter = None,
            recordOwnerGroupFilter = Some("owner group id"),
            nameSort = NameSort.ASC,
            authPrincipal = sharedAuth
          )
          .value
      )
      result.recordSets shouldBe
        List(
          RecordSetGlobalInfo(
            sharedZoneRecord,
            sharedZone.name,
            sharedZone.shared,
            Some(okGroup.name)
          )
        )
    }

    "fail if recordNameFilter is fewer than two characters" in {
      val result = leftResultOf(
        underTest
          .listRecordSets(
            startFrom = None,
            maxItems = None,
            recordNameFilter = "a",
            recordTypeFilter = None,
            recordOwnerGroupFilter = Some("owner group id"),
            nameSort = NameSort.ASC,
            authPrincipal = okAuth
          )
          .value
      )
      result shouldBe an[InvalidRequest]
    }
  }

  "listRecordSetData" should {
    "return the recordSetData" in {
      doReturn(IO.pure(Set(okGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      doReturn(IO.pure(Set(sharedZone)))
        .when(mockZoneRepo)
        .getZones(Set(sharedZone.id))

      doReturn(
        IO.pure(
          ListRecordSetResults(
            List(sharedZoneRecord),
            recordNameFilter = Some("aaaa*"),
            nameSort = NameSort.ASC,
            recordOwnerGroupFilter = Some("owner group id")
          )
        )
      ).when(mockRecordDataRepo)
        .listRecordSetData(
          zoneId = any[Option[String]],
          startFrom = any[Option[String]],
          maxItems = any[Option[Int]],
          recordNameFilter = any[Option[String]],
          recordTypeFilter = any[Option[Set[RecordType.RecordType]]],
          recordOwnerGroupFilter = any[Option[String]],
          nameSort = any[NameSort.NameSort]
        )

      val result = rightResultOf(
        underTest
          .searchRecordSets(
            startFrom = None,
            maxItems = None,
            recordNameFilter = "aaaa*",
            recordTypeFilter = None,
            recordOwnerGroupFilter = Some("owner group id"),
            nameSort = NameSort.ASC,
            authPrincipal = sharedAuth
          )
          .value
      )
      result.recordSets shouldBe
        List(
          RecordSetGlobalInfo(
            sharedZoneRecord,
            sharedZone.name,
            sharedZone.shared,
            Some(okGroup.name)
          )
        )
    }

    "fail recordSetData if recordNameFilter is fewer than two characters" in {
      val result = leftResultOf(
        underTest
          .searchRecordSets(
            startFrom = None,
            maxItems = None,
            recordNameFilter = "a",
            recordTypeFilter = None,
            recordOwnerGroupFilter = Some("owner group id"),
            nameSort = NameSort.ASC,
            authPrincipal = okAuth
          )
          .value
      )
      result shouldBe an[InvalidRequest]
    }
  }


  "listRecordSetsByZone" should {
    "return the recordSets" in {
      doReturn(IO.pure(Set(okGroup)))
        .when(mockGroupRepo)
        .getGroups(Set(okGroup.id, "not-in-backend"))

      doReturn(
        IO.pure(
          ListRecordSetResults(
            List(sharedZoneRecord, sharedZoneRecordNotFoundOwnerGroup),
            nameSort = NameSort.ASC
          )
        )
      ).when(mockRecordRepo)
        .listRecordSets(
          zoneId = Some(sharedZone.id),
          startFrom = None,
          maxItems = None,
          recordNameFilter = None,
          recordTypeFilter = None,
          recordOwnerGroupFilter = None,
          nameSort = NameSort.ASC
        )

      val result: ListRecordSetsByZoneResponse = rightResultOf(
        underTest
          .listRecordSetsByZone(
            sharedZone.id,
            startFrom = None,
            maxItems = None,
            recordNameFilter = None,
            authPrincipal = sharedAuth,
            recordTypeFilter = None,
            recordOwnerGroupFilter = None,
            nameSort = NameSort.ASC
          )
          .value
      )
      result.recordSets shouldBe
        List(
          RecordSetListInfo(
            RecordSetInfo(sharedZoneRecord, Some(okGroup.name)),
            AccessLevel.Delete
          ),
          RecordSetListInfo(
            RecordSetInfo(sharedZoneRecordNotFoundOwnerGroup, None),
            AccessLevel.Delete
          )
        )
    }
    "return the recordSet for support admin" in {
      doReturn(IO.pure(Set()))
        .when(mockGroupRepo)
        .getGroups(Set())

      doReturn(IO.pure(ListRecordSetResults(List(aaaa), nameSort = NameSort.ASC)))
        .when(mockRecordRepo)
        .listRecordSets(
          zoneId = Some(okZone.id),
          startFrom = None,
          maxItems = None,
          recordNameFilter = None,
          recordTypeFilter = None,
          recordOwnerGroupFilter = None,
          nameSort = NameSort.ASC
        )

      val result: ListRecordSetsByZoneResponse = rightResultOf(
        underTest
          .listRecordSetsByZone(
            okZone.id,
            startFrom = None,
            maxItems = None,
            recordNameFilter = None,
            recordTypeFilter = None,
            recordOwnerGroupFilter = None,
            nameSort = NameSort.ASC,
            authPrincipal = AuthPrincipal(okAuth.signedInUser.copy(isSupport = true), Seq.empty)
          )
          .value
      )
      result.recordSets shouldBe List(
        RecordSetListInfo(RecordSetInfo(aaaa, None), AccessLevel.Read)
      )
    }
    "fails when the account is not authorized" in {
      val result = leftResultOf(
        underTest
          .listRecordSetsByZone(
            zoneNotAuthorized.id,
            startFrom = None,
            maxItems = None,
            recordNameFilter = None,
            recordTypeFilter = None,
            recordOwnerGroupFilter = None,
            nameSort = NameSort.ASC,
            authPrincipal = okAuth
          )
          .value
      )
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
        maxItems = 100
      )
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
        maxItems = 100
      )
      result shouldBe expectedResults
    }

    "return a NotAuthorizedError" in {
      val error = leftResultOf(
        underTest.listRecordSetChanges(zoneNotAuthorized.id, authPrincipal = okAuth).value
      )
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
        maxItems = 100
      )
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
          underTest.getRecordSetChange(sharedZone.id, pendingCreateSharedRecord.id, okAuth).value
        )
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
        underTest.getRecordSetChange(zoneActive.id, pendingCreateAAAA.id, dummyAuth).value
      )

      error shouldBe a[NotAuthorizedError]
    }

    "return a NotAuthorizedError if the user is in the record owner group but the zone is not shared" in {
      doReturn(IO.pure(Some(zoneNotAuthorized))).when(mockZoneRepo).getZone(zoneNotAuthorized.id)
      doReturn(IO.pure(Some(pendingCreateSharedRecordNotSharedZone)))
        .when(mockRecordChangeRepo)
        .getRecordSetChange(zoneNotAuthorized.id, pendingCreateSharedRecordNotSharedZone.id)

      val error = leftResultOf(
        underTest
          .getRecordSetChange(
            zoneNotAuthorized.id,
            pendingCreateSharedRecordNotSharedZone.id,
            okAuth
          )
          .value
      )

      error shouldBe a[NotAuthorizedError]
    }
  }

  "formatRecordNameFilter" should {
    "return an FQDN from an IPv4 address" in {
      rightResultOf(underTest.formatRecordNameFilter("10.10.0.25").value) shouldBe
        "25.0.10.10.in-addr.arpa."
    }

    "return an FQDN from an IPv6 address" in {
      rightResultOf(underTest.formatRecordNameFilter("10.10.0.25").value) shouldBe
        "25.0.10.10.in-addr.arpa."
    }

    "return a string with a trailing dot" in {
      rightResultOf(underTest.formatRecordNameFilter("thing.com").value) shouldBe
        "thing.com."
    }
  }
}
