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

import org.joda.time.DateTime
import vinyldns.api.domain.auth.AuthPrincipal
import vinyldns.api.domain.dns.DnsConversions
import vinyldns.api.domain.zone.{RecordSetChangeInfo, Zone, ZoneCommand, ZoneCommandResult}

object RecordSetChangeStatus extends Enumeration {
  type RecordSetChangeStatus = Value
  val Pending, Submitted, Validated, Applied, Verified, Complete, Failed = Value

  def isDone(status: RecordSetChangeStatus): Boolean = (status == Complete || status == Failed)
}

object RecordSetChangeType extends Enumeration {
  type RecordSetChangeType = Value
  val Create, Update, Delete = Value
}
import RecordSetChangeStatus._
import RecordSetChangeType._

case class RecordSetChange(
    zone: Zone,
    recordSet: RecordSet,
    userId: String,
    changeType: RecordSetChangeType,
    status: RecordSetChangeStatus = RecordSetChangeStatus.Pending,
    created: DateTime = DateTime.now,
    systemMessage: Option[String] = None,
    updates: Option[RecordSet] = None,
    id: String = UUID.randomUUID().toString,
    singleBatchChangeIds: List[String] = List())
    extends ZoneCommand
    with ZoneCommandResult {

  val zoneId: String = zone.id

  def successful: RecordSetChange =
    copy(
      status = RecordSetChangeStatus.Complete,
      systemMessage = None,
      recordSet = recordSet
        .copy(status = RecordSetStatus.Active, updated = Some(DateTime.now)))

  def failed(message: String): RecordSetChange = failed(Some(message))

  def failed(message: Option[String] = None): RecordSetChange =
    copy(
      status = RecordSetChangeStatus.Failed,
      systemMessage = message,
      recordSet = recordSet
        .copy(status = RecordSetStatus.Inactive, updated = Some(DateTime.now)))

  def submitted: RecordSetChange = copy(status = RecordSetChangeStatus.Submitted)

  def isDone: Boolean = RecordSetChangeStatus.isDone(status)

  def isComplete: Boolean = status == RecordSetChangeStatus.Complete

  def withUserId(newUserId: String): RecordSetChange = this.copy(userId = newUserId)

  override def toString: String = {
    val sb = new StringBuilder
    sb.append("RecordSetChange: [")
    sb.append("id=\"").append(id).append("\"; ")
    sb.append("userId=\"").append(userId).append("\"; ")
    sb.append("changeType=\"").append(changeType.toString).append("\"; ")
    sb.append("status=\"").append(status.toString).append("\"; ")
    sb.append("systemMessage=\"").append(systemMessage.toString).append("\"; ")
    sb.append("zoneId=\"").append(zone.id).append("\"; ")
    sb.append("zoneName=\"").append(zone.name).append("\"; ")
    sb.append(recordSet.toString)
    sb.append(" ]")
    sb.append("singleBatchChangeIds=\"").append(singleBatchChangeIds).append("\"; ")
    sb.toString
  }
}

object RecordSetChange extends DnsConversions {

  def forAdd(
      recordSet: RecordSet,
      zone: Zone,
      userId: String,
      singleBatchChangeIds: List[String]): RecordSetChange =
    RecordSetChange(
      zone = zone,
      recordSet = recordSet.copy(
        name = relativize(recordSet.name, zone.name),
        id = UUID.randomUUID().toString, // TODO once user cant specify ID, no need to refresh it here
        created = DateTime.now,
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
      auth: Option[AuthPrincipal] = None): RecordSetChange =
    forAdd(recordSet, zone, auth.map(_.userId).getOrElse("system"), List())

  def forAdd(recordSet: RecordSet, zone: Zone, auth: AuthPrincipal): RecordSetChange =
    forAdd(recordSet, zone, auth.userId, List())

  def forUpdate(
      replacing: RecordSet,
      newRecordSet: RecordSet,
      zone: Zone,
      userId: String,
      singleBatchChangeIds: List[String]): RecordSetChange =
    RecordSetChange(
      zone = zone,
      recordSet = newRecordSet.copy(
        id = replacing.id,
        name = relativize(newRecordSet.name, zone.name),
        status = RecordSetStatus.PendingUpdate,
        updated = Some(DateTime.now)),
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
      auth: Option[AuthPrincipal] = None): RecordSetChange =
    forUpdate(replacing, newRecordSet, zone, auth.map(_.userId).getOrElse("system"), List())

  def forUpdate(
      replacing: RecordSet,
      newRecordSet: RecordSet,
      zone: Zone,
      auth: AuthPrincipal): RecordSetChange =
    forUpdate(replacing, newRecordSet, zone, auth.userId, List())

  def forDelete(
      recordSet: RecordSet,
      zone: Zone,
      userId: String,
      singleBatchChangeIds: List[String]): RecordSetChange =
    RecordSetChange(
      zone = zone,
      recordSet = recordSet.copy(
        name = relativize(recordSet.name, zone.name),
        status = RecordSetStatus.PendingDelete,
        updated = Some(DateTime.now)
      ),
      userId = userId,
      changeType = RecordSetChangeType.Delete,
      updates = Some(recordSet),
      singleBatchChangeIds = singleBatchChangeIds
    )

  def forDelete(
      recordSet: RecordSet,
      zone: Zone,
      auth: Option[AuthPrincipal] = None): RecordSetChange =
    forDelete(recordSet, zone, auth.map(_.userId).getOrElse("system"), List())

  def forDelete(recordSet: RecordSet, zone: Zone, auth: AuthPrincipal): RecordSetChange =
    forDelete(recordSet, zone, auth.userId, List())

  def forSyncAdd(recordSet: RecordSet, zone: Zone): RecordSetChange =
    RecordSetChange(
      zone = zone,
      recordSet = recordSet
        .copy(name = relativize(recordSet.name, zone.name), status = RecordSetStatus.Active),
      userId = "system",
      changeType = RecordSetChangeType.Create,
      status = RecordSetChangeStatus.Complete,
      systemMessage = Some("Change applied via zone sync")
    )

  def forSyncUpdate(replacing: RecordSet, newRecordSet: RecordSet, zone: Zone): RecordSetChange =
    RecordSetChange(
      zone = zone,
      recordSet = newRecordSet.copy(
        id = replacing.id,
        name = relativize(newRecordSet.name, zone.name),
        status = RecordSetStatus.Active,
        updated = Some(DateTime.now)),
      userId = "system",
      changeType = RecordSetChangeType.Update,
      status = RecordSetChangeStatus.Complete,
      updates = Some(replacing),
      systemMessage = Some("Change applied via zone sync")
    )

  def forSyncDelete(recordSet: RecordSet, zone: Zone): RecordSetChange =
    RecordSetChange(
      zone = zone,
      recordSet = recordSet.copy(name = relativize(recordSet.name, zone.name)),
      userId = "system",
      changeType = RecordSetChangeType.Delete,
      status = RecordSetChangeStatus.Complete,
      updates = Some(recordSet),
      systemMessage = Some("Change applied via zone sync")
    )
}

case class ListRecordSetChangesResponse(
    zoneId: String,
    recordSetChanges: List[RecordSetChangeInfo] = Nil,
    nextId: Option[String],
    startFrom: Option[String],
    maxItems: Int)

object ListRecordSetChangesResponse {
  def apply(
      zoneId: String,
      listResults: ListRecordSetChangesResults,
      info: List[RecordSetChangeInfo]): ListRecordSetChangesResponse =
    ListRecordSetChangesResponse(
      zoneId,
      info,
      listResults.nextId,
      listResults.startFrom,
      listResults.maxItems)
}
