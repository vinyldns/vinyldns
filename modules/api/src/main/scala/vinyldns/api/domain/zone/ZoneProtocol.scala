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

import org.joda.time.DateTime
import vinyldns.core.domain.record.RecordSetChangeStatus.RecordSetChangeStatus
import vinyldns.core.domain.record.RecordSetChangeType.RecordSetChangeType
import vinyldns.core.domain.record.RecordSetStatus.RecordSetStatus
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.{RecordData, RecordSet, RecordSetChange}
import vinyldns.core.domain.zone.{ACLRuleInfo, AccessLevel, Zone, ZoneACL, ZoneConnection}
import vinyldns.core.domain.zone.AccessLevel.AccessLevel
import vinyldns.core.domain.zone.ZoneStatus.ZoneStatus

case class ZoneACLInfo(rules: Set[ACLRuleInfo])

case class ZoneInfo(
    name: String,
    email: String,
    status: ZoneStatus,
    created: DateTime,
    updated: Option[DateTime],
    id: String,
    connection: Option[ZoneConnection],
    transferConnection: Option[ZoneConnection],
    account: String,
    shared: Boolean,
    acl: ZoneACLInfo,
    adminGroupId: String,
    adminGroupName: String,
    latestSync: Option[DateTime],
    backendId: Option[String],
    accessLevel: AccessLevel)

object ZoneInfo {
  def apply(
      zone: Zone,
      aclInfo: ZoneACLInfo,
      groupName: String,
      accessLevel: AccessLevel): ZoneInfo =
    ZoneInfo(
      name = zone.name,
      email = zone.email,
      status = zone.status,
      created = zone.created,
      updated = zone.updated,
      id = zone.id,
      connection = zone.connection,
      transferConnection = zone.transferConnection,
      account = zone.account,
      shared = zone.shared,
      acl = aclInfo,
      adminGroupId = zone.adminGroupId,
      adminGroupName = groupName,
      latestSync = zone.latestSync,
      backendId = zone.backendId,
      accessLevel = accessLevel
    )
}

case class ZoneSummaryInfo(
    name: String,
    email: String,
    status: ZoneStatus,
    created: DateTime,
    updated: Option[DateTime],
    id: String,
    connection: Option[ZoneConnection],
    transferConnection: Option[ZoneConnection],
    account: String,
    shared: Boolean,
    acl: ZoneACL,
    adminGroupId: String,
    adminGroupName: String,
    latestSync: Option[DateTime],
    backendId: Option[String],
    accessLevel: AccessLevel)

object ZoneSummaryInfo {
  def apply(zone: Zone, groupName: String, accessLevel: AccessLevel): ZoneSummaryInfo =
    ZoneSummaryInfo(
      name = zone.name,
      email = zone.email,
      status = zone.status,
      created = zone.created,
      updated = zone.updated,
      id = zone.id,
      connection = zone.connection,
      transferConnection = zone.transferConnection,
      account = zone.account,
      shared = zone.shared,
      acl = zone.acl,
      adminGroupId = zone.adminGroupId,
      adminGroupName = groupName,
      latestSync = zone.latestSync,
      zone.backendId,
      accessLevel = accessLevel
    )
}

case class RecordSetListInfo(
    zoneId: String,
    name: String,
    typ: RecordType,
    ttl: Long,
    status: RecordSetStatus,
    created: DateTime,
    updated: Option[DateTime],
    records: List[RecordData],
    id: String,
    account: String,
    accessLevel: AccessLevel,
    ownerGroupId: Option[String],
    ownerGroupName: Option[String])

object RecordSetListInfo {
  def apply(recordSet: RecordSetInfo, accessLevel: AccessLevel): RecordSetListInfo =
    RecordSetListInfo(
      zoneId = recordSet.zoneId,
      name = recordSet.name,
      typ = recordSet.typ,
      ttl = recordSet.ttl,
      status = recordSet.status,
      created = recordSet.created,
      updated = recordSet.updated,
      records = recordSet.records,
      id = recordSet.id,
      account = recordSet.account,
      accessLevel = accessLevel,
      ownerGroupId = recordSet.ownerGroupId,
      ownerGroupName = recordSet.ownerGroupName
    )
}

