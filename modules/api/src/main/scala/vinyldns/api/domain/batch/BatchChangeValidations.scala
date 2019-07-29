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

import cats.data._
import cats.implicits._
import vinyldns.api.VinylDNSConfig
import vinyldns.api.domain.DomainValidations._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.api.domain.batch.BatchChangeInterfaces._
import vinyldns.api.domain.batch.BatchTransformations._
import vinyldns.api.domain.zone.ZoneRecordValidations
import vinyldns.core.domain.record._
import vinyldns.api.domain.AccessValidationAlgebra
import vinyldns.core.domain._
import vinyldns.core.domain.batch.{BatchChange, BatchChangeApprovalStatus, RecordKey}
import vinyldns.core.domain.membership.Group

trait BatchChangeValidationsAlgebra {

  def validateBatchChangeInput(
      input: BatchChangeInput,
      existingGroup: Option[Group],
      authPrincipal: AuthPrincipal): BatchResult[Unit]

  def validateInputChanges(input: List[ChangeInput]): ValidatedBatch[ChangeInput]

  def validateChangesWithContext(
      changes: ValidatedBatch[ChangeForValidation],
      existingRecords: ExistingRecordSets,
      auth: AuthPrincipal,
      batchOwnerGroupId: Option[String]): ValidatedBatch[ChangeForValidation]

  def canGetBatchChange(
      batchChange: BatchChange,
      auth: AuthPrincipal): Either[BatchChangeErrorResponse, Unit]

  def validateBatchChangeRejection(
      batchChange: BatchChange,
      authPrincipal: AuthPrincipal): Either[BatchChangeErrorResponse, Unit]

  def validateBatchChangeApproval(
      batchChange: BatchChange,
      authPrincipal: AuthPrincipal): Either[BatchChangeErrorResponse, Unit]
}

