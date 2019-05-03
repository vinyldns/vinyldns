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

/*
  The list batch changes endpoint returns a list of summaries, not the actual batch changes
 */
case class BatchChangeSummaryResponse(
    userId: String,
    userName: String,
    createdTimestamp: String,
    totalChanges: Int,
    status: BatchChangeStatus.BatchChangeStatus,
    id: String,
    comments: Option[String] = None,
    ownerGroupId: Option[String] = None
)

object BatchChangeSummaryResponse extends OptionRW with BatchChangeStatusRW {
  implicit val batchChangeSummaryRw: ReadWriter[BatchChangeSummaryResponse] =
    macroRW[BatchChangeSummaryResponse]
}
