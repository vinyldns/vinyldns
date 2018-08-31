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

import java.util.UUID

import vinyldns.api.domain.batch.SingleChangeStatus.SingleChangeStatus
import vinyldns.api.domain.record.RecordData
import vinyldns.api.domain.record.RecordType.RecordType

sealed trait SingleChange {
  val id: String
  val status: SingleChangeStatus
  val systemMessage: Option[String]
  val recordChangeId: Option[String]
  val recordSetId: Option[String]
  val zoneId: String
  val recordName: String
  val typ: RecordType
  val inputName: String
  val zoneName: String
  val recordKey = RecordKey(zoneId, recordName, typ)

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
    extends SingleChange

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
    extends SingleChange

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
