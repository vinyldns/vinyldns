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

import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.zone.AccessLevel.AccessLevel

case class ACLRuleInfo(
    accessLevel: AccessLevel,
    allowDottedHosts: Boolean,
    description: Option[String],
    userId: Option[String],
    groupId: Option[String],
    recordMask: Option[String],
    recordTypes: Set[RecordType],
    displayName: Option[String] = None
                      )

object ACLRuleInfo {
  def apply(aCLRule: ACLRule, name: Option[String]): ACLRuleInfo =
    ACLRuleInfo(
      accessLevel = aCLRule.accessLevel,
      allowDottedHosts = aCLRule.allowDottedHosts,
      description = aCLRule.description,
      userId = aCLRule.userId,
      groupId = aCLRule.groupId,
      recordMask = aCLRule.recordMask,
      recordTypes = aCLRule.recordTypes,
      displayName = name
    )
}
