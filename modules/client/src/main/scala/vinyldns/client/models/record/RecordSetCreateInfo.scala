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

import vinyldns.core.domain.record.RecordType
import upickle.default._
import vinyldns.client.models.OptionRW

case class RecordSetCreateInfo(
    zoneId: String,
    `type`: RecordType.RecordType,
    name: String,
    ttl: Int,
    records: List[RecordData],
    ownerGroupId: Option[String],
    ownerGroupName: Option[String])
    extends RecordSetModalInfo

object RecordSetCreateInfo extends RecordSetTypeRW with OptionRW {
  implicit val rw: ReadWriter[RecordSetCreateInfo] = macroRW

  def apply(zoneId: String): RecordSetCreateInfo =
    new RecordSetCreateInfo(zoneId, RecordType.A, "", 300, List(RecordData()), None, None)
}
