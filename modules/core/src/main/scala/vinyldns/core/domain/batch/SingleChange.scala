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
import vinyldns.core.domain.SingleChangeError
import vinyldns.core.domain.batch.SingleChangeStatus.SingleChangeStatus
import vinyldns.core.domain.record.RecordData
import vinyldns.core.domain.record.RecordType.RecordType

sealed trait SingleChange {
  val id: String
  val status: SingleChangeStatus
  val systemMessage: Option[String]
  val recordChangeId: Option[String]
  val recordSetId: Option[String]
  val zoneId: Option[String]
  val recordName: Option[String]
  val typ: RecordType
  val inputName: String
  val zoneName: Option[String]
  val validationErrors: List[SingleChangeError]
  val recordKey: Option[RecordKey] = (zoneId, recordName, typ) match {
    case (Some(zid), Some(rname), t) => Some(RecordKey(zid, rname, t))
    case _ => None
  }

  def withFailureMessage(error: String): SingleChange = this match {
    case add: SingleAddChange =>
      add.copy(status = SingleChangeStatus.Failed, systemMessage = Some(error))
    case delete: SingleDeleteRRSetChange =>
      delete.copy(status = SingleChangeStatus.Failed, systemMessage = Some(error))
  }

  def withDoesNotExistMessage(error: String): SingleChange = this match {
    case add: SingleAddChange =>
      add.copy(status = SingleChangeStatus.Failed, systemMessage = Some(error))
    case delete: SingleDeleteRRSetChange =>
      delete.copy(status = SingleChangeStatus.Complete, systemMessage = Some(error))
  }

  def withProcessingError(message: Option[String], failedRecordChangeId: String): SingleChange =
    this match {
      case add: SingleAddChange =>
        add.copy(
          status = SingleChangeStatus.Failed,
          systemMessage = message,
          recordChangeId = Some(failedRecordChangeId)
        )
      case delete: SingleDeleteRRSetChange =>
        delete.copy(
          status = SingleChangeStatus.Failed,
          systemMessage = message,
          recordChangeId = Some(failedRecordChangeId)
        )
    }

  def complete(message: Option[String], completeRecordChangeId: String, recordSetId: String): SingleChange = this match {
    case add: SingleAddChange =>
      add.copy(
        status = SingleChangeStatus.Complete,
        systemMessage = message,
        recordChangeId = Some(completeRecordChangeId),
        recordSetId = Some(recordSetId)
      )
    case delete: SingleDeleteRRSetChange =>
      delete.copy(
        status = SingleChangeStatus.Complete,
        systemMessage = message,
        recordChangeId = Some(completeRecordChangeId),
        recordSetId = Some(recordSetId)
      )
  }

  def reject: SingleChange = this match {
    case sad: SingleAddChange => sad.copy(status = SingleChangeStatus.Rejected)
    case sdc: SingleDeleteRRSetChange => sdc.copy(status = SingleChangeStatus.Rejected)
  }

  def cancel: SingleChange = this match {
    case sad: SingleAddChange => sad.copy(status = SingleChangeStatus.Cancelled)
    case sdc: SingleDeleteRRSetChange => sdc.copy(status = SingleChangeStatus.Cancelled)
  }

  def updateValidationErrors(errors: List[SingleChangeError]): SingleChange =
    this match {
      case sad: SingleAddChange => sad.copy(validationErrors = errors)
      case sdc: SingleDeleteRRSetChange => sdc.copy(validationErrors = errors)
    }
}

final case class SingleAddChange(
    zoneId: Option[String],
    zoneName: Option[String],
    recordName: Option[String],
    inputName: String,
    typ: RecordType,
    ttl: Long,
    recordData: RecordData,
    status: SingleChangeStatus,
    systemMessage: Option[String],
    recordChangeId: Option[String],
    recordSetId: Option[String],
    validationErrors: List[SingleChangeError] = List.empty,
    id: String = UUID.randomUUID().toString
) extends SingleChange

final case class SingleDeleteRRSetChange(
    zoneId: Option[String],
    zoneName: Option[String],
    recordName: Option[String],
    inputName: String,
    typ: RecordType,
    recordData: Option[RecordData],
    status: SingleChangeStatus,
    systemMessage: Option[String],
    recordChangeId: Option[String],
    recordSetId: Option[String],
    validationErrors: List[SingleChangeError] = List.empty,
    id: String = UUID.randomUUID().toString
) extends SingleChange

/*
 - Pending has not yet been processed
 - Complete has been processed - must have recordChangeId
 - Failed had some error (see systemMessage) - may have recordChangeId, not required
 - NeedsReview means there was a validation error and it needs manual review
 - Rejected means the reviewer has rejected this batch change
 - Cancelled means the batch change creator decided to stop the batch change before it's reviewed
 */
object SingleChangeStatus extends Enumeration {
  type SingleChangeStatus = Value
  val Pending, Complete, Failed, NeedsReview, Rejected, Cancelled = Value
}

case class RecordKey(zoneId: String, recordName: String, recordType: RecordType)
case class RecordKeyData(zoneId: String, recordName: String, recordType: RecordType, recordData: RecordData)

object RecordKey {
  def apply(zoneId: String, recordName: String, recordType: RecordType): RecordKey =
    new RecordKey(zoneId, recordName.toLowerCase, recordType)
}

object RecordKeyData {
  def apply(zoneId: String, recordName: String, recordType: RecordType, recordData: RecordData): RecordKeyData =
    new RecordKeyData(zoneId, recordName.toLowerCase, recordType, recordData)
}

object OwnerType extends Enumeration {
  type OwnerType = Value
  val Record, Zone = Value
}
