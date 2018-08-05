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

import cats.data.NonEmptyList
import cats.implicits._
import vinyldns.api.domain.DomainValidations._
import vinyldns.api.domain.auth.AuthPrincipal
import vinyldns.api.domain.batch.BatchChangeInterfaces._
import vinyldns.api.domain.batch.BatchTransformations._
import vinyldns.api.domain.record._
import vinyldns.api.domain.{AccessValidationAlgebra, _}

// turn on coverage once we start implementing these
// $COVERAGE-OFF$
trait BatchChangeValidationsAlgebra {

  def validateBatchChangeInputSize(input: BatchChangeInput): Either[BatchChangeErrorResponse, Unit]

  def validateInputChanges(input: List[ChangeInput]): ValidatedBatch[ChangeInput]

  // Note: once we add cnames, deletes in,
  // this will also want the prior request context (all requests before the current)
  // so the caller will likely have to do some kinda funky fold. Will deal with that later
  def validateChangesWithContext(
      changes: ValidatedBatch[ChangeForValidation],
      existingRecords: ExistingRecordSets,
      auth: AuthPrincipal): ValidatedBatch[ChangeForValidation]

  def canGetBatchChange(
      batchChange: BatchChange,
      auth: AuthPrincipal): Either[BatchChangeErrorResponse, Unit]
}

