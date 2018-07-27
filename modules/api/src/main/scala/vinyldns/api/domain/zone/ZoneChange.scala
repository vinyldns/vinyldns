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

import java.util.UUID

import org.joda.time.DateTime
import vinyldns.api.domain.auth.AuthPrincipal
import vinyldns.api.domain.record.RecordSetChange
import vinyldns.api.domain.zone

object ZoneChangeStatus extends Enumeration {
  type ZoneChangeStatus = Value
  val Pending, Complete, Failed, Synced = Value
}

object ZoneChangeType extends Enumeration {
  type ZoneChangeType = Value
  val Create, Update, Delete, Sync = Value
}

import vinyldns.api.domain.zone.ZoneChangeStatus._
import vinyldns.api.domain.zone.ZoneChangeType._

trait ZoneCommandResult
case class ZoneChange(
    zone: Zone,
    userId: String,
    changeType: ZoneChangeType,
    status: ZoneChangeStatus = ZoneChangeStatus.Pending,
    created: DateTime = DateTime.now,
    systemMessage: Option[String] = None,
    id: String = UUID.randomUUID().toString)
    extends ZoneCommand
    with ZoneCommandResult {

  val zoneId: String = zone.id

  override def toString: String = {
    val sb = new StringBuilder
    sb.append("ZoneChange: [")
    sb.append("id=\"").append(id).append("\"; ")
    sb.append("userId=\"").append(userId).append("\"; ")
    sb.append("changeType=\"").append(changeType.toString).append("\"; ")
    sb.append("status=\"").append(status.toString).append("\"; ")
    sb.append("systemMessage=\"").append(systemMessage.toString).append("\"; ")
    sb.append("created=\"").append(created.getMillis.toString).append("\"; ")
    sb.append(zone.toString)
    sb.append(" ]")
    sb.toString
  }
}

object ZoneChange {
  import ZoneChangeStatus._

  def forAdd(
      zone: Zone,
      authPrincipal: AuthPrincipal,
      status: ZoneChangeStatus = Pending): ZoneChange =
    ZoneChange(
      zone
        .copy(id = UUID.randomUUID().toString, created = DateTime.now, status = ZoneStatus.Syncing),
      authPrincipal.userId,
      ZoneChangeType.Create,
      status
    )

  def forUpdate(newZone: Zone, oldZone: Zone, authPrincipal: AuthPrincipal): ZoneChange =
    ZoneChange(
      newZone.copy(updated = Some(DateTime.now), connection = fixConn(oldZone, newZone)),
      authPrincipal.userId,
      ZoneChangeType.Update,
      ZoneChangeStatus.Pending
    )

  def forSync(zone: Zone, authPrincipal: AuthPrincipal): ZoneChange =
    ZoneChange(
      zone.copy(updated = Some(DateTime.now), status = ZoneStatus.Syncing),
      authPrincipal.userId,
      ZoneChangeType.Sync,
      ZoneChangeStatus.Pending
    )

  def forDelete(zone: Zone, authPrincipal: AuthPrincipal): ZoneChange =
    ZoneChange(
      zone.copy(updated = Some(DateTime.now), status = ZoneStatus.Deleted),
      authPrincipal.userId,
      ZoneChangeType.Delete,
      ZoneChangeStatus.Pending
    )

  private def fixConn(oldZ: Zone, newZ: Zone): Option[ZoneConnection] =
    newZ.connection.map(newConn => {
      val oldConn = oldZ.connection.getOrElse(newConn)
      newConn.copy(key = if (oldConn.key == newConn.decrypted().key) oldConn.key else newConn.key)
    })
}

case class ZoneHistory(
    zoneId: String,
    zoneChanges: List[ZoneChange] = Nil,
    recordSetChanges: List[RecordSetChange] = Nil)

case class ListZoneChangesResponse(
    zoneId: String,
    zoneChanges: List[ZoneChange] = Nil,
    nextId: Option[String],
    startFrom: Option[String],
    maxItems: Int)

object ListZoneChangesResponse {
  def apply(zoneId: String, listResults: ListZoneChangesResults): ListZoneChangesResponse =
    zone.ListZoneChangesResponse(
      zoneId,
      listResults.items,
      listResults.nextId,
      listResults.startFrom,
      listResults.maxItems)
}
