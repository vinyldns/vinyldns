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

package vinyldns.client.models.batch

import japgolly.scalajs.react.vdom.html_<^.<
import japgolly.scalajs.react.vdom.VdomElement
import vinyldns.client.models.OptionRW
import vinyldns.core.domain.batch.SingleChangeStatus
import upickle.default._
import vinyldns.client.components.RecordDataDisplay
import vinyldns.client.models.record.{RecordData, RecordSetTypeRW}
import vinyldns.core.domain.record.RecordType

case class SingleChangeResponse(
    changeType: String,
    inputName: String,
    `type`: RecordType.RecordType,
    status: SingleChangeStatus.SingleChangeStatus,
    recordName: String,
    zoneName: String,
    zoneId: String,
    id: String,
    recordChangeId: Option[String] = None,
    recordSetId: Option[String] = None,
    ttl: Option[Int] = None,
    systemMessage: Option[String] = None,
    record: Option[RecordData] = None
) {
  def recordDataDisplay: VdomElement =
    this.record match {
      case Some(r) =>
        RecordDataDisplay(RecordDataDisplay.Props(List(r), this.`type`))
      case None => <.p
    }
}

object SingleChangeResponse extends OptionRW with SingleChangeStatusRW with RecordSetTypeRW {
  implicit val singleChangeResponseRW: ReadWriter[SingleChangeResponse] = macroRW
}

trait SingleChangeStatusRW {
  implicit val singleChangeStatusRW: ReadWriter[SingleChangeStatus.SingleChangeStatus] =
    readwriter[ujson.Value]
      .bimap[SingleChangeStatus.SingleChangeStatus](
        fromType => ujson.Value.JsonableString(fromType.toString),
        toType => {
          val raw = toType.toString().replaceAll("^\"|\"$", "")
          SingleChangeStatus.withName(raw)
        }
      )
}
