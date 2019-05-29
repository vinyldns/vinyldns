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

import vinyldns.core.domain.batch.SingleChangeStatus.SingleChangeStatus
import vinyldns.core.domain.record.RecordData
import vinyldns.core.domain.record.RecordType.RecordType

sealed trait SingleChange {
  val id: String
  val status: SingleChangeStatus
  val systemMessage: Option[String]
  val typ: RecordType
  val inputName: String
  val recordSetId: Option[String]

  def withFailureMessage(error: String): SingleChange = this match {
    case add: SingleAddChange =>
      add.copy(status = SingleChangeStatus.Failed, systemMessage = Some(error))
    case delete: SingleDeleteChange =>
      delete.copy(status = SingleChangeStatus.Failed, systemMessage = Some(error))
    case unapprovedAdd: UnapprovedSingleAddChange =>
      unapprovedAdd.copy(status = SingleChangeStatus.Failed, systemMessage = Some(error))
    case unapprovedDelete: UnapprovedSingleDeleteChange =>
      unapprovedDelete.copy(status = SingleChangeStatus.Failed, systemMessage = Some(error))
  }

  // TODO technically withProcessingError/complete should only work on ApprovedSingleChange types
  // but it's pretty baked in at this point
  def withProcessingError(message: Option[String], failedRecordChangeId: String): SingleChange =
    this match {
      case add: SingleAddChange =>
        add.copy(
          status = SingleChangeStatus.Failed,
          systemMessage = message,
          recordChangeId = Some(failedRecordChangeId))
      case delete: SingleDeleteChange =>
        delete.copy(
          status = SingleChangeStatus.Failed,
          systemMessage = message,
          recordChangeId = Some(failedRecordChangeId))
      case unapproved => unapproved
    }

  def complete(completeRecordChangeId: String, recordSetId: String): SingleChange = this match {
    case add: SingleAddChange =>
      add.copy(
        status = SingleChangeStatus.Complete,
        recordChangeId = Some(completeRecordChangeId),
        recordSetId = Some(recordSetId))
    case delete: SingleDeleteChange =>
      delete.copy(
        status = SingleChangeStatus.Complete,
        recordChangeId = Some(completeRecordChangeId),
        recordSetId = Some(recordSetId))
    case unapproved => unapproved
  }
}

sealed trait ApprovedSingleChange extends SingleChange {
  val recordChangeId: Option[String]
  val zoneId: String
  val recordName: String
  val zoneName: String
  val recordKey = RecordKey(zoneId, recordName, typ)
}

final case class UnapprovedSingleAddChange(
    zoneId: Option[String],
    zoneName: Option[String],
    recordName: Option[String],
    inputName: String,
    typ: RecordType,
    ttl: Long,
    recordData: RecordData,
    status: SingleChangeStatus,
    systemMessage: Option[String],
    recordSetId: Option[String],
    id: String = UUID.randomUUID().toString)
    extends SingleChange

final case class UnapprovedSingleDeleteChange(
    zoneId: Option[String],
    zoneName: Option[String],
    recordName: Option[String],
    inputName: String,
    typ: RecordType,
    status: SingleChangeStatus,
    systemMessage: Option[String],
    recordSetId: Option[String],
    id: String = UUID.randomUUID().toString)
    extends SingleChange

final case class SingleAddChange(
    zoneId: String,
    zoneName: String,
    recordName: String,
    inputName: String,
    typ: RecordType,
    ttl: Long,
    recordData: RecordData,
    status: SingleChangeStatus,
    systemMessage: Option[String],
    recordChangeId: Option[String],
    recordSetId: Option[String],
    id: String = UUID.randomUUID().toString)
    extends ApprovedSingleChange

final case class SingleDeleteChange(
    zoneId: String,
    zoneName: String,
    recordName: String,
    inputName: String,
    typ: RecordType,
    status: SingleChangeStatus,
    systemMessage: Option[String],
    recordChangeId: Option[String],
    recordSetId: Option[String],
    id: String = UUID.randomUUID().toString)
    extends ApprovedSingleChange

/*
 - Pending has not yet been processed
 - Complete has been processed - must have recordChangeId
 - Failed had some error (see systemMessage) - may have recordChangeId, not required
 */
object SingleChangeStatus extends Enumeration {
  type SingleChangeStatus = Value
  val Pending, Complete, Failed = Value
}

case class RecordKey(zoneId: String, recordName: String, recordType: RecordType)

object RecordKey {
  def apply(zoneId: String, recordName: String, recordType: RecordType): RecordKey =
    new RecordKey(zoneId, recordName.toLowerCase, recordType)
}
