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

package vinyldns.core.domain.zone

import AccessLevel.AccessLevel
import vinyldns.core.domain.record.RecordType.RecordType

object AccessLevel extends Enumeration {
  type AccessLevel = Value
  val NoAccess, Delete, Write, Read = Value
}

case class ACLRule(
    accessLevel: AccessLevel,
    allowDottedHosts: Boolean = false,
    description: Option[String] = None,
    userId: Option[String] = None,
    groupId: Option[String] = None,
    recordMask: Option[String] = None, // regular expression for record names
    recordTypes: Set[RecordType] = Set.empty
                  )

object ACLRule {
  final val DESCRIPTION_MAX = 255

  def apply(aclRuleInfo: ACLRuleInfo): ACLRule =
    ACLRule(
      aclRuleInfo.accessLevel,
      aclRuleInfo.allowDottedHosts,
      aclRuleInfo.description,
      aclRuleInfo.userId,
      aclRuleInfo.groupId,
      aclRuleInfo.recordMask,
      aclRuleInfo.recordTypes,
    )
}
