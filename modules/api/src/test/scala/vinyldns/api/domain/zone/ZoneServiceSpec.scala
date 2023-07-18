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

import org.mockito.Matchers.{any, anyString}
import org.mockito.Mockito.{doReturn, reset}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import cats.implicits._
import vinyldns.api.Interfaces._
import cats.effect._
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import vinyldns.api.config.ValidEmailConfig
import vinyldns.api.domain.access.AccessValidations
import vinyldns.api.domain.membership.{EmailValidationError, MembershipService}
import vinyldns.core.domain.record.RecordSetRepository
//import vinyldns.api.domain.membership.{EmailValidationError, MembershipService}
import vinyldns.api.repository.TestDataLoader
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership._
import vinyldns.core.domain.zone._
import vinyldns.core.queue.MessageQueue
import vinyldns.core.TestMembershipData._
import vinyldns.core.TestZoneData._
import vinyldns.core.crypto.NoOpCrypto
import vinyldns.core.domain.Encrypted
import vinyldns.core.domain.backend.BackendResolver

class ZoneServiceSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with EitherValues {

  private val mockZoneRepo = mock[ZoneRepository]
  private val mockGroupRepo = mock[GroupRepository]
  private val mockUserRepo = mock[UserRepository]
  private val mockZoneChangeRepo = mock[ZoneChangeRepository]
  private val mockMessageQueue = mock[MessageQueue]
  private val mockBackendResolver = mock[BackendResolver]
  private val badConnection = ZoneConnection("bad", "bad", Encrypted("bad"), "bad")
  private val abcZoneSummary = ZoneSummaryInfo(abcZone, abcGroup.name, AccessLevel.Delete)
  private val xyzZoneSummary = ZoneSummaryInfo(xyzZone, xyzGroup.name, AccessLevel.NoAccess)
  private val zoneIp4ZoneSummary = ZoneSummaryInfo(zoneIp4, abcGroup.name, AccessLevel.Delete)
  private val zoneIp6ZoneSummary = ZoneSummaryInfo(zoneIp6, abcGroup.name, AccessLevel.Delete)
  private val mockMembershipRepo = mock[MembershipRepository]
  private val mockGroupChangeRepo = mock[GroupChangeRepository]
  private val mockRecordSetRepo = mock[RecordSetRepository]
  private val mockValidEmailConfig = ValidEmailConfig(valid_domains = List("test.com", "*dummy.com"),2)
  private val mockValidEmailConfigNew = ValidEmailConfig(valid_domains = List(),2)
  private val mockMembershipService = new MembershipService(mockGroupRepo,
    mockUserRepo,
    mockMembershipRepo,
    mockZoneRepo,
    mockGroupChangeRepo,
    mockRecordSetRepo,
    mockValidEmailConfig)



  object TestConnectionValidator extends ZoneConnectionValidatorAlgebra {
    def validateZoneConnections(zone: Zone): Result[Unit] =
      if (zone.connection.contains(badConnection)) {
        ConnectionFailed(zone, "bad").asLeft.toResult
      } else {
        ().toResult
      }

    def isValidBackendId(backendId: Option[String]): Either[Throwable, Unit] = backendId match {
      case Some("badId") => InvalidRequest("bad id").asLeft[Unit]
      case _ => Right(())
    }
  }

  private val underTest = new ZoneService(
    mockZoneRepo,
    mockGroupRepo,
    mockUserRepo,
    mockZoneChangeRepo,
    TestConnectionValidator,
    mockMessageQueue,
    new ZoneValidations(1000),
    new AccessValidations(),
    mockBackendResolver,
    NoOpCrypto.instance,
    mockMembershipService
  )
  private val underTestNew = new ZoneService(
    mockZoneRepo,
    mockGroupRepo,
    mockUserRepo,
    mockZoneChangeRepo,
    TestConnectionValidator,
    mockMessageQueue,
    new ZoneValidations(1000),
    new AccessValidations(),
    mockBackendResolver,
    NoOpCrypto.instance,
    new MembershipService(mockGroupRepo,
      mockUserRepo,
      mockMembershipRepo,
      mockZoneRepo,
      mockGroupChangeRepo,
      mockRecordSetRepo,
      mockValidEmailConfigNew)
  )

  private val createZoneAuthorized = CreateZoneInput(
    "ok.zone.recordsets.",
    "test@test.com",
    connection = testConnection,
    adminGroupId = okGroup.id
  )

  private val updateZoneAuthorized = UpdateZoneInput(
    okZone.id,
    "ok.zone.recordsets.",
    "test@test.com",
    connection = testConnection,
    adminGroupId = okGroup.id
  )

  override protected def beforeEach(): Unit = {
    reset(mockGroupRepo, mockZoneRepo, mockUserRepo)
    doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
    doReturn(IO.unit).when(mockMessageQueue).send(any[ZoneChange])
  }

  "Creating Zones" should {
    "return an appropriate zone change response" in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)

      val resultChange: ZoneChange =
        underTest.connectToZone(createZoneAuthorized, okAuth).map(_.asInstanceOf[ZoneChange]).value.unsafeRunSync().toOption.get

      resultChange.changeType shouldBe ZoneChangeType.Create
      Option(resultChange.created) shouldBe defined
      resultChange.status shouldBe ZoneChangeStatus.Pending
      resultChange.userId shouldBe okAuth.userId

