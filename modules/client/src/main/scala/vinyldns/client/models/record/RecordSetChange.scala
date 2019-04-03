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

package vinyldns.client.models.record

import upickle.default._
import vinyldns.client.models.OptionRW
import vinyldns.client.models.zone.Zone
import vinyldns.core.domain.record.RecordSetChangeStatus
import vinyldns.core.domain.record.RecordSetChangeType

case class RecordSetChange(
    zone: Zone,
    recordSet: RecordSet,
    userId: String,
    created: String,
    id: String,
    userName: String,
    changeType: RecordSetChangeType.RecordSetChangeType,
    status: RecordSetChangeStatus.RecordSetChangeStatus,
    systemMessage: Option[String] = None,
    update: Option[RecordSet] = None) {
  def recordSetName(): String =
    update match {
      case Some(u) => u.name
      case None => recordSet.name
    }

  def recordSetType(): String =
    update match {
      case Some(u) => u.`type`.toString
      case None => recordSet.`type`.toString
    }
}

object RecordSetChange extends OptionRW {
  implicit val recordSetChangeTypeRW: ReadWriter[RecordSetChangeType.RecordSetChangeType] =
    readwriter[ujson.Value]
      .bimap[RecordSetChangeType.RecordSetChangeType](
        fromChange => ujson.Value.JsonableString(fromChange.toString),
        toChange => {
          val raw = toChange.toString().replaceAll("^\"|\"$", "")
          RecordSetChangeType.withName(raw)
        }
      )

  implicit val recordSetChangeStatusRW: ReadWriter[RecordSetChangeStatus.RecordSetChangeStatus] =
    readwriter[ujson.Value].bimap[RecordSetChangeStatus.RecordSetChangeStatus](
      fromStatus => ujson.Value.JsonableString(fromStatus.toString),
      toStatus => {
        val raw = toStatus.toString().replaceAll("^\"|\"$", "")
        RecordSetChangeStatus.withName(raw)
      }
    )
  implicit val rw: ReadWriter[RecordSetChange] = macroRW
}
