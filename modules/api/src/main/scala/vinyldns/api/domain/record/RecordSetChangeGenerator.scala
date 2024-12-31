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

package vinyldns.api.domain.record

import java.util.UUID
import java.time.temporal.ChronoUnit
import java.time.Instant
import vinyldns.api.backend.dns.DnsConversions
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.zone.Zone
import vinyldns.core.domain.record._

object RecordSetChangeGenerator extends DnsConversions {

  def forAdd(
      recordSet: RecordSet,
      zone: Zone,
      userId: String,
      singleBatchChangeIds: List[String]
  ): RecordSetChange =
    RecordSetChange(
      zone = zone,
      recordSet = recordSet.copy(
        name = relativize(recordSet.name, zone.name),
        id = UUID.randomUUID().toString, // TODO once user cant specify ID, no need to refresh it here
        created = Instant.now.truncatedTo(ChronoUnit.MILLIS),
        status = RecordSetStatus.Pending
      ),
      userId = userId,
      changeType = RecordSetChangeType.Create,
      status = RecordSetChangeStatus.Pending,
      singleBatchChangeIds = singleBatchChangeIds
    )

  def forAdd(
      recordSet: RecordSet,
      zone: Zone,
      auth: Option[AuthPrincipal] = None
  ): RecordSetChange =
    forAdd(recordSet, zone, auth.map(_.userId).getOrElse("system"), List())

  def forAdd(recordSet: RecordSet, zone: Zone, auth: AuthPrincipal): RecordSetChange =
    forAdd(recordSet, zone, auth.userId, List())

  def forUpdate(
      replacing: RecordSet,
      newRecordSet: RecordSet,
      zone: Zone,
      userId: String,
      singleBatchChangeIds: List[String]
  ): RecordSetChange =
    RecordSetChange(
      zone = zone,
      recordSet = newRecordSet.copy(
        id = replacing.id,
        name = relativize(newRecordSet.name, zone.name),
        status = RecordSetStatus.PendingUpdate,
        updated = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS))
      ),
      userId = userId,
      changeType = RecordSetChangeType.Update,
      status = RecordSetChangeStatus.Pending,
      updates = Some(replacing),
      singleBatchChangeIds = singleBatchChangeIds
    )

  def forUpdate(
      replacing: RecordSet,
      newRecordSet: RecordSet,
      zone: Zone,
      auth: Option[AuthPrincipal] = None
  ): RecordSetChange =
    forUpdate(replacing, newRecordSet, zone, auth.map(_.userId).getOrElse("system"), List())

  def forUpdate(
      replacing: RecordSet,
      newRecordSet: RecordSet,
      zone: Zone,
      auth: AuthPrincipal
  ): RecordSetChange =
    forUpdate(replacing, newRecordSet, zone, auth.userId, List())

  def forDelete(
      recordSet: RecordSet,
      zone: Zone,
      userId: String,
      singleBatchChangeIds: List[String]
  ): RecordSetChange =
    RecordSetChange(
      zone = zone,
      recordSet = recordSet.copy(
        name = relativize(recordSet.name, zone.name),
        status = RecordSetStatus.PendingDelete,
        updated = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS))
      ),
      userId = userId,
      changeType = RecordSetChangeType.Delete,
      updates = Some(recordSet),
      singleBatchChangeIds = singleBatchChangeIds
    )

  def forOutOfSync(
   recordSet: RecordSet,
   zone: Zone,
   userId: String,
   singleBatchChangeIds: List[String]
 ): RecordSetChange =
    RecordSetChange(
      zone = zone,
      recordSet = recordSet.copy(
        name = relativize(recordSet.name, zone.name),
        status = RecordSetStatus.PendingDelete,
        updated = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS))
      ),
      userId = userId,
      changeType = RecordSetChangeType.Sync,
      updates = Some(recordSet),
      singleBatchChangeIds = singleBatchChangeIds
    )

  def forDelete(
      recordSet: RecordSet,
      zone: Zone,
      auth: Option[AuthPrincipal] = None
  ): RecordSetChange =
    forDelete(recordSet, zone, auth.map(_.userId).getOrElse("system"), List())

  def forDelete(recordSet: RecordSet, zone: Zone, auth: AuthPrincipal): RecordSetChange =
    forDelete(recordSet, zone, auth.userId, List())

  private def forSyncAdd(
      recordSet: RecordSet,
      zone: Zone,
      systemMessage: Option[String]
  ): RecordSetChange =
    RecordSetChange(
      zone = zone,
      recordSet = recordSet
        .copy(name = relativize(recordSet.name, zone.name), status = RecordSetStatus.Active),
      userId = "system",
      changeType = RecordSetChangeType.Create,
      status = RecordSetChangeStatus.Complete,
      systemMessage = systemMessage
    )

  private def forSyncUpdate(
      replacing: RecordSet,
      newRecordSet: RecordSet,
      zone: Zone,
      systemMessage: Option[String]
  ): RecordSetChange =
    RecordSetChange(
      zone = zone,
      recordSet = newRecordSet.copy(
        id = replacing.id,
        name = relativize(newRecordSet.name, zone.name),
        status = RecordSetStatus.Active,
        updated = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)),
        ownerGroupId = replacing.ownerGroupId
      ),
      userId = "system",
      changeType = RecordSetChangeType.Update,
      status = RecordSetChangeStatus.Complete,
      updates = Some(replacing),
      systemMessage = systemMessage
    )

  private def forSyncDelete(
      recordSet: RecordSet,
      zone: Zone,
      systemMessage: Option[String]
  ): RecordSetChange =
    RecordSetChange(
      zone = zone,
      recordSet = recordSet.copy(name = relativize(recordSet.name, zone.name)),
      userId = "system",
      changeType = RecordSetChangeType.Delete,
      status = RecordSetChangeStatus.Complete,
      updates = Some(recordSet),
      systemMessage = systemMessage
    )

  def forZoneSyncAdd(recordSet: RecordSet, zone: Zone): RecordSetChange =
    forSyncAdd(recordSet, zone, Some("Change applied via zone sync"))

  def forZoneSyncUpdate(
      replacing: RecordSet,
      newRecordSet: RecordSet,
      zone: Zone
  ): RecordSetChange =
    forSyncUpdate(replacing, newRecordSet, zone, Some("Change applied via zone sync"))

  def forZoneSyncDelete(recordSet: RecordSet, zone: Zone): RecordSetChange =
    forSyncDelete(recordSet, zone, Some("Change applied via zone sync"))

  def forRecordSyncAdd(recordSet: RecordSet, zone: Zone): RecordSetChange =
    forSyncAdd(recordSet, zone, Some("Change applied via record set sync"))

  def forRecordSyncUpdate(
      replacing: RecordSet,
      newRecordSet: RecordSet,
      zone: Zone
  ): RecordSetChange =
    forSyncUpdate(replacing, newRecordSet, zone, Some("Change applied via record set sync"))

  def forRecordSyncDelete(recordSet: RecordSet, zone: Zone): RecordSetChange =
    forSyncDelete(recordSet, zone, Some("Change applied via record set sync"))
}
