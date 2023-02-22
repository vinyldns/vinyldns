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

package vinyldns.core.domain.zone

import java.util.UUID

import java.time.Instant
import java.time.temporal.ChronoUnit

object ZoneChangeStatus extends Enumeration {
  type ZoneChangeStatus = Value
  val Pending, Failed, Synced = Value
}

object ZoneChangeType extends Enumeration {
  type ZoneChangeType = Value
  val Create, Update, Delete, Sync, AutomatedSync = Value
}

import vinyldns.core.domain.zone.ZoneChangeStatus._
import vinyldns.core.domain.zone.ZoneChangeType._

case class ZoneChange(
    zone: Zone,
    userId: String,
    changeType: ZoneChangeType,
    status: ZoneChangeStatus = ZoneChangeStatus.Pending,
    created: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    systemMessage: Option[String] = None,
    id: String = UUID.randomUUID().toString
) extends ZoneCommand
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
    sb.append("created=\"").append(created.toEpochMilli.toString).append("\"; ")
    sb.append(zone.toString)
    sb.append(" ]")
    sb.toString
  }
}
