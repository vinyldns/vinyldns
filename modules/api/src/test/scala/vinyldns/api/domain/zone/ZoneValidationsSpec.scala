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

import cats.scalatest.EitherMatchers
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.ResultHelpers
import vinyldns.core.TestZoneData._
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.{ACLRule, ZoneACL}

class ZoneValidationsSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ResultHelpers
    with EitherMatchers {

  val testing = new ZoneValidations(syncDelayMillis = 10000)

  import testing._

  "outsideSyncDelay" should {
    "return ok when the zone has not been synced" in {
      val zone = okZone.copy(latestSync = None)
      outsideSyncDelay(zone) should be(right)
    }
    "return ok when the zone sync is not within the sync delay" in {
      val zone = okZone.copy(latestSync = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS).minusMillis(10001)))
      outsideSyncDelay(zone) should be(right)
    }
    "return RecentSyncError when the zone sync is within the sync delay" in {
      val zone = okZone.copy(latestSync = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)))
      val error = leftValue(outsideSyncDelay(zone))
      error shouldBe a[RecentSyncError]
    }
  }

  "isValidAclRule" should {
    "fail if mask is an invalid regex" in {
      val invalidRegexMaskRuleInfo = baseAclRuleInfo.copy(recordMask = Some("x{5,-3}"))
      val error = leftValue(isValidAclRule(ACLRule(invalidRegexMaskRuleInfo)))
      error shouldBe a[InvalidRequest]
    }

    "fail if given an invalid CIDR rule" in {
      val invalidPtrAclRuleInfo = baseAclRuleInfo.copy(
        recordMask = Some("not a cidr rule"),
        recordTypes = Set(RecordType.PTR)
      )
      val error = leftValue(isValidAclRule(ACLRule(invalidPtrAclRuleInfo)))
      error shouldBe a[InvalidRequest]
      error.getMessage shouldBe "PTR types must have no mask or a valid CIDR mask: Invalid CIDR block"
    }

    "fail if there are multiple record types including PTR and mask is regex" in {
      val invalidMultipleTypeInfo = baseAclRuleInfo.copy(
        recordMask = Some("regex"),
        recordTypes = Set(RecordType.A, RecordType.AAAA, RecordType.CNAME, RecordType.PTR)
      )
      val error = leftValue(isValidAclRule(ACLRule(invalidMultipleTypeInfo)))
      error shouldBe a[InvalidRequest]
    }

    "fail if there are multiple record types including PTR and mask is cidr" in {
      val invalidMultipleTypeInfo = baseAclRuleInfo.copy(
        recordMask = Some("10.10.10.10/5"),
        recordTypes = Set(RecordType.A, RecordType.AAAA, RecordType.CNAME, RecordType.PTR)
      )
      val error = leftValue(isValidAclRule(ACLRule(invalidMultipleTypeInfo)))
      error shouldBe a[InvalidRequest]
    }

    "pass if there are multiple record types including PTR and mask is None" in {
      val validMultipleTypeNoneAclRuleInfo = baseAclRuleInfo.copy(
        recordTypes = Set(RecordType.A, RecordType.AAAA, RecordType.CNAME, RecordType.PTR)
      )
      isValidAclRule(ACLRule(validMultipleTypeNoneAclRuleInfo)) should be(right)
    }

    "fail if the ACL has neither user nor group id" in {
      val acl = baseAclRuleInfo.copy(groupId = None, userId = None)
      val error = leftValue(isValidAclRule(ACLRule(acl)))
      error shouldBe a[InvalidRequest]
    }

    "fail if the ACL has both user and group ids" in {
      val acl = baseAclRuleInfo.copy(groupId = Some("grp"), userId = Some("usr"))
      val error = leftValue(isValidAclRule(ACLRule(acl)))
      error shouldBe a[InvalidRequest]
    }
  }

  "isValidZoneAcl" should {
    "fail if any rule is invalid" in {
      val invalidAclRule = ACLRule(baseAclRuleInfo.copy(recordMask = Some("x{5,-3}")))
      val goodRule = baseAclRule.copy(recordTypes = Set(RecordType.A))
      val zoneAcl = ZoneACL(Set(baseAclRule, goodRule, invalidAclRule))
      val error = leftValue(isValidZoneAcl(zoneAcl))
      error shouldBe a[InvalidRequest]
    }
    "succeed if all rules are valid" in {
      val goodRule = baseAclRule.copy(recordTypes = Set(RecordType.A))
      val zoneAcl = ZoneACL(Set(baseAclRule, goodRule))
      isValidZoneAcl(zoneAcl) should be(right)
    }
  }
  "isValidGenerateZoneConnection" should {
    "fail if any zone provider connection is invalid" in {
      val isInValidGenerateZoneConnection = isValidGenerateZoneConn (500, "Connection invalid")
      isInValidGenerateZoneConnection should be(left)

    }
    "succeed if all zone provider connection are valid" in {
      val isValidGenerateZoneConnection = isValidGenerateZoneConn (200, "Connection Success")
      isValidGenerateZoneConnection should be(right)
    }
  }
}