      val resultZone = resultChange.zone
      Option(resultZone.id) shouldBe defined
      resultZone.email shouldBe okZone.email
      resultZone.name shouldBe okZone.name
      resultZone.status shouldBe ZoneStatus.Syncing
      resultZone.connection shouldBe okZone.connection
      resultZone.shared shouldBe false
    }

    "make zone isTest flag false if the user isTest flag is false" in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)

      val nonTestUser = okAuth.copy(signedInUser = okAuth.signedInUser.copy(isTest = false))
      val resultChange: ZoneChange =
        underTest
          .connectToZone(createZoneAuthorized, nonTestUser)
          .map(_.asInstanceOf[ZoneChange])
          .value.unsafeRunSync().toOption.get

      resultChange.zone.isTest shouldBe false
    }

    "make zone isTest flag true if the user isTest flag is true" in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)

      val testUser = okAuth.copy(signedInUser = okAuth.signedInUser.copy(isTest = true))
      testUser.isTestUser shouldBe true
      val resultChange: ZoneChange =
        underTest
          .connectToZone(createZoneAuthorized, testUser)
          .map(_.asInstanceOf[ZoneChange])
          .value.unsafeRunSync().toOption.get

      resultChange.zone.isTest shouldBe true
    }

    "return a ZoneAlreadyExists error if the zone exists" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZoneByName(anyString)

      val error = underTest.connectToZone(createZoneAuthorized, okAuth).value.unsafeRunSync().swap.toOption.get

      error shouldBe a[ZoneAlreadyExistsError]
    }

    "return an InvalidZoneAdminError error if the zone admin group does not exist" in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)
      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(anyString)

      val error = underTest.connectToZone(createZoneAuthorized, okAuth).value.unsafeRunSync().swap.toOption.get

      error shouldBe an[InvalidGroupError]
    }

    "allow the zone to be created if it exists and the zone is deleted" in {
      doReturn(IO.pure(Some(zoneDeleted))).when(mockZoneRepo).getZoneByName(anyString)

      val resultChange: ZoneChange =
        underTest.connectToZone(createZoneAuthorized, okAuth).map(_.asInstanceOf[ZoneChange]).value.unsafeRunSync().toOption.get

      resultChange.changeType shouldBe ZoneChangeType.Create
    }

    "return a NotAuthorizedError when zone recurrence schedule is set by a non-superuser" in {
      doReturn(IO.pure(Some(zoneDeleted))).when(mockZoneRepo).getZoneByName(anyString)

      val newZone = createZoneAuthorized.copy(recurrenceSchedule = Some("0/5 0 0 ? * * *"))
      val error = underTest.connectToZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get

      error shouldBe an[NotAuthorizedError]
    }

    "allow the zone to be created when zone recurrence schedule is set by a superuser" in {
      doReturn(IO.pure(Some(zoneDeleted))).when(mockZoneRepo).getZoneByName(anyString)

      val newZone = createZoneAuthorized.copy(recurrenceSchedule = Some("0/5 0 0 ? * * *"))
      val resultChange: ZoneChange = underTest.connectToZone(newZone, superUserAuth).map(_.asInstanceOf[ZoneChange]).value.unsafeRunSync().toOption.get

      resultChange.changeType shouldBe ZoneChangeType.Create
      resultChange.zone.recurrenceSchedule shouldBe Some("0/5 0 0 ? * * *")
    }

    "return a InvalidRequest when zone recurrence schedule cron expression is invalid" in {
      doReturn(IO.pure(Some(zoneDeleted))).when(mockZoneRepo).getZoneByName(anyString)

      val newZone = createZoneAuthorized.copy(recurrenceSchedule = Some("abcd"))
      val error = underTest.connectToZone(newZone, superUserAuth).value.unsafeRunSync().swap.toOption.get

      error shouldBe an[InvalidRequest]
    }

    "return an error if the zone create includes a bad acl rule" in {
      val badAcl = ACLRule(baseAclRuleInfo.copy(recordMask = Some("x{5,-3}")))
      val newZone = createZoneAuthorized.copy(acl = ZoneACL(Set(badAcl)))

      val error = underTest.connectToZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe an[InvalidRequest]
    }
    "return the result if the zone created includes an valid email" in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)

      val newZone = createZoneAuthorized.copy(email ="test@ok.dummy.com")
      val resultChange: ZoneChange =
        underTest.connectToZone(newZone, okAuth).map(_.asInstanceOf[ZoneChange]).value.unsafeRunSync().toOption.get
      resultChange.changeType shouldBe ZoneChangeType.Create
      Option(resultChange.created) shouldBe defined
      resultChange.status shouldBe ZoneChangeStatus.Pending
      resultChange.userId shouldBe okAuth.userId

      val resultZone = resultChange.zone
      Option(resultZone.id) shouldBe defined
      resultZone.email shouldBe newZone.email
      resultZone.name shouldBe newZone.name
      resultZone.status shouldBe ZoneStatus.Syncing
      resultZone.connection shouldBe newZone.connection
      resultZone.shared shouldBe false
    }

    "return the result if the zone created includes an valid email with number of dots " in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)

      val newZone = createZoneAuthorized.copy(email = "test@ok.dummy.com")
      val resultChange: ZoneChange =
        underTest.connectToZone(newZone, okAuth).map(_.asInstanceOf[ZoneChange]).value.unsafeRunSync().toOption.get
      resultChange.changeType shouldBe ZoneChangeType.Create
      Option(resultChange.created) shouldBe defined
      resultChange.status shouldBe ZoneChangeStatus.Pending
      resultChange.userId shouldBe okAuth.userId

      val resultZone = resultChange.zone
      Option(resultZone.id) shouldBe defined
      resultZone.email shouldBe newZone.email
      resultZone.name shouldBe newZone.name
      resultZone.status shouldBe ZoneStatus.Syncing
      resultZone.connection shouldBe newZone.connection
      resultZone.shared shouldBe false
    }

    "return the result if the zone created includes empty Domain" in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)

      val newZone = createZoneAuthorized.copy(email = "test@abc.com")
      val resultChange: ZoneChange =
        underTestNew.connectToZone(newZone, okAuth).map(_.asInstanceOf[ZoneChange]).value.unsafeRunSync().toOption.get
      resultChange.changeType shouldBe ZoneChangeType.Create
      Option(resultChange.created) shouldBe defined
      resultChange.status shouldBe ZoneChangeStatus.Pending
      resultChange.userId shouldBe okAuth.userId

      val resultZone = resultChange.zone
      Option(resultZone.id) shouldBe defined
      resultZone.email shouldBe newZone.email
      resultZone.name shouldBe newZone.name
      resultZone.status shouldBe ZoneStatus.Syncing
      resultZone.connection shouldBe newZone.connection
      resultZone.shared shouldBe false
    }
    "return an EmailValidationError if an email is invalid" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZoneByName(anyString)
      val newZone = createZoneAuthorized.copy(email = "test@my.com")
      val error = underTest.connectToZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case with number of dots" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZoneByName(anyString)
      val newZone = createZoneAuthorized.copy(email = "test@ok.ok.dummy.com")
      val error = underTest.connectToZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 1" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZoneByName(anyString)
      val newZone = createZoneAuthorized.copy(email = "test.ok.com")
      val error = underTest.connectToZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if a domain is invalid test case 1" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZoneByName(anyString)
      val newZone = createZoneAuthorized.copy(email = "test@ok.com")
      val error = underTest.connectToZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 2" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZoneByName(anyString)
      val newZone = createZoneAuthorized.copy(email = "test@.@.test.com")
      val error = underTest.connectToZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 3" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZoneByName(anyString)
      val newZone = createZoneAuthorized.copy(email = "test@.@@.test.com")
      val error = underTest.connectToZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 4" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZoneByName(anyString)
      val newZone = createZoneAuthorized.copy(email = "@te@st@test.com")
      val error = underTest.connectToZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 5" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZoneByName(anyString)
      val newZone = createZoneAuthorized.copy(email = ".test@test.com")
      val error = underTest.connectToZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 6" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZoneByName(anyString)
      val newZone = createZoneAuthorized.copy(email = "te.....st@test.com")
      val error = underTest.connectToZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "succeed if zone is shared and user is a super user" in {
      val newZone = createZoneAuthorized.copy(shared = true)
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)

      val resultZone =
        underTest.connectToZone(newZone, superUserAuth).map(_.asInstanceOf[ZoneChange]).value.unsafeRunSync().toOption.get.zone

      Option(resultZone.id) should not be None
      resultZone.email shouldBe okZone.email
      resultZone.name shouldBe okZone.name
      resultZone.status shouldBe ZoneStatus.Syncing
      resultZone.connection shouldBe okZone.connection
      resultZone.shared shouldBe true
    }

    "succeed if zone is shared and user is both a zone admin and support user" in {
      val newZone = createZoneAuthorized.copy(shared = true)
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)

      val resultZone =
        underTest
          .connectToZone(newZone, supportUserAuth)
          .map(_.asInstanceOf[ZoneChange])
          .value.unsafeRunSync().toOption.get.zone

      Option(resultZone.id) should not be None
      resultZone.email shouldBe okZone.email
      resultZone.name shouldBe okZone.name
      resultZone.status shouldBe ZoneStatus.Syncing
      resultZone.connection shouldBe okZone.connection
      resultZone.shared shouldBe true
    }

    "return a NotAuthorizedError if zone is shared and user is not a super or zone admin and support user" in {
      val newZone = createZoneAuthorized.copy(shared = true)
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)

      val error = underTest.connectToZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[NotAuthorizedError]
    }

    "return an InvalidRequest if zone has a specified backend ID that is invalid" in {
      val newZone = createZoneAuthorized.copy(backendId = Some("badId"))

      val error = underTest.connectToZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe an[InvalidRequest]
    }
  }

  "Updating Zones" should {
    "return an update zone change response" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val doubleAuth = AuthPrincipal(TestDataLoader.okUser, Seq(twoUserGroup.id, okGroup.id))
      val updateZoneInput = updateZoneAuthorized.copy(adminGroupId = twoUserGroup.id)

      val resultChange: ZoneChange =
        underTest
          .updateZone(updateZoneInput, doubleAuth)
          .map(_.asInstanceOf[ZoneChange])
          .value.unsafeRunSync().toOption.get

      resultChange.zone.id shouldBe okZone.id
      resultChange.changeType shouldBe ZoneChangeType.Update
      resultChange.zone.adminGroupId shouldBe updateZoneInput.adminGroupId
      resultChange.zone.adminGroupId should not be updateZoneAuthorized.adminGroupId
    }

    "return a NotAuthorizedError when zone recurrence schedule is updated by a non-superuser" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val doubleAuth = AuthPrincipal(TestDataLoader.okUser, Seq(twoUserGroup.id, okGroup.id))
      val updateZoneInput = updateZoneAuthorized.copy(recurrenceSchedule = Some("0/5 0 0 ? * * *"))
      val error = underTest
        .updateZone(updateZoneInput, doubleAuth).value.unsafeRunSync().swap.toOption.get

      error shouldBe an[NotAuthorizedError]
    }

    "return a InvalidRequest when zone recurrence schedule cron expression is invalid" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val updateZoneInput = updateZoneAuthorized.copy(recurrenceSchedule = Some("abcd"))
      val error = underTest
        .updateZone(updateZoneInput, superUserAuth).value.unsafeRunSync().swap.toOption.get

      error shouldBe an[InvalidRequest]
    }

    "allow the zone to be created when zone recurrence schedule is set by a superuser" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val updateZoneInput = updateZoneAuthorized.copy(recurrenceSchedule = Some("0/5 0 0 ? * * *"))
      val resultChange: ZoneChange =
        underTest
          .updateZone(updateZoneInput, superUserAuth)
          .map(_.asInstanceOf[ZoneChange])
          .value.unsafeRunSync().toOption.get

      resultChange.zone.id shouldBe okZone.id
      resultChange.changeType shouldBe ZoneChangeType.Update
      resultChange.zone.recurrenceSchedule shouldBe updateZoneInput.recurrenceSchedule
    }

    "not validate connection if unchanged" in {
      val oldZone = okZone.copy(connection = Some(badConnection))
      doReturn(IO.pure(Some(oldZone))).when(mockZoneRepo).getZone(anyString)

      val newZone =
        updateZoneAuthorized.copy(connection = Some(badConnection))

      val doubleAuth = AuthPrincipal(TestDataLoader.okUser, Seq(okGroup.id, okGroup.id))

      val resultChange: ZoneChange =
          underTest
            .updateZone(newZone, doubleAuth)
            .map(_.asInstanceOf[ZoneChange])
            .value.unsafeRunSync().toOption.get

      resultChange.zone.id shouldBe oldZone.id
      resultChange.zone.connection shouldBe oldZone.connection
    }

    "validate connection and fail if changed to bad" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val newZone =
        updateZoneAuthorized.copy(connection = Some(badConnection), adminGroupId = okGroup.id)

      val error = underTest.updateZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[ConnectionFailed]
    }

    "return an error if the user is not authorized for the zone" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val noAuth = AuthPrincipal(TestDataLoader.okUser, Seq())

      val error = underTest.updateZone(updateZoneAuthorized, noAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[NotAuthorizedError]
    }

    "return an error if the zone update adds a bad acl rule" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val badAcl = ACLRule(baseAclRuleInfo.copy(recordMask = Some("x{5,-3}")))
      val newZone = updateZoneAuthorized.copy(acl = ZoneACL(Set(badAcl)))

      val error = underTest.updateZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe an[InvalidRequest]
    }
    "return the result if the zone updated includes an valid email" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val doubleAuth = AuthPrincipal(TestDataLoader.okUser, Seq(twoUserGroup.id, okGroup.id))
      val updateZoneInput = updateZoneAuthorized.copy(adminGroupId = twoUserGroup.id,email="test@dummy.com")

      val resultChange: ZoneChange =
        underTest
          .updateZone(updateZoneInput, doubleAuth)
          .map(_.asInstanceOf[ZoneChange])
          .value.unsafeRunSync().toOption.get

      resultChange.zone.id shouldBe okZone.id
      resultChange.changeType shouldBe ZoneChangeType.Update
      resultChange.zone.adminGroupId shouldBe updateZoneInput.adminGroupId
      resultChange.zone.adminGroupId should not be updateZoneAuthorized.adminGroupId
    }
    "return the result if the zone updated includes an empty domain" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val doubleAuth = AuthPrincipal(TestDataLoader.okUser, Seq(twoUserGroup.id, okGroup.id))
      val updateZoneInput = updateZoneAuthorized.copy(adminGroupId = twoUserGroup.id, email = "test@ok.com")

      val resultChange: ZoneChange =
        underTestNew
          .updateZone(updateZoneInput, doubleAuth)
          .map(_.asInstanceOf[ZoneChange])
          .value.unsafeRunSync().toOption.get

      resultChange.zone.id shouldBe okZone.id
      resultChange.changeType shouldBe ZoneChangeType.Update
      resultChange.zone.adminGroupId shouldBe updateZoneInput.adminGroupId
      resultChange.zone.adminGroupId should not be updateZoneAuthorized.adminGroupId
    }

    "succeed if zone shared flag is updated and user is a super user" in {
      val newZone = updateZoneAuthorized.copy(shared = false)
      doReturn(IO.pure(Some(Zone(createZoneAuthorized.copy(shared = true), false))))
        .when(mockZoneRepo)
        .getZone(newZone.id)

      val result =
        underTest
          .updateZone(newZone, AuthPrincipal(superUser, List.empty))
          .value.unsafeRunSync().toOption.get

      result shouldBe a[ZoneChange]
    }

    "succeed if zone shared flag is updated and user is both a zone admin and support user" in {
      val newZone = updateZoneAuthorized.copy(shared = false)
      doReturn(IO.pure(Some(Zone(createZoneAuthorized.copy(shared = true), false))))
        .when(mockZoneRepo)
        .getZone(newZone.id)

      val result =
        underTest
          .updateZone(newZone, supportUserAuth)
          .value.unsafeRunSync().toOption.get

      result shouldBe a[ZoneChange]
    }

    "return a NotAuthorizedError if zone shared flag is updated and user is not a super or zone admin " +
      "and support user" in {
      val newZone = updateZoneAuthorized.copy(shared = false)
      doReturn(IO.pure(Some(Zone(createZoneAuthorized.copy(shared = true), false))))
        .when(mockZoneRepo)
        .getZone(newZone.id)

      val error = underTest.updateZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[NotAuthorizedError]
    }

    "succeed if zone shared flag is unchanged and user is not a super or zone admin and support user" in {
      val newZone = updateZoneAuthorized.copy(shared = true, adminGroupId = okGroup.id)
      doReturn(IO.pure(Some(Zone(createZoneAuthorized.copy(shared = true), false))))
        .when(mockZoneRepo)
        .getZone(newZone.id)

      val result = underTest.updateZone(newZone, okAuth).value.unsafeRunSync().toOption.get
      result shouldBe a[ZoneChange]
    }
    "return an InvalidRequest if zone has a specified backend ID that is invalid" in {
      val newZone = updateZoneAuthorized.copy(backendId = Some("badId"))

      val error = underTest.updateZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe an[InvalidRequest]
    }
    "return an EmailValidationError if an invalid email is entered while updating the zone" in {
      val newZone = updateZoneAuthorized.copy(email ="test.ok.com")
      val error = underTest.updateZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe an[EmailValidationError]
    }
    "return an EmailValidationError if a domain is invalid test case 1" in {
      val newZone = updateZoneAuthorized.copy(email = "test@ok.com")
      val error = underTest.updateZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe an[EmailValidationError]
    }

    "return an EmailValidationError if an email is invalid test case 2" in {
      val newZone = updateZoneAuthorized.copy(email = "test@.@.test.com")
      val error = underTest.updateZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 3" in {
      val newZone = updateZoneAuthorized.copy(email = "test@.@@.test.com")
      val error = underTest.updateZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 4" in {
      val newZone=updateZoneAuthorized.copy(email = "@te@st@test.com")
      val error = underTest.updateZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 5" in {
      val newZone = updateZoneAuthorized.copy(email = ".test@test.com")
      val error = underTest.updateZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 6" in {
      val newZone = updateZoneAuthorized.copy(email = "te.....st@test.com")
      val error = underTest.updateZone(newZone, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }
  }

  "Deleting Zones" should {
    "return an delete zone change response" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val resultChange: ZoneChange =
        underTest.deleteZone(okZone.id, okAuth).map(_.asInstanceOf[ZoneChange]).value.unsafeRunSync().toOption.get

      resultChange.zone.id shouldBe okZone.id
      resultChange.changeType shouldBe ZoneChangeType.Delete
    }

    "return an error if the user is not authorized for the zone" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val noAuth = AuthPrincipal(TestDataLoader.okUser, Seq())

      val error = underTest.deleteZone(okZone.id, noAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[NotAuthorizedError]
    }
  }

  "Syncing a zone" should {
    "return a sync zone response" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val resultChange: ZoneChange =
        underTest.syncZone(okZone.id, okAuth).map(_.asInstanceOf[ZoneChange]).value.unsafeRunSync().toOption.get

      resultChange.zone.id shouldBe okZone.id
      resultChange.changeType shouldBe ZoneChangeType.Sync
      resultChange.status shouldBe ZoneChangeStatus.Pending
    }

    "return an error if the user is not authorized for the zone" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val noAuth = AuthPrincipal(TestDataLoader.okUser, Seq())

      val error = underTest.syncZone(okZone.id, noAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[NotAuthorizedError]
    }
  }

  "Getting a Zone" should {
    "fail with no zone returned" in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZone("notAZoneId")

      val error = underTest.getZone("notAZoneId", okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[ZoneNotFoundError]
    }

    "return an error if the user is not authorized for the zone" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val noAuth = AuthPrincipal(TestDataLoader.okUser, Seq())

      val error = underTest.getZone(okZone.id, noAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[NotAuthorizedError]
    }

    "return the appropriate zone as a ZoneInfo" in {
      doReturn(IO.pure(Some(abcZone))).when(mockZoneRepo).getZone(abcZone.id)
      doReturn(IO.pure(ListUsersResults(Seq(), None)))
        .when(mockUserRepo)
        .getUsers(any[Set[String]], any[Option[String]], any[Option[Int]])
      doReturn(IO.pure(Set(abcGroup))).when(mockGroupRepo).getGroups(any[Set[String]])
      doReturn(IO.pure(Some(abcGroup))).when(mockGroupRepo).getGroup(anyString)

      val expectedZoneInfo =
        ZoneInfo(abcZone, ZoneACLInfo(Set()), abcGroup.name, AccessLevel.Delete)
      val result = underTest.getZone(abcZone.id, abcAuth).value.unsafeRunSync()
      result.right.value shouldBe expectedZoneInfo
    }

    "filter out ACL rules that have no matching group or user" in {
      val goodUser = User("goodUser", "access", Encrypted("secret"))
      val goodGroup = Group("goodGroup", "email")

      val goodUserRule = baseAclRule.copy(userId = Some(goodUser.id), groupId = None)
      val badUserRule = goodUserRule.copy(userId = Some("bad"))
      val goodGroupRule = baseAclRule.copy(userId = None, groupId = Some(goodGroup.id))
      val badGroupRule = goodGroupRule.copy(groupId = Some("bad"))
      val goodAllRule = baseAclRule.copy(userId = None, groupId = None)

      val goodUserRuleInfo = ACLRuleInfo(goodUserRule, Some("goodUser"))
      val goodGroupRuleInfo = ACLRuleInfo(goodGroupRule, Some("goodGroup"))
      val goodAllRuleInfo = ACLRuleInfo(goodAllRule, Some("All Users"))

      val acl = ZoneACL(Set(goodUserRule, badUserRule, goodGroupRule, badGroupRule, goodAllRule))
      val zoneWithRules = abcZone.copy(acl = acl)

      doReturn(IO.pure(Some(zoneWithRules))).when(mockZoneRepo).getZone(zoneWithRules.id)
      doReturn(IO.pure(ListUsersResults(Seq(goodUser), None)))
        .when(mockUserRepo)
        .getUsers(any[Set[String]], any[Option[String]], any[Option[Int]])
      doReturn(IO.pure(Set(goodGroup))).when(mockGroupRepo).getGroups(any[Set[String]])
      doReturn(IO.pure(Some(goodGroup))).when(mockGroupRepo).getGroup(anyString)

      val expectedZoneInfo = ZoneInfo(
        zoneWithRules,
        ZoneACLInfo(Set(goodUserRuleInfo, goodGroupRuleInfo, goodAllRuleInfo)),
        goodGroup.name,
        AccessLevel.Delete
      )
      val result: ZoneInfo = underTest.getZone(zoneWithRules.id, abcAuth).value.unsafeRunSync().toOption.get
      result shouldBe expectedZoneInfo
    }

    "return Unknown group name if zone admin group cannot be found" in {
      doReturn(IO.pure(Some(abcZone))).when(mockZoneRepo).getZone(abcZone.id)
      doReturn(IO.pure(ListUsersResults(Seq(), None)))
        .when(mockUserRepo)
        .getUsers(any[Set[String]], any[Option[String]], any[Option[Int]])
      doReturn(IO.pure(Set(abcGroup))).when(mockGroupRepo).getGroups(any[Set[String]])
      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(anyString)

      val expectedZoneInfo =
        ZoneInfo(abcZone, ZoneACLInfo(Set()), "Unknown group name", AccessLevel.Delete)
      val result: ZoneInfo = underTest.getZone(abcZone.id, abcAuth).value.unsafeRunSync().toOption.get
      result shouldBe expectedZoneInfo
    }

    "return a zone by name with failure when no zone is found" in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName("someZoneName.")

      val error = underTest.getZoneByName("someZoneName", okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[ZoneNotFoundError]
    }

    "return the appropriate zone as a ZoneInfo on getZoneByName" in {
      doReturn(IO.pure(Some(abcZone))).when(mockZoneRepo).getZoneByName(abcZone.name)
      doReturn(IO.pure(ListUsersResults(Seq(), None)))
        .when(mockUserRepo)
        .getUsers(any[Set[String]], any[Option[String]], any[Option[Int]])
      doReturn(IO.pure(Set(abcGroup))).when(mockGroupRepo).getGroups(any[Set[String]])
      doReturn(IO.pure(Some(abcGroup))).when(mockGroupRepo).getGroup(anyString)

      val expectedZoneInfo =
        ZoneInfo(abcZone, ZoneACLInfo(Set()), abcGroup.name, AccessLevel.Delete)
      val result = underTest.getZoneByName("abc.zone.recordsets", abcAuth).value.unsafeRunSync()
      result.right.value shouldBe expectedZoneInfo
    }
  }

  "ListZones" should {
    "not fail with no zones returned" in {
      doReturn(IO.pure(ListZonesResults(List())))
        .when(mockZoneRepo)
        .listZones(abcAuth, None, None, 100, false, true)
      doReturn(IO.pure(Set(abcGroup))).when(mockGroupRepo).getGroups(any[Set[String]])

      val result: ListZonesResponse = underTest.listZones(abcAuth).value.unsafeRunSync().toOption.get
      result.zones shouldBe List()
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe None
      result.nextId shouldBe None
      result.ignoreAccess shouldBe false
    }

    "return the appropriate zones" in {
      doReturn(IO.pure(ListZonesResults(List(abcZone))))
        .when(mockZoneRepo)
        .listZones(abcAuth, None, None, 100, false, true)
      doReturn(IO.pure(Set(abcGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListZonesResponse = underTest.listZones(abcAuth).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcZoneSummary)
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe None
      result.nextId shouldBe None
      result.ignoreAccess shouldBe false
    }

    "return all zones" in {
      doReturn(IO.pure(ListZonesResults(List(abcZone, xyzZone, zoneIp4, zoneIp6), ignoreAccess = true, includeReverse = true)))
        .when(mockZoneRepo)
        .listZones(abcAuth, None, None, 100, true, true)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListZonesResponse =
        underTest.listZones(abcAuth, ignoreAccess = true, includeReverse = true).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcZoneSummary, xyzZoneSummary, zoneIp4ZoneSummary, zoneIp6ZoneSummary)
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe None
      result.nextId shouldBe None
      result.ignoreAccess shouldBe true
      result.includeReverse shouldBe true
    }

    "return all forward zones" in {
      doReturn(IO.pure(ListZonesResults(List(abcZone, xyzZone), ignoreAccess = true, includeReverse = false)))
        .when(mockZoneRepo)
        .listZones(abcAuth, None, None, 100, true, false)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListZonesResponse =
        underTest.listZones(abcAuth, ignoreAccess = true, includeReverse = false).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcZoneSummary, xyzZoneSummary)
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe None
      result.nextId shouldBe None
      result.ignoreAccess shouldBe true
      result.includeReverse shouldBe false
    }

    "name filter must be used to return zones by admin group name, when search by admin group option is true" in {
      doReturn(IO.pure(Set(abcGroup)))
        .when(mockGroupRepo)
        .getGroupsByName(any[String])
      doReturn(IO.pure(ListZonesResults(List(abcZone, zoneIp4, zoneIp6), ignoreAccess = true, zonesFilter = Some("abcGroup"))))
        .when(mockZoneRepo)
        .listZonesByAdminGroupIds(abcAuth, None, 100, Set(abcGroup.id), ignoreAccess = true, includeReverse = true)
      doReturn(IO.pure(Set(abcGroup))).when(mockGroupRepo).getGroups(any[Set[String]])

      // When searchByAdminGroup is true, zones are filtered by admin group name given in nameFilter
      val result: ListZonesResponse =
        underTest.listZones(abcAuth, Some("abcGroup"), None, 100, searchByAdminGroup = true, ignoreAccess = true).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcZoneSummary, zoneIp4ZoneSummary, zoneIp6ZoneSummary)
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe Some("abcGroup")
      result.nextId shouldBe None
      result.ignoreAccess shouldBe true
      result.includeReverse shouldBe true
    }

    "name filter must be used to return forward zones by admin group name, when search by admin group option is true and includeReverse is false" in {
      doReturn(IO.pure(Set(abcGroup)))
        .when(mockGroupRepo)
        .getGroupsByName(any[String])
      doReturn(IO.pure(ListZonesResults(List(abcZone), ignoreAccess = true, zonesFilter = Some("abcGroup"), includeReverse = false)))
        .when(mockZoneRepo)
        .listZonesByAdminGroupIds(abcAuth, None, 100, Set(abcGroup.id), ignoreAccess = true, includeReverse = false)
      doReturn(IO.pure(Set(abcGroup))).when(mockGroupRepo).getGroups(any[Set[String]])

      // When searchByAdminGroup is true, zones are filtered by admin group name given in nameFilter.
      // Reverse zones are excluded when includeReverse is false.
      val result: ListZonesResponse =
        underTest.listZones(abcAuth, Some("abcGroup"), None, 100, searchByAdminGroup = true, ignoreAccess = true, includeReverse = false).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcZoneSummary)
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe Some("abcGroup")
      result.nextId shouldBe None
      result.ignoreAccess shouldBe true
      result.includeReverse shouldBe false
    }

    "name filter must be used to return zone by zone name, when search by admin group option is false" in {
      doReturn(IO.pure(Set(abcGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])
      doReturn(IO.pure(ListZonesResults(List(abcZone), ignoreAccess = true, zonesFilter = Some("abcZone"))))
        .when(mockZoneRepo)
        .listZones(abcAuth, Some("abcZone"), None, 100, true, true)

      // When searchByAdminGroup is false, zone name given in nameFilter is returned
      val result: ListZonesResponse =
        underTest.listZones(abcAuth, Some("abcZone"), None, 100, searchByAdminGroup = false, ignoreAccess = true).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcZoneSummary)
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe Some("abcZone")
      result.nextId shouldBe None
      result.ignoreAccess shouldBe true
    }

    "return Unknown group name if zone admin group cannot be found" in {
      doReturn(IO.pure(ListZonesResults(List(abcZone, xyzZone))))
        .when(mockZoneRepo)
        .listZones(abcAuth, None, None, 100, false, true)
      doReturn(IO.pure(Set(okGroup))).when(mockGroupRepo).getGroups(any[Set[String]])

      val result: ListZonesResponse = underTest.listZones(abcAuth).value.unsafeRunSync().toOption.get
      val expectedZones =
        List(abcZoneSummary, xyzZoneSummary).map(_.copy(adminGroupName = "Unknown group name"))
      result.zones shouldBe expectedZones
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe None
      result.nextId shouldBe None
    }

    "set the nextId appropriately" in {
      doReturn(
        IO.pure(
          ListZonesResults(
            List(abcZone, xyzZone),
            maxItems = 2,
            nextId = Some("zone2."),
            ignoreAccess = false
          )
        )
      ).when(mockZoneRepo)
        .listZones(abcAuth, None, None, 2, false, true)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListZonesResponse =
        underTest.listZones(abcAuth, maxItems = 2).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcZoneSummary, xyzZoneSummary)
      result.maxItems shouldBe 2
      result.startFrom shouldBe None
      result.nameFilter shouldBe None
      result.nextId shouldBe Some("zone2.")
    }

    "set the nameFilter when provided" in {
      doReturn(
        IO.pure(
          ListZonesResults(
            List(abcZone, xyzZone),
            zonesFilter = Some("foo"),
            maxItems = 2,
            nextId = Some("zone2."),
            ignoreAccess = false
          )
        )
      ).when(mockZoneRepo)
        .listZones(abcAuth, Some("foo"), None, 2, false, true)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListZonesResponse =
        underTest.listZones(abcAuth, nameFilter = Some("foo"), maxItems = 2).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcZoneSummary, xyzZoneSummary)
      result.nameFilter shouldBe Some("foo")
      result.nextId shouldBe Some("zone2.")
      result.maxItems shouldBe 2
    }

    "set the startFrom when provided" in {
      doReturn(
        IO.pure(
          ListZonesResults(
            List(abcZone, xyzZone),
            startFrom = Some("zone4."),
            maxItems = 2,
            ignoreAccess = false
          )
        )
      ).when(mockZoneRepo)
        .listZones(abcAuth, None, Some("zone4."), 2, false, true)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListZonesResponse =
        underTest.listZones(abcAuth, startFrom = Some("zone4."), maxItems = 2).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcZoneSummary, xyzZoneSummary)
      result.startFrom shouldBe Some("zone4.")
    }

    "set the nextId to be the current result set size plus the start from" in {
      doReturn(
        IO.pure(
          ListZonesResults(
            List(abcZone, xyzZone),
            startFrom = Some("zone4."),
            maxItems = 2,
            nextId = Some("zone6."),
            ignoreAccess = false
          )
        )
      ).when(mockZoneRepo)
        .listZones(abcAuth, None, Some("zone4."), 2, false, true)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListZonesResponse =
        underTest.listZones(abcAuth, startFrom = Some("zone4."), maxItems = 2).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcZoneSummary, xyzZoneSummary)
      result.nextId shouldBe Some("zone6.")
    }
  }

  "listZoneChanges" should {
    "retrieve the zone changes" in {
      doReturn(IO.pure(Some(okZone)))
        .when(mockZoneRepo)
        .getZone(okZone.id)
      doReturn(IO.pure(ListZoneChangesResults(List(zoneUpdate, zoneCreate))))
        .when(mockZoneChangeRepo)
        .listZoneChanges(okZone.id, startFrom = None, maxItems = 100)

      val result: ListZoneChangesResponse =
        underTest.listZoneChanges(okZone.id, okAuth).value.unsafeRunSync().toOption.get

      result.zoneChanges shouldBe List(zoneUpdate, zoneCreate)
      result.zoneId shouldBe okZone.id
    }

    "return a zone with no changes if no changes exist" in {
      doReturn(IO.pure(Some(okZone)))
        .when(mockZoneRepo)
        .getZone(okZone.id)
      doReturn(IO.pure(ListZoneChangesResults(items = Nil)))
        .when(mockZoneChangeRepo)
        .listZoneChanges(okZone.id, startFrom = None, maxItems = 100)

      val result: ListZoneChangesResponse =
        underTest.listZoneChanges(okZone.id, okAuth).value.unsafeRunSync().toOption.get

      result.zoneChanges shouldBe empty
      result.zoneId shouldBe okZone.id
    }

    "return a NotAuthorizedError" in {
      doReturn(IO.pure(Some(zoneNotAuthorized)))
        .when(mockZoneRepo)
        .getZone(zoneNotAuthorized.id)

      val error = underTest.listZoneChanges(zoneNotAuthorized.id, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[NotAuthorizedError]
    }

    "return the zone changes sorted by created date desc" in {
      // zone change 2 is later than zone change 1 and should come first
      doReturn(IO.pure(Some(okZone)))
        .when(mockZoneRepo)
        .getZone(okZone.id)

      doReturn(IO.pure(ListZoneChangesResults(List(zoneUpdate, zoneCreate))))
        .when(mockZoneChangeRepo)
        .listZoneChanges(zoneId = okZone.id, startFrom = None, maxItems = 100)

      val result: ListZoneChangesResponse =
        underTest.listZoneChanges(okZone.id, okAuth).value.unsafeRunSync().toOption.get

      result.zoneChanges.head shouldBe zoneUpdate
      result.zoneChanges(1) shouldBe zoneCreate
    }
  }

  "listFailedZoneChanges" should {
    "retrieve the zone changes" in {

      doReturn(IO.pure(ListFailedZoneChangesResults(
        List(zoneUpdate.copy(status = ZoneChangeStatus.Failed),zoneCreate.copy(status = ZoneChangeStatus.Failed))
      )))
        .when(mockZoneChangeRepo)
        .listFailedZoneChanges(100,0)

      val result: ListFailedZoneChangesResponse =
        underTest.listFailedZoneChanges(okAuth).value.unsafeRunSync().toOption.get

      result.failedZoneChanges shouldBe
        List(zoneUpdate.copy(status = ZoneChangeStatus.Failed),zoneCreate.copy(status = ZoneChangeStatus.Failed))
      result.failedZoneChanges.head shouldBe zoneUpdate.copy(status = ZoneChangeStatus.Failed)
      result.failedZoneChanges(1) shouldBe zoneCreate.copy(status = ZoneChangeStatus.Failed)
    }

    "retrieve the zone changes with startFrom and maxItems" in {

      doReturn(IO.pure(ListFailedZoneChangesResults(
        List(zoneUpdate.copy(status = ZoneChangeStatus.Failed),zoneCreate.copy(status = ZoneChangeStatus.Failed))
      ))).when(mockZoneChangeRepo)
        .listFailedZoneChanges(1,1)

      val result: ListFailedZoneChangesResponse =
        underTest.listFailedZoneChanges(okAuth,1,1).value.unsafeRunSync().toOption.get
      result.startFrom shouldBe 1
      result.nextId shouldBe 0
      result.maxItems shouldBe 1
    }
  }

  "AddAclRule" should {
    "fail if the user is not authorized for the zone" in {
      doReturn(IO.pure(Some(zoneNotAuthorized))).when(mockZoneRepo).getZone(anyString)

      val error =
        underTest.addACLRule(zoneNotAuthorized.id, baseAclRuleInfo, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[NotAuthorizedError]
    }

    "generate a zone update if the request is valid" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val result: ZoneChange =
        underTest
          .addACLRule(okZone.id, userAclRuleInfo, okAuth)
          .map(_.asInstanceOf[ZoneChange])
          .value.unsafeRunSync().toOption.get

      result.changeType shouldBe ZoneChangeType.Update
      result.zone.acl.rules.size shouldBe 1
      result.zone.acl.rules should contain(userAclRule)
    }

    "fail if mask is an invalid regex" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val invalidRegexMaskRuleInfo = baseAclRuleInfo.copy(recordMask = Some("x{5,-3}"))
      val error =
        underTest.addACLRule(okZone.id, invalidRegexMaskRuleInfo, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe an[InvalidRequest]
    }
  }

  "DeleteAclRule" should {
    "fail if the user is not authorized for the zone" in {
      doReturn(IO.pure(Some(zoneNotAuthorized))).when(mockZoneRepo).getZone(anyString)

      val error =
        underTest.deleteACLRule(zoneNotAuthorized.id, baseAclRuleInfo, okAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[NotAuthorizedError]
    }

    "generate a zone update if the request is valid" in {
      val acl = ZoneACL(Set(userAclRule))
      val zone = okZone.copy(acl = acl)
      doReturn(IO.pure(Some(zone))).when(mockZoneRepo).getZone(anyString)

      val result: ZoneChange =
        underTest
          .deleteACLRule(zone.id, userAclRuleInfo, okAuth)
          .map(_.asInstanceOf[ZoneChange])
          .value.unsafeRunSync().toOption.get

      result.changeType shouldBe ZoneChangeType.Update
      result.zone.acl.rules.size shouldBe 0
    }
  }
}
