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

import vinyldns.core.domain.batch.BatchChangeStatus
import upickle.default._
import vinyldns.client.models.OptionRW

trait BatchChangeStatusRW {
  implicit val batchChangeStatusRW: ReadWriter[BatchChangeStatus.BatchChangeStatus] =
    readwriter[ujson.Value]
      .bimap[BatchChangeStatus.BatchChangeStatus](
        fromType => ujson.Value.JsonableString(fromType.toString),
        toType => {
          val raw = toType.toString().replaceAll("^\"|\"$", "")
          BatchChangeStatus.withName(raw)
        }
      )
}

case class BatchChangeResponse(
    userId: String,
    userName: String,
    createdTimestamp: String,
    changes: List[SingleChangeResponse],
    status: BatchChangeStatus.BatchChangeStatus,
    id: String,
    comments: Option[String] = None,
    ownerGroupName: Option[String] = None,
    ownerGroupId: Option[String] = None)

object BatchChangeResponse extends OptionRW with BatchChangeStatusRW {
  implicit val batchChangeRw: ReadWriter[BatchChangeResponse] = macroRW[BatchChangeResponse]
}
