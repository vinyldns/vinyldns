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

package vinyldns.api.domain.access

import cats.scalatest.EitherMatchers
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.{ResultHelpers, VinylDNSTestHelpers}
import vinyldns.api.domain.zone.{NotAuthorizedError, RecordSetInfo, RecordSetListInfo}
import vinyldns.core.TestMembershipData._
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import vinyldns.core.domain.{Encrypted, Fqdn}
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.User
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.{ACLRule, AccessLevel, Zone, ZoneACL}
import org.scalatestplus.mockito.MockitoSugar

class AccessValidationsSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ResultHelpers
    with EitherMatchers {

  private val userReadAcl =
    ACLRule(AccessLevel.Read, userId = Some(okAuth.userId), groupId = None)
  private val userWriteAcl =
    ACLRule(AccessLevel.Write, userId = Some(okAuth.userId), groupId = None)
  private val userNoAccessAcl =
    ACLRule(AccessLevel.NoAccess, userId = Some(okAuth.userId), groupId = None)
  private val userDeleteAcl =
    ACLRule(AccessLevel.Delete, userId = Some(okAuth.userId), groupId = None)
  private val groupReadAcl = ACLRule(AccessLevel.Read, userId = None, groupId = Some(okGroup.id))
  private val groupWriteAcl = ACLRule(AccessLevel.Write, userId = None, groupId = Some(okGroup.id))
  private val groupDeleteAcl = ACLRule(AccessLevel.Delete, userId = None, groupId = Some(okGroup.id))
  private val groupAclNone = ACLRule(AccessLevel.NoAccess, userId = None, groupId = Some(okGroup.id))
  private val allReadACL = ACLRule(AccessLevel.Read, userId = None, groupId = None)

  private val badUserWriteAcl = ACLRule(AccessLevel.Write, userId = Some("bad-id"), groupId = None)
  private val badGroupReadAcl =
    ACLRule(AccessLevel.Read, userId = None, groupId = Some("bad-group"))

  private val accessValidationTest = new AccessValidations(
    sharedApprovedTypes =
      List(RecordType.A, RecordType.AAAA, RecordType.CNAME, RecordType.PTR, RecordType.TXT)
  )
  private val groupIds = Seq(okGroup.id, twoUserGroup.id)
  private val userAccessNone = okUser.copy(id = "NoAccess")
  private val userAuthNone = AuthPrincipal(userAccessNone, groupIds)
  private val userAclNone =
    ACLRule(AccessLevel.NoAccess, userId = Some(userAuthNone.userId), groupId = None)
  private val zoneInNone = zoneNotAuthorized.copy(acl = ZoneACL(Set(userAclNone)))

  private val userAccessRead = okUser.copy(id = "ReadAccess")
  private val userAuthRead = AuthPrincipal(userAccessRead, groupIds)
  private val userAclRead =
    ACLRule(AccessLevel.Read, userId = Some(userAuthRead.userId), groupId = None)
  private val zoneInRead = zoneNotAuthorized.copy(acl = ZoneACL(Set(userAclRead)))

  private val userAccessWrite = okUser.copy(id = "WriteAccess")
  private val userAuthWrite = AuthPrincipal(userAccessWrite, groupIds)
  private val userAclWrite =
    ACLRule(AccessLevel.Write, userId = Some(userAuthWrite.userId), groupId = None)
  private val zoneInWrite = zoneNotAuthorized.copy(acl = ZoneACL(Set(userAclWrite)))

  private val userAccessDelete = okUser.copy(id = "DeleteAccess")
  private val userAuthDelete = AuthPrincipal(userAccessDelete, groupIds)
  private val userAclDelete =
    ACLRule(AccessLevel.Delete, userId = Some(userAuthDelete.userId), groupId = None)
  private val zoneInDelete = zoneNotAuthorized.copy(acl = ZoneACL(Set(userAclDelete)))

  private val userAccessGlobalAcl = okUser.copy(id = "GlobalACL")
  private val userAuthGlobalAcl = AuthPrincipal(userAccessGlobalAcl, groupIds)
  private val globalAcl = GlobalAcl(List(okGroup.id), List(".*foo.*"))
  private val globalAclTest = new AccessValidations(
    GlobalAcls(List(globalAcl)),
    VinylDNSTestHelpers.sharedApprovedTypes
  )

  private val testUser = User("test", "test", Encrypted("test"), isTest = true)

  "canSeeZone" should {
    "return a NotAuthorizedError if the user is not admin or super user with no acl rules" in {
      val error = leftValue(accessValidationTest.canSeeZone(okAuth, zoneNotAuthorized))
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the user is an admin or super user" in {
      val auth = okAuth.copy(
        signedInUser = okAuth.signedInUser.copy(isSuper = true),
        memberGroupIds = Seq.empty
      )
      accessValidationTest.canSeeZone(auth, okZone) should be(right)
    }

    "return true if there is an acl rule for the user in the zone" in {
      val rule = ACLRule(AccessLevel.Read, userId = Some(okAuth.userId))
      val zoneIn = zoneNotAuthorized.copy(acl = ZoneACL(Set(rule)))

      accessValidationTest.canSeeZone(okAuth, zoneIn) should be(right)
    }

    "return true if the user is a support admin" in {
      val supportAuth = okAuth.copy(
        signedInUser = okAuth.signedInUser.copy(isSupport = true),
        memberGroupIds = Seq.empty
      )
      accessValidationTest.canSeeZone(supportAuth, okZone) should be(right)
    }

    "return true if the zone is shared and user does not have other access" in {
      accessValidationTest.canSeeZone(okAuth, sharedZone) should be(right)
    }
  }

  "canSeeZoneChange" should {
    "return a NotAuthorizedError if the user is not admin or super user with no acl rules" in {
      val error = leftValue(accessValidationTest.canSeeZoneChange(okAuth, zoneNotAuthorized))
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the user is an admin or super user" in {
      val auth = okAuth.copy(
        signedInUser = okAuth.signedInUser.copy(isSuper = true),
        memberGroupIds = Seq.empty
      )
      accessValidationTest.canSeeZoneChange(auth, okZone) should be(right)
    }

    "return false if there is an acl rule for the user in the zone" in {
      val rule = ACLRule(AccessLevel.Read, userId = Some(okAuth.userId))
      val zoneIn = zoneNotAuthorized.copy(acl = ZoneACL(Set(rule)))

      val error = leftValue(accessValidationTest.canSeeZoneChange(okAuth, zoneIn))
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the user is a support admin" in {
      val supportAuth = okAuth.copy(
        signedInUser = okAuth.signedInUser.copy(isSupport = true),
        memberGroupIds = Seq.empty
      )
      accessValidationTest.canSeeZone(supportAuth, okZone) should be(right)
    }

    "return true if the zone is shared and user does not have other access" in {
      accessValidationTest.canSeeZone(okAuth, sharedZone) should be(right)
    }
  }

  "canChangeZone" should {
    "return a NotAuthorizedError if the user is not admin or super user" in {
      val error = leftValue(
        accessValidationTest
          .canChangeZone(okAuth, zoneNotAuthorized.name, zoneNotAuthorized.adminGroupId)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the user is an admin or super user" in {
      accessValidationTest.canChangeZone(okAuth, okZone.name, okZone.adminGroupId) should be(right)
    }

    "return true if the user is an admin and a support user" in {
      val auth = okAuth.copy(
        signedInUser = okAuth.signedInUser.copy(isSupport = true),
        memberGroupIds = Seq(okGroup.id)
      )
      accessValidationTest.canChangeZone(auth, okZone.name, okZone.adminGroupId) should be(right)
    }

    "return a NotAuthorizedError if the user is a support user only" in {
      val auth = okAuth.copy(
        signedInUser = okAuth.signedInUser.copy(isSupport = true),
        memberGroupIds = Seq.empty
      )
      val error = leftValue(
        accessValidationTest
          .canChangeZone(auth, okZone.name, okZone.adminGroupId)
      )
      error shouldBe a[NotAuthorizedError]
    }
  }

  "canAddRecordSet" should {
    "return a NotAuthorizedError if the user has AccessLevel.NoAccess" in {
      val error = leftValue(
        accessValidationTest
          .canAddRecordSet(userAuthNone, "test", RecordType.A, zoneInNone)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return a NotAuthorizedError if the user has AccessLevel.Read" in {
      val error = leftValue(
        accessValidationTest
          .canAddRecordSet(userAuthRead, "test", RecordType.A, zoneInRead)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the user has AccessLevel.Write" in {
      accessValidationTest.canAddRecordSet(userAuthWrite, "test", RecordType.A, zoneInWrite) should be(
        right
      )
    }

    "return true if the user has AccessLevel.Delete" in {
      accessValidationTest.canAddRecordSet(userAuthDelete, "test", RecordType.A, zoneInDelete) should be(
        right
      )
    }

    "return a NotAuthorizedError if the user is a test user in a non-test zone" in {
      val auth = okAuth.copy(signedInUser = testUser)

      val error = leftValue(
        accessValidationTest
          .canAddRecordSet(auth, "test", RecordType.A, okZone)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return access as calculated if the user is a test user in a test zone" in {
      val auth = okAuth.copy(signedInUser = testUser)
      val zone = okZone.copy(isTest = true)

      accessValidationTest
        .canAddRecordSet(auth, "test", RecordType.A, zone) should be(right)
    }
    "return true if recordset is NS and user is in the admin group" in {
      val zone = okZone
      val auth = okAuth
      accessValidationTest.canAddRecordSet(auth, ns.name, ns.typ, zone) should be(right)
    }

    "return true if recordset is NS and the user has ACL access " in {
      accessValidationTest.canAddRecordSet(
        userAuthWrite,
        "someRecordName",
        RecordType.NS,
        zoneInWrite
      ) should be(right)
    }

    "return true if recordset matches the global ACL" in {
      globalAclTest.canAddRecordSet(
        userAuthGlobalAcl,
        "someRecordName",
        RecordType.A,
        okZone.copy(name = "foo.comcast.com")
      ) should be(right)
    }

    "return false if the record set does not match the global ACL" in {
      val auth = userAuthGlobalAcl.copy(memberGroupIds = Seq("not-authorized"))

      val error = leftValue(
        globalAclTest
          .canAddRecordSet(auth, "test-foo", RecordType.A, okZone)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the record set is a PTR and the ptrdname matches the global ACL" in {
      globalAclTest.canAddRecordSet(
        userAuthGlobalAcl,
        "someRecordName",
        RecordType.PTR,
        zoneIp4,
        List(PTRData(Fqdn("test.foo.comcast.net")))
      ) should be(right)
    }
  }

  "canUpdateRecordSet" should {
    "return a NotAuthorizedError if the user has AccessLevel.NoAccess" in {
      val error = leftValue(
        accessValidationTest
          .canUpdateRecordSet(userAuthNone, "test", RecordType.A, zoneInNone, None)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return a NotAuthorizedError if the user has AccessLevel.Read" in {
      val error = leftValue(
        accessValidationTest
          .canUpdateRecordSet(userAuthRead, "test", RecordType.A, zoneInRead, None)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the user has AccessLevel.Write" in {
      accessValidationTest.canUpdateRecordSet(
        userAuthWrite,
        "test",
        RecordType.A,
        zoneInWrite,
        None
      ) should be(right)
    }

    "return true if the user has AccessLevel.Delete" in {
      val userAccess = okUser.copy(id = "Delete")
      val userAuth = AuthPrincipal(userAccess, groupIds)
      val userAcl = ACLRule(AccessLevel.Delete, userId = Some(userAuth.userId), groupId = None)
      val zoneIn = zoneNotAuthorized.copy(acl = ZoneACL(Set(userAcl)))
      accessValidationTest.canUpdateRecordSet(userAuth, "test", RecordType.A, zoneIn, None) should be(
        right
      )
    }

    "return true if the user has AccessLevel.Read or AccessLevel.NoAccess and superUserCanUpdateOwnerGroup is true" in {
      accessValidationTest.canUpdateRecordSet(userAuthRead, "test", RecordType.A, zoneInRead,
        None, superUserCanUpdateOwnerGroup = true) should be(right)
      accessValidationTest.canUpdateRecordSet(userAuthNone, "test", RecordType.A, zoneInNone,
        None, superUserCanUpdateOwnerGroup = true) should be(right)
    }

    "return true if the user is in the owner group and the zone is shared" in {
      val zone = okZone.copy(shared = true)
      val record = aaaa.copy(zoneId = zone.id, ownerGroupId = Some(oneUserDummyGroup.id))
      val userAccess = listOfDummyUsers.head
      val userAuth = AuthPrincipal(userAccess, Seq(oneUserDummyGroup.id))
      accessValidationTest.canUpdateRecordSet(
        userAuth,
        record.name,
        record.typ,
        zone,
        Some(oneUserDummyGroup.id)
      ) should be(right)
    }

    "return false if the user is in the owner group and the zone is not shared" in {
      val zone = okZone.copy(shared = false)
      val record = aaaa.copy(zoneId = zone.id, ownerGroupId = Some(oneUserDummyGroup.id))
      val userAccess = listOfDummyUsers.head
      val userAuth = AuthPrincipal(userAccess, Seq(oneUserDummyGroup.id))
      accessValidationTest.canUpdateRecordSet(
        userAuth,
        record.name,
        record.typ,
        zone,
        Some(oneUserDummyGroup.id)
      ) should be(left)
    }

    "return a NotAuthorizedError if the user is a test user in a non-test zone" in {
      val auth = okAuth.copy(signedInUser = testUser)

      val error = leftValue(
        accessValidationTest
          .canUpdateRecordSet(auth, "test", RecordType.A, okZone, None)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return access as calculated if the user is a test user in a test zone" in {
      val auth = okAuth.copy(signedInUser = testUser)
      val zone = okZone.copy(isTest = true)

      accessValidationTest
        .canUpdateRecordSet(auth, "test", RecordType.A, zone, None) should be(right)
    }

    "return true if recordset matches the global ACL" in {
      globalAclTest.canUpdateRecordSet(
        userAuthGlobalAcl,
        "someRecordName",
        RecordType.A,
        okZone.copy(name = "foo.comcast.com"),
        None
      ) should be(right)
    }

    "return false if the record set does not match the global ACL" in {
      val auth = userAuthGlobalAcl.copy(memberGroupIds = Seq("not-authorized"))

      val error = leftValue(
        globalAclTest
          .canUpdateRecordSet(auth, "test-foo", RecordType.A, okZone, None)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the record set is a PTR and the ptrdname matches the global ACL" in {
      globalAclTest.canUpdateRecordSet(
        userAuthGlobalAcl,
        "someRecordName",
        RecordType.PTR,
        zoneIp4,
        None,
        newRecordData = List(PTRData(Fqdn("test.foo.comcast.net")))
      ) should be(right)
    }
  }

  "canDeleteRecordSet" should {
    "return a NotAuthorizedError if the user has AccessLevel.NoAccess" in {
      val error = leftValue(
        accessValidationTest
          .canDeleteRecordSet(userAuthNone, "test", RecordType.A, zoneInNone, None)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return a NotAuthorizedError if the user has AccessLevel.Read" in {
      val error = leftValue(
        accessValidationTest
          .canDeleteRecordSet(userAuthRead, "test", RecordType.A, zoneInRead, None)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return a NotAuthorizedError if the user has AccessLevel.Write" in {
      val error = leftValue(
        accessValidationTest
          .canDeleteRecordSet(userAuthWrite, "test", RecordType.A, zoneInWrite, None)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the user has AccessLevel.Delete" in {
      accessValidationTest.canDeleteRecordSet(
        userAuthDelete,
        "test",
        RecordType.A,
        zoneInDelete,
        None
      ) should be(right)
    }

    "return a NotAuthorizedError if the user is a test user in a non-test zone" in {
      val auth = okAuth.copy(signedInUser = testUser)

      val error = leftValue(
        accessValidationTest
          .canDeleteRecordSet(auth, "test", RecordType.A, okZone, None)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return access as calculated if the user is a test user in a test zone" in {
      val auth = okAuth.copy(signedInUser = testUser)
      val zone = okZone.copy(isTest = true)

      accessValidationTest
        .canDeleteRecordSet(auth, "test", RecordType.A, zone, None) should be(right)
    }

    "return true if recordset matches the global ACL" in {
      globalAclTest.canDeleteRecordSet(
        userAuthGlobalAcl,
        "someRecordName",
        RecordType.A,
        okZone.copy(name = "foo.comcast.com"),
        None
      ) should be(right)
    }

    "return false if the record set does not match the global ACL" in {
      val auth = userAuthGlobalAcl.copy(memberGroupIds = Seq("not-authorized"))

      val error = leftValue(
        globalAclTest
          .canDeleteRecordSet(auth, "test-foo", RecordType.A, okZone, None)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the record set is a PTR and the ptrdname matches the global ACL" in {
      globalAclTest.canDeleteRecordSet(
        userAuthGlobalAcl,
        "someRecordName",
        RecordType.PTR,
        zoneIp4,
        None,
        List(PTRData(Fqdn("test.foo.comcast.net")))
      ) should be(right)
    }
  }

  "canViewRecordSet" should {
    "return a NotAuthorizedError if the user has AccessLevel.NoAccess" in {
      val error = leftValue(
        accessValidationTest
          .canViewRecordSet(userAuthNone, "test", RecordType.A, zoneInNone, None)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the user has AccessLevel.Read" in {
      accessValidationTest.canViewRecordSet(userAuthRead, "test", RecordType.A, zoneInRead, None) should be(
        right
      )
    }

    "return true if the user has AccessLevel.Write" in {
      accessValidationTest.canViewRecordSet(userAuthWrite, "test", RecordType.A, zoneInWrite, None) should be(
        right
      )
    }

    "return true if the user has AccessLevel.Delete" in {
      accessValidationTest.canViewRecordSet(
        userAuthDelete,
        "test",
        RecordType.A,
        zoneInDelete,
        None
      ) should be(right)
    }

    "return true if the user is in the recordSet owner group and the recordSet is in a shared zone" in {
      val recordSet = sharedZoneRecord
      accessValidationTest.canViewRecordSet(
        okAuth,
        recordSet.name,
        recordSet.typ,
        sharedZone,
        recordSet.ownerGroupId
      ) should be(right)
    }

    "return a NotAuthorizedError if the user is in the recordSet owner group but it is not in a shared zone" in {
      val recordSet = notSharedZoneRecordWithOwnerGroup
      val error = leftValue(
        accessValidationTest.canViewRecordSet(
          okAuth,
          recordSet.name,
          recordSet.typ,
          zoneNotAuthorized,
          recordSet.ownerGroupId
        )
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return a NotAuthorizedError if the user is a test user in a non-test zone" in {
      val auth = okAuth.copy(signedInUser = testUser)

      val error = leftValue(
        accessValidationTest
          .canViewRecordSet(auth, "test", RecordType.A, okZone, None)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return access as calculated if the user is a test user in a test zone" in {
      val auth = okAuth.copy(signedInUser = testUser)
      val zone = okZone.copy(isTest = true)

      accessValidationTest
        .canViewRecordSet(auth, "test", RecordType.A, zone, None) should be(right)
    }

    "return true if recordset matches the global ACL" in {
      globalAclTest.canViewRecordSet(
        userAuthGlobalAcl,
        "someRecordName",
        RecordType.A,
        okZone.copy(name = "foo.comcast.com"),
        None
      ) should be(right)
    }

    "return false if the record set does not match the global ACL" in {
      val auth = userAuthGlobalAcl.copy(memberGroupIds = Seq("not-authorized"))

      val error = leftValue(
        globalAclTest
          .canViewRecordSet(auth, "test-foo", RecordType.A, okZone, None)
      )
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the record set is a PTR and the ptrdname matches the global ACL" in {
      globalAclTest.canViewRecordSet(
        userAuthGlobalAcl,
        "someRecordName",
        RecordType.PTR,
        zoneIp4,
        None,
        List(PTRData(Fqdn("test.foo.comcast.net")))
      ) should be(right)
    }
  }

  "getAccessLevel" should {
    "return AccessLevel.Delete if the user is admin" in {
      val mockRecordSet = mock[RecordSet]
      val result =
        accessValidationTest.getAccessLevel(
          okAuth,
          mockRecordSet.name,
          mockRecordSet.typ,
          okZone,
          None
        )
      result shouldBe AccessLevel.Delete
    }

    "return AccessLevel.Delete if the user is a record owner and zone is shared" in {
      val recordOwnerAuth = AuthPrincipal(okUser.copy(id = "recordOwner"), Seq(okGroup.id))
      val result =
        accessValidationTest.getAccessLevel(
          recordOwnerAuth,
          sharedZoneRecord.name,
          sharedZoneRecord.typ,
          sharedZone,
          sharedZoneRecord.ownerGroupId
        )
      result shouldBe AccessLevel.Delete
    }

    "return AccessLevel.Delete if the zone is shared and the record is unowned and an approved record type" in {
      val result =
        accessValidationTest.getAccessLevel(
          okAuth,
          sharedZoneRecordNoOwnerGroup.name,
          RecordType.AAAA,
          sharedZone,
          None
        )
      result shouldBe AccessLevel.Delete
    }

    "return AccessLevel.NoAccess if the zone is shared and the record is unowned but not an approved record type" in {
      val result =
        accessValidationTest.getAccessLevel(
          okAuth,
          sharedZoneRecordNotApprovedRecordType.name,
          RecordType.MX,
          sharedZone,
          None
        )
      result shouldBe AccessLevel.NoAccess
    }

    "return the result of getAccessLevel if the user is a record owner but zone is not shared" in {
      val result =
        accessValidationTest.getAccessLevel(
          okAuth,
          notSharedZoneRecordWithOwnerGroup.name,
          notSharedZoneRecordWithOwnerGroup.typ,
          zoneNotAuthorized,
          notSharedZoneRecordWithOwnerGroup.ownerGroupId
        )
      result shouldBe AccessLevel.NoAccess
    }

    "return AccessLevel.Read if the user is support only" in {
      val result = accessValidationTest.getAccessLevel(
        supportUserAuth,
        "test",
        RecordType.A,
        okZone.copy(adminGroupId = "not-a-real-group"),
        None
      )
      result shouldBe AccessLevel.Read
    }

    "return AccessLevel.Delete if the user is super user not in the admin group" in {
  val result = accessValidationTest.getAccessLevel(
    superUserAuth,
    "test",
    RecordType.A,
    okZone.copy(adminGroupId = "not-a-real-group"),
    None
  )
  result shouldBe AccessLevel.Delete
}

    "return the result of getAccessLevel if the user is support but also an admin" in {
      val supportAuth =
        okAuth.copy(signedInUser = okAuth.signedInUser.copy(isSupport = true))
      val result =
        accessValidationTest.getAccessLevel(supportAuth, "test", RecordType.A, okZone, None)
      result shouldBe AccessLevel.Delete
    }

    "return the result of getAccessLevel if the user is support but also has ACL rule access" in {
      val mockRecordSet = mock[RecordSet]
      val userAccess = okUser.copy(id = "Write", isSupport = true)
      val userAuth = AuthPrincipal(userAccess, groupIds)
      val userAcl = ACLRule(AccessLevel.Write, userId = Some(userAuth.userId), groupId = None)
      val zoneIn = zoneNotAuthorized.copy(acl = ZoneACL(Set(userAcl)))

      val result =
        accessValidationTest.getAccessLevel(
          userAuth,
          mockRecordSet.name,
          mockRecordSet.typ,
          zoneIn,
          None
        )
      result shouldBe AccessLevel.Write
    }

    "return the result of getAccessLevel if user is not admin/super" in {
      val userAccess = okUser.copy(id = "Read")
      val userAuth = AuthPrincipal(userAccess, groupIds)
      val userAcl = ACLRule(AccessLevel.Read, userId = Some(userAuth.userId), groupId = None)
      val zoneIn = zoneNotAuthorized.copy(acl = ZoneACL(Set(userAcl)))

      val result =
        accessValidationTest.getAccessLevel(userAuth, "test", RecordType.A, zoneIn, None)
      result shouldBe AccessLevel.Read
    }
  }

  "getAccessFromAcl" should {

    "choose a rule associated with a particular user" in {
      val zoneAcl = ZoneACL(Set(userWriteAcl))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result =
        accessValidationTest.getAccessFromAcl(okAuth, mockRecordSet.name, mockRecordSet.typ, zone)
      result shouldBe AccessLevel.Write
    }

    "choose a rule associated with a group a user is in" in {
      val zoneAcl = ZoneACL(Set(groupReadAcl))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result =
        accessValidationTest.getAccessFromAcl(okAuth, mockRecordSet.name, mockRecordSet.typ, zone)
      result shouldBe AccessLevel.Read
    }

    "choose a rule associated with all users" in {
      val zoneAcl = ZoneACL(Set(allReadACL))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result =
        accessValidationTest.getAccessFromAcl(okAuth, mockRecordSet.name, mockRecordSet.typ, zone)
      result shouldBe AccessLevel.Read
    }

    "default to NoAccess if no rules are set" in {
      val mockRecordSet = mock[RecordSet]
      val zoneAcl = ZoneACL()
      val zone = Zone("name", "email", acl = zoneAcl)

      val result =
        accessValidationTest.getAccessFromAcl(okAuth, mockRecordSet.name, mockRecordSet.typ, zone)
      result shouldBe AccessLevel.NoAccess
    }

    "ignore rules for different users, irrelevant groups" in {
      val zoneAcl = ZoneACL(Set(badGroupReadAcl, badUserWriteAcl))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result =
        accessValidationTest.getAccessFromAcl(okAuth, mockRecordSet.name, mockRecordSet.typ, zone)
      result shouldBe AccessLevel.NoAccess
    }

    "choose user over group rules" in {
      val zoneAcl = ZoneACL(Set(userWriteAcl, groupReadAcl))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result =
        accessValidationTest.getAccessFromAcl(okAuth, mockRecordSet.name, mockRecordSet.typ, zone)
      result shouldBe AccessLevel.Write
    }

    "prioritize more permissive user rules in a tie" in {
      val zoneAcl = ZoneACL(Set(userReadAcl, userWriteAcl))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result =
        accessValidationTest.getAccessFromAcl(okAuth, mockRecordSet.name, mockRecordSet.typ, zone)
      result shouldBe AccessLevel.Write
    }

    "prioritize user over group rules, regardless of permissiveness" in {
      val zoneAcl = ZoneACL(Set(userReadAcl, groupWriteAcl))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result =
        accessValidationTest.getAccessFromAcl(okAuth, mockRecordSet.name, mockRecordSet.typ, zone)
      result shouldBe AccessLevel.Read
    }

    "prioritize NoAccess over other user rules" in {
      val zoneAcl = ZoneACL(Set(userNoAccessAcl, userReadAcl))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result =
        accessValidationTest.getAccessFromAcl(okAuth, mockRecordSet.name, mockRecordSet.typ, zone)
      result shouldBe AccessLevel.NoAccess
    }

    "prioritize user over group rules, even when group rule is NoAccess" in {
      val zoneAcl = ZoneACL(Set(userDeleteAcl, groupAclNone))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result =
        accessValidationTest.getAccessFromAcl(okAuth, mockRecordSet.name, mockRecordSet.typ, zone)
      result shouldBe AccessLevel.Delete
    }

    "prioritize user over group rules, and more permissive user rules in a tie" in {
      val zoneAcl = ZoneACL(Set(userWriteAcl, userReadAcl, groupDeleteAcl))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result =
        accessValidationTest.getAccessFromAcl(okAuth, mockRecordSet.name, mockRecordSet.typ, zone)
      result shouldBe AccessLevel.Write
    }

    "prioritize user over group rules, and choose NoAccess over other rules" in {
      val zoneAcl = ZoneACL(Set(userNoAccessAcl, userReadAcl, groupDeleteAcl))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result =
        accessValidationTest.getAccessFromAcl(okAuth, mockRecordSet.name, mockRecordSet.typ, zone)
      result shouldBe AccessLevel.NoAccess
    }

    "choose group over all-user" in {
      val zoneAcl = ZoneACL(Set(groupWriteAcl, allReadACL))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result =
        accessValidationTest.getAccessFromAcl(okAuth, mockRecordSet.name, mockRecordSet.typ, zone)
      result shouldBe AccessLevel.Write
    }

    "apply to specific record type" in {
      val rs = RecordSet(
        zoneNotAuthorized.id,
        "ok",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )
      val aclRuleA =
        ACLRule(AccessLevel.Read, userId = Some(okAuth.userId), recordTypes = Set(RecordType.A))

      val zoneAcl = ZoneACL(Set(aclRuleA))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.Read
    }

    "not apply to all if record type specified" in {
      val rs = RecordSet(
        zoneNotAuthorized.id,
        "ok",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )
      val aclRuleA =
        ACLRule(AccessLevel.Read, userId = Some(okAuth.userId), recordTypes = Set(RecordType.AAAA))

      val zoneAcl = ZoneACL(Set(aclRuleA))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.NoAccess
    }

    "prioritize more specific record type lists" in {
      val rs = RecordSet(
        zoneNotAuthorized.id,
        "ok",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )
      val aclRuleA =
        ACLRule(AccessLevel.Write, userId = Some(okAuth.userId), recordTypes = Set(RecordType.A))
      val aclRuleMany = ACLRule(
        AccessLevel.Read,
        userId = Some(okAuth.userId),
        recordTypes = Set(RecordType.A, RecordType.AAAA)
      )

      val zoneAcl = ZoneACL(Set(aclRuleA, aclRuleMany))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.Write
    }

    "prioritize record type lists over all" in {
      val rs = RecordSet(
        zoneNotAuthorized.id,
        "ok",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )
      val aclRuleA =
        ACLRule(AccessLevel.Write, userId = Some(okAuth.userId), recordTypes = Set(RecordType.A))
      val aclRuleAll =
        ACLRule(AccessLevel.Read, userId = Some(okAuth.userId), recordTypes = Set())

      val zoneAcl = ZoneACL(Set(aclRuleA, aclRuleAll))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.Write
    }

    "filter in based on record mask" in {
      val rs = RecordSet(
        zoneNotAuthorized.id,
        "rsname",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )
      val aclRule = userReadAcl.copy(recordMask = Some("rs.*"))

      val zoneAcl = ZoneACL(Set(aclRule))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.Read
    }

    "exclude records based on record mask" in {
      val rs = RecordSet(
        zoneNotAuthorized.id,
        "rsname",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )
      val aclRule = userReadAcl.copy(recordMask = Some("bad.*"))

      val zoneAcl = ZoneACL(Set(aclRule))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.NoAccess
    }

    "prioritize a record mask over apply to all" in {
      val rs = RecordSet(
        zoneNotAuthorized.id,
        "rsname",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )
      val aclRuleRM = userReadAcl.copy(recordMask = Some("rs.*"))
      val aclRuleAll = userWriteAcl.copy(recordMask = None)

      val zoneAcl = ZoneACL(Set(aclRuleAll, aclRuleRM))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.Read
    }
  }

  "ruleAppliesToRecordNameIPv4" should {

    "filter in/out record set based on CIDR rule of 1 (lower bound for ip4 CIDR rules)" in {
      val aclRule = userReadAcl.copy(recordMask = Some("120.1.2.0/1"))
      val znTrue = Zone("40.120.in-addr.arpa.", "email")
      val rsTrue =
        RecordSet("id", "20.3", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))
      val znFalse = Zone("255.129.in-addr.arpa.", "email")
      val rsFalse =
        RecordSet("id", "255.255", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

      accessValidationTest.ruleAppliesToRecordName(rsTrue.name, rsTrue.typ, znTrue, aclRule) shouldBe true
      accessValidationTest.ruleAppliesToRecordName(rsFalse.name, rsFalse.typ, znFalse, aclRule) shouldBe false
    }

    "filter in/out record set based on CIDR rule of 8" in {
      val aclRule = userReadAcl.copy(recordMask = Some("202.204.62.208/8"))
      val znTrue = Zone("202.in-addr.arpa.", "email")
      val rsTrue =
        RecordSet("id", "32.23.100", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))
      val znFalse = Zone("1.23.100.in-addr.arpa.", "email")
      val rsFalse = RecordSet("id", "3", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

      accessValidationTest.ruleAppliesToRecordName(rsTrue.name, rsTrue.typ, znTrue, aclRule) shouldBe true
      accessValidationTest.ruleAppliesToRecordName(rsFalse.name, rsFalse.typ, znFalse, aclRule) shouldBe false
    }

    "filter in/out record set based on CIDR rule of 32 (upper bound for ip4 CIDR rules)" in {
      val aclRule = userReadAcl.copy(recordMask = Some("120.1.2.0/32"))
      val znTrue = Zone("1.120.in-addr.arpa.", "email")
      val rsTrue = RecordSet("id", "0.2", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))
      val znFalse = Zone("23.10.in-addr.arpa.", "email")
      val rsFalse = RecordSet("id", "3", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

      accessValidationTest.ruleAppliesToRecordName(rsTrue.name, rsTrue.typ, znTrue, aclRule) shouldBe true
      accessValidationTest.ruleAppliesToRecordName(rsFalse.name, rsFalse.typ, znFalse, aclRule) shouldBe false
    }

    "include rules that has no mask defined and filter in record set" in {
      val aclRule = userReadAcl.copy()
      val zn = Zone("202.in-addr.arpa.", "email")
      val rs =
        RecordSet("id", "32.23.100", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

      accessValidationTest.ruleAppliesToRecordName(rs.name, rs.typ, zn, aclRule) shouldBe true
    }

    "not apply when regex could match a ptr" in {
      val rs = RecordSet(
        "id",
        "000.000.000.000",
        RecordType.PTR,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )
      val aclRule = userReadAcl.copy(recordMask = Some(".*0.*"))

      val zoneAcl = ZoneACL(Set(aclRule))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.NoAccess
    }
  }

  "ruleAppliesToRecordNameIPv6" should {

    "filter in/out record set based on CIDR rule of 8 (lower bound for ip6 CIDR rules)" in {
      val aclRule = userReadAcl.copy(recordMask = Some("5bbe:ffa5:631b:4777:5887:c84a:844e:a3c2/8"))
      val znTrue = Zone("7.7.4.b.1.3.6.5.a.f.f.e.b.b.5.ip6.arpa.", "email")
      val rsTrue = RecordSet(
        "id",
        "2.c.3.a.e.4.4.8.a.4.8.c.7.8.8.5.7",
        RecordType.PTR,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )
      val znFalse = Zone("5.b.e.f.9.d.2.f.9.5.c.c.7.4.a.a.8.ip6.arpa.", "email")
      val rsFalse = RecordSet(
        "id",
        "2.3.d.f.3.7.3.4.8.a.5.b.d.1.7",
        RecordType.PTR,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )

      accessValidationTest.ruleAppliesToRecordName(rsTrue.name, rsTrue.typ, znTrue, aclRule) shouldBe true
      accessValidationTest.ruleAppliesToRecordName(rsFalse.name, rsFalse.typ, znFalse, aclRule) shouldBe false
    }

    "filter in/out record set based on CIDR rule of 76" in {
      val aclRule =
        userReadAcl.copy(recordMask = Some("5bbe:ffa5:631b:4777:5887:c84a:844e:a3c2/76"))
      val znTrue = Zone("7.7.4.b.1.3.6.5.a.f.f.e.b.b.5.ip6.arpa.", "email")
      val rsTrue = RecordSet(
        "id",
        "f.f.f.f.f.4.4.8.a.4.8.c.7.8.8.5.7",
        RecordType.PTR,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )
      val znFalse = Zone("5.b.e.f.9.d.2.f.9.5.c.c.7.4.a.a.8.ip6.arpa.", "email")
      val rsFalse = RecordSet(
        "id",
        "f.3.d.f.3.7.3.4.8.a.5.b.d.1.7",
        RecordType.PTR,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )

      accessValidationTest.ruleAppliesToRecordName(rsTrue.name, rsTrue.typ, znTrue, aclRule) shouldBe true
      accessValidationTest.ruleAppliesToRecordName(rsFalse.name, rsFalse.typ, znFalse, aclRule) shouldBe false
    }

    "filter in/out record set based on CIDR rule of 128 (upper bound for ip6 CIDR rules)" in {
      val aclRule =
        userReadAcl.copy(recordMask = Some("5bbe:ffa5:631b:4777:5887:c84a:844e:a3c2/128"))
      val znTrue = Zone("7.7.4.b.1.3.6.5.a.f.f.e.b.b.5.ip6.arpa.", "email")
      val rsTrue = RecordSet(
        "id",
        "2.c.3.a.e.4.4.8.a.4.8.c.7.8.8.5.7",
        RecordType.PTR,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )
      val znFalse = Zone("5.b.e.f.9.d.2.f.9.5.c.c.7.4.a.a.8.ip6.arpa.", "email")
      val rsFalse = RecordSet(
        "id",
        "f.3.d.f.3.7.3.4.8.a.5.b.d.1.7",
        RecordType.PTR,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )

      accessValidationTest.ruleAppliesToRecordName(rsTrue.name, rsTrue.typ, znTrue, aclRule) shouldBe true
      accessValidationTest.ruleAppliesToRecordName(rsFalse.name, rsFalse.typ, znFalse, aclRule) shouldBe false
    }

    "include rules that has no mask defined and filter in record set" in {
      val aclRule = userReadAcl.copy()
      val zn = Zone("7.7.4.b.1.3.6.5.a.f.f.e.b.b.5.ip6.arpa.", "email")
      val rs = RecordSet(
        "id",
        "f.f.f.f.f.4.4.8.a.4.8.c.7.8.8.5.7",
        RecordType.PTR,
        200,
        RecordSetStatus.Active,
        Instant.now.truncatedTo(ChronoUnit.MILLIS)
      )

      accessValidationTest.ruleAppliesToRecordName(rs.name, rs.typ, zn, aclRule) shouldBe true
    }
  }

  "getListAccessLevels" should {
    val multiRecordList = List("rs1", "rs2", "rs3").map { name =>
      RecordSetInfo(
        RecordSet("zoneId", name, RecordType.A, 100, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS)),
        None
      )
    }

    "return access level DELETE if the user is admin/super of the zone" in {
      val zone = Zone("test", "test", adminGroupId = okGroup.id)
      val recordList = List(RecordSetInfo(rsOk.copy(zoneId = zone.id), None))

      val result = accessValidationTest.getListAccessLevels(okAuth, recordList, zone)

      val expected = recordList.map(RecordSetListInfo(_, AccessLevel.Delete))
      result shouldBe expected
    }

    "return access level NOACCESS if there is no ACL rule for the user" in {

      val zone = Zone("test", "test")

      val result = accessValidationTest.getListAccessLevels(okAuth, multiRecordList, zone)

      val expected = multiRecordList.map(RecordSetListInfo(_, AccessLevel.NoAccess))
      result shouldBe expected
    }

    "return access level Read if there is no ACL rule for the user and user is a support admin" in {
      val supportAuth = okAuth.copy(
        signedInUser = okAuth.signedInUser.copy(isSupport = true),
        memberGroupIds = Seq.empty
      )

      val zone = Zone("test", "test")

      val result = accessValidationTest.getListAccessLevels(supportAuth, multiRecordList, zone)

      val expected = multiRecordList.map(RecordSetListInfo(_, AccessLevel.Read))
      result shouldBe expected
    }

    "return the appropriate access level for each RecordSet in the list" in {
      val rs1 =
        RecordSetInfo(
          RecordSet("zoneId", "rs1", RecordType.A, 100, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS)),
          None
        )
      val rs2 = rs1.copy(name = "rs2")
      val rs3 = rs1.copy(name = "rs3")
      val recordList = List(rs1, rs2, rs3)

      val rs1Rule =
        ACLRule(AccessLevel.Write, userId = Some(okAuth.userId), recordMask = Some("rs1"))
      val rs2Rule =
        ACLRule(AccessLevel.NoAccess, groupId = Some(okGroup.id), recordMask = Some("rs2"))
      val aclRules = ZoneACL(Set(groupReadAcl, rs1Rule, rs2Rule))
      val zone = Zone("test", "test", acl = aclRules)

      val result = accessValidationTest.getListAccessLevels(okAuth, recordList, zone)

      val expected = List(
        RecordSetListInfo(rs1, AccessLevel.Write),
        RecordSetListInfo(rs2, AccessLevel.NoAccess),
        RecordSetListInfo(rs3, AccessLevel.Read)
      )
      result shouldBe expected
    }

  }

  "getZoneAccess" should {
    "return access level Delete if user is a super user" in {
      accessValidationTest.getZoneAccess(superUserAuth, okZone) should be(AccessLevel.Delete)
    }

    "return access level Delete if user is a zone admin" in {
      accessValidationTest.getZoneAccess(okAuth, okZone) should be(AccessLevel.Delete)
    }

    "return access level Read if user is a support user" in {
      accessValidationTest.getZoneAccess(supportUserAuth, abcZone) should be(AccessLevel.Read)
    }

    "return access level Read if zone is shared and user is not an admin" in {
      accessValidationTest.getZoneAccess(okAuth, sharedZone) should be(AccessLevel.Read)
    }

    "return access level Read if zone is private and user is an ACL rule" in {
      val goodUserRule = baseAclRule.copy(userId = Some(okUser.id), groupId = None)
      val acl = ZoneACL(Set(goodUserRule))
      val aclZone = abcZone.copy(acl = acl)

      accessValidationTest.getZoneAccess(okAuth, aclZone) should be(AccessLevel.Read)
    }

    "return access level NoAccess if zone is private and user is not an admin" in {
      accessValidationTest.getZoneAccess(okAuth, abcZone) should be(AccessLevel.NoAccess)
    }
  }
}
