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
import vinyldns.core.domain.batch.BatchChangeApprovalStatus.BatchChangeApprovalStatus

case class BatchChange(
    userId: String,
    userName: String,
    comments: Option[String],
    createdTimestamp: DateTime,
    changes: List[SingleChange],
    ownerGroupId: Option[String] = None,
    approvalStatus: BatchChangeApprovalStatus,
    reviewerId: Option[String] = None,
    reviewComment: Option[String] = None,
    reviewTimestamp: Option[DateTime] = None,
    id: String = UUID.randomUUID().toString) {
  val status: BatchChangeStatus = {
    val singleStatuses = changes.map(_.status)
    val hasPending = singleStatuses.contains(SingleChangeStatus.Pending)
    val hasFailed = singleStatuses.contains(SingleChangeStatus.Failed)
    val hasComplete = singleStatuses.contains(SingleChangeStatus.Complete)

    BatchChangeStatus.calculateBatchStatus(approvalStatus, hasPending, hasFailed, hasComplete)
  }
}

/*
 - Pending if at least one change is still in pending state
 - Complete means all changes are in complete state
 - Failed means all changes are in failure state
 - PartialFailure means some have failed, the rest are complete
 */
object BatchChangeStatus extends Enumeration {
  type BatchChangeStatus = Value
  val Pending, Complete, Failed, PartialFailure = Value

  def calculateBatchStatus(
      approvalStatus: BatchChangeApprovalStatus,
      hasPending: Boolean,
      hasFailed: Boolean,
      hasComplete: Boolean): BatchChangeStatus =
    approvalStatus match {
      case BatchChangeApprovalStatus.PendingApproval => BatchChangeStatus.Pending
      case BatchChangeApprovalStatus.ManuallyRejected => BatchChangeStatus.Failed
      case _ =>
        (hasPending, hasFailed, hasComplete) match {
          case (true, _, _) => BatchChangeStatus.Pending
          case (_, true, true) => BatchChangeStatus.PartialFailure
          case (_, true, false) => BatchChangeStatus.Failed
          case _ => BatchChangeStatus.Complete
        }
    }
}

object BatchChangeApprovalStatus extends Enumeration {
  type BatchChangeApprovalStatus = Value
  val AutoApproved, PendingApproval, ManuallyApproved, ManuallyRejected = Value

  private val valueMap =
    BatchChangeApprovalStatus.values.map(v => v.toString.toLowerCase -> v).toMap

  def find(status: String): Option[BatchChangeApprovalStatus] =
    valueMap.get(status.toLowerCase)
}

case class BatchChangeInfo(
    userId: String,
    userName: String,
    comments: Option[String],
    createdTimestamp: DateTime,
    changes: List[SingleChange],
    ownerGroupId: Option[String],
    id: String,
    status: BatchChangeStatus,
    ownerGroupName: Option[String]
)

object BatchChangeInfo {
  def apply(batchChange: BatchChange, ownerGroupName: Option[String] = None): BatchChangeInfo = {
    import batchChange._
    BatchChangeInfo(
      userId,
      userName,
      comments,
      createdTimestamp,
      changes,
      ownerGroupId,
      id,
      status,
      ownerGroupName
    )
  }
}
