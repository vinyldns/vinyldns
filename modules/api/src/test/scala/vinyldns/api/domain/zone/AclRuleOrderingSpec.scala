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

import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpecLike}
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.api.{GroupTestData, ResultHelpers, VinylDNSTestData}

class AclRuleOrderingSpec
    extends WordSpecLike
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with ResultHelpers
    with GroupTestData
    with VinylDNSTestData {

  //Generate rules with a mix of types/access/etc
  private val rules = for {
    level <- AccessLevel.values
    mask <- List(None, Some("rs.*"))
    typeVal <- List(Set[RecordType](), Set(RecordType.A), Set(RecordType.A, RecordType.CNAME))
    userId <- List(Some("userIdVal"), None)
    groupId <- if (userId.isEmpty) List(Some("groupIdVal"), None) else List(None)
  } yield
    ACLRule(
      accessLevel = level,
      recordMask = mask,
      recordTypes = typeVal,
      userId = userId,
      groupId = groupId)

  "ACLRuleOrdering" should {
    //Sort all
    implicit val order: ACLRuleOrdering = ACLRuleOrdering
    val sortedRules = rules.toList.sorted

    "Prioritize rules in order of user type first" in {
      def userConversion(rule: ACLRule): Int = (rule.userId, rule.groupId) match {
        case (Some(_), None) => 1
        case (None, Some(_)) => 2
        case (None, None) => 3
        case _ => -1 //wont hit this case
      }

      //validate each against next
      (1 until sortedRules.size).foreach { i =>
        userConversion(sortedRules(i - 1)) shouldBe <=(userConversion(sortedRules(i)))
      }
    }

    "Prioritize rules in order of record mask 2nd" in {
      (1 until sortedRules.size).foreach { i =>
        val lowerRule = sortedRules(i - 1)
        val upperRule = sortedRules(i)

        if (sameUserGroupLevel(lowerRule, upperRule)) {
          val isOrdered = (lowerRule.recordMask == upperRule.recordMask) ||
            (lowerRule.recordMask == Some("rs.*") && upperRule.recordMask.isEmpty)

          isOrdered shouldBe true
        }
      }
    }

    "Prioritize rules in order of record type 3rd" in {
      def typeConversion(rule: ACLRule): Int =
        if (rule.recordTypes == Set(RecordType.A)) 1
        else if (rule.recordTypes == Set(RecordType.A, RecordType.CNAME)) 2
        else 3

      (1 until sortedRules.size).foreach { i =>
        val lowerRule = sortedRules(i - 1)
        val upperRule = sortedRules(i)

        if (sameUserGroupLevel(lowerRule, upperRule) && lowerRule.recordMask == upperRule.recordMask) {
          typeConversion(lowerRule) shouldBe <=(typeConversion(upperRule))
        }
      }
    }

    "Prioritize rules in order of access level last" in {
      (1 until sortedRules.size).foreach { i =>
        val lowerRule = sortedRules(i - 1)
        val upperRule = sortedRules(i)

        if (sameUserGroupLevel(lowerRule, upperRule) &&
          lowerRule.recordMask == upperRule.recordMask &&
          lowerRule.recordTypes == upperRule.recordTypes) {

          lowerRule.accessLevel shouldBe <=(upperRule.accessLevel)
        }
      }
    }
  }

  "PTRACLRuleOrdering" should {

    "Prioritize PTRACL rules based off ip4 CIDR rule masks" in {
      val sortedRules = List(
        ACLRule(accessLevel = AccessLevel.NoAccess, recordMask = Option("72.47.111.200/32")),
        ACLRule(accessLevel = AccessLevel.NoAccess, recordMask = Option("72.47.111.200/20")),
        ACLRule(accessLevel = AccessLevel.NoAccess, recordMask = Option("72.47.111.200/1")),
        ACLRule(accessLevel = AccessLevel.NoAccess)
      )
      val unsortedRules = List(sortedRules(1), sortedRules(0), sortedRules(3), sortedRules(2))

      implicit val ruleOrder: ACLRuleOrdering = PTRACLRuleOrdering
      val testSortedRules = unsortedRules.sorted

      testSortedRules shouldBe sortedRules
    }

    "Prioritize PTRACL rules based off ip6 CIDR rule masks" in {
      val sortedRules = List(
        ACLRule(
          accessLevel = AccessLevel.NoAccess,
          recordMask = Option("71db:5a84:373f:d328:aa47:cc59:f2d9:feb5/128")),
        ACLRule(
          accessLevel = AccessLevel.NoAccess,
          recordMask = Option("71db:5a84:373f:d328:aa47:cc59:f2d9:feb5/80")),
        ACLRule(
          accessLevel = AccessLevel.NoAccess,
          recordMask = Option("71db:5a84:373f:d328:aa47:cc59:f2d9:feb5/8")),
        ACLRule(accessLevel = AccessLevel.NoAccess)
      )
      val unsortedRules = List(sortedRules(1), sortedRules(0), sortedRules(3), sortedRules(2))

      implicit val ruleOrder: ACLRuleOrdering = PTRACLRuleOrdering
      val testSortedRules = unsortedRules.sorted

      testSortedRules shouldBe sortedRules
    }
  }

  private def sameUserGroupLevel(rule1: ACLRule, rule2: ACLRule): Boolean =
    rule1.groupId == rule2.groupId && rule1.userId == rule2.userId
}
