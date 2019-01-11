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

import vinyldns.api.domain.batch.BatchChangeInterfaces.ValidatedBatch
import vinyldns.api.domain.batch.BatchTransformations.ChangeForValidation
import vinyldns.core.domain.batch.SingleChange

/* Error response options */
sealed trait BatchChangeErrorResponse
// This separates error by change requested
final case class InvalidBatchChangeResponses(
    changeRequests: List[ChangeInput],
    changeRequestResponses: ValidatedBatch[ChangeForValidation])
    extends BatchChangeErrorResponse
// The request itself is invalid in this case, so we fail fast
final case class ChangeLimitExceeded(limit: Int) extends BatchChangeErrorResponse {
  def message: String = s"Cannot request more than $limit changes in a single batch change request"
}
final case class BatchChangeIsEmpty(limit: Int) extends BatchChangeErrorResponse {
  def message: String =
    s"Batch change contained no changes. Batch change must have at least one change, up to a maximum of $limit changes."
}
final case class GroupDoesNotExist(id: String) extends BatchChangeErrorResponse {
  def message: String = s"""Group with ID "$id" was not found"""
}
final case class NotAMemberOfOwnerGroup(ownerGroupId: String, userName: String)
    extends BatchChangeErrorResponse {
  def message: String =
    s"""User "$userName" is not a member of group "$ownerGroupId". Owner group ID is only required for """ +
      "record set changes in shared zones."
}
final case class BatchChangeNotFound(id: String) extends BatchChangeErrorResponse {
  def message: String = s"Batch change with id $id cannot be found"
}
final case class UserNotAuthorizedError(itemId: String) extends BatchChangeErrorResponse {
  def message: String = s"User does not have access to item $itemId"
}
final case class BatchConversionError(change: SingleChange) extends BatchChangeErrorResponse {
  def message: String =
    s"""Batch conversion for processing failed to convert change with name "${change.inputName}"
       |and type "${change.typ}"""".stripMargin
}
final case class UnknownConversionError(message: String) extends BatchChangeErrorResponse
