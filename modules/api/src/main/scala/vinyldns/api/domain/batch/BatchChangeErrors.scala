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

package vinyldns.api.domain.batch

import org.joda.time.DateTime
import vinyldns.api.config.Messages
import vinyldns.api.domain.MessagesService.finalMessages
import vinyldns.api.domain.batch.BatchChangeInterfaces.ValidatedBatch
import vinyldns.api.domain.batch.BatchTransformations.ChangeForValidation
import vinyldns.core.domain.DomainValidationError
import vinyldns.core.domain.batch.{BatchChange, SingleChange}

/* Error response options */
sealed trait BatchChangeErrorResponse
final case class InvalidBatchChangeInput(errors: List[DomainValidationError])
    extends BatchChangeErrorResponse

// This separates error by change requested
final case class InvalidBatchChangeResponses(
    changeRequests: List[ChangeInput],
    changeRequestResponses: ValidatedBatch[ChangeForValidation]
) extends BatchChangeErrorResponse

final case class BatchChangeFailedApproval(batchChange: BatchChange)
    extends BatchChangeErrorResponse

final case class BatchChangeNotFound(id: String) extends BatchChangeErrorResponse {
  def message: String = finalMessages("batch-change-not-found") match {
    case Messages(_, _, message) =>
      message.format(id)
  }
}
final case class UserNotAuthorizedError(itemId: String) extends BatchChangeErrorResponse {
  def message: String = finalMessages("user-not-authorized") match {
    case Messages(_, _, message) =>
      message.format(itemId)
  }
}
final case class BatchConversionError(change: SingleChange) extends BatchChangeErrorResponse {
  def message: String = finalMessages("batch-conversion-error") match {
    case Messages(_, _, message) =>
      message.format(change.inputName, change.typ).stripMargin
  }
}
final case class UnknownConversionError(message: String) extends BatchChangeErrorResponse

final case class BatchChangeNotPendingReview(id: String) extends BatchChangeErrorResponse {
  def message: String = finalMessages("batch-change-not-pending") match {
    case Messages(_, _, message) =>
      message.format(id)
  }
}

final case class BatchRequesterNotFound(userId: String, userName: String)
    extends BatchChangeErrorResponse {
  def message: String = finalMessages("batch-requester-not-found") match {
    case Messages(_, _, message) =>
      message.format(userId, userName)
  }
}

case object ScheduledChangesDisabled extends BatchChangeErrorResponse {
  val message: String = finalMessages("scheduled-changes-disabled") match {
    case Messages(_, _, message) =>
      message
  }
}

case object ScheduledTimeMustBeInFuture extends BatchChangeErrorResponse {
  val message: String = finalMessages("time-must-be-in-future") match {
    case Messages(_, _, message) =>
      message
  }
}

final case class ScheduledChangeNotDue(scheduledTime: DateTime) extends BatchChangeErrorResponse {
  val message: String = finalMessages("change-not-due") match {
    case Messages(_, _, message) =>
      message.format(scheduledTime)
  }
}

case object ManualReviewRequiresOwnerGroup extends BatchChangeErrorResponse {
  val message: String = finalMessages("require-owner-group") match {
    case Messages(_, _, message) =>
      message
  }
}
