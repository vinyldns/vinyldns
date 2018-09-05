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

package vinyldns.core

import vinyldns.core.domain.zone._
import TestMembershipData._

object TestZoneData {

  /* ZONES */
  val okZone: Zone = Zone("ok.zone.recordsets.", "test@test.com", adminGroupId = okGroup.id)

  val validConnection =
    ZoneConnection("connectionName", "connectionKeyName", "connectionKey", "127.0.0.1")

  val zoneActive: Zone = Zone(
    "some.zone.name.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = Some(validConnection))

  val userAclRule: ACLRule = ACLRule(AccessLevel.Read, userId = Some("someUser"))

  val groupAclRule: ACLRule = ACLRule(AccessLevel.Read, groupId = Some("someGroup"))

  /* ZONE CHANGES */
  val zoneChangePending: ZoneChange =
    ZoneChange(okZone, "ok", ZoneChangeType.Update, ZoneChangeStatus.Pending)

}
