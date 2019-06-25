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
  val recordChangeId: Option[String]
  val recordSetId: Option[String]
  val zoneId: Option[String]
  val recordName: Option[String]
  val typ: RecordType
  val inputName: String
  val zoneName: Option[String]
  val recordKey: Option[RecordKey] = (zoneId, recordName, typ) match {
    case (Some(zid), Some(rname), t) => Some(RecordKey(zid, rname, t))
    case _ => None
  }

  def withFailureMessage(error: String): SingleChange = this match {
    case add: SingleAddChange =>
      add.copy(status = SingleChangeStatus.Failed, systemMessage = Some(error))
    case delete: SingleDeleteChange =>
      delete.copy(status = SingleChangeStatus.Failed, systemMessage = Some(error))
  }

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
    id: String = UUID.randomUUID().toString)
    extends SingleChange

final case class SingleDeleteChange(
    zoneId: Option[String],
    zoneName: Option[String],
    recordName: Option[String],
    inputName: String,
    typ: RecordType,
    status: SingleChangeStatus,
    systemMessage: Option[String],
    recordChangeId: Option[String],
    recordSetId: Option[String],
    id: String = UUID.randomUUID().toString)
    extends SingleChange

/*
 - Pending has not yet been processed
 - Complete has been processed - must have recordChangeId
 - Failed had some error (see systemMessage) - may have recordChangeId, not required
 - UnValidated means there was a validation error and it needs manual review
 */
object SingleChangeStatus extends Enumeration {
  type SingleChangeStatus = Value
  val Pending, Complete, Failed, UnValidated = Value
}

case class RecordKey(zoneId: String, recordName: String, recordType: RecordType)

object RecordKey {
  def apply(zoneId: String, recordName: String, recordType: RecordType): RecordKey =
    new RecordKey(zoneId, recordName.toLowerCase, recordType)
}
