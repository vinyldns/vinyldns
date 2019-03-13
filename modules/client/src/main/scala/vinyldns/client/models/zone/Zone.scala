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
import upickle.default.{ReadWriter, macroRW}

case class Zone(
    name: String,
    email: String,
    adminGroupId: String,
    adminGroupName: String,
    connection: Option[ZoneConnection] = None,
    transferConnection: Option[ZoneConnection] = None,
    status: String,
    created: String,
    account: String,
    shared: Boolean,
    acl: List[ACLRule],
)

object Zone extends OptionRW {
  implicit val rw: ReadWriter[Zone] = macroRW
}

case class ZoneCreateInfo(
    name: String,
    email: String,
    adminGroupId: String,
    connection: Option[ZoneConnection] = None,
    transferConnection: Option[ZoneConnection] = None
)

object ZoneCreateInfo extends OptionRW {

  implicit val rw: ReadWriter[ZoneCreateInfo] = macroRW
}

case class ZoneConnection(
    primaryServer: String,
    keyName: String,
    name: String,
    key: String
)

object ZoneConnection {
  implicit val rw: ReadWriter[ZoneConnection] = macroRW
}
