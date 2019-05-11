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
import vinyldns.client.components.RecordDataDisplay
import vinyldns.client.models.OptionRW
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.zone.AccessLevel

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

trait AccessLevelRW {
  implicit val accessLevelRW: ReadWriter[AccessLevel.AccessLevel] =
    readwriter[ujson.Value]
      .bimap[AccessLevel.AccessLevel](
        fromType => ujson.Value.JsonableString(fromType.toString),
        toType => {
          val raw = toType.toString().replaceAll("^\"|\"$", "")
          AccessLevel.withName(raw)
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
    accessLevel: Option[AccessLevel.AccessLevel] = None,
    ownerGroupId: Option[String] = None,
    ownerGroupName: Option[String] = None)
    extends RecordSetModalInfo {

  def canUpdate(zoneName: String): Boolean =
    if (this.`type` == RecordType.SOA) false
    else if (this.`type` == RecordType.NS && this.name == zoneName) false
    else
      this.accessLevel.contains(AccessLevel.Write) || this.accessLevel.contains(AccessLevel.Delete)

  def canDelete(zoneName: String): Boolean =
    if (this.`type` == RecordType.SOA) false
    else if (this.`type` == RecordType.NS && this.name == zoneName) false
    else this.accessLevel.contains(AccessLevel.Delete)

  def recordDataDisplay: VdomElement =
    RecordDataDisplay(RecordDataDisplay.Props(this.records, this.`type`, this.id))
}

object RecordSetResponse extends OptionRW with RecordSetTypeRW with AccessLevelRW {
  implicit val rw: ReadWriter[RecordSetResponse] = macroRW

  def labelHasInvalidDot(recordName: String, recordType: RecordType, zoneName: String): Boolean = {
    val canHaveDots =
      List(RecordType.PTR, RecordType.NS, RecordType.SOA, RecordType.SRV, RecordType.NAPTR)

    (recordName, recordType) match {
      case (noDot, _) if !noDot.contains(".") => false
      case (_, whitelistedType) if canHaveDots.contains(whitelistedType) => false
      case (dottedApex, _) if dottedApex.stripPrefix(".") == zoneName.stripPrefix(".") => false
      case _ => true
    }
  }
}
