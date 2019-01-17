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

import java.util.UUID

import org.joda.time.DateTime
import vinyldns.core.domain.record._
import TestZoneData._
import TestMembershipData._
import vinyldns.core.domain.zone.Zone

object TestRecordSetData {

  /* RECORDSETS */
  val rsOk: RecordSet = RecordSet(
    okZone.id,
    "ok",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AData("10.1.1.1")))

  val abcRecord: RecordSet = RecordSet(
    abcZone.id,
    "aaaa",
    RecordType.AAAA,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(AAAAData("1:2:3:4:5:6:7:8")))

  val aaaa: RecordSet = RecordSet(
    okZone.id,
    "aaaa",
    RecordType.AAAA,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(AAAAData("1:2:3:4:5:6:7:8")))

  val aaaaOrigin: RecordSet = RecordSet(
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

  val ptrIp4: RecordSet = RecordSet(
    zoneIp4.id,
    "30",
    RecordType.PTR,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(PTRData("ptr")))

  val ptrIp6: RecordSet = RecordSet(
    zoneIp6.id,
    "4.0.3.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0",
    RecordType.PTR,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(PTRData("ptr")))

  val srv: RecordSet = RecordSet(
    okZone.id,
    "srv",
    RecordType.SRV,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(SRVData(1, 2, 3, "target")))

  val mx: RecordSet = RecordSet(
    okZone.id,
    "mx",
    RecordType.MX,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(MXData(3, "mx")))

  val ns: RecordSet = RecordSet(
    okZone.id,
    okZone.name,
    RecordType.NS,
    300,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    records = List(NSData("ns1.test.com"), NSData("ns2.test.com")))

  val txt: RecordSet = RecordSet(
    okZone.id,
    "txt",
    RecordType.TXT,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(TXTData("txt")))

  val sharedZoneRecord: RecordSet = RecordSet(
    sharedZone.id,
    "aaaa",
    RecordType.AAAA,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(AAAAData("1:2:3:4:5:6:7:8")),
    ownerGroupId = Some(okGroup.id))

  val sharedZoneRecordNoOwnerGroup: RecordSet =
    sharedZoneRecord.copy(name = "records", ownerGroupId = None)

  val notSharedZoneRecordWithOwnerGroup: RecordSet =
    rsOk.copy(zoneNotAuthorized.id, "okWithOwnerGroupID", ownerGroupId = Some(okGroup.id))

  /* RECORDSET CHANGES */

  def makeTestAddChange(
      recordSet: RecordSet,
      zone: Zone = okZone,
      userId: String = okUser.id): RecordSetChange =
    RecordSetChange(
      zone,
      recordSet.copy(
        id = UUID.randomUUID().toString,
        created = DateTime.now,
        status = RecordSetStatus.Pending
      ),
      userId,
      RecordSetChangeType.Create,
      RecordSetChangeStatus.Pending
    )

  def makeTestUpdateChange(
      oldRecordSet: RecordSet,
      newRecordSet: RecordSet,
      zone: Zone = okZone,
      userId: String = okUser.id): RecordSetChange =
    RecordSetChange(
      zone,
      newRecordSet.copy(
        id = oldRecordSet.id,
        status = RecordSetStatus.PendingUpdate,
        updated = Some(DateTime.now)),
      userId,
      RecordSetChangeType.Update,
      RecordSetChangeStatus.Pending,
      updates = Some(oldRecordSet)
    )

  def makeTestDeleteChange(
      recordSet: RecordSet,
      zone: Zone = okZone,
      userId: String = okUser.id): RecordSetChange =
    RecordSetChange(
      zone,
      recordSet.copy(
        status = RecordSetStatus.PendingDelete,
        updated = Some(DateTime.now)
      ),
      userId,
      RecordSetChangeType.Delete,
      RecordSetChangeStatus.Pending,
      updates = Some(recordSet)
    )

  val pendingCreateAAAA: RecordSetChange = makeTestAddChange(aaaa, zoneActive)
  val completeCreateAAAA: RecordSetChange =
    pendingCreateAAAA.copy(status = RecordSetChangeStatus.Complete)

  val pendingCreateCNAME: RecordSetChange = makeTestAddChange(cname, zoneActive)
  val completeCreateCNAME: RecordSetChange =
    pendingCreateCNAME.copy(status = RecordSetChangeStatus.Complete)

  val pendingUpdateAAAA: RecordSetChange =
    makeTestUpdateChange(aaaa, aaaa.copy(ttl = aaaa.ttl + 100), zoneActive)

  val pendingCreateNS: RecordSetChange = makeTestAddChange(ns, zoneActive)
  val completeCreateNS: RecordSetChange =
    pendingCreateNS.copy(status = RecordSetChangeStatus.Complete)

  /* CHANGESETS */
  val pendingChangeSet: ChangeSet = ChangeSet(Seq(pendingCreateAAAA, pendingCreateCNAME))

  val pendingUpdateChangeSet: ChangeSet = ChangeSet(Seq(pendingUpdateAAAA))
}
