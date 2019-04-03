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
import cats.effect._
import cats.implicits._
import org.joda.time.DateTime
import vinyldns.api.domain.DomainValidations._
import vinyldns.api.domain.ReverseZoneHelpers.ptrIsInZone
import vinyldns.api.domain.batch.BatchChangeInterfaces._
import vinyldns.api.domain.batch.BatchTransformations._
import vinyldns.api.domain.dns.DnsConversions._
import vinyldns.api.domain.{RecordAlreadyExists, ZoneDiscoveryError}
import vinyldns.api.repository.ApiDataAccessor
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.batch._
import vinyldns.core.domain.membership.{Group, GroupRepository}
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.record.RecordSetRepository
import vinyldns.core.domain.zone.ZoneRepository

object BatchChangeService {
  def apply(
      dataAccessor: ApiDataAccessor,
      batchChangeValidations: BatchChangeValidationsAlgebra,
      batchChangeConverter: BatchChangeConverterAlgebra): BatchChangeService =
    new BatchChangeService(
      dataAccessor.zoneRepository,
      dataAccessor.recordSetRepository,
      dataAccessor.groupRepository,
      batchChangeValidations,
      dataAccessor.batchChangeRepository,
      batchChangeConverter
    )
}

class BatchChangeService(
    zoneRepository: ZoneRepository,
    recordSetRepository: RecordSetRepository,
    groupRepository: GroupRepository,
    batchChangeValidations: BatchChangeValidationsAlgebra,
    batchChangeRepo: BatchChangeRepository,
    batchChangeConverter: BatchChangeConverterAlgebra)
    extends BatchChangeServiceAlgebra {

  import batchChangeValidations._

  def applyBatchChange(
      batchChangeInput: BatchChangeInput,
      auth: AuthPrincipal): BatchResult[BatchChange] =
    for {
      existingGroup <- getOwnerGroup(batchChangeInput.ownerGroupId)
      _ <- validateBatchChangeInput(batchChangeInput, existingGroup, auth)
      inputValidatedSingleChanges = validateInputChanges(batchChangeInput.changes)
      zoneMap <- getZonesForRequest(inputValidatedSingleChanges).toBatchResult
      changesWithZones = zoneDiscovery(inputValidatedSingleChanges, zoneMap)
      recordSets <- getExistingRecordSets(changesWithZones).toBatchResult
      validatedSingleChanges = validateChangesWithContext(
        changesWithZones,
        recordSets,
        auth,
        batchChangeInput.ownerGroupId)
      changeForConversion <- buildResponse(batchChangeInput, validatedSingleChanges, auth).toBatchResult
      conversionResult <- batchChangeConverter.sendBatchForProcessing(
        changeForConversion,
        zoneMap,
        recordSets,
        batchChangeInput.ownerGroupId)
    } yield conversionResult.batchChange

  def getBatchChange(id: String, auth: AuthPrincipal): BatchResult[BatchChangeInfo] =
    for {
      batchChange <- getExistingBatchChange(id)
      _ <- canGetBatchChange(batchChange, auth).toBatchResult
      rsOwnerGroup <- getOwnerGroup(batchChange.ownerGroupId)
      rsOwnerGroupName = rsOwnerGroup.map(_.name)
    } yield BatchChangeInfo(batchChange, rsOwnerGroupName)

  def getExistingBatchChange(id: String): BatchResult[BatchChange] =
    batchChangeRepo
      .getBatchChange(id)
      .map {
        case Some(bc) => Right(bc)
        case None => Left(BatchChangeNotFound(id))
      }
      .toBatchResult

  def getZonesForRequest(changes: ValidatedBatch[ChangeInput]): IO[ExistingZones] = {

    // zone name possibilities for all non-PTR changes
    def getPossibleNonPtrZoneNames(nonPtr: List[ChangeInput]): Set[String] = {
      val apexZoneNames = nonPtr.map(_.inputName)
      val nonApexZoneNames = apexZoneNames.map(getZoneFromNonApexFqdn)
      (apexZoneNames ++ nonApexZoneNames).filterNot(_ == "").toSet
    }

    // ipv4 search will be by filter, NOT by specific name because of classless reverse zone delegation
    def getPtrIpv4ZoneFilters(ipv4ptr: List[ChangeInput]): Set[String] =
      ipv4ptr.flatMap(input => getIPv4NonDelegatedZoneName(input.inputName)).toSet

    // zone name possibilities for ipv6 PTR
    def getPossiblePtrIpv6ZoneNames(ipv6ptr: List[ChangeInput]): Set[String] = {
      // TODO - should move min/max into config at some point. For now, only look for /20 through /64 zones by name
      val zoneCidrSmallest = 64 // largest CIDR means smaller zone
      val zoneCidrLargest = 20

      /*
        Logic here is tricky. Each digit is 4 bits, and there are 128 bits total.
        For a /20 zone, you need to keep 20/4 = 5 bits. That means you should drop (128 - 20)/4 = 27 characters
       */
      val toDropSmallest = (128 - zoneCidrSmallest) / 4
      val toDropLargest = (128 - zoneCidrLargest) / 4

      val ipv6ptrFullReverseNames =
        ipv6ptr.flatMap(input => getIPv6FullReverseName(input.inputName)).toSet
      ipv6ptrFullReverseNames.flatMap { name =>
        (toDropSmallest to toDropLargest).map { index =>
          // times 2 here because there are dots between each nibble in this form
          name.substring(index * 2)
        }
      }
    }

    val (ptr, nonPTR) = changes.getValid.partition(_.typ == PTR)
    val (ipv4ptr, ipv6ptr) = ptr.partition(ch => validateIpv4Address(ch.inputName).isValid)

    val nonPTRZoneNames = getPossibleNonPtrZoneNames(nonPTR)
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
      changes: ValidatedBatch[ChangeForValidation]): IO[ExistingRecordSets] = {
    val uniqueGets = changes.getValid.map { change =>
      change.inputChange.typ match {
        case PTR => s"${change.recordName}.${change.zone.name}"
        case _ => change.inputChange.inputName
      }
    }.toSet
    val allRecordSets = recordSetRepository.getRecordSetsByFQDNs(uniqueGets)

    allRecordSets.map(ExistingRecordSets)
  }

  def getOwnerGroup(ownerGroupId: Option[String]): BatchResult[Option[Group]] = {
    val ownerGroup = for {
      groupId <- OptionT.fromOption[IO](ownerGroupId)
      group <- OptionT(groupRepository.getGroup(groupId))
    } yield group
    ownerGroup.value.toBatchResult
  }

  def zoneDiscovery(
      changes: ValidatedBatch[ChangeInput],
      zoneMap: ExistingZones): ValidatedBatch[ChangeForValidation] =
    changes.mapValid { change =>
      change.typ match {
        case A | AAAA | TXT | MX => standardZoneDiscovery(change, zoneMap)
        case CNAME => cnameZoneDiscovery(change, zoneMap)
        case PTR if validateIpv4Address(change.inputName).isValid =>
          ptrIpv4ZoneDiscovery(change, zoneMap)
        case PTR if validateIpv6Address(change.inputName).isValid =>
          ptrIpv6ZoneDiscovery(change, zoneMap)
        case _ => ZoneDiscoveryError(change.inputName).invalidNel
      }
    }

  def standardZoneDiscovery(
      change: ChangeInput,
      zoneMap: ExistingZones): SingleValidation[ChangeForValidation] = {
    val nonApexName = getZoneFromNonApexFqdn(change.inputName)
    val apexZone = zoneMap.getByName(change.inputName)
    val nonApexZone = zoneMap.getByName(nonApexName)

    apexZone.orElse(nonApexZone) match {
      case Some(zn) =>
        ChangeForValidation(zn, relativize(change.inputName, zn.name), change).validNel
      case None => ZoneDiscoveryError(change.inputName).invalidNel
    }
  }

  def cnameZoneDiscovery(
      change: ChangeInput,
      zoneMap: ExistingZones): SingleValidation[ChangeForValidation] = {
    val nonApexName = getZoneFromNonApexFqdn(change.inputName)
    val apexZone = zoneMap.getByName(change.inputName)
    val nonApexZone = zoneMap.getByName(nonApexName)

    (apexZone, nonApexZone) match {
      case (None, Some(zn)) =>
        ChangeForValidation(zn, relativize(change.inputName, zn.name), change).validNel
      case (Some(_), _) => RecordAlreadyExists(change.inputName).invalidNel
      case (None, None) => ZoneDiscoveryError(change.inputName).invalidNel
    }
  }

  def ptrIpv4ZoneDiscovery(
      change: ChangeInput,
      zoneMap: ExistingZones): SingleValidation[ChangeForValidation] = {
    val recordName = change.inputName.split('.').takeRight(1).mkString
    val validZones =
      zoneMap.getipv4PTRMatches(change.inputName).filter(ptrIsInZone(_, recordName, PTR).isRight)

    val zone = {
      if (validZones.size > 1) validZones.find(zn => zn.name.contains("/"))
      else validZones.headOption
    }
    zone match {
      case Some(z) => ChangeForValidation(z, recordName, change).validNel
      case None => ZoneDiscoveryError(change.inputName).invalidNel
    }
  }

  def ptrIpv6ZoneDiscovery(
      change: ChangeInput,
      zoneMap: ExistingZones): SingleValidation[ChangeForValidation] = {
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
      } yield ChangeForValidation(zone, recordName, change).validNel

      changeForValidation.getOrElse(ZoneDiscoveryError(change.inputName).invalidNel)
    }
  }

  def buildResponse(
      batchChangeInput: BatchChangeInput,
      transformed: ValidatedBatch[ChangeForValidation],
      auth: AuthPrincipal): Either[BatchChangeErrorResponse, BatchChange] =
    if (transformed.forall(_.isValid)) {
      val changes = transformed.getValid.map(_.asNewStoredChange)
      BatchChange(
        auth.userId,
        auth.signedInUser.userName,
        batchChangeInput.comments,
        DateTime.now,
        changes,
        batchChangeInput.ownerGroupId).asRight
    } else {
      InvalidBatchChangeResponses(batchChangeInput.changes, transformed).asLeft
    }

  def addOwnerGroupNamesToSummaries(
      summaries: List[BatchChangeSummary],
      groups: Set[Group]): List[BatchChangeSummary] =
    summaries.map { summary =>
      val groupName =
        summary.ownerGroupId.flatMap(groupId => groups.find(_.id == groupId).map(_.name))
      summary.copy(ownerGroupName = groupName)
    }

  def listBatchChangeSummaries(
      auth: AuthPrincipal,
      startFrom: Option[Int] = None,
      maxItems: Int = 100): BatchResult[BatchChangeSummaryList] =
    for {
      listResults <- batchChangeRepo
        .getBatchChangeSummariesByUserId(auth.userId, startFrom, maxItems)
        .toBatchResult
      rsOwnerGroupIds = listResults.batchChanges.flatMap(_.ownerGroupId).toSet
      rsOwnerGroups <- groupRepository.getGroups(rsOwnerGroupIds).toBatchResult
      summariesWithGroupNames = addOwnerGroupNamesToSummaries(
        listResults.batchChanges,
        rsOwnerGroups)
      listWithGroupNames = listResults.copy(batchChanges = summariesWithGroupNames)
    } yield listWithGroupNames
}
