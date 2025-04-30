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

import java.time.Instant
import vinyldns.core.domain.record.RecordSetChangeStatus.RecordSetChangeStatus
import vinyldns.core.domain.record.RecordSetChangeType.RecordSetChangeType
import vinyldns.core.domain.record.RecordSetStatus.RecordSetStatus
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.{OwnerShipTransfer, RecordData, RecordSet, RecordSetChange}
import vinyldns.core.domain.zone.{ACLRuleInfo, AccessLevel, GenerateZone, GenerateZoneStatus, Zone, ZoneACL, ZoneChange, ZoneConnection, ZoneGenerationResponse}
import vinyldns.core.domain.zone.AccessLevel.AccessLevel
import vinyldns.core.domain.zone.GenerateZoneStatus.GenerateZoneStatus
import vinyldns.core.domain.zone.ZoneStatus.ZoneStatus

import java.time.temporal.ChronoUnit
import java.util.UUID

case class ZoneACLInfo(rules: Set[ACLRuleInfo])

case class ZoneInfo(
                     name: String,
                     email: String,
                     status: ZoneStatus,
                     created: Instant,
                     updated: Option[Instant],
                     id: String,
                     connection: Option[ZoneConnection],
                     transferConnection: Option[ZoneConnection],
                     account: String,
                     shared: Boolean,
                     acl: ZoneACLInfo,
                     adminGroupId: String,
                     adminGroupName: String,
                     latestSync: Option[Instant],
                     backendId: Option[String],
                     recurrenceSchedule: Option[String],
                     scheduleRequestor: Option[String],
                     accessLevel: AccessLevel
                   )

object ZoneInfo {
  def apply(
             zone: Zone,
             aclInfo: ZoneACLInfo,
             groupName: String,
             accessLevel: AccessLevel
           ): ZoneInfo =
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
      recurrenceSchedule = zone.recurrenceSchedule,
      scheduleRequestor = zone.scheduleRequestor,
      accessLevel = accessLevel
    )
}

case class ZoneDetails(
                     name: String,
                     email: String,
                     status: ZoneStatus,
                     adminGroupId: String,
                     adminGroupName: String,
                   )

object ZoneDetails {
  def apply(
             zone: Zone,
             groupName: String,
           ): ZoneDetails =
    ZoneDetails(
      name = zone.name,
      email = zone.email,
      status = zone.status,
      adminGroupId = zone.adminGroupId,
      adminGroupName = groupName,
    )
}

case class ZoneSummaryInfo(
                            name: String,
                            email: String,
                            status: ZoneStatus,
                            created: Instant,
                            updated: Option[Instant],
                            id: String,
                            connection: Option[ZoneConnection],
                            transferConnection: Option[ZoneConnection],
                            account: String,
                            shared: Boolean,
                            acl: ZoneACL,
                            adminGroupId: String,
                            adminGroupName: String,
                            latestSync: Option[Instant],
                            backendId: Option[String],
                            recurrenceSchedule: Option[String],
                            scheduleRequestor: Option[String],
                            accessLevel: AccessLevel
                          )

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
      recurrenceSchedule = zone.recurrenceSchedule,
      scheduleRequestor = zone.scheduleRequestor,
      accessLevel = accessLevel
    )
}

case class GenerateZoneSummaryInfo(
                                    groupId: String,
                                    email: String,
                                    provider: String, // "powerdns", "cloudflare", "google", "bind"
                                    zoneName: String,
                                    status:  GenerateZoneStatus = GenerateZoneStatus.Active,
                                    serverId: Option[String] = None, // The ID of the sever (PowerDNS)
                                    kind: Option[String] = None, // Zone type (PowerDNS/Cloudflare/Bind)
                                    masters: Option[List[String]] = None, // Master servers (for slave zones, PowerDNS)
                                    nameservers: Option[List[String]] = None, // NS records
                                    description: Option[String] = None, // description (Google)
                                    visibility: Option[String] = None, // Public or Private (Google)
                                    accountId: Option[String] = None, // Account ID (Cloudflare)
                                    projectId: Option[String] = None, // GCP Project ID (Google)
                                    admin_email: Option[String] = None, // NS IpAddress (Bind)
                                    ttl: Option[Int] = None, // TTL (Bind)
                                    refresh: Option[Int] = None, // Refresh (Bind)
                                    retry: Option[Int] = None, // Retry (Bind)
                                    expire: Option[Int] = None, // Expire (Bind)
                                    negative_cache_ttl: Option[Int] = None, // Negative Cache TTL (Bind)
                                    response: Option[ZoneGenerationResponse] = None,
                                    id: String = UUID.randomUUID().toString,
                                    created: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS),
                                    updated: Option[Instant] = None,
                                    groupName: String,
                                    accessLevel: AccessLevel
                          )