class BatchChangeValidations(changeLimit: Int, accessValidation: AccessValidationAlgebra)
    extends BatchChangeValidationsAlgebra {

  import RecordType._
  import accessValidation._

  def validateBatchChangeInputSize(
      input: BatchChangeInput): Either[BatchChangeErrorResponse, Unit] =
    if (input.changes.isEmpty) {
      BatchChangeIsEmpty(changeLimit).asLeft
    } else if (input.changes.length > changeLimit) {
      ChangeLimitExceeded(changeLimit).asLeft
    } else {
      ().asRight
    }

  /* input validations */

  def validateInputChanges(input: List[ChangeInput]): ValidatedBatch[ChangeInput] =
    input.map {
      case a: AddChangeInput => validateAddChangeInput(a).map(_ => a)
      case d: DeleteChangeInput => validateInputName(d).map(_ => d)
    }

  def validateAddChangeInput(addChangeInput: AddChangeInput): SingleValidation[Unit] = {
    val validTTL = validateTTL(addChangeInput.ttl).asUnit
    val validRecord = validateRecordData(addChangeInput.record)
    val validInput = validateInputName(addChangeInput)

    validTTL |+| validRecord |+| validInput
  }

  def validateRecordData(record: RecordData): SingleValidation[Unit] =
    record match {
      case a: AData => validateIpv4Address(a.address).asUnit
      case aaaa: AAAAData => validateIpv6Address(aaaa.address).asUnit
      case cname: CNAMEData => validateHostName(cname.cname).asUnit
      case ptr: PTRData => validateHostName(ptr.ptrdname).asUnit
      case txt: TXTData => validateTxtTextLength(txt.text).asUnit
      case mx: MXData =>
        validateMxPreference(mx.preference).asUnit |+| validateHostName(mx.exchange).asUnit
      case other => InvalidBatchRecordType(other.toString).invalidNel[Unit]
    }

  def validateInputName(change: ChangeInput): SingleValidation[Unit] =
    change.typ match {
      case A | AAAA | MX =>
        validateHostName(change.inputName).asUnit |+| notInReverseZone(change)
      case CNAME | TXT => validateHostName(change.inputName).asUnit
      case PTR => validatePtrIp(change.inputName)
      case other => InvalidBatchRecordType(other.toString).invalidNel[Unit]
    }

  def validatePtrIp(ip: String): SingleValidation[Unit] = {
    val validIpv4 = validateIpv4Address(ip).asUnit
    val validIpv6 = validateIpv6Address(ip).asUnit

    validIpv4.findValid(validIpv6).leftMap(_ => NonEmptyList.of(InvalidIPAddress(ip)))
  }

  def notInReverseZone(change: ChangeInput): SingleValidation[Unit] =
    if (change.inputName.endsWith("in-addr.arpa.") || change.inputName.endsWith("ip6.arpa."))
      RecordInReverseZoneError(change.inputName, change.typ.toString).invalidNel
    else ().validNel

  /* context validations */

  def validateChangesWithContext(
      changes: ValidatedBatch[ChangeForValidation],
      existingRecords: ExistingRecordSets,
      auth: AuthPrincipal): ValidatedBatch[ChangeForValidation] = {
    val changeGroups = ChangeForValidationMap(changes.getValid)

    // Updates are a combination of an add and delete for a record with the same name and type in a zone.
    changes.mapValid {
      case addUpdate: AddChangeForValidation
          if changeGroups.containsDeleteChangeForValidation(addUpdate.recordKey) =>
        validateAddUpdateWithContext(addUpdate, changeGroups, auth)
      case add: AddChangeForValidation =>
        validateAddWithContext(add, changeGroups, existingRecords, auth)
      case deleteUpdate: DeleteChangeForValidation
          if changeGroups.containsAddChangeForValidation(deleteUpdate.recordKey) =>
        validateDeleteUpdateWithContext(deleteUpdate, existingRecords, auth)
      case del: DeleteChangeForValidation => validateDeleteWithContext(del, existingRecords, auth)
    }
  }

  def validateDeleteWithContext(
      change: DeleteChangeForValidation,
      existingRecords: ExistingRecordSets,
      auth: AuthPrincipal): SingleValidation[ChangeForValidation] = {
    val validations = recordExists(
      change.zone.id,
      change.recordName,
      change.inputChange.inputName,
      change.inputChange.typ,
      existingRecords) |+|
      userCanDeleteRecordSet(change, auth)

    validations.map(_ => change)
  }

  def validateAddUpdateWithContext(
      change: AddChangeForValidation,
      changeGroups: ChangeForValidationMap,
      auth: AuthPrincipal): SingleValidation[ChangeForValidation] = {
    // Updates require checking against other batch changes since multiple adds
    // could potentially be grouped with a single delete
    val typedValidations = change.inputChange.typ match {
      case CNAME | PTR | TXT => recordIsUniqueInBatch(change, changeGroups)
      case _ => ().validNel
    }

    val validations = typedValidations |+|
      userCanAddUpdateRecordSet(change, auth)

    validations.map(_ => change)
  }

  def validateDeleteUpdateWithContext(
      change: DeleteChangeForValidation,
      existingRecords: ExistingRecordSets,
      auth: AuthPrincipal): SingleValidation[ChangeForValidation] = {
    val validations = recordExists(
      change.zone.id,
      change.recordName,
      change.inputChange.inputName,
      change.inputChange.typ,
      existingRecords) |+|
      userCanAddUpdateRecordSet(change, auth)

    validations.map(_ => change)
  }

  def validateAddWithContext(
      change: AddChangeForValidation,
      changeGroups: ChangeForValidationMap,
      existingRecords: ExistingRecordSets,
      auth: AuthPrincipal): SingleValidation[ChangeForValidation] = {
    val typedValidations = change.inputChange.typ match {
      case A | AAAA | MX =>
        noCnameWithRecordNameInExistingRecords(
          change.zone.id,
          change.recordName,
          change.inputChange.inputName,
          existingRecords,
          changeGroups)
      case CNAME =>
        cnameHasUniqueNameInExistingRecords(
          change.zone.id,
          change.recordName,
          change.inputChange.inputName,
          existingRecords,
          changeGroups) |+|
          cnameHasUniqueNameInBatch(change, changeGroups)
      case PTR | TXT =>
        noCnameWithRecordNameInExistingRecords(
          change.zone.id,
          change.recordName,
          change.inputChange.inputName,
          existingRecords,
          changeGroups) |+|
          recordIsUniqueInBatch(change, changeGroups)
      case other => InvalidBatchRecordType(other.toString).invalidNel
    }

    val validations =
      typedValidations |+|
        userCanAddUpdateRecordSet(change, auth) |+|
        recordDoesNotExist(
          change.zone.id,
          change.recordName,
          change.inputChange.inputName,
          change.inputChange.typ,
          existingRecords)

    validations.map(_ => change)
  }

  def cnameHasUniqueNameInBatch(
      cnameChange: AddChangeForValidation,
      changeGroups: ChangeForValidationMap): SingleValidation[Unit] = {
    val duplicateNameChangeInBatch = RecordType.values.toList.exists { recordType =>
      changeGroups
        .getList(RecordKey(cnameChange.zone.id, cnameChange.recordName, recordType))
        .exists(chg => chg.isAddChangeForValidation && chg != cnameChange)
    }

    if (duplicateNameChangeInBatch) {
      RecordNameNotUniqueInBatch(cnameChange.inputChange.inputName, CNAME).invalidNel
    } else ().validNel
  }

  def recordIsUniqueInBatch(
      change: AddChangeForValidation,
      changeGroups: ChangeForValidationMap): SingleValidation[Unit] = {
    val duplicateNameChangeInBatch = changeGroups
      .getList(RecordKey(change.zone.id, change.recordName, change.inputChange.typ))
      .exists(chg => chg.isAddChangeForValidation && chg != change)

    if (duplicateNameChangeInBatch) {
      RecordNameNotUniqueInBatch(change.inputChange.inputName, change.inputChange.typ).invalidNel
    } else ().validNel
  }

  def recordDoesNotExist(
      zoneId: String,
      recordName: String,
      inputName: String,
      typ: RecordType,
      existingRecordSets: ExistingRecordSets): SingleValidation[Unit] =
    existingRecordSets.get(zoneId, recordName, typ) match {
      case Some(_) => RecordAlreadyExists(inputName).invalidNel
      case None => ().validNel
    }

  def recordExists(
      zoneId: String,
      recordName: String,
      inputName: String,
      typ: RecordType,
      existingRecordSets: ExistingRecordSets): SingleValidation[Unit] =
    existingRecordSets.get(zoneId, recordName, typ) match {
      case Some(_) => ().validNel
      case None => RecordDoesNotExist(inputName).invalidNel
    }

  def noCnameWithRecordNameInExistingRecords(
      zoneId: String,
      recordName: String,
      inputName: String,
      existingRecordSets: ExistingRecordSets,
      changeGroups: ChangeForValidationMap): SingleValidation[Unit] = {
    val cnameExists = existingRecordSets.get(zoneId, recordName, CNAME).isDefined
    val matchList = changeGroups.getList(RecordKey(zoneId, recordName, CNAME))
    val isBeingDeleted = matchList.forall(_.isDeleteChangeForValidation) && matchList.nonEmpty

    (cnameExists, isBeingDeleted) match {
      case (false, _) => ().validNel
      case (true, true) => ().validNel
      case (true, false) => CnameIsNotUniqueError(inputName, CNAME).invalidNel
    }
  }

  def cnameHasUniqueNameInExistingRecords(
      zoneId: String,
      recordName: String,
      inputName: String,
      existingRecordSets: ExistingRecordSets,
      changeGroups: ChangeForValidationMap): SingleValidation[Unit] = {
    val existingRecordSetsMatch = existingRecordSets.getRecordSetMatch(zoneId, recordName)

    val hasNonDeletedExistingRs = existingRecordSetsMatch.find { rs =>
      val matchList = changeGroups.getList(RecordKey(rs.zoneId, rs.name, rs.typ))
      matchList.exists(!_.isDeleteChangeForValidation) || matchList.isEmpty
    }

    hasNonDeletedExistingRs match {
      case Some(rs) => CnameIsNotUniqueError(inputName, rs.typ).invalidNel
      case None => ().validNel
    }
  }

  // TODO this only works because behind the scenes our ADD and UPDATE is the same
  // - should update that in the AccessValidations in a different PR
  def userCanAddUpdateRecordSet(
      input: ChangeForValidation,
      authPrincipal: AuthPrincipal): SingleValidation[Unit] = {
    val result = canAddRecordSet(authPrincipal, input.recordName, input.inputChange.typ, input.zone)
    result.leftMap(_ => UserIsNotAuthorized(authPrincipal.userId)).toValidatedNel
  }

  def userCanDeleteRecordSet(
      input: ChangeForValidation,
      authPrincipal: AuthPrincipal): SingleValidation[Unit] = {
    val result =
      canDeleteRecordSet(authPrincipal, input.recordName, input.inputChange.typ, input.zone)
    result.leftMap(_ => UserIsNotAuthorized(authPrincipal.userId)).toValidatedNel
  }

  def canGetBatchChange(
      batchChange: BatchChange,
      auth: AuthPrincipal): Either[BatchChangeErrorResponse, Unit] =
    if (auth.signedInUser.isSuper || auth.userId == batchChange.userId) {
      ().asRight
    } else {
      UserNotAuthorizedError(batchChange.id).asLeft
    }
}
// $COVERAGE-ON$
