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

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.effect._
import cats.implicits._

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.api.domain.DomainValidations._
import vinyldns.api.domain.auth.AuthPrincipalProvider
import vinyldns.api.domain.batch.BatchChangeInterfaces._
import vinyldns.api.domain.batch.BatchTransformations._
import vinyldns.api.backend.dns.DnsConversions._
import vinyldns.api.domain.membership.MembershipService
import vinyldns.api.repository.ApiDataAccessor
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.batch.BatchChangeApprovalStatus.BatchChangeApprovalStatus
import vinyldns.core.domain.batch._
import vinyldns.core.domain.batch.BatchChangeApprovalStatus._
import vinyldns.core.domain.batch.BatchChangeStatus.BatchChangeStatus
import vinyldns.core.domain.{CnameAtZoneApexError, SingleChangeError, UserIsNotAuthorizedError, ZoneDiscoveryError}
import vinyldns.core.domain.membership.{Group, GroupRepository, ListUsersResults, User, UserRepository}
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.record.RecordSetRepository
import vinyldns.core.domain.zone.ZoneRepository
import vinyldns.core.notifier.{AllNotifiers, Notification}

object BatchChangeService {
  def apply(
      dataAccessor: ApiDataAccessor,
      membershipService: MembershipService,
      batchChangeValidations: BatchChangeValidationsAlgebra,
      batchChangeConverter: BatchChangeConverterAlgebra,
      manualReviewEnabled: Boolean,
      authProvider: AuthPrincipalProvider,
      notifiers: AllNotifiers,
      scheduledChangesEnabled: Boolean,
      v6DiscoveryNibbleBoundaries: V6DiscoveryNibbleBoundaries,
      defaultTtl: Long
  ): BatchChangeService =
    new BatchChangeService(
      dataAccessor.zoneRepository,
      dataAccessor.recordSetRepository,
      membershipService,
      dataAccessor.groupRepository,
      batchChangeValidations,
      dataAccessor.batchChangeRepository,
      batchChangeConverter,
      dataAccessor.userRepository,
      manualReviewEnabled,
      authProvider,
      notifiers,
      scheduledChangesEnabled,
      v6DiscoveryNibbleBoundaries,
      defaultTtl
    )
}

