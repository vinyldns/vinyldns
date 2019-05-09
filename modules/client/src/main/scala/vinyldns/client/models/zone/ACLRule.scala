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

package vinyldns.client.models.zone

import upickle.default._
import vinyldns.client.models.OptionRW
import vinyldns.client.models.record.{AccessLevelRW, RecordSetTypeRW}
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.zone.AccessLevel
import vinyldns.core.domain.zone.AccessLevel.AccessLevel

/*
  A ZoneResponse has acl rules come in the format {rules: [...]}
 */
case class Rules(rules: List[ACLRule])

object Rules {
  implicit val rw: ReadWriter[Rules] = macroRW
}

case class ACLRule(
    accessLevel: AccessLevel.AccessLevel,
    recordTypes: Seq[RecordType.RecordType],
    description: Option[String] = None,
    userId: Option[String] = None,
    userName: Option[String] = None,
    groupId: Option[String] = None,
    recordMask: Option[String] = None,
    displayName: Option[String] = None)

object ACLRule extends OptionRW with RecordSetTypeRW with AccessLevelRW {
  object AclType extends Enumeration {
    type AclType = Value
    val User, Group = Value
  }

  def apply(): ACLRule = ACLRule(AccessLevel.Read, Seq())

  def toAccessLevelDisplay(accessLevel: AccessLevel): String =
    accessLevel match {
      case AccessLevel.NoAccess => "No Access"
      case AccessLevel.Read => "Read"
      case AccessLevel.Write => "Read + Write"
      case AccessLevel.Delete => "Read + Write + Delete"
    }
  implicit val rw: ReadWriter[ACLRule] = macroRW
}
