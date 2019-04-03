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

package vinyldns.client.models.zone

import vinyldns.client.models.OptionRW
import upickle.default._
import vinyldns.core.domain.zone.ZoneStatus

case class Zone(
    id: String,
    name: String,
    email: String,
    adminGroupId: String,
    status: ZoneStatus.ZoneStatus,
    created: String,
    account: String,
    shared: Boolean,
    acl: Rules,
    latestSync: Option[String] = None,
    adminGroupName: Option[String] = None,
    connection: Option[ZoneConnection] = None,
    transferConnection: Option[ZoneConnection] = None
)

object Zone extends OptionRW {
  implicit val recordSetChangeTypeRW: ReadWriter[ZoneStatus.ZoneStatus] =
    readwriter[ujson.Value]
      .bimap[ZoneStatus.ZoneStatus](
        fromStatus => ujson.Value.JsonableString(fromStatus.toString),
        toStatus => {
          val raw = toStatus.toString().replaceAll("^\"|\"$", "")
          ZoneStatus.withName(raw)
        }
      )

  implicit val rw: ReadWriter[Zone] = macroRW
}

/*
  The GET zone route, at least through the portal, returns a {zone: ...}
 */
case class GetZone(zone: Zone)

object GetZone {
  implicit val rw: ReadWriter[GetZone] = macroRW
}