class BatchChangeService(
    zoneRepository: ZoneRepository,
    recordSetRepository: RecordSetRepository,
    membershipService: MembershipService,
    groupRepository: GroupRepository,
    batchChangeValidations: BatchChangeValidationsAlgebra,
    batchChangeRepo: BatchChangeRepository,
    batchChangeConverter: BatchChangeConverterAlgebra,
    userRepository: UserRepository,
    manualReviewEnabled: Boolean,
    authProvider: AuthPrincipalProvider,
    notifiers: AllNotifiers,
    scheduledChangesEnabled: Boolean,
    v6zoneNibbleBoundaries: V6DiscoveryNibbleBoundaries,
    defaultTtl: Long
) extends BatchChangeServiceAlgebra {

  import batchChangeValidations._

  val logger: Logger = LoggerFactory.getLogger(classOf[BatchChangeService])
  def applyBatchChange(
      batchChangeInput: BatchChangeInput,
      auth: AuthPrincipal,
      allowManualReview: Boolean
  ): BatchResult[BatchChange] =
    for {
      validationOutput <- applyBatchChangeValidationFlow(batchChangeInput, auth, isApproved = false)
      changeForConversion <- buildResponse(
        batchChangeInput,
        validationOutput.validatedChanges,
        auth,
        allowManualReview
      ).toBatchResult
      serviceCompleteBatch <- convertOrSave(
        changeForConversion,
        validationOutput.existingZones,
        validationOutput.groupedChanges,
        batchChangeInput.ownerGroupId
      )
    } yield serviceCompleteBatch

  def applyBatchChangeValidationFlow(
      batchChangeInput: BatchChangeInput,
      auth: AuthPrincipal,
      isApproved: Boolean
  ): BatchResult[BatchValidationFlowOutput] =
    for {
      existingGroup <- getOwnerGroup(batchChangeInput.ownerGroupId)
      _ <- validateBatchChangeInput(batchChangeInput, existingGroup, auth)
      inputValidatedSingleChanges = validateInputChanges(batchChangeInput.changes, isApproved)
      zoneMap <- getZonesForRequest(inputValidatedSingleChanges).toBatchResult
      changesWithZones = zoneDiscovery(inputValidatedSingleChanges, zoneMap)
      recordSets <- getExistingRecordSets(changesWithZones, zoneMap).toBatchResult
      withTtl = doTtlMapping(changesWithZones, recordSets)
      groupedChanges = ChangeForValidationMap(withTtl, recordSets)
      validatedSingleChanges = validateChangesWithContext(
        groupedChanges,
        auth,
        isApproved,
        batchChangeInput.ownerGroupId
      )
      errorGroupIds <- getGroupIdsFromUnauthorizedErrors(validatedSingleChanges)
      validatedSingleChangesWithGroups = errorGroupMapping(errorGroupIds, validatedSingleChanges)
    } yield BatchValidationFlowOutput(validatedSingleChangesWithGroups, zoneMap, groupedChanges)

  def getGroupIdsFromUnauthorizedErrors(
      changes: ValidatedBatch[ChangeForValidation]
  ): BatchResult[Set[Group]] = {
    val list = changes.getInvalid.collect {
      case d: UserIsNotAuthorizedError => d.ownerGroupId
    }.toSet
    groupRepository.getGroups(list).toBatchResult
  }

  def errorGroupMapping(
      groups: Set[Group],
      validations: ValidatedBatch[ChangeForValidation]
  ): ValidatedBatch[ChangeForValidation] =
    validations.map {
      case Invalid(err) =>
        err.map {
          case e: UserIsNotAuthorizedError =>
            val group = groups.find(_.id == e.ownerGroupId)
            val updatedError = UserIsNotAuthorizedError(
              e.userName,
              e.ownerGroupId,
              e.ownerType,
              group.map(_.email),
              group.map(_.name)
            )
            logger.error(updatedError.message)
            updatedError
          case e =>
            logger.error(e.message)
            e
        }.invalid
      case valid => valid
    }

  def rejectBatchChange(
      batchChangeId: String,
      authPrincipal: AuthPrincipal,
      rejectBatchChangeInput: RejectBatchChangeInput
  ): BatchResult[BatchChange] =
    for {
      batchChange <- getExistingBatchChange(batchChangeId)
      bypassTestCheck <- getBypassTestCheckForReject(authPrincipal, batchChange)
      _ <- validateBatchChangeRejection(batchChange, authPrincipal, bypassTestCheck).toBatchResult
      rejectedBatchChange <- rejectBatchChange(
        batchChange,
        rejectBatchChangeInput.reviewComment,
        authPrincipal.signedInUser.id
      )
      _ <- notifiers.notify(Notification(rejectedBatchChange)).toBatchResult
    } yield rejectedBatchChange

  def approveBatchChange(
      batchChangeId: String,
      authPrincipal: AuthPrincipal,
      approveBatchChangeInput: ApproveBatchChangeInput
  ): BatchResult[BatchChange] =
    for {
      batchChange <- getExistingBatchChange(batchChangeId)
      requesterAuth <- EitherT.fromOptionF(
        authProvider.getAuthPrincipalByUserId(batchChange.userId),
        BatchRequesterNotFound(batchChange.userId, batchChange.userName)
      )
      _ <- validateBatchChangeApproval(batchChange, authPrincipal, requesterAuth.isTestUser).toBatchResult
      asInput = BatchChangeInput(batchChange)
      reviewInfo = BatchChangeReviewInfo(
        authPrincipal.userId,
        approveBatchChangeInput.reviewComment
      )
      validationOutput <- applyBatchChangeValidationFlow(asInput, requesterAuth, isApproved = true)
      changeForConversion = rebuildBatchChangeForUpdate(
        batchChange,
        validationOutput.validatedChanges,
        reviewInfo
      )
      serviceCompleteBatch <- convertOrSave(
        changeForConversion,
        validationOutput.existingZones,
        validationOutput.groupedChanges,
        batchChange.ownerGroupId
      )
      response <- buildResponseForApprover(serviceCompleteBatch).toBatchResult
    } yield response

  def cancelBatchChange(
      batchChangeId: String,
      authPrincipal: AuthPrincipal
  ): BatchResult[BatchChange] =
    for {
      batchChange <- getExistingBatchChange(batchChangeId)
      _ <- validateBatchChangeCancellation(batchChange, authPrincipal).toBatchResult
      cancelledBatchChange <- cancelBatchChange(batchChange)
      _ <- notifiers.notify(Notification(cancelledBatchChange)).toBatchResult
    } yield cancelledBatchChange

  def getBatchChange(id: String, auth: AuthPrincipal): BatchResult[BatchChangeInfo] =
    for {
      batchChange <- getExistingBatchChange(id)
      _ <- canGetBatchChange(batchChange, auth).toBatchResult
      rsOwnerGroup <- getOwnerGroup(batchChange.ownerGroupId)
      rsOwnerGroupName = rsOwnerGroup.map(_.name)
      reviewer <- getReviewer(batchChange.reviewerId)
      reviewerUserName = reviewer.map(_.userName)
    } yield BatchChangeInfo(batchChange, rsOwnerGroupName, reviewerUserName)

  def getExistingBatchChange(id: String): BatchResult[BatchChange] =
    batchChangeRepo
      .getBatchChange(id)
      .map {
        case Some(bc) => Right(bc)
        case None => Left(BatchChangeNotFound(id))
      }
      .toBatchResult

  def getZonesForRequest(changes: ValidatedBatch[ChangeInput]): IO[ExistingZones] = {
    // ipv4 search will be by filter, NOT by specific name because of classless reverse zone delegation
    def getPtrIpv4ZoneFilters(ipv4ptr: List[ChangeInput]): Set[String] =
      ipv4ptr.flatMap(input => getIPv4NonDelegatedZoneName(input.inputName)).toSet

    // zone name possibilities for ipv6 PTR
    def getPossiblePtrIpv6ZoneNames(ipv6ptr: List[ChangeInput]): Set[String] = {
      val toDropSmallest = 32 - v6zoneNibbleBoundaries.max
      val toDropLargest = 32 - v6zoneNibbleBoundaries.min

      val ipv6ptrFullReverseNames =
        ipv6ptr.flatMap(input => getIPv6FullReverseName(input.inputName)).toSet
      ipv6ptrFullReverseNames.flatMap { name =>
        (toDropSmallest to toDropLargest).map { index =>
          // times 2 here because there are dots between each nibble in this form
          name.substring(index * 2)
        }
      }
    }

    def getPossibleForwardZoneNames(changeInputs: List[ChangeInput]): Set[String] =
      changeInputs.flatMap(record => getAllPossibleZones(record.inputName)).toSet

    val (ptr, nonPTR) = changes.getValid.partition(_.typ == PTR)
    val (ipv4ptr, ipv6ptr) = ptr.partition(ch => validateIpv4Address(ch.inputName).isValid)

    val nonPTRZoneNames = getPossibleForwardZoneNames(nonPTR)
    val ipv4ptrZoneFilters = getPtrIpv4ZoneFilters(ipv4ptr)
    val ipv6ZoneNames = getPossiblePtrIpv6ZoneNames(ipv6ptr)

    val nonIpv4ZoneLookup = zoneRepository.getZonesByNames(nonPTRZoneNames ++ ipv6ZoneNames)
    val ipv4ZoneLookup = zoneRepository.getZonesByFilters(ipv4ptrZoneFilters)

    for {
      nonIpv4Zones <- nonIpv4ZoneLookup
      ipv4Zones <- ipv4ZoneLookup
    } yield ExistingZones(ipv4Zones ++ nonIpv4Zones)
  }

  def getExistingRecordSets(
      changes: ValidatedBatch[ChangeForValidation],
      zoneMap: ExistingZones
  ): IO[ExistingRecordSets] = {
    val uniqueGets = changes.getValid.map { change =>
      change.inputChange.typ match {
        case PTR => s"${change.recordName}.${change.zone.name}"
        case _ => change.inputChange.inputName
      }
    }.toSet

    for {
      allRecordSets <- recordSetRepository.getRecordSetsByFQDNs(uniqueGets)
      recordSetsWithExistingZone = allRecordSets.filter(rs => zoneMap.getById(rs.zoneId))
    } yield ExistingRecordSets(recordSetsWithExistingZone)
  }

  def doTtlMapping(
      changes: ValidatedBatch[ChangeForValidation],
      existingRecordSets: ExistingRecordSets
  ): ValidatedBatch[ChangeForValidation] =
    changes.mapValid {
      case add: AddChangeForValidation =>
        existingRecordSets
          .get(add.recordKey)
          .map { rs =>
            add.copy(existingRecordTtl = Some(rs.ttl))
          }
          .getOrElse(add)
          .validNel
      case del: DeleteRRSetChangeForValidation => del.validNel
    }

  def getOwnerGroup(ownerGroupId: Option[String]): BatchResult[Option[Group]] = {
    val ownerGroup = for {
      groupId <- OptionT.fromOption[IO](ownerGroupId)
      group <- OptionT(groupRepository.getGroup(groupId))
    } yield group
    ownerGroup.value.toBatchResult
  }

  def getReviewer(reviewerId: Option[String]): BatchResult[Option[User]] = {
    val reviewer = for {
      uid <- OptionT.fromOption[IO](reviewerId)
      user <- OptionT(userRepository.getUser(uid))
    } yield user
    reviewer.value.toBatchResult
  }

  def zoneDiscovery(
      changes: ValidatedBatch[ChangeInput],
      zoneMap: ExistingZones
  ): ValidatedBatch[ChangeForValidation] =
    changes.mapValid { change =>
      change.typ match {
        case A | AAAA | CNAME | MX | TXT | NS | NAPTR | SRV => forwardZoneDiscovery(change, zoneMap)
        case PTR if validateIpv4Address(change.inputName).isValid =>
          ptrIpv4ZoneDiscovery(change, zoneMap)
        case PTR if validateIpv6Address(change.inputName).isValid =>
          ptrIpv6ZoneDiscovery(change, zoneMap)
        case _ => ZoneDiscoveryError(change.inputName).invalidNel
      }
    }

  def forwardZoneDiscovery(
      change: ChangeInput,
      zoneMap: ExistingZones
  ): SingleValidation[ChangeForValidation] = {

    // getAllPossibleZones is ordered most to least specific, so 1st match is right
    val zone = getAllPossibleZones(change.inputName).map(zoneMap.getByName).collectFirst {
      case Some(zn) => zn
    }

    zone match {
      case Some(zn) if zn.name == change.inputName && change.typ == CNAME =>
        CnameAtZoneApexError(zn.name).invalidNel
      case Some(zn) =>
        ChangeForValidation(zn, relativize(change.inputName, zn.name), change, defaultTtl).validNel
      case None => ZoneDiscoveryError(change.inputName).invalidNel
    }
  }

  def ptrIpv4ZoneDiscovery(
      change: ChangeInput,
      zoneMap: ExistingZones
  ): SingleValidation[ChangeForValidation] = {
    val recordName = change.inputName.split('.').takeRight(1).mkString
    val validZones = zoneMap.getipv4PTRMatches(change.inputName)

    val zone = {
      if (validZones.size > 1) validZones.find(zn => zn.name.contains("/"))
      else validZones.headOption
    }
    zone match {
      case Some(z) => ChangeForValidation(z, recordName, change, defaultTtl).validNel
      case None => ZoneDiscoveryError(change.inputName).invalidNel
    }
  }

  def ptrIpv6ZoneDiscovery(
      change: ChangeInput,
      zoneMap: ExistingZones
  ): SingleValidation[ChangeForValidation] = {
    val zones = zoneMap.getipv6PTRMatches(change.inputName)

    if (zones.isEmpty)
      ZoneDiscoveryError(change.inputName).invalidNel
    else {
      // the longest ipv6 zone name that matches this record is the zone holding the most limited IP space
      val zoneName = zones.map(_.name).foldLeft("") { (longestName, name) =>
        if (name.length > longestName.length) {
          name
        } else longestName
      }

      val changeForValidation = for {
        zone <- zoneMap.getByName(zoneName)
        recordName <- {
          // remove zone name from fqdn for recordname
          getIPv6FullReverseName(change.inputName).map(_.dropRight(zone.name.length + 1))
        }
      } yield ChangeForValidation(zone, recordName, change, defaultTtl).validNel

      changeForValidation.getOrElse(ZoneDiscoveryError(change.inputName).invalidNel)
    }
  }

  def buildResponse(
      batchChangeInput: BatchChangeInput,
      transformed: ValidatedBatch[ChangeForValidation],
      auth: AuthPrincipal,
      allowManualReview: Boolean
  ): Either[BatchChangeErrorResponse, BatchChange] = {

    // Respond with a fatal error that kicks the change out to the user
    def errorResponse =
      InvalidBatchChangeResponses(batchChangeInput.changes, transformed).asLeft

    // Respond with a response to advance to manual review
    def manualReviewResponse = {
      val changes = transformed.zip(batchChangeInput.changes).map {
        case (validated, input) =>
          validated match {
            case Valid(v) => v.asStoredChange()
            case Invalid(e) => input.asNewStoredChange(e, defaultTtl)
          }
      }
      BatchChange(
        auth.userId,
        auth.signedInUser.userName,
        batchChangeInput.comments,
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        changes,
        batchChangeInput.ownerGroupId,
        BatchChangeApprovalStatus.PendingReview,
        scheduledTime = batchChangeInput.scheduledTime
      ).asRight
    }

    // Respond with a response to process immediately
    def processNowResponse = {
      val changes = transformed.getValid.map(_.asStoredChange())
      BatchChange(
        auth.userId,
        auth.signedInUser.userName,
        batchChangeInput.comments,
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        changes,
        batchChangeInput.ownerGroupId,
        BatchChangeApprovalStatus.AutoApproved,
        scheduledTime = batchChangeInput.scheduledTime
      ).asRight
    }

    val allErrors = transformed
      .collect {
        case Invalid(e) => e
      }
      .flatMap(_.toList)

    // Tells us that we have soft errors, and no errors are hard errors
    val hardErrorsPresent = allErrors.exists(_.isFatal)
    val noErrors = allErrors.isEmpty
    val isScheduled = batchChangeInput.scheduledTime.isDefined && this.scheduledChangesEnabled
    val isNSRecordsPresent = batchChangeInput.changes.exists(_.typ == NS)

    if (hardErrorsPresent) {
      // Always error out
      errorResponse
    } else if (noErrors && !isScheduled && !isNSRecordsPresent) {
      // There are no errors and this is not scheduled, so process immediately
      processNowResponse
    } else if (this.manualReviewEnabled && allowManualReview || isNSRecordsPresent) {
      if ((noErrors && isScheduled) || batchChangeInput.ownerGroupId.isDefined) {
        // There are no errors and this is scheduled
        // or we have soft errors and owner group is defined
        // advance to manual review if manual review is ok
        manualReviewResponse
      } else {
        ManualReviewRequiresOwnerGroup.asLeft
      }
    } else {
      // Cannot go to manual review, and we have soft errors, so just return a failure
      errorResponse
    }
  }

  def rebuildBatchChangeForUpdate(
      existingBatchChange: BatchChange,
      transformed: ValidatedBatch[ChangeForValidation],
      reviewInfo: BatchChangeReviewInfo
  ): BatchChange = {
    val changes = transformed.zip(existingBatchChange.changes).map {
      case (validated, existing) =>
        validated match {
          case Valid(v) => v.asStoredChange(Some(existing.id))
          case Invalid(errors) =>
            existing.updateValidationErrors(errors.map(e => SingleChangeError(e)).toList)
        }
    }

    if (transformed.forall(_.isValid)) {
      existingBatchChange
        .copy(
          changes = changes,
          approvalStatus = BatchChangeApprovalStatus.ManuallyApproved,
          reviewerId = Some(reviewInfo.reviewerId),
          reviewComment = reviewInfo.reviewComment,
          reviewTimestamp = Some(reviewInfo.reviewTimestamp)
        )
    } else {
      existingBatchChange.copy(changes = changes)
    }
  }

  def convertOrSave(
      batchChange: BatchChange,
      existingZones: ExistingZones,
      groupedChanges: ChangeForValidationMap,
      ownerGroupId: Option[String]
  ): BatchResult[BatchChange] = batchChange.approvalStatus match {
    case AutoApproved =>
      // send on to the converter, it will be saved there
      batchChangeConverter
        .sendBatchForProcessing(batchChange, existingZones, groupedChanges, ownerGroupId)
        .map(_.batchChange)
    case ManuallyApproved if manualReviewEnabled =>
      // send on to the converter, it will be saved there
      batchChangeConverter
        .sendBatchForProcessing(batchChange, existingZones, groupedChanges, ownerGroupId)
        .map(_.batchChange)
    case PendingReview if manualReviewEnabled =>
      // save the change, will need to return to it later on approval
      batchChangeRepo.save(batchChange).toBatchResult
    // TODO: handle PendingReview if manualReviewEnabled is false
    case _ =>
      // this should not be called with a rejected change (or if manual review is off)!
      logger.error(
        s"convertOrSave called with a rejected batch change; " +
          s"batchChangeId=${batchChange.id}; manualReviewEnabled=$manualReviewEnabled"
      )
      UnknownConversionError("Cannot convert a rejected batch change").toLeftBatchResult
  }

  def buildResponseForApprover(
      batchChange: BatchChange
  ): Either[BatchChangeErrorResponse, BatchChange] =
    batchChange.approvalStatus match {
      case ManuallyApproved => batchChange.asRight
      case _ => BatchChangeFailedApproval(batchChange).asLeft
    }

  def addOwnerGroupNamesToSummaries(
      summaries: List[BatchChangeSummary],
      groups: Set[Group]
  ): List[BatchChangeSummary] =
    summaries.map { summary =>
      val groupName =
        summary.ownerGroupId.flatMap(groupId => groups.find(_.id == groupId).map(_.name))
      summary.copy(ownerGroupName = groupName)
    }

  def addReviewerUserNamesToSummaries(
      summaries: List[BatchChangeSummary],
      reviewers: ListUsersResults
  ): List[BatchChangeSummary] =
    summaries.map { summary =>
      val userName =
        summary.reviewerId.flatMap(userId => reviewers.users.find(_.id == userId).map(_.userName))
      summary.copy(reviewerName = userName)
    }

  def listBatchChangeSummaries(
      auth: AuthPrincipal,
      userName: Option[String] = None,
      groupName: Option[String] = None,
      dateTimeStartRange: Option[String] = None,
      dateTimeEndRange: Option[String] = None,
      startFrom: Option[Int] = None,
      maxItems: Int = 100,
      ignoreAccess: Boolean = false,
      batchStatus: Option[BatchChangeStatus] = None,
      approvalStatus: Option[BatchChangeApprovalStatus] = None
  ): BatchResult[BatchChangeSummaryList] = {
    val userId = if (ignoreAccess && auth.isSystemAdmin) None else Some(auth.userId)
    val submitterUserName = if(userName.isDefined && userName.get.isEmpty) None else userName
    val startDateTime = if(dateTimeStartRange.isDefined && dateTimeStartRange.get.isEmpty) None else dateTimeStartRange
    val endDateTime = if(dateTimeEndRange.isDefined && dateTimeEndRange.get.isEmpty) None else dateTimeEndRange
    for {
      mId <- membershipService.listMyGroups(groupName, None, maxItems,auth,false,false).
        map(_.groups.map(_.members.map(_.id).mkString("', '")).mkString).getOrElse("None").toBatchResult
      uid = if (groupName.isDefined) Some(mId) else userId
      listResults <- batchChangeRepo
        .getBatchChangeSummaries(uid, submitterUserName, startDateTime, endDateTime, startFrom, maxItems, batchStatus, approvalStatus)
        .toBatchResult
      rsOwnerGroupIds = listResults.batchChanges.flatMap(_.ownerGroupId).toSet
      rsOwnerGroups <- groupRepository.getGroups(rsOwnerGroupIds).toBatchResult
      summariesWithGroupNames = addOwnerGroupNamesToSummaries(
        listResults.batchChanges,
        rsOwnerGroups
      )
      reviewerIds = listResults.batchChanges.flatMap(_.reviewerId).toSet
      reviewerUserNames <- userRepository.getUsers(reviewerIds, None, Some(maxItems)).toBatchResult
      summariesWithReviewerUserNames = addReviewerUserNamesToSummaries(
        summariesWithGroupNames,
        reviewerUserNames
      )
      listWithGroupNames = listResults.copy(
        batchChanges = summariesWithReviewerUserNames,
        ignoreAccess = ignoreAccess,
        approvalStatus = approvalStatus,
        userName = userName,
        dateTimeStartRange = dateTimeStartRange,
        dateTimeEndRange = dateTimeEndRange
      )
    } yield listWithGroupNames
  }

  def rejectBatchChange(
      batchChange: BatchChange,
      reviewComment: Option[String],
      reviewerId: String
  ): BatchResult[BatchChange] = {
    val rejectedSingleChanges = batchChange.changes.map(_.reject)

    // Update rejection attributes and single changes for batch change
    val rejectedBatch = batchChange.copy(
      approvalStatus = BatchChangeApprovalStatus.ManuallyRejected,
      reviewerId = Some(reviewerId),
      reviewComment = reviewComment,
      reviewTimestamp = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)),
      changes = rejectedSingleChanges
    )

    batchChangeRepo.save(rejectedBatch).toBatchResult
  }

  def cancelBatchChange(batchChange: BatchChange): BatchResult[BatchChange] = {
    val cancelledSingleChanges = batchChange.changes.map(_.cancel)

    // Update rejection attributes and single changes for batch change
    val cancelledBatch = batchChange.copy(
      approvalStatus = BatchChangeApprovalStatus.Cancelled,
      cancelledTimestamp = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)),
      changes = cancelledSingleChanges
    )

    batchChangeRepo.save(cancelledBatch).toBatchResult
  }

  def getBypassTestCheckForReject(
      rejecterAuth: AuthPrincipal,
      batchChange: BatchChange
  ): BatchResult[Boolean] =
    if (!rejecterAuth.isTestUser) {
      // if the rejecting user isnt a test user, we dont need to get the batch creator's info, can just pass along
      // true to bypass the test check
      true.toRightBatchResult
    } else {
      for {
        user <- userRepository.getUser(batchChange.userId).toBatchResult
        isTest <- user match {
          case Some(u) => u.isTest.toRightBatchResult
          case None =>
            BatchRequesterNotFound(batchChange.userId, batchChange.userName).toLeftBatchResult
        }
      } yield isTest
    }
}