case class RecordSetInfo(
    zoneId: String,
    name: String,
    typ: RecordType,
    ttl: Long,
    status: RecordSetStatus,
    created: DateTime,
    updated: Option[DateTime],
    records: List[RecordData],
    id: String,
    account: String,
    ownerGroupId: Option[String],
    ownerGroupName: Option[String])

object RecordSetInfo {
  def apply(recordSet: RecordSet, groupName: Option[String]): RecordSetInfo =
    RecordSetInfo(
      zoneId = recordSet.zoneId,
      name = recordSet.name,
      typ = recordSet.typ,
      ttl = recordSet.ttl,
      status = recordSet.status,
      created = recordSet.created,
      updated = recordSet.updated,
      records = recordSet.records,
      id = recordSet.id,
      account = recordSet.account,
      ownerGroupId = recordSet.ownerGroupId,
      ownerGroupName = groupName
    )
}

case class RecordSetChangeInfo(
    zone: Zone,
    recordSet: RecordSet,
    userId: String,
    changeType: RecordSetChangeType,
    status: RecordSetChangeStatus,
    created: DateTime,
    systemMessage: Option[String],
    updates: Option[RecordSet],
    id: String,
    userName: String)

object RecordSetChangeInfo {
  def apply(recordSetChange: RecordSetChange, name: Option[String]): RecordSetChangeInfo =
    RecordSetChangeInfo(
      recordSetChange.zone,
      recordSetChange.recordSet,
      recordSetChange.userId,
      recordSetChange.changeType,
      recordSetChange.status,
      recordSetChange.created,
      recordSetChange.systemMessage,
      recordSetChange.updates,
      recordSetChange.id,
      userName = name.getOrElse("unknown user")
    )
}

case class ListZonesResponse(
    zones: List[ZoneSummaryInfo],
    nameFilter: Option[String],
    startFrom: Option[String] = None,
    nextId: Option[String] = None,
    maxItems: Int = 100,
    ignoreAccess: Boolean = false)

// Errors
case class InvalidRequest(msg: String) extends Throwable(msg)

case class UnrecognizedRequest(msg: String) extends Throwable(msg)

case class RecordSetAlreadyExists(msg: String) extends Throwable(msg)

case class UnexpectedError(msg: String) extends Throwable(msg)

case class ZoneAlreadyExistsError(msg: String) extends Throwable(msg)

case class ZoneNotFoundError(msg: String) extends Throwable(msg)

case class RecordSetNotFoundError(msg: String) extends Throwable(msg)

case class PendingUpdateError(msg: String) extends Throwable(msg)

case class ZoneInactiveError(msg: String) extends Throwable(msg)

case class RecordSetChangeNotFoundError(msg: String) extends Throwable(msg)

case class NotAuthorizedError(msg: String) extends Throwable(msg)

case class ZoneUnavailableError(msg: String) extends Throwable(msg)

case class InvalidGroupError(msg: String) extends Throwable(msg)

case class InvalidSyncStateError(msg: String) extends Throwable(msg)

case class RecentSyncError(msg: String) extends Throwable(msg)

case class ConnectionFailed(zone: Zone, message: String) extends Throwable(message)

case class ZoneValidationFailed(zone: Zone, errors: List[String], message: String)
    extends Throwable(message)

case class ZoneTooLargeError(msg: String) extends Throwable(msg)

object ZoneTooLargeError {
  def apply(zone: Zone, zoneSize: Int, maxZoneSize: Int): ZoneTooLargeError = new ZoneTooLargeError(
    s"""
       |ZoneTooLargeError: Zone '${zone.name}' (id: '${zone.id}') contains $zoneSize records
       |which exceeds the max of $maxZoneSize
    """.stripMargin.replace("\n", " ")
  )
}
