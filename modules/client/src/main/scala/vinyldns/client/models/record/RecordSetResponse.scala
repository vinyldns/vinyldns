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

import japgolly.scalajs.react.vdom.html_<^.VdomElement
import upickle.default._
import vinyldns.client.models.OptionRW
import vinyldns.core.domain.record.RecordType

trait RecordSetTypeRW {
  implicit val recordTypeRW: ReadWriter[RecordType.RecordType] =
    readwriter[ujson.Value]
      .bimap[RecordType.RecordType](
        fromType => ujson.Value.JsonableString(fromType.toString),
        toType => {
          val raw = toType.toString().replaceAll("^\"|\"$", "")
          RecordType.withName(raw)
        }
      )
}

case class RecordSetResponse(
    id: String,
    `type`: RecordType.RecordType,
    zoneId: String,
    name: String,
    ttl: Int,
    status: String,
    records: List[RecordData],
    account: String,
    created: String,
    accessLevel: Option[String] = None,
    ownerGroupId: Option[String] = None,
    ownerGroupName: Option[String] = None)
    extends RecordSetModalInfo {

  def canUpdate(zoneName: String): Boolean =
    (this.accessLevel == Some("Update") || this.accessLevel == Some("Delete")) &&
      this.`type` != RecordType.SOA &&
      !(this.`type` == RecordType.NS && this.name == zoneName)

  def canDelete(zoneName: String): Boolean =
    this.accessLevel == Some("Delete") &&
      this.`type` != RecordType.SOA &&
      !(this.`type` == RecordType.NS && this.name == zoneName)

  def recordDataDisplay: VdomElement = RecordData.toDisplay(this.records, this.`type`)
}

object RecordSetResponse extends OptionRW with RecordSetTypeRW {
  implicit val rw: ReadWriter[RecordSetResponse] = macroRW
}
