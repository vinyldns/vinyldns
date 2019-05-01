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
import org.scalatest._
import org.scalatest.mockito.MockitoSugar
import cats.implicits._
import vinyldns.api.Interfaces._
import vinyldns.api.domain.AccessValidations
import vinyldns.api.ResultHelpers
import cats.effect._
import vinyldns.api.repository.TestDataLoader
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership._
import vinyldns.core.domain.zone._
import vinyldns.core.queue.MessageQueue
import vinyldns.core.TestMembershipData._
import vinyldns.core.TestZoneData._

import scala.concurrent.duration._

class ZoneServiceSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with ResultHelpers
    with BeforeAndAfterEach
    with EitherValues {

  private val mockZoneRepo = mock[ZoneRepository]
  private val mockGroupRepo = mock[GroupRepository]
  private val mockUserRepo = mock[UserRepository]
  private val mockZoneChangeRepo = mock[ZoneChangeRepository]
  private val mockMessageQueue = mock[MessageQueue]
  private val badConnection = ZoneConnection("bad", "bad", "bad", "bad")
  private val abcZoneSummary = ZoneSummaryInfo(abcZone, abcGroup.name)
  private val xyzZoneSummary = ZoneSummaryInfo(xyzZone, xyzGroup.name)

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
    AccessValidations)

  private val createZoneAuthorized = CreateZoneInput(
    "ok.zone.recordsets.",
    "test@test.com",
    connection = testConnection,
    adminGroupId = okGroup.id)

  private val updateZoneAuthorized = UpdateZoneInput(
    okZone.id,
    "ok.zone.recordsets.",
    "updated-test@test.com",
    connection = testConnection,
    adminGroupId = okGroup.id)

  override protected def beforeEach(): Unit = {
    reset(mockGroupRepo, mockZoneRepo, mockUserRepo)
    doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
    doReturn(IO.unit).when(mockMessageQueue).send(any[ZoneChange])
  }

  "Creating Zones" should {
    "return an appropriate zone change response" in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)

      val resultChange: ZoneChange = rightResultOf(
        underTest.connectToZone(createZoneAuthorized, okAuth).map(_.asInstanceOf[ZoneChange]).value)

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
      val resultChange: ZoneChange = rightResultOf(
        underTest
          .connectToZone(createZoneAuthorized, nonTestUser)
          .map(_.asInstanceOf[ZoneChange])
          .value)

      resultChange.zone.isTest shouldBe false
    }

    "make zone isTest flag true if the user isTest flag is true" in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)

      val testUser = okAuth.copy(signedInUser = okAuth.signedInUser.copy(isTest = true))
      testUser.isTestUser shouldBe true
      val resultChange: ZoneChange = rightResultOf(
        underTest
          .connectToZone(createZoneAuthorized, testUser)
          .map(_.asInstanceOf[ZoneChange])
          .value)

      resultChange.zone.isTest shouldBe true
    }

    "return a ZoneAlreadyExists error if the zone exists" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZoneByName(anyString)

      val error = leftResultOf(underTest.connectToZone(createZoneAuthorized, okAuth).value)

      error shouldBe a[ZoneAlreadyExistsError]
    }

    "return an InvalidZoneAdminError error if the zone admin group does not exist" in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)
      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(anyString)

      val error = leftResultOf(underTest.connectToZone(createZoneAuthorized, okAuth).value)

      error shouldBe an[InvalidGroupError]
    }

    "allow the zone to be created if it exists and the zone is deleted" in {
      doReturn(IO.pure(Some(zoneDeleted))).when(mockZoneRepo).getZoneByName(anyString)

      val resultChange: ZoneChange = rightResultOf(
        underTest.connectToZone(createZoneAuthorized, okAuth).map(_.asInstanceOf[ZoneChange]).value)
      resultChange.changeType shouldBe ZoneChangeType.Create
    }

    "return an error if the zone create includes a bad acl rule" in {
      val badAcl = ACLRule(baseAclRuleInfo.copy(recordMask = Some("x{5,-3}")))
      val newZone = createZoneAuthorized.copy(acl = ZoneACL(Set(badAcl)))

      val error = leftResultOf(underTest.connectToZone(newZone, okAuth).value)
      error shouldBe an[InvalidRequest]
    }

    "succeed if zone is shared and user is a super user" in {
      val newZone = createZoneAuthorized.copy(shared = true)
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)

      val resultZone = rightResultOf(
        underTest.connectToZone(newZone, superUserAuth).map(_.asInstanceOf[ZoneChange]).value).zone

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

      val resultZone = rightResultOf(
        underTest
          .connectToZone(newZone, supportUserAuth)
          .map(_.asInstanceOf[ZoneChange])
          .value).zone

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

      val error = leftResultOf(underTest.connectToZone(newZone, okAuth).value)
      error shouldBe a[NotAuthorizedError]
    }

    "return an InvalidRequest if zone has a specified backend ID that is invalid" in {
      val newZone = createZoneAuthorized.copy(backendId = Some("badId"))

      val error = leftResultOf(underTest.connectToZone(newZone, okAuth).value)
      error shouldBe an[InvalidRequest]
    }
  }

  "Updating Zones" should {
    "return an update zone change response" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val doubleAuth = AuthPrincipal(TestDataLoader.okUser, Seq(twoUserGroup.id, okGroup.id))
      val updateZoneInput = updateZoneAuthorized.copy(adminGroupId = twoUserGroup.id)

      val resultChange: ZoneChange = rightResultOf(
        underTest
          .updateZone(updateZoneInput, doubleAuth)
          .map(_.asInstanceOf[ZoneChange])
          .value,
        duration = 2.seconds)

      resultChange.zone.id shouldBe okZone.id
      resultChange.changeType shouldBe ZoneChangeType.Update
      resultChange.zone.adminGroupId shouldBe updateZoneInput.adminGroupId
      resultChange.zone.adminGroupId should not be updateZoneAuthorized.adminGroupId
    }

    "not validate connection if unchanged" in {
      val oldZone = okZone.copy(connection = Some(badConnection))
      doReturn(IO.pure(Some(oldZone))).when(mockZoneRepo).getZone(anyString)

      val newZone =
        updateZoneAuthorized.copy(connection = Some(badConnection))

      val doubleAuth = AuthPrincipal(TestDataLoader.okUser, Seq(okGroup.id, okGroup.id))

      val resultChange: ZoneChange =
        rightResultOf(
          underTest
            .updateZone(newZone, doubleAuth)
            .map(_.asInstanceOf[ZoneChange])
            .value)
      resultChange.zone.id shouldBe oldZone.id
      resultChange.zone.connection shouldBe oldZone.connection
    }

    "validate connection and fail if changed to bad" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val newZone =
        updateZoneAuthorized.copy(connection = Some(badConnection), adminGroupId = okGroup.id)

      val error = leftResultOf(underTest.updateZone(newZone, okAuth).value)
      error shouldBe a[ConnectionFailed]
    }

    "return an error if the user is not authorized for the zone" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val noAuth = AuthPrincipal(TestDataLoader.okUser, Seq())

      val error = leftResultOf(underTest.updateZone(updateZoneAuthorized, noAuth).value)
      error shouldBe a[NotAuthorizedError]
    }

    "return an error if the zone update adds a bad acl rule" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val badAcl = ACLRule(baseAclRuleInfo.copy(recordMask = Some("x{5,-3}")))
      val newZone = updateZoneAuthorized.copy(acl = ZoneACL(Set(badAcl)))

      val error = leftResultOf(underTest.updateZone(newZone, okAuth).value)
      error shouldBe an[InvalidRequest]
    }

    "succeed if zone shared flag is updated and user is a super user" in {
      val newZone = updateZoneAuthorized.copy(shared = false)
      doReturn(IO.pure(Some(Zone(createZoneAuthorized.copy(shared = true), false))))
        .when(mockZoneRepo)
        .getZone(newZone.id)

      val result = rightResultOf(
        underTest
          .updateZone(newZone, AuthPrincipal(superUser, List.empty))
          .value)
      result shouldBe a[ZoneChange]
    }

    "succeed if zone shared flag is updated and user is both a zone admin and support user" in {
      val newZone = updateZoneAuthorized.copy(shared = false)
      doReturn(IO.pure(Some(Zone(createZoneAuthorized.copy(shared = true), false))))
        .when(mockZoneRepo)
        .getZone(newZone.id)

      val result = rightResultOf(
        underTest
          .updateZone(newZone, supportUserAuth)
          .value)
      result shouldBe a[ZoneChange]
    }

    "return a NotAuthorizedError if zone shared flag is updated and user is not a super or zone admin " +
      "and support user" in {
      val newZone = updateZoneAuthorized.copy(shared = false)
      doReturn(IO.pure(Some(Zone(createZoneAuthorized.copy(shared = true), false))))
        .when(mockZoneRepo)
        .getZone(newZone.id)

      val error = leftResultOf(underTest.updateZone(newZone, okAuth).value)
      error shouldBe a[NotAuthorizedError]
    }

    "succeed if zone shared flag is unchanged and user is not a super or zone admin and support user" in {
      val newZone = updateZoneAuthorized.copy(shared = true, adminGroupId = okGroup.id)
      doReturn(IO.pure(Some(Zone(createZoneAuthorized.copy(shared = true), false))))
        .when(mockZoneRepo)
        .getZone(newZone.id)

      val result = rightResultOf(underTest.updateZone(newZone, okAuth).value)
      result shouldBe a[ZoneChange]
    }
    "return an InvalidRequest if zone has a specified backend ID that is invalid" in {
      val newZone = updateZoneAuthorized.copy(backendId = Some("badId"))

      val error = leftResultOf(underTest.updateZone(newZone, okAuth).value)
      error shouldBe an[InvalidRequest]
    }
  }

  "Deleting Zones" should {
    "return an delete zone change response" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val resultChange: ZoneChange =
        rightResultOf(underTest.deleteZone(okZone.id, okAuth).map(_.asInstanceOf[ZoneChange]).value)

      resultChange.zone.id shouldBe okZone.id
      resultChange.changeType shouldBe ZoneChangeType.Delete
    }

    "return an error if the user is not authorized for the zone" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val noAuth = AuthPrincipal(TestDataLoader.okUser, Seq())

      val error = leftResultOf(underTest.deleteZone(okZone.id, noAuth).value)
      error shouldBe a[NotAuthorizedError]
    }
  }

  "Syncing a zone" should {
    "return a sync zone response" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val resultChange: ZoneChange =
        rightResultOf(underTest.syncZone(okZone.id, okAuth).map(_.asInstanceOf[ZoneChange]).value)

      resultChange.zone.id shouldBe okZone.id
      resultChange.changeType shouldBe ZoneChangeType.Sync
      resultChange.status shouldBe ZoneChangeStatus.Pending
    }

    "return an error if the user is not authorized for the zone" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val noAuth = AuthPrincipal(TestDataLoader.okUser, Seq())

      val error = leftResultOf(underTest.syncZone(okZone.id, noAuth).value)
      error shouldBe a[NotAuthorizedError]
    }
  }

  "Getting a Zone" should {
    "fail with no zone returned" in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZone("notAZoneId")

      val error = leftResultOf(underTest.getZone("notAZoneId", okAuth).value)
      error shouldBe a[ZoneNotFoundError]
    }

    "return an error if the user is not authorized for the zone" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val noAuth = AuthPrincipal(TestDataLoader.okUser, Seq())

      val error = leftResultOf(underTest.getZone(okZone.id, noAuth).value)
      error shouldBe a[NotAuthorizedError]
    }

    "return the appropriate zone as a ZoneInfo" in {
      doReturn(IO.pure(Some(abcZone))).when(mockZoneRepo).getZone(abcZone.id)
      doReturn(IO.pure(ListUsersResults(Seq(), None)))
        .when(mockUserRepo)
        .getUsers(any[Set[String]], any[Option[String]], any[Option[Int]])
      doReturn(IO.pure(Set(abcGroup))).when(mockGroupRepo).getGroups(any[Set[String]])
      doReturn(IO.pure(Some(abcGroup))).when(mockGroupRepo).getGroup(anyString)

      val expectedZoneInfo = ZoneInfo(abcZone, ZoneACLInfo(Set()), abcGroup.name)
      val result = underTest.getZone(abcZone.id, abcAuth).value.unsafeRunSync()
      result.right.value shouldBe expectedZoneInfo
    }

    "filter out ACL rules that have no matching group or user" in {
      val goodUser = User("goodUser", "access", "secret")
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
        goodGroup.name)
      val result: ZoneInfo = rightResultOf(underTest.getZone(zoneWithRules.id, abcAuth).value)
      result shouldBe expectedZoneInfo
    }

    "return Unknown group name if zone admin group cannot be found" in {
      doReturn(IO.pure(Some(abcZone))).when(mockZoneRepo).getZone(abcZone.id)
      doReturn(IO.pure(ListUsersResults(Seq(), None)))
        .when(mockUserRepo)
        .getUsers(any[Set[String]], any[Option[String]], any[Option[Int]])
      doReturn(IO.pure(Set(abcGroup))).when(mockGroupRepo).getGroups(any[Set[String]])
      doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(anyString)

      val expectedZoneInfo = ZoneInfo(abcZone, ZoneACLInfo(Set()), "Unknown group name")
      val result: ZoneInfo = rightResultOf(underTest.getZone(abcZone.id, abcAuth).value)
      result shouldBe expectedZoneInfo
    }

    "return a zone by name with failure when no zone is found" in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName("someZoneName.")

      val error = leftResultOf(underTest.getZoneByName("someZoneName", okAuth).value)
      error shouldBe a[ZoneNotFoundError]
    }

    "return the appropriate zone as a ZoneInfo on getZoneByName" in {
      doReturn(IO.pure(Some(abcZone))).when(mockZoneRepo).getZoneByName(abcZone.name)
      doReturn(IO.pure(ListUsersResults(Seq(), None)))
        .when(mockUserRepo)
        .getUsers(any[Set[String]], any[Option[String]], any[Option[Int]])
      doReturn(IO.pure(Set(abcGroup))).when(mockGroupRepo).getGroups(any[Set[String]])
      doReturn(IO.pure(Some(abcGroup))).when(mockGroupRepo).getGroup(anyString)

      val expectedZoneInfo = ZoneInfo(abcZone, ZoneACLInfo(Set()), abcGroup.name)
      val result = underTest.getZoneByName("abc.zone.recordsets", abcAuth).value.unsafeRunSync()
      result.right.value shouldBe expectedZoneInfo
    }
  }

  "ListZones" should {
    "not fail with no zones returned" in {
      doReturn(IO.pure(ListZonesResults(List())))
        .when(mockZoneRepo)
        .listZones(abcAuth, None, None, 100)
      doReturn(IO.pure(Set(abcGroup))).when(mockGroupRepo).getGroups(any[Set[String]])

      val result: ListZonesResponse = rightResultOf(underTest.listZones(abcAuth).value)
      result.zones shouldBe List()
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe None
      result.nextId shouldBe None
    }

    "return the appropriate zones" in {
      doReturn(IO.pure(ListZonesResults(List(abcZone, xyzZone))))
        .when(mockZoneRepo)
        .listZones(abcAuth, None, None, 100)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListZonesResponse = rightResultOf(underTest.listZones(abcAuth).value)
      result.zones shouldBe List(abcZoneSummary, xyzZoneSummary)
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe None
      result.nextId shouldBe None
    }

    "return Unknown group name if zone admin group cannot be found" in {
      doReturn(IO.pure(ListZonesResults(List(abcZone, xyzZone))))
        .when(mockZoneRepo)
        .listZones(abcAuth, None, None, 100)
      doReturn(IO.pure(Set(okGroup))).when(mockGroupRepo).getGroups(any[Set[String]])

      val result: ListZonesResponse = rightResultOf(underTest.listZones(abcAuth).value)
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
        IO.pure(ListZonesResults(List(abcZone, xyzZone), maxItems = 2, nextId = Some("zone2."))))
        .when(mockZoneRepo)
        .listZones(abcAuth, None, None, 2)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListZonesResponse =
        rightResultOf(underTest.listZones(abcAuth, maxItems = 2).value)
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
            nextId = Some("zone2."))))
        .when(mockZoneRepo)
        .listZones(abcAuth, Some("foo"), None, 2)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListZonesResponse =
        rightResultOf(underTest.listZones(abcAuth, nameFilter = Some("foo"), maxItems = 2).value)
      result.zones shouldBe List(abcZoneSummary, xyzZoneSummary)
      result.nameFilter shouldBe Some("foo")
      result.nextId shouldBe Some("zone2.")
      result.maxItems shouldBe 2
    }

    "set the startFrom when provided" in {
      doReturn(
        IO.pure(ListZonesResults(List(abcZone, xyzZone), startFrom = Some("zone4."), maxItems = 2)))
        .when(mockZoneRepo)
        .listZones(abcAuth, None, Some("zone4."), 2)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListZonesResponse =
        rightResultOf(underTest.listZones(abcAuth, startFrom = Some("zone4."), maxItems = 2).value)
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
            nextId = Some("zone6."))))
        .when(mockZoneRepo)
        .listZones(abcAuth, None, Some("zone4."), 2)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListZonesResponse =
        rightResultOf(underTest.listZones(abcAuth, startFrom = Some("zone4."), maxItems = 2).value)
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
        rightResultOf(underTest.listZoneChanges(okZone.id, okAuth).value)

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
        rightResultOf(underTest.listZoneChanges(okZone.id, okAuth).value)

      result.zoneChanges shouldBe empty
      result.zoneId shouldBe okZone.id
    }

    "return a NotAuthorizedError" in {
      doReturn(IO.pure(Some(zoneNotAuthorized)))
        .when(mockZoneRepo)
        .getZone(zoneNotAuthorized.id)

      val error = leftResultOf(underTest.listZoneChanges(zoneNotAuthorized.id, okAuth).value)
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
        rightResultOf(underTest.listZoneChanges(okZone.id, okAuth).value)

      result.zoneChanges.head shouldBe zoneUpdate
      result.zoneChanges(1) shouldBe zoneCreate
    }
  }

  "AddAclRule" should {
    "fail if the user is not authorized for the zone" in {
      doReturn(IO.pure(Some(zoneNotAuthorized))).when(mockZoneRepo).getZone(anyString)

      val error =
        leftResultOf(underTest.addACLRule(zoneNotAuthorized.id, baseAclRuleInfo, okAuth).value)
      error shouldBe a[NotAuthorizedError]
    }

    "generate a zone update if the request is valid" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val result: ZoneChange = rightResultOf(
        underTest
          .addACLRule(okZone.id, userAclRuleInfo, okAuth)
          .map(_.asInstanceOf[ZoneChange])
          .value)

      result.changeType shouldBe ZoneChangeType.Update
      result.zone.acl.rules.size shouldBe 1
      result.zone.acl.rules should contain(userAclRule)
    }

    "fail if mask is an invalid regex" in {
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZone(anyString)

      val invalidRegexMaskRuleInfo = baseAclRuleInfo.copy(recordMask = Some("x{5,-3}"))
      val error =
        leftResultOf(underTest.addACLRule(okZone.id, invalidRegexMaskRuleInfo, okAuth).value)
      error shouldBe an[InvalidRequest]
    }
  }

  "DeleteAclRule" should {
    "fail if the user is not authorized for the zone" in {
      doReturn(IO.pure(Some(zoneNotAuthorized))).when(mockZoneRepo).getZone(anyString)

      val error =
        leftResultOf(underTest.deleteACLRule(zoneNotAuthorized.id, baseAclRuleInfo, okAuth).value)
      error shouldBe a[NotAuthorizedError]
    }

    "generate a zone update if the request is valid" in {
      val acl = ZoneACL(Set(userAclRule))
      val zone = okZone.copy(acl = acl)
      doReturn(IO.pure(Some(zone))).when(mockZoneRepo).getZone(anyString)

      val result: ZoneChange = rightResultOf(
        underTest
          .deleteACLRule(zone.id, userAclRuleInfo, okAuth)
          .map(_.asInstanceOf[ZoneChange])
          .value)

      result.changeType shouldBe ZoneChangeType.Update
      result.zone.acl.rules.size shouldBe 0
    }
  }
}
