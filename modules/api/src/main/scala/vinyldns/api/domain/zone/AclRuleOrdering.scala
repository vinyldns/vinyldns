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

import com.comcast.ip4s.Cidr
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.zone.ACLRule

trait ACLRuleOrdering extends Ordering[ACLRule] {
  private def sortableUserValue(rule: ACLRule) =
    if (rule.userId.isDefined)
      1
    else if (rule.groupId.isDefined)
      2
    else
      3

  def sortableRecordMaskValue(rule: ACLRule): Int

  private def sortableRecordTypeValue(rule: ACLRule) =
    if (rule.recordTypes.isEmpty) RecordType.values.size else rule.recordTypes.size

  def compare(rule1: ACLRule, rule2: ACLRule): Int =
    // tuples compare in order 1st to last
    (
      sortableUserValue(rule1),
      sortableRecordMaskValue(rule1),
      sortableRecordTypeValue(rule1),
      rule1.accessLevel
    ).compare(
      sortableUserValue(rule2),
      sortableRecordMaskValue(rule2),
      sortableRecordTypeValue(rule2),
      rule2.accessLevel
    )
}

object ACLRuleOrdering extends ACLRuleOrdering {
  def sortableRecordMaskValue(rule: ACLRule): Int =
    rule.recordMask match {
      case Some(".*") => 2
      case Some(_) => 1
      case None => 2
    }
}

object PTRACLRuleOrdering extends ACLRuleOrdering {
  def sortableRecordMaskValue(rule: ACLRule): Int = {
    val slash = rule.recordMask match {
      case Some(cidrRule) => Cidr.fromString(cidrRule).get.prefixBits
      case None => 0
    }
    128 - slash
  }
}
