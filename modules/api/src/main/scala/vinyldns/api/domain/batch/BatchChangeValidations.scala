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

import java.time.Instant
import java.time.temporal.ChronoUnit
import cats.data._
import cats.implicits._
import vinyldns.api.config.{BatchChangeConfig, HighValueDomainConfig, ManualReviewConfig, ScheduledChangesConfig}
import vinyldns.api.domain.DomainValidations._
import vinyldns.api.domain.access.AccessValidationsAlgebra
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.api.domain.batch.BatchChangeInterfaces._
import vinyldns.api.domain.batch.BatchTransformations._
import vinyldns.api.domain.zone.ZoneRecordValidations.isStringInRegexList
import vinyldns.api.domain.zone.ZoneRecordValidations
import vinyldns.core.domain.DomainHelpers.omitTrailingDot
import vinyldns.core.domain.record._
import vinyldns.core.domain._
import vinyldns.core.domain.batch.{BatchChange, BatchChangeApprovalStatus, OwnerType, RecordKey, RecordKeyData}
import vinyldns.core.domain.membership.Group
import vinyldns.core.domain.zone.Zone
import scala.util.matching.Regex

trait BatchChangeValidationsAlgebra {

  def validateBatchChangeInput(
      input: BatchChangeInput,
      existingGroup: Option[Group],
      authPrincipal: AuthPrincipal
  ): BatchResult[Unit]

  def validateInputChanges(
      input: List[ChangeInput],
      isApproved: Boolean
  ): ValidatedBatch[ChangeInput]

  def validateChangesWithContext(
    groupedChanges: ChangeForValidationMap,
    auth: AuthPrincipal,
    isApproved: Boolean,
    batchOwnerGroupId: Option[String]
  ): ValidatedBatch[ChangeForValidation]

  def canGetBatchChange(
      batchChange: BatchChange,
      auth: AuthPrincipal
  ): Either[BatchChangeErrorResponse, Unit]

  def validateBatchChangeRejection(
      batchChange: BatchChange,
      authPrincipal: AuthPrincipal,
      bypassTestValidation: Boolean
  ): Either[BatchChangeErrorResponse, Unit]

  def validateBatchChangeApproval(
      batchChange: BatchChange,
      authPrincipal: AuthPrincipal,
      isTestChange: Boolean
  ): Either[BatchChangeErrorResponse, Unit]

  def validateBatchChangeCancellation(
      batchChange: BatchChange,
      authPrincipal: AuthPrincipal
  ): Either[BatchChangeErrorResponse, Unit]
}

