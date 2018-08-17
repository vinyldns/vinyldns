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

package vinyldns.api.domain

import cats.scalatest.EitherMatchers
import org.joda.time.DateTime
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpecLike}
import vinyldns.api.domain.auth.AuthPrincipal
import vinyldns.api.domain.record._
import vinyldns.api.domain.zone.{AccessLevel, NotAuthorizedError, RecordSetInfo, _}
import vinyldns.api.{GroupTestData, ResultHelpers, VinylDNSTestData}

class AccessValidationsSpec
    extends WordSpecLike
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with ResultHelpers
    with GroupTestData
    with VinylDNSTestData
    with EitherMatchers {

  private val userReadAcl =
    ACLRule(AccessLevel.Read, userId = Some(okUserAuth.userId), groupId = None)
  private val userWriteAcl =
    ACLRule(AccessLevel.Write, userId = Some(okUserAuth.userId), groupId = None)
  private val groupReadAcl = ACLRule(AccessLevel.Read, userId = None, groupId = Some(okGroup.id))
  private val groupWriteAcl = ACLRule(AccessLevel.Write, userId = None, groupId = Some(okGroup.id))
  private val allReadACL = ACLRule(AccessLevel.Read, userId = None, groupId = None)

  private val badUserWriteAcl = ACLRule(AccessLevel.Write, userId = Some("bad-id"), groupId = None)
  private val badGroupReadAcl =
    ACLRule(AccessLevel.Read, userId = None, groupId = Some("bad-group"))

  private val accessValidationTest = AccessValidations

  private val groupIds = Seq(okGroup.id, twoUserGroup.id)
  private val userAccessNone = okUser.copy(id = "NoAccess")
  private val userAuthNone = AuthPrincipal(userAccessNone, groupIds)
  private val userAclNone =
    ACLRule(AccessLevel.NoAccess, userId = Some(userAuthNone.userId), groupId = None)
  private val zoneInNone = memberZoneNotAuthorized.copy(acl = ZoneACL(Set(userAclNone)))

  private val userAccessRead = okUser.copy(id = "ReadAccess")
  private val userAuthRead = AuthPrincipal(userAccessRead, groupIds)
  private val userAclRead =
    ACLRule(AccessLevel.Read, userId = Some(userAuthRead.userId), groupId = None)
  private val zoneInRead = memberZoneNotAuthorized.copy(acl = ZoneACL(Set(userAclRead)))

  private val userAccessWrite = okUser.copy(id = "WriteAccess")
  private val userAuthWrite = AuthPrincipal(userAccessWrite, groupIds)
  private val userAclWrite =
    ACLRule(AccessLevel.Write, userId = Some(userAuthWrite.userId), groupId = None)
  private val zoneInWrite = memberZoneNotAuthorized.copy(acl = ZoneACL(Set(userAclWrite)))

  private val userAccessDelete = okUser.copy(id = "DeleteAccess")
  private val userAuthDelete = AuthPrincipal(userAccessDelete, groupIds)
  private val userAclDelete =
    ACLRule(AccessLevel.Delete, userId = Some(userAuthDelete.userId), groupId = None)
  private val zoneInDelete = memberZoneNotAuthorized.copy(acl = ZoneACL(Set(userAclDelete)))

  "canSeeZone" should {
    "return a NotAuthorizedError if the user is not admin or super user with no acl rules" in {
      val error = leftValue(accessValidationTest.canSeeZone(okUserAuth, memberZoneNotAuthorized))
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the user is an admin or super user" in {
      val auth = okAuth.copy(
        signedInUser = okGroupAuth.signedInUser.copy(isSuper = true),
        memberGroupIds = Seq.empty)
      accessValidationTest.canSeeZone(auth, memberOkZoneAuthorized) should be(right)
    }

    "return true if there is an acl rule for the user in the zone" in {
      val rule = ACLRule(AccessLevel.Read, userId = Some(okUserAuth.userId))
      val zoneIn = memberZoneNotAuthorized.copy(acl = ZoneACL(Set(rule)))

      accessValidationTest.canSeeZone(okUserAuth, zoneIn) should be(right)
    }
  }

  "canChangeZone" should {
    "return a NotAuthorizedError if the user is not admin or super user" in {
      val error = leftValue(accessValidationTest.canChangeZone(okUserAuth, memberZoneNotAuthorized))
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the user is an admin or super user" in {
      accessValidationTest.canChangeZone(okUserAuth, memberOkZoneAuthorized) should be(right)
    }
  }

  "canAddRecordSet" should {
    "return a NotAuthorizedError if the user has AccessLevel.NoAccess" in {
      val mockRecordSet = mock[RecordSet]

      val error = leftValue(
        accessValidationTest
          .canAddRecordSet(userAuthNone, mockRecordSet.name, mockRecordSet.typ, zoneInNone))
      error shouldBe a[NotAuthorizedError]
    }

    "return a NotAuthorizedError if the user has AccessLevel.Read" in {
      val mockRecordSet = mock[RecordSet]

      val error = leftValue(
        accessValidationTest
          .canAddRecordSet(userAuthRead, mockRecordSet.name, mockRecordSet.typ, zoneInRead))
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the user has AccessLevel.Write" in {
      val mockRecordSet = mock[RecordSet]
      accessValidationTest.canAddRecordSet(
        userAuthWrite,
        mockRecordSet.name,
        mockRecordSet.typ,
        zoneInWrite) should be(right)
    }

    "return true if the user has AccessLevel.Delete" in {
      val mockRecordSet = mock[RecordSet]
      accessValidationTest.canAddRecordSet(
        userAuthDelete,
        mockRecordSet.name,
        mockRecordSet.typ,
        zoneInDelete) should be(right)
    }

    "return true if recordset is NS and user is a superuser" in {
      val auth = okAuth.copy(
        signedInUser = okGroupAuth.signedInUser.copy(isSuper = true),
        memberGroupIds = Seq.empty)
      accessValidationTest.canAddRecordSet(auth, ns.name, ns.typ, memberZoneNotAuthorized) should be(
        right)
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
        zoneInWrite) should be(right)
    }
  }
  "canUpdateRecordSet" should {
    "return a NotAuthorizedError if the user has AccessLevel.NoAccess" in {
      val mockRecordSet = mock[RecordSet]

      val error = leftValue(
        accessValidationTest
          .canUpdateRecordSet(userAuthNone, mockRecordSet.name, mockRecordSet.typ, zoneInNone))
      error shouldBe a[NotAuthorizedError]
    }

    "return a NotAuthorizedError if the user has AccessLevel.Read" in {
      val mockRecordSet = mock[RecordSet]

      val error = leftValue(
        accessValidationTest
          .canUpdateRecordSet(userAuthRead, mockRecordSet.name, mockRecordSet.typ, zoneInRead))
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the user has AccessLevel.Write" in {
      val mockRecordSet = mock[RecordSet]
      accessValidationTest.canUpdateRecordSet(
        userAuthWrite,
        mockRecordSet.name,
        mockRecordSet.typ,
        zoneInWrite) should be(right)
    }

    "return true if the user has AccessLevel.Delete" in {
      val mockRecordSet = mock[RecordSet]
      val userAccess = okUser.copy(id = "Delete")
      val userAuth = AuthPrincipal(userAccess, groupIds)
      val userAcl = ACLRule(AccessLevel.Delete, userId = Some(userAuth.userId), groupId = None)
      val zoneIn = memberZoneNotAuthorized.copy(acl = ZoneACL(Set(userAcl)))
      accessValidationTest.canUpdateRecordSet(
        userAuth,
        mockRecordSet.name,
        mockRecordSet.typ,
        zoneIn) should be(right)
    }
  }

  "canDeleteRecordSet" should {
    "return a NotAuthorizedError if the user has AccessLevel.NoAccess" in {
      val mockRecordSet = mock[RecordSet]

      val error = leftValue(
        accessValidationTest
          .canDeleteRecordSet(userAuthNone, mockRecordSet.name, mockRecordSet.typ, zoneInNone))
      error shouldBe a[NotAuthorizedError]
    }

    "return a NotAuthorizedError if the user has AccessLevel.Read" in {
      val mockRecordSet = mock[RecordSet]

      val error = leftValue(
        accessValidationTest
          .canDeleteRecordSet(userAuthRead, mockRecordSet.name, mockRecordSet.typ, zoneInRead))
      error shouldBe a[NotAuthorizedError]
    }

    "return a NotAuthorizedError if the user has AccessLevel.Write" in {
      val mockRecordSet = mock[RecordSet]

      val error = leftValue(
        accessValidationTest
          .canDeleteRecordSet(userAuthWrite, mockRecordSet.name, mockRecordSet.typ, zoneInWrite))
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the user has AccessLevel.Delete" in {
      val mockRecordSet = mock[RecordSet]
      accessValidationTest.canDeleteRecordSet(
        userAuthDelete,
        mockRecordSet.name,
        mockRecordSet.typ,
        zoneInDelete) should be(right)
    }
  }

  "canViewRecordSet" should {
    "return a NotAuthorizedError if the user has AccessLevel.NoAccess" in {
      val mockRecordSet = mock[RecordSet]

      val error = leftValue(
        accessValidationTest
          .canViewRecordSet(userAuthNone, mockRecordSet.name, mockRecordSet.typ, zoneInNone))
      error shouldBe a[NotAuthorizedError]
    }

    "return true if the user has AccessLevel.Read" in {
      val mockRecordSet = mock[RecordSet]
      accessValidationTest.canViewRecordSet(
        userAuthRead,
        mockRecordSet.name,
        mockRecordSet.typ,
        zoneInRead) should be(right)
    }

    "return true if the user has AccessLevel.Write" in {
      val mockRecordSet = mock[RecordSet]

      accessValidationTest.canViewRecordSet(
        userAuthWrite,
        mockRecordSet.name,
        mockRecordSet.typ,
        zoneInWrite) should be(right)
    }

    "return true if the user has AccessLevel.Delete" in {
      val mockRecordSet = mock[RecordSet]
      accessValidationTest.canViewRecordSet(
        userAuthDelete,
        mockRecordSet.name,
        mockRecordSet.typ,
        zoneInDelete) should be(right)
    }
  }

  "isAdminOrSuper" should {
    "return false if the user is not admin/super (membership auth)" in {
      val auth = okGroupAuth.copy(
        signedInUser = okGroupAuth.signedInUser.copy(isSuper = false),
        memberGroupIds = Seq.empty)
      val result = accessValidationTest.hasZoneAdminAccess(auth, memberZoneNotAuthorized)
      result shouldBe false
    }

    "return true if the user is super (membership auth)" in {
      val auth = okGroupAuth.copy(
        signedInUser = okGroupAuth.signedInUser.copy(isSuper = true),
        memberGroupIds = Seq.empty)
      val result = accessValidationTest.hasZoneAdminAccess(auth, memberZoneNotAuthorized)
      result shouldBe true
    }

    "return true if the user is not super but is admin (membership auth)" in {
      val result = accessValidationTest.hasZoneAdminAccess(okGroupAuth, memberOkZoneAuthorized)
      result shouldBe true
    }
  }

  "getAccessLevel" should {
    "return AccessLevel.Delete if the user is admin/super" in {
      val mockRecordSet = mock[RecordSet]
      val result = accessValidationTest.getAccessLevel(
        okUserAuth,
        mockRecordSet.name,
        mockRecordSet.typ,
        memberOkZoneAuthorized)
      result shouldBe AccessLevel.Delete
    }

    "return the result of getAccessLevel if user is not admin/super" in {
      val mockRecordSet = mock[RecordSet]
      val userAccess = okUser.copy(id = "Read")
      val userAuth = AuthPrincipal(userAccess, groupIds)
      val userAcl = ACLRule(AccessLevel.Read, userId = Some(userAuth.userId), groupId = None)
      val zoneIn = memberZoneNotAuthorized.copy(acl = ZoneACL(Set(userAcl)))

      val result =
        accessValidationTest.getAccessLevel(userAuth, mockRecordSet.name, mockRecordSet.typ, zoneIn)
      result shouldBe AccessLevel.Read
    }
  }

  "getAccessFromAcl" should {

    "choose a rule associated with a particular user" in {
      val zoneAcl = ZoneACL(Set(userWriteAcl))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result = accessValidationTest.getAccessFromAcl(
        okUserAuth,
        mockRecordSet.name,
        mockRecordSet.typ,
        zone)
      result shouldBe AccessLevel.Write
    }

    "choose a rule associated with a group a user is in" in {
      val zoneAcl = ZoneACL(Set(groupReadAcl))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result = accessValidationTest.getAccessFromAcl(
        okUserAuth,
        mockRecordSet.name,
        mockRecordSet.typ,
        zone)
      result shouldBe AccessLevel.Read
    }

    "choose a rule associated with all users" in {
      val zoneAcl = ZoneACL(Set(allReadACL))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result = accessValidationTest.getAccessFromAcl(
        okUserAuth,
        mockRecordSet.name,
        mockRecordSet.typ,
        zone)
      result shouldBe AccessLevel.Read
    }

    "default to NoAccess if no rules are set" in {
      val mockRecordSet = mock[RecordSet]
      val zoneAcl = ZoneACL()
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(
        okUserAuth,
        mockRecordSet.name,
        mockRecordSet.typ,
        zone)
      result shouldBe AccessLevel.NoAccess
    }

    "ignore rules for different users, irrelevant groups" in {
      val zoneAcl = ZoneACL(Set(badGroupReadAcl, badUserWriteAcl))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result = accessValidationTest.getAccessFromAcl(
        okUserAuth,
        mockRecordSet.name,
        mockRecordSet.typ,
        zone)
      result shouldBe AccessLevel.NoAccess
    }

    "choose user over group rules" in {
      val zoneAcl = ZoneACL(Set(userWriteAcl, groupReadAcl))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result = accessValidationTest.getAccessFromAcl(
        okUserAuth,
        mockRecordSet.name,
        mockRecordSet.typ,
        zone)
      result shouldBe AccessLevel.Write
    }

    "choose group over all-user" in {
      val zoneAcl = ZoneACL(Set(groupWriteAcl, allReadACL))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result = accessValidationTest.getAccessFromAcl(
        okUserAuth,
        mockRecordSet.name,
        mockRecordSet.typ,
        zone)
      result shouldBe AccessLevel.Write
    }

    "prioritize more restrictive user rules in a tie" in {
      val zoneAcl = ZoneACL(Set(userReadAcl, userWriteAcl))
      val zone = Zone("name", "email", acl = zoneAcl)

      val mockRecordSet = mock[RecordSet]
      val result = accessValidationTest.getAccessFromAcl(
        okUserAuth,
        mockRecordSet.name,
        mockRecordSet.typ,
        zone)
      result shouldBe AccessLevel.Read
    }

    "apply to specific record type" in {
      val rs = RecordSet(
        memberZoneNotAuthorized.id,
        "ok",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        DateTime.now)
      val aclRuleA =
        ACLRule(AccessLevel.Read, userId = Some(okUserAuth.userId), recordTypes = Set(RecordType.A))

      val zoneAcl = ZoneACL(Set(aclRuleA))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okUserAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.Read
    }

    "not apply to all if record type specified" in {
      val rs = RecordSet(
        memberZoneNotAuthorized.id,
        "ok",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        DateTime.now)
      val aclRuleA = ACLRule(
        AccessLevel.Read,
        userId = Some(okUserAuth.userId),
        recordTypes = Set(RecordType.AAAA))

      val zoneAcl = ZoneACL(Set(aclRuleA))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okUserAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.NoAccess
    }

    "prioritize more specific record type lists" in {
      val rs = RecordSet(
        memberZoneNotAuthorized.id,
        "ok",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        DateTime.now)
      val aclRuleA = ACLRule(
        AccessLevel.Write,
        userId = Some(okUserAuth.userId),
        recordTypes = Set(RecordType.A))
      val aclRuleMany = ACLRule(
        AccessLevel.Read,
        userId = Some(okUserAuth.userId),
        recordTypes = Set(RecordType.A, RecordType.AAAA))

      val zoneAcl = ZoneACL(Set(aclRuleA, aclRuleMany))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okUserAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.Write
    }

    "prioritize record type lists over all" in {
      val rs = RecordSet(
        memberZoneNotAuthorized.id,
        "ok",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        DateTime.now)
      val aclRuleA = ACLRule(
        AccessLevel.Write,
        userId = Some(okUserAuth.userId),
        recordTypes = Set(RecordType.A))
      val aclRuleAll =
        ACLRule(AccessLevel.Read, userId = Some(okUserAuth.userId), recordTypes = Set())

      val zoneAcl = ZoneACL(Set(aclRuleA, aclRuleAll))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okUserAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.Write
    }

    "filter in based on record mask" in {
      val rs = RecordSet(
        memberZoneNotAuthorized.id,
        "rsname",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        DateTime.now)
      val aclRule = userReadAcl.copy(recordMask = Some("rs.*"))

      val zoneAcl = ZoneACL(Set(aclRule))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okUserAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.Read
    }

    "exclude records based on record mask" in {
      val rs = RecordSet(
        memberZoneNotAuthorized.id,
        "rsname",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        DateTime.now)
      val aclRule = userReadAcl.copy(recordMask = Some("bad.*"))

      val zoneAcl = ZoneACL(Set(aclRule))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okUserAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.NoAccess
    }

    "prioritize a record mask over apply to all" in {
      val rs = RecordSet(
        memberZoneNotAuthorized.id,
        "rsname",
        RecordType.A,
        200,
        RecordSetStatus.Active,
        DateTime.now)
      val aclRuleRM = userReadAcl.copy(recordMask = Some("rs.*"))
      val aclRuleAll = userWriteAcl.copy(recordMask = None)

      val zoneAcl = ZoneACL(Set(aclRuleAll, aclRuleRM))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okUserAuth, rs.name, rs.typ, zone)
      result shouldBe AccessLevel.Read
    }
  }

  "ruleAppliesToRecordNameIPv4" should {

    "filter in/out record set based on CIDR rule of 0 (lower bound for ip4 CIDR rules)" in {
      val aclRule = userReadAcl.copy(recordMask = Some("120.1.2.0/0"))
      val znTrue = Zone("40.120.in-addr.arpa.", "email")
      val rsTrue =
        RecordSet("id", "20.3", RecordType.PTR, 200, RecordSetStatus.Active, DateTime.now)
      val znFalse = Zone("255.129.in-addr.arpa.", "email")
      val rsFalse =
        RecordSet("id", "255.255", RecordType.PTR, 200, RecordSetStatus.Active, DateTime.now)

      accessValidationTest.ruleAppliesToRecordName(rsTrue.name, rsTrue.typ, znTrue, aclRule) shouldBe true
      accessValidationTest.ruleAppliesToRecordName(rsFalse.name, rsFalse.typ, znFalse, aclRule) shouldBe false
    }

    "filter in/out record set based on CIDR rule of 8" in {
      val aclRule = userReadAcl.copy(recordMask = Some("202.204.62.208/8"))
      val znTrue = Zone("202.in-addr.arpa.", "email")
      val rsTrue =
        RecordSet("id", "32.23.100", RecordType.PTR, 200, RecordSetStatus.Active, DateTime.now)
      val znFalse = Zone("1.23.100.in-addr.arpa.", "email")
      val rsFalse = RecordSet("id", "3", RecordType.PTR, 200, RecordSetStatus.Active, DateTime.now)

      accessValidationTest.ruleAppliesToRecordName(rsTrue.name, rsTrue.typ, znTrue, aclRule) shouldBe true
      accessValidationTest.ruleAppliesToRecordName(rsFalse.name, rsFalse.typ, znFalse, aclRule) shouldBe false
    }

    "filter in/out record set based on CIDR rule of 32 (upper bound for ip4 CIDR rules)" in {
      val aclRule = userReadAcl.copy(recordMask = Some("120.1.2.0/32"))
      val znTrue = Zone("1.120.in-addr.arpa.", "email")
      val rsTrue = RecordSet("id", "0.2", RecordType.PTR, 200, RecordSetStatus.Active, DateTime.now)
      val znFalse = Zone("23.10.in-addr.arpa.", "email")
      val rsFalse = RecordSet("id", "3", RecordType.PTR, 200, RecordSetStatus.Active, DateTime.now)

      accessValidationTest.ruleAppliesToRecordName(rsTrue.name, rsTrue.typ, znTrue, aclRule) shouldBe true
      accessValidationTest.ruleAppliesToRecordName(rsFalse.name, rsFalse.typ, znFalse, aclRule) shouldBe false
    }

    "include rules that has no mask defined and filter in record set" in {
      val aclRule = userReadAcl.copy()
      val zn = Zone("202.in-addr.arpa.", "email")
      val rs =
        RecordSet("id", "32.23.100", RecordType.PTR, 200, RecordSetStatus.Active, DateTime.now)

      accessValidationTest.ruleAppliesToRecordName(rs.name, rs.typ, zn, aclRule) shouldBe true
    }

    "not apply when regex could match a ptr" in {
      val rs = RecordSet(
        "id",
        "000.000.000.000",
        RecordType.PTR,
        200,
        RecordSetStatus.Active,
        DateTime.now)
      val aclRule = userReadAcl.copy(recordMask = Some(".*0.*"))

      val zoneAcl = ZoneACL(Set(aclRule))
      val zone = Zone("name", "email", acl = zoneAcl)

      val result = accessValidationTest.getAccessFromAcl(okUserAuth, rs.name, rs.typ, zone)
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
        DateTime.now)
      val znFalse = Zone("5.b.e.f.9.d.2.f.9.5.c.c.7.4.a.a.8.ip6.arpa.", "email")
      val rsFalse = RecordSet(
        "id",
        "2.3.d.f.3.7.3.4.8.a.5.b.d.1.7",
        RecordType.PTR,
        200,
        RecordSetStatus.Active,
        DateTime.now)

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
        DateTime.now)
      val znFalse = Zone("5.b.e.f.9.d.2.f.9.5.c.c.7.4.a.a.8.ip6.arpa.", "email")
      val rsFalse = RecordSet(
        "id",
        "f.3.d.f.3.7.3.4.8.a.5.b.d.1.7",
        RecordType.PTR,
        200,
        RecordSetStatus.Active,
        DateTime.now)

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
        DateTime.now)
      val znFalse = Zone("5.b.e.f.9.d.2.f.9.5.c.c.7.4.a.a.8.ip6.arpa.", "email")
      val rsFalse = RecordSet(
        "id",
        "f.3.d.f.3.7.3.4.8.a.5.b.d.1.7",
        RecordType.PTR,
        200,
        RecordSetStatus.Active,
        DateTime.now)

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
        DateTime.now)

      accessValidationTest.ruleAppliesToRecordName(rs.name, rs.typ, zn, aclRule) shouldBe true
    }
  }

  "getListAccessLevels" should {
    "return access level DELETE if the user is admin/super of the zone" in {
      val zone = Zone("test", "test", adminGroupId = okGroup.id)
      val recordList = List(rsOk.copy(zoneId = zone.id))

      val result = accessValidationTest.getListAccessLevels(okUserAuth, recordList, zone)

      val expected = recordList.map(RecordSetInfo(_, AccessLevel.Delete))
      result shouldBe expected
    }

    "return access level NOACCESS if there is no ACL rule for the user" in {
      val recordList = List("rs1", "rs2", "rs3").map {
        RecordSet("zoneId", _, RecordType.A, 100, RecordSetStatus.Active, DateTime.now())
      }
      val zone = Zone("test", "test")

      val result = accessValidationTest.getListAccessLevels(okUserAuth, recordList, zone)

      val expected = recordList.map(RecordSetInfo(_, AccessLevel.NoAccess))
      result shouldBe expected
    }

    "return the appropriate access level for each RecordSet in the list" in {
      val rs1 =
        RecordSet("zoneId", "rs1", RecordType.A, 100, RecordSetStatus.Active, DateTime.now())
      val rs2 = rs1.copy(name = "rs2")
      val rs3 = rs1.copy(name = "rs3")
      val recordList = List(rs1, rs2, rs3)

      val rs1Rule =
        ACLRule(AccessLevel.Write, userId = Some(okUserAuth.userId), recordMask = Some("rs1"))
      val rs2Rule =
        ACLRule(AccessLevel.NoAccess, groupId = Some(okGroup.id), recordMask = Some("rs2"))
      val aclRules = ZoneACL(Set(groupReadAcl, rs1Rule, rs2Rule))
      val zone = Zone("test", "test", acl = aclRules)

      val result = accessValidationTest.getListAccessLevels(okUserAuth, recordList, zone)

      val expected = List(
        RecordSetInfo(rs1, AccessLevel.Write),
        RecordSetInfo(rs2, AccessLevel.NoAccess),
        RecordSetInfo(rs3, AccessLevel.Read))
      result shouldBe expected
    }

  }
}
