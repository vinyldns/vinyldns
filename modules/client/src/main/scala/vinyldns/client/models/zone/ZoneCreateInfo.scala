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

case class ZoneCreateInfo(
    name: String,
    email: String,
    adminGroupId: String,
    adminGroupName: Option[String],
    shared: Boolean,
    connection: Option[ZoneConnection],
    transferConnection: Option[ZoneConnection])
    extends ZoneModalInfo

object ZoneCreateInfo extends OptionRW {
  implicit val rw: ReadWriter[ZoneCreateInfo] = macroRW
}
