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

package vinyldns.core.domain.batch

import java.util.UUID

import org.joda.time.DateTime
import vinyldns.core.domain.batch.BatchChangeStatus.BatchChangeStatus

case class BatchChangeSummary(
    userId: String,
    userName: String,
    comments: Option[String],
    createdTimestamp: DateTime,
    totalChanges: Int,
    status: BatchChangeStatus,
    ownerGroupId: Option[String],
    id: String = UUID.randomUUID().toString,
    ownerGroupName: Option[String] = None)

object BatchChangeSummary {
  def apply(batchchange: BatchChange): BatchChangeSummary =
    BatchChangeSummary(
      batchchange.userId,
      batchchange.userName,
      batchchange.comments,
      batchchange.createdTimestamp,
      batchchange.changes.length,
      batchchange.status,
      batchchange.ownerGroupId,
      batchchange.id
    )

  def apply(batchChangeInfo: BatchChangeInfo): BatchChangeSummary = {
    import batchChangeInfo._
    BatchChangeSummary(
      userId,
      userName,
      comments,
      createdTimestamp,
      changes.length,
      status,
      ownerGroupId,
      id,
      ownerGroupName
    )
  }
}

case class BatchChangeSummaryList(
    batchChanges: List[BatchChangeSummary],
    startFrom: Option[Int] = None,
    nextId: Option[Int] = None,
    maxItems: Int = 100,
    ignoreAccess: Boolean = false)
