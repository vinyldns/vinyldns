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

import java.util.UUID

import org.joda.time.DateTime
import vinyldns.api.domain.record.{ListRecordSetChangesResponse, RecordSetChangeGenerator}
import vinyldns.api.domain.zone._
import vinyldns.api.repository.TestDataLoader
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
  val notAuth: AuthPrincipal = AuthPrincipal(TestDataLoader.dummyUser, Seq.empty)

  val testConnection: Option[ZoneConnection] = Some(
    ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1"))

  val okZone: Zone = Zone("ok.zone.recordsets.", "test@test.com", adminGroupId = grp.id)
  val zoneDeleted: Zone = okZone.copy(status = ZoneStatus.Deleted)
  val zoneActive: Zone = Zone(
    "ok.zone.recordsets.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection)

  val zoneAuthorized: Zone = zoneActive.copy(adminGroupId = grp.id)
  val zoneNotAuthorized: Zone =
    zoneActive.copy(id = UUID.randomUUID().toString, adminGroupId = UUID.randomUUID().toString)
  val zoneIp4: Zone = okZone.copy(name = "0.162.198.in-addr.arpa.")
  val zoneIp6: Zone = okZone.copy(name = "1.9.e.f.c.c.7.2.9.6.d.f.ip6.arpa.")

  val rsOk: RecordSet = RecordSet(
    okZone.id,
    "ok",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AData("10.1.1.1")))

  val aaaa: RecordSet = RecordSet(
    okZone.id,
    "aaaa",
    RecordType.AAAA,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(AAAAData("1:2:3:4:5:6:7:8")))

  val validAAAAToOrigin: RecordSet = RecordSet(
    okZone.id,
    "@",
    RecordType.AAAA,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(AAAAData("1:2:3:4:5:6:7:8")))

  val cname: RecordSet = RecordSet(
    okZone.id,
    "cname",
    RecordType.CNAME,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(CNAMEData("cname")))

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

  val ptrIp4: RecordSet = RecordSet(
    okZone.id,
    "30",
    RecordType.PTR,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(PTRData("ptr")))

  val ptrIp6: RecordSet = RecordSet(
    okZone.id,
    "4.0.3.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0",
    RecordType.PTR,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(PTRData("ptr")))

  val soa: RecordSet = RecordSet(
    ptrIp4.name,
    "soa.foo.bar.com",
    RecordType.SOA,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(SOAData("something", "other", 1, 2, 3, 5, 6)))

  val srv: RecordSet = RecordSet(
    okZone.id,
    "srv",
    RecordType.SRV,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(SRVData(1, 2, 3, "target")))

  val ns: RecordSet = RecordSet(
    okZone.id,
    okZone.name,
    RecordType.NS,
    300,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    records = List(NSData("ns1.test.com"), NSData("ns2.test.com")))

  val mx: RecordSet = RecordSet(
    okZone.id,
    "mx",
    RecordType.MX,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(MXData(3, "mx")))

  val txt: RecordSet = RecordSet(
    okZone.id,
    "txt",
    RecordType.TXT,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(TXTData("txt")))

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

  val abcGroup: Group = Group("abc", "abc", id = "abc", memberIds = Set("abc"))
  val abcAuth: AuthPrincipal = okAuth.copy(
    signedInUser = okAuth.signedInUser.copy(userName = "abc", id = "abc"),
    memberGroupIds = List(abcGroup.id, grp.id)
  )
  val xyzGroup: Group = Group("xyz", "xyz", id = "xyz", memberIds = Set("xyz"))
  val xyzAuth: AuthPrincipal = okAuth.copy(
    signedInUser = okAuth.signedInUser.copy(userName = "xyz", id = "xyz"),
    memberGroupIds = List(xyzGroup.id, grp.id)
  )
  val abcZone: Zone = Zone("abc.", "abc@xyz.com", adminGroupId = "abc")
  val xyzZone: Zone = Zone("xyz.", "abc@xyz.com", adminGroupId = "xyz")
  val notAuthorizedZone: Zone = Zone("notAuth.", "notAuth@notAuth.com")
  val notAuthorizedAuth: AuthPrincipal =
    okAuth.copy(signedInUser = okAuth.signedInUser.copy(userName = "not auth", id = "not auth"))

  val abcRecord: RecordSet = RecordSet(
    abcZone.id,
    "aaaa",
    RecordType.AAAA,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(AAAAData("1:2:3:4:5:6:7:8")))

  val pendingCreateAAAA: RecordSetChange = RecordSetChangeGenerator.forAdd(aaaa, zoneActive, okAuth)
  val pendingCreateCNAME: RecordSetChange =
    RecordSetChangeGenerator.forAdd(cname, zoneActive, okAuth)
  val pendingChangeSet: ChangeSet = ChangeSet(Seq(pendingCreateAAAA, pendingCreateCNAME))
  val pendingCreateNS: RecordSetChange = RecordSetChangeGenerator.forAdd(ns, zoneActive, okAuth)

  val aaaaUpdated: RecordSet = aaaa.copy(ttl = aaaa.ttl + 100)
  val pendingUpdateAAAA: RecordSetChange =
    RecordSetChangeGenerator.forUpdate(aaaa, aaaaUpdated, zoneActive, okAuth)
  val pendingDeleteAAAA: RecordSetChange =
    RecordSetChangeGenerator.forDelete(aaaa, zoneActive, okAuth)
  val completeCreateAAAA: RecordSetChange =
    pendingCreateAAAA.copy(status = RecordSetChangeStatus.Complete)
  val completeCreateCNAME: RecordSetChange =
    pendingCreateCNAME.copy(status = RecordSetChangeStatus.Complete)
  val completeChangeSet: ChangeSet = ChangeSet(Seq(completeCreateAAAA, completeCreateCNAME))
  val completeCreateNS: RecordSetChange =
    pendingCreateNS.copy(status = RecordSetChangeStatus.Complete)
  val completeRecordSetChanges: List[RecordSetChange] =
    List(pendingCreateAAAA, pendingCreateCNAME, completeCreateAAAA, completeCreateCNAME)

  val pendingUpdateChangeSet: ChangeSet = ChangeSet(Seq(pendingUpdateAAAA))

  val listZoneChangesResponse: ListZoneChangesResponse = ListZoneChangesResponse(
    zoneActive.id,
    List(zoneCreate.copy(id = zoneActive.id)),
    nextId = None,
    startFrom = None,
    maxItems = 100)
  val changesWithUserName: List[RecordSetChangeInfo] = List(
    RecordSetChangeInfo(pendingCreateAAAA, Some("ok")),
    RecordSetChangeInfo(pendingCreateCNAME, Some("ok")),
    RecordSetChangeInfo(completeCreateAAAA, Some("ok")),
    RecordSetChangeInfo(completeCreateCNAME, Some("ok"))
  )
  val listRecordSetChangesResponse: ListRecordSetChangesResponse = ListRecordSetChangesResponse(
    zoneActive.id,
    changesWithUserName,
    nextId = None,
    startFrom = None,
    maxItems = 100)

  val zoneChangeComplete: ZoneChange =
    ZoneChange(okZone, "ok", ZoneChangeType.Update, ZoneChangeStatus.Complete)
  val zoneChangePending: ZoneChange =
    ZoneChange(okZone, "ok", ZoneChangeType.Update, ZoneChangeStatus.Pending)
  val zoneChangeSynced: ZoneChange =
    ZoneChange(okZone, "ok", ZoneChangeType.Update, ZoneChangeStatus.Synced)
  val zoneChangeFailed: ZoneChange =
    ZoneChange(okZone, "ok", ZoneChangeType.Update, ZoneChangeStatus.Failed)

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

  val userAclRule: ACLRule = ACLRule(
    AccessLevel.Read,
    Some("desc"),
    Some("johnny"),
    None,
    Some("www-*"),
    Set(RecordType.A, RecordType.AAAA, RecordType.CNAME))
  val userAclRuleInfo: ACLRuleInfo = ACLRuleInfo(
    AccessLevel.Read,
    Some("desc"),
    Some("johnny"),
    None,
    Some("www-*"),
    Set(RecordType.A, RecordType.AAAA, RecordType.CNAME))
  val groupAclRule: ACLRule = ACLRule(
    AccessLevel.Read,
    Some("desc"),
    None,
    Some("group"),
    Some("www-*"),
    Set(RecordType.A, RecordType.AAAA, RecordType.CNAME))
  val groupAclRuleInfo: ACLRule = ACLRule(
    AccessLevel.Read,
    Some("desc"),
    None,
    Some("group"),
    Some("www-*"),
    Set(RecordType.A, RecordType.AAAA, RecordType.CNAME))
  val zoneAcl: ZoneACL = ZoneACL(Set(userAclRule, groupAclRule))

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

  def anonymize(zoneChange: ZoneChange): ZoneChange =
    zoneChange.copy(id = "a", created = fakeTime, zone = anonymizeTimeOnly(zoneChange.zone))

  def anonymize(changeSet: ChangeSet): ChangeSet =
    changeSet.copy(
      id = "a",
      createdTimestamp = fakeTime.getMillis,
      processingTimestamp = 0,
      changes = changeSet.changes
        .map(anonymize)
        .sortBy(rs => (rs.recordSet.name, rs.recordSet.typ))
    )

  def anonymize(zoneDiff: ZoneDiff): ZoneDiff =
    zoneDiff.copy(
      anonymize(zoneDiff.zoneChange),
      zoneDiff.recordSetChanges
        .map(anonymize)
        .sortBy(rs => (rs.recordSet.name, rs.recordSet.typ)))

  def anonymize(zone: Zone): Zone = {
    val newUpdate = zone.updated.map(_ => fakeTime)
    val newLatestSync = zone.latestSync.map(_ => fakeTime)
    zone.copy(id = "a", created = fakeTime, updated = newUpdate, latestSync = newLatestSync)
  }

  def anonymizeTimeOnly(zone: Zone): Zone = {
    val newUpdate = zone.updated.map(_ => fakeTime)
    val newLatestSync = zone.latestSync.map(_ => fakeTime)
    zone.copy(created = fakeTime, updated = newUpdate, latestSync = newLatestSync)
  }
}
