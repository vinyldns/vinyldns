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
import org.joda.time.DateTime

object TestZoneData {

  /* ZONE CONNECTIONS */
  val validConnection =
    ZoneConnection("connectionName", "connectionKeyName", "connectionKey", "127.0.0.1")

  val testConnection: Option[ZoneConnection] = Some(
    ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1"))

  /* ZONES */
  val okZone: Zone = Zone(
    "ok.zone.recordsets.",
    "test@test.com",
    adminGroupId = okGroup.id,
    connection = testConnection)
  val abcZone: Zone = Zone("abc.zone.recordsets.", "test@test.com", adminGroupId = abcGroup.id)
  val xyzZone: Zone = Zone("xyz.", "abc@xyz.com", adminGroupId = xyzGroup.id)
  val zoneIp4: Zone = Zone("0.162.198.in-addr.arpa.", "test@test.com", adminGroupId = abcGroup.id)
  val zoneIp6: Zone =
    Zone("1.9.e.f.c.c.7.2.9.6.d.f.ip6.arpa.", "test@test.com", adminGroupId = abcGroup.id)

  val zoneActive: Zone = Zone(
    "some.zone.name.",
    "test@test.com",
    adminGroupId = okGroup.id,
    status = ZoneStatus.Active,
    connection = Some(validConnection))

  val zoneDeleted: Zone = Zone(
    "some.deleted.zone.",
    "test@test.com",
    status = ZoneStatus.Deleted,
    connection = Some(validConnection))

  val zoneNotAuthorized: Zone = Zone("not.auth.zone.", "test@test.com", adminGroupId = "no-id")

  val sharedZone: Zone =
    zoneActive.copy(id = "sharedZoneId", shared = true, adminGroupId = abcGroup.id)

  /* ACL RULES */
  val userAclRule: ACLRule = ACLRule(AccessLevel.Read, userId = Some("someUser"))

  val userAclRuleInfo: ACLRuleInfo = ACLRuleInfo(userAclRule, None)

  val groupAclRule: ACLRule = ACLRule(AccessLevel.Read, groupId = Some("someGroup"))

  val baseAclRuleInfo: ACLRuleInfo =
    ACLRuleInfo(AccessLevel.Read, Some("desc"), None, Some("group"), None, Set.empty)
  val baseAclRule: ACLRule = ACLRule(baseAclRuleInfo)

  /* ZONE CHANGES */
  val zoneChangePending: ZoneChange =
    ZoneChange(okZone, "ok", ZoneChangeType.Update, ZoneChangeStatus.Pending)

  val zoneCreate: ZoneChange = ZoneChange(
    okZone,
    "ok",
    ZoneChangeType.Create,
    ZoneChangeStatus.Complete,
    created = DateTime.now.minus(1000))

  val zoneUpdate: ZoneChange = zoneChangePending.copy(status = ZoneChangeStatus.Complete)

  def makeTestPendingZoneChange(zone: Zone): ZoneChange =
    ZoneChange(zone, "userId", ZoneChangeType.Update, ZoneChangeStatus.Pending)

}
