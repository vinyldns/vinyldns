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

import cats.data.NonEmptyList
import org.joda.time.DateTime
import vinyldns.api.VinylDNSConfig
import vinyldns.core.domain.{DomainValidationError, SingleChangeError}
import vinyldns.core.domain.DomainHelpers.ensureTrailingDot
import vinyldns.core.domain.batch._
import vinyldns.core.domain.record.RecordData
import vinyldns.core.domain.record.RecordType._

final case class BatchChangeInput(
    comments: Option[String],
    changes: List[ChangeInput],
    ownerGroupId: Option[String] = None,
    scheduledTime: Option[DateTime] = None,
    id: String = UUID.randomUUID().toString)

object BatchChangeInput {
  def apply(batchChange: BatchChange): BatchChangeInput = {
    val changes = batchChange.changes.map {
      case add: SingleAddChange => AddChangeInput(add)
      case del: SingleDeleteRRSetChange => DeleteRRSetChangeInput(del)
      case del: SingleDeleteRecordChange => DeleteRecordChangeInput(del)
    }
    new BatchChangeInput(
      batchChange.comments,
      changes,
      batchChange.ownerGroupId,
      batchChange.scheduledTime,
      batchChange.id)
  }
}

sealed trait ChangeInput {
  val inputName: String
  val typ: RecordType
  val id: String
  def asNewStoredChange(errors: NonEmptyList[DomainValidationError]): SingleChange
}

final case class AddChangeInput(
    inputName: String,
    typ: RecordType,
    ttl: Option[Long],
    record: RecordData,
    id: String = UUID.randomUUID().toString)
    extends ChangeInput {

  def asNewStoredChange(errors: NonEmptyList[DomainValidationError]): SingleChange = {
    val knownTtl = ttl.getOrElse(VinylDNSConfig.defaultTtl)
    SingleAddChange(
      None,
      None,
      None,
      inputName,
      typ,
      knownTtl,
      record,
      SingleChangeStatus.NeedsReview,
      None,
      None,
      None,
      errors.toList.map(SingleChangeError(_)),
      id
    )
  }
}

final case class DeleteRRSetChangeInput(
    inputName: String,
    typ: RecordType,
    id: String = UUID.randomUUID().toString)
    extends ChangeInput {
  def asNewStoredChange(errors: NonEmptyList[DomainValidationError]): SingleChange =
    SingleDeleteRRSetChange(
      None,
      None,
      None,
      inputName,
      typ,
      SingleChangeStatus.NeedsReview,
      None,
      None,
      None,
      errors.toList.map(SingleChangeError(_)),
      id
    )
}

// TODO: Remove coverage on/off
// $COVERAGE-OFF$
final case class DeleteRecordChangeInput(
    inputName: String,
    typ: RecordType,
    record: RecordData,
    id: String = UUID.randomUUID().toString)
    extends ChangeInput {
  def asNewStoredChange(errors: NonEmptyList[DomainValidationError]): SingleChange =
    SingleDeleteRecordChange(
      None,
      None,
      None,
      inputName,
      typ,
      record,
      SingleChangeStatus.NeedsReview,
      None,
      None,
      None,
      errors.toList.map(SingleChangeError(_)),
      id
    )
}
// $COVERAGE-ON$

object AddChangeInput {
  def apply(
      inputName: String,
      typ: RecordType,
      ttl: Option[Long],
      record: RecordData,
      id: String = UUID.randomUUID().toString): AddChangeInput = {
    val transformName = typ match {
      case PTR => inputName
      case _ => ensureTrailingDot(inputName)
    }
    new AddChangeInput(transformName, typ, ttl, record, id)
  }

  def apply(sc: SingleAddChange): AddChangeInput = {
    val transformName = sc.typ match {
      case PTR => sc.inputName
      case _ => ensureTrailingDot(sc.inputName)
    }
    AddChangeInput(transformName, sc.typ, Some(sc.ttl), sc.recordData, sc.id)
  }
}

object DeleteRRSetChangeInput {
  def apply(inputName: String, typ: RecordType): DeleteRRSetChangeInput = {
    val transformName = typ match {
      case PTR => inputName
      case _ => ensureTrailingDot(inputName)
    }
    new DeleteRRSetChangeInput(transformName, typ)
  }

  def apply(sc: SingleDeleteRRSetChange): DeleteRRSetChangeInput = {
    val transformName = sc.typ match {
      case PTR => sc.inputName
      case _ => ensureTrailingDot(sc.inputName)
    }
    DeleteRRSetChangeInput(transformName, sc.typ, sc.id)
  }
}

object DeleteRecordChangeInput {
  def apply(inputName: String, typ: RecordType, record: RecordData): DeleteRecordChangeInput = {
    val transformName = typ match {
      case PTR => inputName
      case _ => ensureTrailingDot(inputName)
    }
    new DeleteRecordChangeInput(transformName, typ, record)
  }

  def apply(sdrc: SingleDeleteRecordChange): DeleteRecordChangeInput =
    DeleteRecordChangeInput(sdrc.inputName, sdrc.typ, sdrc.recordData, sdrc.id)
}

object ChangeInputType extends Enumeration {
  type ChangeInputType = Value
  val Add, DeleteRecordSet, DeleteRecord = Value
}

final case class RejectBatchChangeInput(reviewComment: Option[String] = None)

final case class ApproveBatchChangeInput(reviewComment: Option[String] = None)