object GenerateZoneSummaryInfo {
  def apply(zone: GenerateZone, groupName: String, accessLevel: AccessLevel): GenerateZoneSummaryInfo =
    GenerateZoneSummaryInfo(
      zone.groupId,
      zone.email,
      zone.provider,
      zone.zoneName,
      zone.status,
      zone.serverId,
      zone.kind,
      zone.masters,
      zone.nameservers,
      zone.description,
      zone.visibility,
      zone.accountId,
      zone.projectId,
      zone.admin_email,
      zone.ttl,
      zone.refresh,
      zone.retry,
      zone.expire,
      zone.negative_cache_ttl,
      zone.response,
      zone.id,
      zone.created,
      zone.updated,
      groupName,
      accessLevel
    )
}

case class ZoneChangeDeletedInfo(
                         zoneChange: ZoneChange,
                         adminGroupName: String,
                         userName: String,
                         accessLevel: AccessLevel
                          )

object ZoneChangeDeletedInfo {
  def apply(zoneChange: List[ZoneChange],
            groupName: String,
            userName: String,
            accessLevel: AccessLevel)
  : ZoneChangeDeletedInfo =
    ZoneChangeDeletedInfo(
      zoneChange= zoneChange,
      groupName = groupName,
      userName = userName,
      accessLevel = accessLevel
    )
}

case class RecordSetListInfo(
                              zoneId: String,
                              name: String,
                              typ: RecordType,
                              ttl: Long,
                              status: RecordSetStatus,
                              created: Instant,
                              updated: Option[Instant],
                              records: List[RecordData],
                              id: String,
                              account: String,
                              accessLevel: AccessLevel,
                              ownerGroupId: Option[String],
                              ownerGroupName: Option[String],
                              recordSetGroupChange: Option[OwnerShipTransfer],
                              fqdn: Option[String]
                            )

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
      ownerGroupName = recordSet.ownerGroupName,
      recordSetGroupChange = recordSet.recordSetGroupChange,
      fqdn = recordSet.fqdn
    )
}

case class RecordSetInfo(
                          zoneId: String,
                          name: String,
                          typ: RecordType,
                          ttl: Long,
                          status: RecordSetStatus,
                          created: Instant,
                          updated: Option[Instant],
                          records: List[RecordData],
                          id: String,
                          account: String,
                          ownerGroupId: Option[String],
                          ownerGroupName: Option[String],
                          recordSetGroupChange: Option[OwnerShipTransfer],
                          fqdn: Option[String]
                        )

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
      ownerGroupName = groupName,
      recordSetGroupChange = recordSet.recordSetGroupChange,
      fqdn = recordSet.fqdn
    )
}

case class RecordSetGlobalInfo(
                                zoneId: String,
                                name: String,
                                typ: RecordType,
                                ttl: Long,
                                status: RecordSetStatus,
                                created: Instant,
                                updated: Option[Instant],
                                records: List[RecordData],
                                id: String,
                                account: String,
                                ownerGroupId: Option[String],
                                ownerGroupName: Option[String],
                                recordSetGroupChange: Option[OwnerShipTransfer],
                                fqdn: Option[String],
                                zoneName: String,
                                zoneShared: Boolean
                              )

object RecordSetGlobalInfo {
  def apply(
             recordSet: RecordSet,
             zoneName: String,
             zoneShared: Boolean,
             groupName: Option[String]
           ): RecordSetGlobalInfo =
    RecordSetGlobalInfo(
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
      ownerGroupName = groupName,
      recordSetGroupChange = recordSet.recordSetGroupChange,
      fqdn = recordSet.fqdn,
      zoneName = zoneName,
      zoneShared = zoneShared
    )
}

case class RecordSetChangeInfo(
                                zone: Zone,
                                recordSet: RecordSet,
                                userId: String,
                                changeType: RecordSetChangeType,
                                status: RecordSetChangeStatus,
                                created: Instant,
                                systemMessage: Option[String],
                                updates: Option[RecordSet],
                                id: String,
                                userName: String
                              )

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
                              ignoreAccess: Boolean = false,
                              includeReverse: Boolean = true
                            )

case class ListGeneratedZonesResponse(
                                       zones: List[GenerateZoneSummaryInfo],
                                       nameFilter: Option[String],
                                       startFrom: Option[String] = None,
                                       nextId: Option[String] = None,
                                       maxItems: Int = 100,
                                       ignoreAccess: Boolean = false
                                     )

case class RecordSetCount( count: Int = 0 )

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

case class RecordSetValidation(msg: String) extends Throwable(msg)

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