class BatchChangeValidations(
    accessValidation: AccessValidationsAlgebra,
    highValueDomainConfig: HighValueDomainConfig,
    manualReviewConfig: ManualReviewConfig,
    batchChangeConfig: BatchChangeConfig,
    scheduledChangesConfig: ScheduledChangesConfig,
    approvedNameServers: List[Regex]
) extends BatchChangeValidationsAlgebra {

  import RecordType._
  import accessValidation._

  def validateBatchChangeInput(
      input: BatchChangeInput,
      existingGroup: Option[Group],
      authPrincipal: AuthPrincipal
  ): BatchResult[Unit] = {
    val validations = validateBatchChangeInputSize(input) |+| validateOwnerGroupId(
      input.ownerGroupId,
      existingGroup,
      authPrincipal
    )

    for {
      _ <- validations
        .leftMap[BatchChangeErrorResponse](nel => InvalidBatchChangeInput(nel.toList))
        .toEither
        .toBatchResult
      _ <- validateScheduledChange(input, scheduledChangesConfig.enabled).toBatchResult
    } yield ()
  }

  def validateBatchChangeInputSize(input: BatchChangeInput): SingleValidation[Unit] =
    if (input.changes.isEmpty) {
      BatchChangeIsEmpty(batchChangeConfig.limit).invalidNel
    } else if (input.changes.length > batchChangeConfig.limit) {
      ChangeLimitExceeded(batchChangeConfig.limit).invalidNel
    } else {
      ().validNel
    }

  def validateOwnerGroupId(
      ownerGroupId: Option[String],
      existingGroup: Option[Group],
      authPrincipal: AuthPrincipal
  ): SingleValidation[Unit] =
    (ownerGroupId, existingGroup) match {
      case (None, _) => ().validNel
      case (Some(groupId), None) => GroupDoesNotExist(groupId).invalidNel
      case (Some(groupId), Some(_)) =>
        if (authPrincipal.isGroupMember(groupId) || authPrincipal.isSuper) ().validNel
        else NotAMemberOfOwnerGroup(groupId, authPrincipal.signedInUser.userName).invalidNel
    }

  def validateBatchChangeRejection(
      batchChange: BatchChange,
      authPrincipal: AuthPrincipal,
      bypassTestValidation: Boolean
  ): Either[BatchChangeErrorResponse, Unit] =
    validateAuthorizedReviewer(authPrincipal, batchChange, bypassTestValidation) |+| validateBatchChangePendingReview(
      batchChange
    )

  def validateBatchChangeApproval(
      batchChange: BatchChange,
      authPrincipal: AuthPrincipal,
      isTestChange: Boolean
  ): Either[BatchChangeErrorResponse, Unit] =
    validateAuthorizedReviewer(authPrincipal, batchChange, isTestChange) |+| validateBatchChangePendingReview(
      batchChange
    ) |+| validateScheduledApproval(batchChange)

  def validateBatchChangeCancellation(
      batchChange: BatchChange,
      authPrincipal: AuthPrincipal
  ): Either[BatchChangeErrorResponse, Unit] =
    validateBatchChangePendingReview(batchChange) |+| validateCreatorCancellation(
      batchChange,
      authPrincipal
    )

  def validateBatchChangePendingReview(
      batchChange: BatchChange
  ): Either[BatchChangeErrorResponse, Unit] =
    batchChange.approvalStatus match {
      case BatchChangeApprovalStatus.PendingReview => ().asRight
      case _ => BatchChangeNotPendingReview(batchChange.id).asLeft
    }

  def validateAuthorizedReviewer(
      auth: AuthPrincipal,
      batchChange: BatchChange,
      bypassTestValidation: Boolean
  ): Either[BatchChangeErrorResponse, Unit] =
    if (auth.isSystemAdmin && (bypassTestValidation || !auth.isTestUser)) {
      // bypassTestValidation = true for a test change
      ().asRight
    } else {
      UserNotAuthorizedError(batchChange.id).asLeft
    }

  def validateScheduledApproval(batchChange: BatchChange): Either[BatchChangeErrorResponse, Unit] =
    batchChange.scheduledTime match {
      case Some(dt) if dt.isAfter(Instant.now.truncatedTo(ChronoUnit.MILLIS)) => Left(ScheduledChangeNotDue(dt))
      case _ => Right(())
    }

  def validateCreatorCancellation(
      batchChange: BatchChange,
      auth: AuthPrincipal
  ): Either[BatchChangeErrorResponse, Unit] =
    if (batchChange.userId == auth.userId) {
      ().asRight
    } else {
      UserNotAuthorizedError(batchChange.id).asLeft
    }
  /* input validations */

  def validateInputChanges(
      input: List[ChangeInput],
      isApproved: Boolean
  ): ValidatedBatch[ChangeInput] =
    input.map {
      case a: AddChangeInput => validateAddChangeInput(a, isApproved).map(_ => a)
      case d: DeleteRRSetChangeInput => validateDeleteRRSetChangeInput(d, isApproved).map(_ => d)
    }

  def validateAddChangeInput(
      addChangeInput: AddChangeInput,
      isApproved: Boolean
  ): SingleValidation[Unit] = {
    val validTTL = addChangeInput.ttl.map(validateTTL(_).asUnit).getOrElse(().valid)
    val validRecord = validateRecordData(addChangeInput.record, addChangeInput)
    val validInput = validateInputName(addChangeInput, isApproved)

    validTTL |+| validRecord |+| validInput
  }

  def validateDeleteRRSetChangeInput(
    deleteRRSetChangeInput: DeleteRRSetChangeInput,
    isApproved: Boolean
  ): SingleValidation[Unit] = {
    val validRecord = deleteRRSetChangeInput.record match {
      case Some(recordData) => validateRecordData(recordData, deleteRRSetChangeInput)
      case None => ().validNel
    }
    val validInput = validateInputName(deleteRRSetChangeInput, isApproved)

    validRecord |+| validInput
  }

  def validateRecordData(record: RecordData,change: ChangeInput): SingleValidation[Unit] =
    record match {
      case a: AData => validateIpv4Address(a.address).asUnit
      case aaaa: AAAAData => validateIpv6Address(aaaa.address).asUnit
      case cname: CNAMEData =>
        /*
        To validate the zone is reverse
         */
        val isIPv4: Boolean = change.inputName.toLowerCase.endsWith("in-addr.arpa.")
        val isIPv6: Boolean = change.inputName.toLowerCase.endsWith("ip6.arpa.")
        val isReverse: Boolean = isIPv4 || isIPv6
        validateCname(cname.cname,isReverse).asUnit
      case ptr: PTRData => validateHostName(ptr.ptrdname).asUnit
      case txt: TXTData => validateTxtTextLength(txt.text).asUnit
      case mx: MXData =>
        validateMX_NAPTR_SRVData(mx.preference, "preference", "MX").asUnit |+| validateHostName(mx.exchange).asUnit
      case ns: NSData => validateHostName(ns.nsdname).asUnit
      case naptr: NAPTRData => validateMX_NAPTR_SRVData(naptr.preference, "preference", "NAPTR").asUnit |+| validateMX_NAPTR_SRVData(naptr.order, "order", "NAPTR").asUnit |+| validateHostName(naptr.replacement).asUnit |+| validateNaptrFlag(naptr.flags).asUnit |+| validateNaptrRegexp(naptr.regexp).asUnit
      case srv: SRVData => validateMX_NAPTR_SRVData(srv.priority, "priority", "SRV").asUnit |+| validateMX_NAPTR_SRVData(srv.port, "port", "SRV").asUnit |+| validateMX_NAPTR_SRVData(srv.weight, "weight", "SRV").asUnit |+| validateHostName(srv.target).asUnit
      case other =>
        InvalidBatchRecordType(other.toString, SupportedBatchChangeRecordTypes.get).invalidNel[Unit]
    }

  def validateInputName(change: ChangeInput, isApproved: Boolean): SingleValidation[Unit] = {
    val typedChecks = change.typ match {
      case A | AAAA | MX | NS | NAPTR | SRV =>
        validateHostName(change.inputName).asUnit |+| notInReverseZone(change)
      case CNAME | TXT =>
        validateHostName(change.inputName).asUnit
      case PTR =>
        validatePtrIp(change.inputName)
      case other =>
        InvalidBatchRecordType(other.toString, SupportedBatchChangeRecordTypes.get).invalidNel[Unit]
    }
    typedChecks |+| isNotHighValueDomain(change) |+| doesNotRequireManualReview(change, isApproved)
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
    groupedChanges: ChangeForValidationMap,
    auth: AuthPrincipal,
    isApproved: Boolean,
    batchOwnerGroupId: Option[String]
  ): ValidatedBatch[ChangeForValidation] =
  // Updates are a combination of an add and delete for a record with the same name and type in a zone.
    groupedChanges.changes.mapValid {
      case add: AddChangeForValidation
        if groupedChanges
          .getLogicalChangeType(add.recordKey)
          .contains(LogicalChangeType.Add) =>
        validateAddWithContext(add, groupedChanges, auth, isApproved, batchOwnerGroupId)
      case addUpdate: AddChangeForValidation =>
        validateAddUpdateWithContext(addUpdate, groupedChanges, auth, isApproved, batchOwnerGroupId)
      // These cases MUST be below adds because:
      // - order matters
      // - all AddChangeForValidations are covered by this point
      case delete
          if groupedChanges
            .getLogicalChangeType(delete.recordKey)
            .contains(LogicalChangeType.FullDelete) =>
        validateDeleteWithContext(delete, groupedChanges, auth, isApproved)
      case deleteUpdate =>
        validateDeleteUpdateWithContext(deleteUpdate, groupedChanges, auth, isApproved)
    }

  def newRecordSetIsNotDotted(change: AddChangeForValidation): SingleValidation[Unit] =
    if (change.recordName != change.zone.name && change.recordName.contains("."))
      ZoneDiscoveryError(change.inputChange.inputName).invalidNel
    else
      ().validNel

  def matchRecordData(existingRecordSetData: List[RecordData], recordData: RecordData): Boolean = {
    existingRecordSetData.par.exists { rd =>
      rd == recordData
    }
  }

  def ensureRecordExists(
    change: ChangeForValidation,
    groupedChanges: ChangeForValidationMap
  ): Boolean = {
    change match {
      // For DeleteRecord inputs, need to verify that the record data actually exists
      case DeleteRRSetChangeForValidation(_, _, DeleteRRSetChangeInput(_, _, _, Some(recordData)))
        if !groupedChanges
          .getExistingRecordSet(change.recordKey)
          .exists(rs => matchRecordData(rs.records, recordData)) =>
        false
      case _ =>
        true
    }
  }

  def updateSystemMessage(changeInput: ChangeInput, systemMessage: String): ChangeInput = {
    changeInput match {
      case dci: DeleteRRSetChangeInput => dci.copy(systemMessage = Some(systemMessage))
      case _ => changeInput
    }
  }

  def validateDeleteWithContext(
      change: ChangeForValidation,
      groupedChanges: ChangeForValidationMap,
      auth: AuthPrincipal,
      isApproved: Boolean
  ): SingleValidation[ChangeForValidation] = {

    val nonExistentRecordDeleteMessage = "This record does not exist. No further action is required."
    val nonExistentRecordDataDeleteMessage = "Record data entered does not exist. No further action is required."

    val recordData = change match {
      case AddChangeForValidation(_, _, inputChange, _, _) => inputChange.record.toString
      case DeleteRRSetChangeForValidation(_, _, inputChange) => inputChange.record.map(_.toString).getOrElse("")
    }

    val addInBatch = groupedChanges.getProposedAdds(change.recordKey)
    val isSameRecordUpdateInBatch = recordData.nonEmpty && addInBatch.contains(RecordData.fromString(recordData, change.inputChange.typ).get)

    // Perform the system message update based on the condition
    val updatedChange = if (groupedChanges.getExistingRecordSet(change.recordKey).isEmpty && !isSameRecordUpdateInBatch) {
      val updatedChangeInput = updateSystemMessage(change.inputChange, nonExistentRecordDeleteMessage)
      change.withUpdatedInputChange(updatedChangeInput)
    } else if (!ensureRecordExists(change, groupedChanges)) {
      val updatedChangeInput = updateSystemMessage(change.inputChange, nonExistentRecordDataDeleteMessage)
      change.withUpdatedInputChange(updatedChangeInput)
    } else {
      change
    }

    val validations = groupedChanges.getExistingRecordSet(updatedChange.recordKey) match {
      case Some(rs) =>
        userCanDeleteRecordSet(updatedChange, auth, rs.ownerGroupId, rs.records) |+|
          zoneDoesNotRequireManualReview(updatedChange, isApproved)
      case None =>
        if (isSameRecordUpdateInBatch) InvalidUpdateRequest(updatedChange.inputChange.inputName).invalidNel else ().validNel
    }

    validations.map(_ => updatedChange)
  }

  def validateAddUpdateWithContext(
      change: AddChangeForValidation,
      groupedChanges: ChangeForValidationMap,
      auth: AuthPrincipal,
      isApproved: Boolean,
      batchOwnerGroupId: Option[String]
  ): SingleValidation[ChangeForValidation] = {
    // Updates require checking against other batch changes since multiple adds
    // could potentially be grouped with a single delete
    val typedValidations = change.inputChange.typ match {
      case CNAME => recordIsUniqueInBatch(change, groupedChanges)
      case _ => ().validNel
    }

    val commonValidations: SingleValidation[Unit] = {
      groupedChanges.getExistingRecordSet(change.recordKey) match {
        case Some(rs) =>
          userCanUpdateRecordSet(change, auth, rs.ownerGroupId, List(change.inputChange.record)) |+|
            ownerGroupProvidedIfNeeded(
              change,
              groupedChanges.existingRecordSets
                .get(change.zone.id, change.recordName, change.inputChange.typ),
              batchOwnerGroupId
            ) |+|
            zoneDoesNotRequireManualReview(change, isApproved)
        case None =>
          InvalidUpdateRequest(change.inputChange.inputName).invalidNel
      }
    }

    val validations = typedValidations |+| commonValidations

    validations.map(_ => change)
  }

  def validateDeleteUpdateWithContext(
      change: ChangeForValidation,
      groupedChanges: ChangeForValidationMap,
      auth: AuthPrincipal,
      isApproved: Boolean
  ): SingleValidation[ChangeForValidation] = {

    val nonExistentRecordDeleteMessage = "This record does not exist. No further action is required."
    val nonExistentRecordDataDeleteMessage = "Record data entered does not exist. No further action is required."

    // To handle add and delete for the record with same record data is present in the batch
    val recordData = change match {
      case AddChangeForValidation(_, _, inputChange, _, _) => inputChange.record.toString
      case DeleteRRSetChangeForValidation(_, _, inputChange) => inputChange.record.map(_.toString).getOrElse("")
    }

    val addInBatch = groupedChanges.getProposedAdds(change.recordKey)
    val isSameRecordUpdateInBatch = recordData.nonEmpty && addInBatch.contains(RecordData.fromString(recordData, change.inputChange.typ).get)

    // Perform the system message update based on the condition
    val updatedChange = if (groupedChanges.getExistingRecordSet(change.recordKey).isEmpty && !isSameRecordUpdateInBatch) {
      val updatedChangeInput = updateSystemMessage(change.inputChange, nonExistentRecordDeleteMessage)
      change.withUpdatedInputChange(updatedChangeInput)
    } else if (!ensureRecordExists(change, groupedChanges)) {
      val updatedChangeInput = updateSystemMessage(change.inputChange, nonExistentRecordDataDeleteMessage)
      change.withUpdatedInputChange(updatedChangeInput)
    } else {
      change
    }

    val validations =
      groupedChanges.getExistingRecordSet(updatedChange.recordKey) match {
        case Some(rs) =>
          val adds = groupedChanges.getProposedAdds(updatedChange.recordKey).toList
          userCanUpdateRecordSet(updatedChange, auth, rs.ownerGroupId, adds) |+|
            zoneDoesNotRequireManualReview(updatedChange, isApproved)
        case None =>
          if(isSameRecordUpdateInBatch) InvalidUpdateRequest(updatedChange.inputChange.inputName).invalidNel else ().validNel
      }

    validations.map(_ => updatedChange)
  }

  def validateAddWithContext(
    change: AddChangeForValidation,
    groupedChanges: ChangeForValidationMap,
    auth: AuthPrincipal,
    isApproved: Boolean,
    ownerGroupId: Option[String]
  ): SingleValidation[ChangeForValidation] = {
    val typedValidations = change.inputChange.typ match {
      case A | AAAA | MX | SRV | NAPTR =>
        newRecordSetIsNotDotted(change)
      case NS =>
        newRecordSetIsNotDotted(change) |+| nsValidations(change.inputChange.record, change.recordName, change.zone, approvedNameServers)
      case CNAME =>
        cnameHasUniqueNameInBatch(change, groupedChanges) |+|
          newRecordSetIsNotDotted(change)
      case TXT | PTR =>
        ().validNel
      case other =>
        InvalidBatchRecordType(other.toString, SupportedBatchChangeRecordTypes.get).invalidNel
    }

    // To handle add and delete for the record with same record data is present in the batch
    val recordData = change match {
      case AddChangeForValidation(_, _, inputChange, _, _) => inputChange.record.toString
    }

    val deletes = groupedChanges.getProposedDeletes(change.recordKey)
    val isDeleteExists = deletes.nonEmpty
    val isSameRecordUpdateInBatch = if(recordData.nonEmpty){
      if(deletes.contains(RecordData.fromString(recordData, change.inputChange.typ).get)) true else false
    } else false

    val commonValidations: SingleValidation[Unit] = {
      groupedChanges.getExistingRecordSet(change.recordKey) match {
        case Some(_) =>
          ().validNel
        case None =>
          if(isSameRecordUpdateInBatch) InvalidUpdateRequest(change.inputChange.inputName).invalidNel else ().validNel
      }
    }

    val validations =
      typedValidations |+|
        commonValidations |+|
        noIncompatibleRecordExists(change, groupedChanges) |+|
        userCanAddRecordSet(change, auth) |+|
        recordDoesNotExist(
          change.zone.id,
          change.recordName,
          change.inputChange.inputName,
          change.inputChange.typ,
          change.inputChange.record,
          groupedChanges,
          isDeleteExists
        ) |+|
        ownerGroupProvidedIfNeeded(change, None, ownerGroupId) |+|
        zoneDoesNotRequireManualReview(change, isApproved)
    validations.map(_ => change)
  }

  def cnameHasUniqueNameInBatch(
      cnameChange: AddChangeForValidation,
      groupedChanges: ChangeForValidationMap
  ): SingleValidation[Unit] = {

    val duplicateNameChangeInBatch = RecordType.values.toList.exists { recordType =>
      val recordKey = RecordKey(cnameChange.zone.id, cnameChange.recordName, recordType)
      val proposedAdds = groupedChanges.getProposedAdds(recordKey)

      proposedAdds.exists(_ != cnameChange.inputChange.record)
    }

    if (duplicateNameChangeInBatch) {
      RecordNameNotUniqueInBatch(cnameChange.inputChange.inputName, CNAME).invalidNel
    } else ().validNel
  }

  def recordIsUniqueInBatch(
      change: AddChangeForValidation,
      groupedChanges: ChangeForValidationMap
  ): SingleValidation[Unit] = {
    // Ignore true duplicates, but identify multi-records
    val proposedAdds = groupedChanges.getProposedAdds(change.recordKey)

    if (proposedAdds.size > 1) {
      RecordNameNotUniqueInBatch(change.inputChange.inputName, change.inputChange.typ).invalidNel
    } else ().validNel
  }

  def recordDoesNotExist(
      zoneId: String,
      recordName: String,
      inputName: String,
      typ: RecordType,
      recordData: RecordData,
      groupedChanges: ChangeForValidationMap,
      isDeleteExist: Boolean
  ): SingleValidation[Unit] = {
    val record = groupedChanges.getExistingRecordSetData(RecordKeyData(zoneId, recordName, typ, recordData))
    if(record.isDefined) {
      record.get.records.contains(recordData) match {
        case true => ().validNel
        case false => if(isDeleteExist) ().validNel else RecordAlreadyExists(inputName).invalidNel
      }
    } else ().validNel
    }

  def noIncompatibleRecordExists(
      change: AddChangeForValidation,
      groupedChanges: ChangeForValidationMap
  ): SingleValidation[Unit] = {
    // find conflicting types in existing records
    val conflictingExistingTypes = change.inputChange.typ match {
      case CNAME =>
        groupedChanges.existingRecordSets
          .getRecordSetMatch(change.zone.id, change.recordName)
          .map(_.typ)
      case _ =>
        groupedChanges
          .getExistingRecordSet(RecordKey(change.zone.id, change.recordName, CNAME))
          .map(_.typ)
          .toList
    }

    // find one that isnt being deleted
    val nonDelete = conflictingExistingTypes.find { recordType =>
      groupedChanges
        .getProposedRecordData(RecordKey(change.zone.id, change.recordName, recordType))
        .nonEmpty
    }

    nonDelete match {
      case Some(recordType) =>
        CnameIsNotUniqueError(change.inputChange.inputName, recordType).invalidNel
      case None => ().validNel
    }
  }

  def userCanAddRecordSet(
      input: AddChangeForValidation,
      authPrincipal: AuthPrincipal
  ): SingleValidation[Unit] = {
    val result = canAddRecordSet(
      authPrincipal,
      input.recordName,
      input.inputChange.typ,
      input.zone,
      List(input.inputChange.record)
    )
    result
      .leftMap(
        _ =>
          UserIsNotAuthorizedError(
            authPrincipal.signedInUser.userName,
            input.zone.adminGroupId,
            OwnerType.Zone,
            Some(input.zone.email)
          )
      )
      .toValidatedNel
  }

  def userCanUpdateRecordSet(
      input: ChangeForValidation,
      authPrincipal: AuthPrincipal,
      ownerGroupId: Option[String],
      addRecords: List[RecordData]
  ): SingleValidation[Unit] = {
    val result =
      canUpdateRecordSet(
        authPrincipal,
        input.recordName,
        input.inputChange.typ,
        input.zone,
        ownerGroupId,
        false,
        addRecords
      )
    result
      .leftMap(
        _ =>
          ownerGroupId match {
            case Some(id) if input.zone.shared =>
              UserIsNotAuthorizedError(authPrincipal.signedInUser.userName, id, OwnerType.Record)
            case _ =>
              UserIsNotAuthorizedError(
                authPrincipal.signedInUser.userName,
                input.zone.adminGroupId,
                OwnerType.Zone,
                Some(input.zone.email)
              )
          }
      )
      .toValidatedNel
  }

  def userCanDeleteRecordSet(
      input: ChangeForValidation,
      authPrincipal: AuthPrincipal,
      ownerGroupId: Option[String],
      existingRecords: List[RecordData]
  ): SingleValidation[Unit] = {
    val result =
      canDeleteRecordSet(
        authPrincipal,
        input.recordName,
        input.inputChange.typ,
        input.zone,
        ownerGroupId,
        existingRecords
      )
    result
      .leftMap(
        _ =>
          ownerGroupId match {
            case Some(id) if input.zone.shared =>
              UserIsNotAuthorizedError(authPrincipal.signedInUser.userName, id, OwnerType.Record)
            case _ =>
              UserIsNotAuthorizedError(
                authPrincipal.signedInUser.userName,
                input.zone.adminGroupId,
                OwnerType.Zone,
                Some(input.zone.email)
              )
          }
      )
      .toValidatedNel
  }

  def canGetBatchChange(
      batchChange: BatchChange,
      auth: AuthPrincipal
  ): Either[BatchChangeErrorResponse, Unit] =
    if (auth.isSystemAdmin || auth.userId == batchChange.userId) {
      ().asRight
    } else {
      UserNotAuthorizedError(batchChange.id).asLeft
    }

  def isNotHighValueDomain(change: ChangeInput): SingleValidation[Unit] =
    change.typ match {
      case RecordType.PTR =>
        ZoneRecordValidations.isNotHighValueIp(
          highValueDomainConfig.ipList,
          change.inputName
        )
      case _ =>
        ZoneRecordValidations.isNotHighValueFqdn(
          highValueDomainConfig.fqdnRegexes,
          change.inputName
        )
    }

  def doesNotRequireManualReview(change: ChangeInput, isApproved: Boolean): SingleValidation[Unit] =
    if (isApproved) {
      // If we are reviewing, don't need to check whether DNS change needs review
      ().validNel
    } else {
      change.typ match {
        case RecordType.PTR =>
          ZoneRecordValidations.ipDoesNotRequireManualReview(
            manualReviewConfig.ipList,
            change.inputName
          )
        case _ =>
          ZoneRecordValidations.domainDoesNotRequireManualReview(
            manualReviewConfig.domainList,
            change.inputName
          )
      }
    }

  def ownerGroupProvidedIfNeeded(
      change: AddChangeForValidation,
      existingRecord: Option[RecordSet],
      ownerGroupId: Option[String]
  ): SingleValidation[Unit] =
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
      scheduledChangesEnabled: Boolean
  ): Either[BatchChangeErrorResponse, Unit] =
    (scheduledChangesEnabled, input.scheduledTime) match {
      case (_, None) => Right(())
      case (true, Some(scheduledTime)) if scheduledTime.isAfter(Instant.now.truncatedTo(ChronoUnit.MILLIS)) => Right(())
      case (true, _) => Left(ScheduledTimeMustBeInFuture)
      case (false, _) => Left(ScheduledChangesDisabled)
    }

  def zoneDoesNotRequireManualReview(
      change: ChangeForValidation,
      isApproved: Boolean
  ): SingleValidation[Unit] =
    if (isApproved) {
      ().validNel
    } else {
      ZoneRecordValidations.zoneDoesNotRequireManualReview(
        manualReviewConfig.zoneList,
        change.zone.name,
        change.inputChange.inputName
      )
    }

  def nsValidations(
     newRecordSetData: RecordData,
     newRecordSetName: String,
     zone: Zone,
     approvedNameServers: List[Regex]
  ): SingleValidation[Unit] = {

      isNotOrigin(
        newRecordSetName,
        zone,
        s"Record with name $newRecordSetName is an NS record at apex and cannot be added"
      )
      containsApprovedNameServers(newRecordSetData, approvedNameServers)
  }

  def isNotOrigin(recordSet: String, zone: Zone, err: String): SingleValidation[Unit] =
    if(!isOriginRecord(recordSet, omitTrailingDot(zone.name))) ().validNel else InvalidBatchRequest(err).invalidNel

  def isOriginRecord(recordSetName: String, zoneName: String): Boolean =
    recordSetName == "@" || omitTrailingDot(recordSetName) == omitTrailingDot(zoneName)

  def containsApprovedNameServers(
     nsRecordSet: RecordData,
     approvedNameServers: List[Regex]
  ): SingleValidation[Unit] = {
    val nsData = nsRecordSet match {
      case ns: NSData => ns
      case _ => ??? // this would never be the case
    }
    isApprovedNameServer(approvedNameServers, nsData)
  }

  def isApprovedNameServer(
    approvedServerList: List[Regex],
    nsData: NSData
  ): SingleValidation[Unit] =
    if (isStringInRegexList(approvedServerList, nsData.nsdname.fqdn)) {
      ().validNel
    } else {
      NotApprovedNSError(nsData.nsdname.fqdn).invalidNel
    }
}
