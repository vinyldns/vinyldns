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

package vinyldns.api

import org.joda.time.DateTime
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.api.repository.TestDataLoader
import vinyldns.core.TestRecordSetData
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.{Group, User}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._

trait VinylDNSTestData {

  val usr: User = TestDataLoader.okUser
  val grp: Group = Group(
    "ok",
    "test@test.com",
    Some("a test group"),
    memberIds = Set(usr.id),
    adminUserIds = Set(usr.id),
    created = DateTime.now.secondOfDay().roundFloorCopy())
  val okAuth: AuthPrincipal = AuthPrincipal(TestDataLoader.okUser, Seq(grp.id))

  val testConnection: Option[ZoneConnection] = Some(
    ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1"))

  val okZone: Zone = Zone("ok.zone.recordsets.", "test@test.com", adminGroupId = grp.id)
  val zoneActive: Zone = Zone(
    "ok.zone.recordsets.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection)

  val zoneIp4: Zone = okZone.copy(name = "0.162.198.in-addr.arpa.")
  val zoneIp6: Zone = okZone.copy(name = "1.9.e.f.c.c.7.2.9.6.d.f.ip6.arpa.")

  val rsOk: RecordSet = TestRecordSetData.rsOk

  val aaaa: RecordSet = TestRecordSetData.aaaa

  val cname: RecordSet = TestRecordSetData.cname

  val invalidCnameToOrigin: RecordSet = RecordSet(
    okZone.id,
    "@",
    RecordType.CNAME,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(CNAMEData("cname")))

  val invalidNsRecordToOrigin: RecordSet = RecordSet(
    okZone.id,
    "@",
    RecordType.NS,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(NSData("nsdata")))

  val ptrIp4: RecordSet = TestRecordSetData.ptrIp4

  val ptrIp6: RecordSet = TestRecordSetData.ptrIp6

  val srv: RecordSet = TestRecordSetData.srv

  val ns: RecordSet = TestRecordSetData.ns

  val zoneCreate: ZoneChange = ZoneChange(
    okZone,
    "ok",
    ZoneChangeType.Create,
    ZoneChangeStatus.Complete,
    created = DateTime.now.minus(1000))
  val zoneUpdate: ZoneChange = ZoneChange(
    okZone,
    "ok",
    ZoneChangeType.Update,
    ZoneChangeStatus.Complete,
    created = DateTime.now)

  val pendingCreateAAAA: RecordSetChange = RecordSetChangeGenerator.forAdd(aaaa, zoneActive, okAuth)

  val zoneChangePending: ZoneChange =
    ZoneChange(okZone, "ok", ZoneChangeType.Update, ZoneChangeStatus.Pending)

  val atRs: RecordSet = RecordSet(
    okZone.id,
    "@",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AData("10.1.1.1")))

  val fakeTime: DateTime = new DateTime(2010, 1, 1, 0, 0)

  val baseAclRuleInfo: ACLRuleInfo =
    ACLRuleInfo(AccessLevel.Read, Some("desc"), None, Some("group"), None, Set.empty)
  val baseAclRule: ACLRule = ACLRule(baseAclRuleInfo)

  def anonymize(recordSet: RecordSet): RecordSet =
    recordSet.copy(id = "a", created = fakeTime, updated = None)

  def anonymize(recordSetChange: RecordSetChange): RecordSetChange =
    recordSetChange.copy(
      id = "a",
      created = fakeTime,
      recordSet = anonymize(recordSetChange.recordSet),
      updates = recordSetChange.updates.map(anonymize),
      zone = anonymizeTimeOnly(recordSetChange.zone)
    )

  def anonymize(changeSet: ChangeSet): ChangeSet =
    changeSet.copy(
      id = "a",
      createdTimestamp = fakeTime.getMillis,
      processingTimestamp = 0,
      changes = changeSet.changes
        .map(anonymize)
        .sortBy(rs => (rs.recordSet.name, rs.recordSet.typ))
    )

  def anonymizeTimeOnly(zone: Zone): Zone = {
    val newUpdate = zone.updated.map(_ => fakeTime)
    val newLatestSync = zone.latestSync.map(_ => fakeTime)
    zone.copy(created = fakeTime, updated = newUpdate, latestSync = newLatestSync)
  }
}
