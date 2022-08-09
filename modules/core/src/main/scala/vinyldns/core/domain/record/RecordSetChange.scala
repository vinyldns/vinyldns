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

package vinyldns.core.domain.record

import java.util.UUID

import java.time.Instant
import vinyldns.core.domain.zone.{Zone, ZoneCommand, ZoneCommandResult}
import java.time.temporal.ChronoUnit

object RecordSetChangeStatus extends Enumeration {
  type RecordSetChangeStatus = Value
  val Pending, Complete, Failed, Synced = Value

  def isDone(status: RecordSetChangeStatus): Boolean = status == Complete || status == Failed
}

object RecordSetChangeType extends Enumeration {
  type RecordSetChangeType = Value
  val Create, Update, Delete, Sync = Value
}
import RecordSetChangeStatus._
import RecordSetChangeType._

case class RecordSetChange(
    zone: Zone,
    recordSet: RecordSet,
    userId: String,
    changeType: RecordSetChangeType,
    status: RecordSetChangeStatus = RecordSetChangeStatus.Pending,
    created: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    systemMessage: Option[String] = None,
    updates: Option[RecordSet] = None,
    id: String = UUID.randomUUID().toString,
    singleBatchChangeIds: List[String] = List()
) extends ZoneCommand
    with ZoneCommandResult {

  val zoneId: String = zone.id

  def successful: RecordSetChange =
    copy(
      status = RecordSetChangeStatus.Complete,
      systemMessage = None,
      recordSet = recordSet
        .copy(status = RecordSetStatus.Active, updated = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)))
    )

  def failed(message: String): RecordSetChange = failed(Some(message))

  def failed(message: Option[String] = None): RecordSetChange =
    copy(
      status = RecordSetChangeStatus.Failed,
      systemMessage = message,
      recordSet = recordSet
        .copy(status = RecordSetStatus.Inactive, updated = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)))
    )

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