class BatchChangeValidations(
    changeLimit: Int,
    accessValidation: AccessValidationAlgebra,
    multiRecordEnabled: Boolean = false,
    scheduledChangesEnabled: Boolean = false)
    extends BatchChangeValidationsAlgebra {

  import RecordType._
  import accessValidation._

  def validateBatchChangeInput(
      input: BatchChangeInput,
      existingGroup: Option[Group],
      authPrincipal: AuthPrincipal): BatchResult[Unit] = {
    val validations = validateBatchChangeInputSize(input) |+| validateOwnerGroupId(
      input.ownerGroupId,
      existingGroup,
      authPrincipal)

    for {
      _ <- validations
        .leftMap[BatchChangeErrorResponse](nel => InvalidBatchChangeInput(nel.toList))
        .toEither
        .toBatchResult
      _ <- validateScheduledChange(input, scheduledChangesEnabled).toBatchResult
    } yield ()
  }

  def validateBatchChangeInputSize(input: BatchChangeInput): SingleValidation[Unit] =
    if (input.changes.isEmpty) {
      BatchChangeIsEmpty(changeLimit).invalidNel
    } else if (input.changes.length > changeLimit) {
      ChangeLimitExceeded(changeLimit).invalidNel
    } else {
      ().validNel
    }

  def validateOwnerGroupId(
      ownerGroupId: Option[String],
      existingGroup: Option[Group],
      authPrincipal: AuthPrincipal): SingleValidation[Unit] =
    (ownerGroupId, existingGroup) match {
      case (None, _) => ().validNel
      case (Some(groupId), None) => GroupDoesNotExist(groupId).invalidNel
      case (Some(groupId), Some(_)) =>
        if (authPrincipal.isGroupMember(groupId) || authPrincipal.isSuper) ().validNel
        else NotAMemberOfOwnerGroup(groupId, authPrincipal.signedInUser.userName).invalidNel
    }

  def validateBatchChangeRejection(
      batchChange: BatchChange,
      authPrincipal: AuthPrincipal): Either[BatchChangeErrorResponse, Unit] =
    validateAuthorizedReviewer(authPrincipal, batchChange) |+| validateBatchChangePendingReview(
      batchChange)

  def validateBatchChangeApproval(
      batchChange: BatchChange,
      authPrincipal: AuthPrincipal): Either[BatchChangeErrorResponse, Unit] =
    validateAuthorizedReviewer(authPrincipal, batchChange) |+| validateBatchChangePendingReview(
      batchChange)

  def validateBatchChangePendingReview(
      batchChange: BatchChange): Either[BatchChangeErrorResponse, Unit] =
    batchChange.approvalStatus match {
      case BatchChangeApprovalStatus.PendingReview => ().asRight
      case _ => BatchChangeNotPendingReview(batchChange.id).asLeft
    }

  def validateAuthorizedReviewer(
      auth: AuthPrincipal,
      batchChange: BatchChange): Either[BatchChangeErrorResponse, Unit] =
    if (auth.isSystemAdmin) {
      ().asRight
    } else {
      UserNotAuthorizedError(batchChange.id).asLeft
    }

  /* input validations */

  def validateInputChanges(input: List[ChangeInput]): ValidatedBatch[ChangeInput] =
    input.map {
      case a: AddChangeInput => validateAddChangeInput(a).map(_ => a)
      case d: DeleteChangeInput => validateInputName(d).map(_ => d)
    }

  def validateAddChangeInput(addChangeInput: AddChangeInput): SingleValidation[Unit] = {
    val validTTL = addChangeInput.ttl.map(validateTTL(_).asUnit).getOrElse(().valid)
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
      case other =>
        InvalidBatchRecordType(other.toString, SupportedBatchChangeRecordTypes.get).invalidNel[Unit]
    }

  def validateInputName(change: ChangeInput): SingleValidation[Unit] = {
    val typedChecks = change.typ match {
      case A | AAAA | MX =>
        validateHostName(change.inputName).asUnit |+| notInReverseZone(change)
      case CNAME | TXT =>
        validateHostName(change.inputName).asUnit
      case PTR =>
        validatePtrIp(change.inputName)
      case other =>
        InvalidBatchRecordType(other.toString, SupportedBatchChangeRecordTypes.get).invalidNel[Unit]
    }
    typedChecks |+| isNotHighValueDomain(change)
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
      auth: AuthPrincipal,
      batchOwnerGroupId: Option[String]): ValidatedBatch[ChangeForValidation] = {
    val changeGroups = ChangeForValidationMap(changes.getValid)

    // Updates are a combination of an add and delete for a record with the same name and type in a zone.
    changes.mapValid {
      case addUpdate: AddChangeForValidation
          if changeGroups.containsDeleteChangeForValidation(addUpdate.recordKey) =>
        validateAddUpdateWithContext(
          addUpdate,
          changeGroups,
          existingRecords,
          auth,
          batchOwnerGroupId)
      case add: AddChangeForValidation =>
        validateAddWithContext(add, changeGroups, existingRecords, auth, batchOwnerGroupId)
      case deleteUpdate: DeleteChangeForValidation
          if changeGroups.containsAddChangeForValidation(deleteUpdate.recordKey) =>
        validateDeleteUpdateWithContext(deleteUpdate, existingRecords, auth)
      case del: DeleteChangeForValidation => validateDeleteWithContext(del, existingRecords, auth)
    }
  }

  def existingRecordSetIsNotMulti(
      change: ChangeForValidation,
      recordSet: RecordSet): SingleValidation[Unit] =
    if (!multiRecordEnabled & recordSet.records.length > 1) {
      ExistingMultiRecordError(change.inputChange.inputName, recordSet).invalidNel
    } else ().validNel

  def newRecordSetIsNotMulti(
      change: AddChangeForValidation,
      changeGroups: ChangeForValidationMap): SingleValidation[Unit] = {
    lazy val matchingAddRecords = changeGroups
      .getList(change.recordKey)
      .collect {
        case add: AddChangeForValidation => add
      }
    if (!multiRecordEnabled && matchingAddRecords.length > 1)
      NewMultiRecordError(change.inputChange.inputName, change.inputChange.typ).invalidNel
    else ().validNel
  }

  def newRecordSetIsNotDotted(change: AddChangeForValidation): SingleValidation[Unit] =
    if (change.recordName != change.zone.name && change.recordName.contains("."))
      ZoneDiscoveryError(change.inputChange.inputName).invalidNel
    else
      ().validNel

  def validateDeleteWithContext(
      change: DeleteChangeForValidation,
      existingRecords: ExistingRecordSets,
      auth: AuthPrincipal): SingleValidation[ChangeForValidation] = {
    val validations =
      existingRecords.get(change.zone.id, change.recordName, change.inputChange.typ) match {
        case Some(rs) =>
          userCanDeleteRecordSet(change, auth, rs.ownerGroupId) |+|
            existingRecordSetIsNotMulti(change, rs)
        case None => RecordDoesNotExist(change.inputChange.inputName).invalidNel
      }
    validations.map(_ => change)
  }

  def validateAddUpdateWithContext(
      change: AddChangeForValidation,
      changeGroups: ChangeForValidationMap,
      existingRecordSets: ExistingRecordSets,
      auth: AuthPrincipal,
      batchOwnerGroupId: Option[String]): SingleValidation[ChangeForValidation] = {
    // Updates require checking against other batch changes since multiple adds
    // could potentially be grouped with a single delete
    val typedValidations = change.inputChange.typ match {
      case CNAME => recordIsUniqueInBatch(change, changeGroups)
      case _ => newRecordSetIsNotMulti(change, changeGroups)
    }

    val authAndOwnerGroupValidations: SingleValidation[Unit] =
      existingRecordSets.get(change.recordKey) match {
        case Some(rs) =>
          userCanUpdateRecordSet(change, auth, rs.ownerGroupId) |+|
            ownerGroupProvidedIfNeeded(
              change,
              existingRecordSets.get(change.zone.id, change.recordName, change.inputChange.typ),
              batchOwnerGroupId) |+|
            existingRecordSetIsNotMulti(change, rs)
        case None =>
          RecordDoesNotExist(change.inputChange.inputName).invalidNel
      }

    val validations = typedValidations |+| authAndOwnerGroupValidations

    validations.map(_ => change)
  }

  def validateDeleteUpdateWithContext(
      change: DeleteChangeForValidation,
      existingRecords: ExistingRecordSets,
      auth: AuthPrincipal): SingleValidation[ChangeForValidation] = {
    val validations =
      existingRecords.get(change.zone.id, change.recordName, change.inputChange.typ) match {
        case Some(rs) =>
          userCanUpdateRecordSet(change, auth, rs.ownerGroupId) |+|
            existingRecordSetIsNotMulti(change, rs)
        case None => RecordDoesNotExist(change.inputChange.inputName).invalidNel
      }

    validations.map(_ => change)
  }

  def validateAddWithContext(
      change: AddChangeForValidation,
      changeGroups: ChangeForValidationMap,
      existingRecords: ExistingRecordSets,
      auth: AuthPrincipal,
      ownerGroupId: Option[String]): SingleValidation[ChangeForValidation] = {
    val typedValidations = change.inputChange.typ match {
      case A | AAAA | MX =>
        noCnameWithRecordNameInExistingRecords(
          change.zone.id,
          change.recordName,
          change.inputChange.inputName,
          existingRecords,
          changeGroups) |+|
          newRecordSetIsNotMulti(change, changeGroups) |+|
          newRecordSetIsNotDotted(change)
      case CNAME =>
        cnameHasUniqueNameInExistingRecords(
          change.zone.id,
          change.recordName,
          change.inputChange.inputName,
          existingRecords,
          changeGroups) |+|
          cnameHasUniqueNameInBatch(change, changeGroups) |+|
          newRecordSetIsNotDotted(change)
      case TXT | PTR =>
        noCnameWithRecordNameInExistingRecords(
          change.zone.id,
          change.recordName,
          change.inputChange.inputName,
          existingRecords,
          changeGroups) |+|
          newRecordSetIsNotMulti(change, changeGroups)
      case other =>
        InvalidBatchRecordType(other.toString, SupportedBatchChangeRecordTypes.get).invalidNel
    }

    val validations =
      typedValidations |+|
        userCanAddRecordSet(change, auth) |+|
        recordDoesNotExist(
          change.zone.id,
          change.recordName,
          change.inputChange.inputName,
          change.inputChange.typ,
          existingRecords) |+|
        ownerGroupProvidedIfNeeded(change, None, ownerGroupId)

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
      .getList(change.recordKey)
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

  def userCanAddRecordSet(
      input: ChangeForValidation,
      authPrincipal: AuthPrincipal): SingleValidation[Unit] = {
    val result = canAddRecordSet(authPrincipal, input.recordName, input.inputChange.typ, input.zone)
    result.leftMap(_ => UserIsNotAuthorized(authPrincipal.signedInUser.userName)).toValidatedNel
  }

  def userCanUpdateRecordSet(
      input: ChangeForValidation,
      authPrincipal: AuthPrincipal,
      ownerGroupId: Option[String]): SingleValidation[Unit] = {
    val result =
      canUpdateRecordSet(
        authPrincipal,
        input.recordName,
        input.inputChange.typ,
        input.zone,
        ownerGroupId
      )
    result.leftMap(_ => UserIsNotAuthorized(authPrincipal.signedInUser.userName)).toValidatedNel
  }

  def userCanDeleteRecordSet(
      input: ChangeForValidation,
      authPrincipal: AuthPrincipal,
      ownerGroupId: Option[String]): SingleValidation[Unit] = {
    val result =
      canDeleteRecordSet(
        authPrincipal,
        input.recordName,
        input.inputChange.typ,
        input.zone,
        ownerGroupId)
    result.leftMap(_ => UserIsNotAuthorized(authPrincipal.signedInUser.userName)).toValidatedNel
  }

  def canGetBatchChange(
      batchChange: BatchChange,
      auth: AuthPrincipal): Either[BatchChangeErrorResponse, Unit] =
    if (auth.isSystemAdmin || auth.userId == batchChange.userId) {
      ().asRight
    } else {
      UserNotAuthorizedError(batchChange.id).asLeft
    }

  def isNotHighValueDomain(change: ChangeInput): SingleValidation[Unit] =
    change.typ match {
      case RecordType.PTR =>
        ZoneRecordValidations.isNotHighValueIp(VinylDNSConfig.highValueIpList, change.inputName)
      case _ =>
        ZoneRecordValidations.isNotHighValueFqdn(
          VinylDNSConfig.highValueRegexList,
          change.inputName)
    }

  def ownerGroupProvidedIfNeeded(
      change: AddChangeForValidation,
      existingRecord: Option[RecordSet],
      ownerGroupId: Option[String]): SingleValidation[Unit] =
    if (!change.zone.shared || ownerGroupId.isDefined) {
      ().validNel
    } else {
      existingRecord match {
        case Some(rs) if rs.ownerGroupId.isDefined => ().validNel
        case _ =>
          // rs exists without owner group, or this is a create
          MissingOwnerGroupId(change.recordName, change.zone.name).invalidNel
      }
    }

  def validateScheduledChange(
      input: BatchChangeInput,
      scheduledChangesEnabled: Boolean): Either[BatchChangeErrorResponse, Unit] =
    if (scheduledChangesEnabled || input.scheduledTime.isEmpty) Right(())
    else Left(ScheduledChangesDisabled)
}
