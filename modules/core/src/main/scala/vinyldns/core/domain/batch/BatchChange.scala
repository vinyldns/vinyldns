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

import java.time.Instant
import vinyldns.core.domain.batch.BatchChangeStatus.BatchChangeStatus
import vinyldns.core.domain.batch.BatchChangeApprovalStatus.BatchChangeApprovalStatus
import vinyldns.core.domain.zone.{ZoneCommand, ZoneCommandResult}

case class BatchChange(
    userId: String,
    userName: String,
    comments: Option[String],
    createdTimestamp: Instant,
    changes: List[SingleChange],
    ownerGroupId: Option[String] = None,
    approvalStatus: BatchChangeApprovalStatus,
    reviewerId: Option[String] = None,
    reviewComment: Option[String] = None,
    reviewTimestamp: Option[Instant] = None,
    id: String = UUID.randomUUID().toString,
    scheduledTime: Option[Instant] = None,
    cancelledTimestamp: Option[Instant] = None
) {
  val status: BatchChangeStatus = {
    val singleStatuses = changes.map(_.status)
    val hasPending = singleStatuses.contains(SingleChangeStatus.Pending)
    val hasFailed = singleStatuses.contains(SingleChangeStatus.Failed)
    val hasComplete = singleStatuses.contains(SingleChangeStatus.Complete)
    val isScheduled = scheduledTime.isDefined

    BatchChangeStatus.calculateBatchStatus(
      approvalStatus,
      hasPending,
      hasFailed,
      hasComplete,
      isScheduled
    )
  }
}

case class BatchChangeCommand(id: String) extends ZoneCommand with ZoneCommandResult

/*
 - Complete means all changes are in complete state
 - Failed means all changes are in failure state
 - PartialFailure means some have failed, the rest are complete
 - PendingProcessing if at least one change is still in pending state
 - PendingReview if approval status is PendingReview
 - Rejected if approval status is ManuallyRejected
 - Scheduled if approval status is PendingReview and batch change is scheduled
 */
object BatchChangeStatus extends Enumeration {
  type BatchChangeStatus = Value
  val Cancelled, Complete, Failed, PartialFailure, PendingProcessing, PendingReview, Rejected,
      Scheduled =
    Value

  def calculateBatchStatus(
      approvalStatus: BatchChangeApprovalStatus,
      hasPending: Boolean,
      hasFailed: Boolean,
      hasComplete: Boolean,
      isScheduled: Boolean
  ): BatchChangeStatus =
    approvalStatus match {
      case BatchChangeApprovalStatus.PendingReview if isScheduled => BatchChangeStatus.Scheduled
      case BatchChangeApprovalStatus.PendingReview => BatchChangeStatus.PendingReview
      case BatchChangeApprovalStatus.ManuallyRejected => BatchChangeStatus.Rejected
      case BatchChangeApprovalStatus.Cancelled => BatchChangeStatus.Cancelled
      case _ =>
        (hasPending, hasFailed, hasComplete) match {
          case (true, _, _) => BatchChangeStatus.PendingProcessing
          case (false, true, true) => BatchChangeStatus.PartialFailure
          case (false, true, false) => BatchChangeStatus.Failed
          case _ => BatchChangeStatus.Complete
        }
    }

  private val valueMap =
    BatchChangeStatus.values.map(v => v.toString.toLowerCase -> v).toMap

  def find(status: String): Option[BatchChangeStatus] =
    valueMap.get(status.toLowerCase)
}

object BatchChangeApprovalStatus extends Enumeration {
  type BatchChangeApprovalStatus = Value
  val AutoApproved, Cancelled, ManuallyApproved, ManuallyRejected, PendingReview = Value

  private val valueMap =
    BatchChangeApprovalStatus.values.map(v => v.toString.toLowerCase -> v).toMap

  def find(status: String): Option[BatchChangeApprovalStatus] =
    valueMap.get(status.toLowerCase)
}

case class BatchChangeInfo(
    userId: String,
    userName: String,
    comments: Option[String],
    createdTimestamp: Instant,
    changes: List[SingleChange],
    ownerGroupId: Option[String],
    id: String,
    status: BatchChangeStatus,
    ownerGroupName: Option[String],
    approvalStatus: BatchChangeApprovalStatus,
    reviewerId: Option[String],
    reviewerUserName: Option[String],
    reviewComment: Option[String],
    reviewTimestamp: Option[Instant],
    scheduledTime: Option[Instant],
    cancelledTimestamp: Option[Instant]
)

object BatchChangeInfo {
  def apply(
      batchChange: BatchChange,
      ownerGroupName: Option[String] = None,
      reviewerUserName: Option[String] = None
  ): BatchChangeInfo = {
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
      ownerGroupName,
      approvalStatus,
      reviewerId,
      reviewerUserName,
      reviewComment,
      reviewTimestamp,
      scheduledTime,
      cancelledTimestamp
    )
  }
}
