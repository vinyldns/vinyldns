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

package vinyldns.api.domain.record

import vinyldns.api.Interfaces.{Result, _}
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.{Group, GroupRepository, User, UserRepository}
import vinyldns.api.domain.zone._
import vinyldns.api.repository.ApiDataAccessor
import vinyldns.api.route.{ListGlobalRecordSetsResponse, ListRecordSetsByZoneResponse}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.{Zone, ZoneCommandResult, ZoneRepository}
import vinyldns.core.queue.MessageQueue
import cats.data._
import cats.effect.IO
import org.xbill.DNS.ReverseMap
import vinyldns.api.config.HighValueDomainConfig
import vinyldns.api.domain.DomainValidations.{validateIpv4Address, validateIpv6Address}
import vinyldns.api.domain.access.AccessValidationsAlgebra
import vinyldns.core.domain.record.NameSort.NameSort
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.DomainHelpers.ensureTrailingDot
import vinyldns.core.domain.backend.{Backend, BackendResolver}
import vinyldns.core.domain.record.RecordTypeSort.RecordTypeSort

import scala.util.matching.Regex

object RecordSetService {
  def apply(
             dataAccessor: ApiDataAccessor,
             messageQueue: MessageQueue,
             accessValidation: AccessValidationsAlgebra,
             backendResolver: BackendResolver,
             validateRecordLookupAgainstDnsBackend: Boolean,
             highValueDomainConfig: HighValueDomainConfig,
             approvedNameServers: List[Regex],
             useRecordSetCache: Boolean
           ): RecordSetService =
    new RecordSetService(
      dataAccessor.zoneRepository,
      dataAccessor.groupRepository,
      dataAccessor.recordSetRepository,
      dataAccessor.recordSetCacheRepository,
      dataAccessor.recordChangeRepository,
      dataAccessor.userRepository,
      messageQueue,
      accessValidation,
      backendResolver,
      validateRecordLookupAgainstDnsBackend,
      highValueDomainConfig,
      approvedNameServers,
      useRecordSetCache
    )
}

class RecordSetService(
                        zoneRepository: ZoneRepository,
                        groupRepository: GroupRepository,
                        recordSetRepository: RecordSetRepository,
                        recordSetCacheRepository: RecordSetCacheRepository,
                        recordChangeRepository: RecordChangeRepository,
                        userRepository: UserRepository,
                        messageQueue: MessageQueue,
                        accessValidation: AccessValidationsAlgebra,
                        backendResolver: BackendResolver,
                        validateRecordLookupAgainstDnsBackend: Boolean,
                        highValueDomainConfig: HighValueDomainConfig,
                        approvedNameServers: List[Regex],
                        useRecordSetCache: Boolean
                      ) extends RecordSetServiceAlgebra {

  import RecordSetValidations._
  import accessValidation._

  def addRecordSet(recordSet: RecordSet, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      zone <- getZone(recordSet.zoneId)
      change <- RecordSetChangeGenerator.forAdd(recordSet, zone, Some(auth)).toResult
      // because changes happen to the RS in forAdd itself, converting 1st and validating on that
      rsForValidations = change.recordSet
      _ <- isNotHighValueDomain(recordSet, zone, highValueDomainConfig).toResult
      _ <- recordSetDoesNotExist(
        backendResolver.resolve,
        zone,
        rsForValidations,
        validateRecordLookupAgainstDnsBackend
      )
      _ <- validRecordTypes(rsForValidations, zone).toResult
      _ <- validRecordNameLength(rsForValidations, zone).toResult
      _ <- canAddRecordSet(auth, rsForValidations.name, rsForValidations.typ, zone).toResult
      existingRecordsWithName <- recordSetRepository
        .getRecordSetsByName(zone.id, rsForValidations.name)
        .toResult[List[RecordSet]]
      ownerGroup <- getGroupIfProvided(rsForValidations.ownerGroupId)
      _ <- canUseOwnerGroup(rsForValidations.ownerGroupId, ownerGroup, auth).toResult
      _ <- noCnameWithNewName(rsForValidations, existingRecordsWithName, zone).toResult
      _ <- typeSpecificValidations(
        rsForValidations,
        existingRecordsWithName,
        zone,
        None,
        approvedNameServers
      ).toResult
      _ <- messageQueue.send(change).toResult[Unit]
    } yield change

  def updateRecordSet(recordSet: RecordSet, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      zone <- getZone(recordSet.zoneId)
      existing <- getRecordSet(recordSet.id)
      _ <- unchangedRecordName(existing, recordSet, zone).toResult
      _ <- unchangedRecordType(existing, recordSet).toResult
      _ <- unchangedZoneId(existing, recordSet).toResult
      change <- RecordSetChangeGenerator.forUpdate(existing, recordSet, zone, Some(auth)).toResult
      // because changes happen to the RS in forUpdate itself, converting 1st and validating on that
      rsForValidations = change.recordSet
      _ <- isNotHighValueDomain(recordSet, zone, highValueDomainConfig).toResult
      _ <- canUpdateRecordSet(auth, existing.name, existing.typ, zone, existing.ownerGroupId).toResult
      ownerGroup <- getGroupIfProvided(rsForValidations.ownerGroupId)
      _ <- canUseOwnerGroup(rsForValidations.ownerGroupId, ownerGroup, auth).toResult
      _ <- notPending(existing).toResult
      existingRecordsWithName <- recordSetRepository
        .getRecordSetsByName(zone.id, rsForValidations.name)
        .toResult[List[RecordSet]]
      _ <- isUniqueUpdate(
        backendResolver.resolve,
        rsForValidations,
        existingRecordsWithName,
        zone,
        validateRecordLookupAgainstDnsBackend
      )
      _ <- noCnameWithNewName(rsForValidations, existingRecordsWithName, zone).toResult
      _ <- typeSpecificValidations(
        rsForValidations,
        existingRecordsWithName,
        zone,
        Some(existing),
        approvedNameServers
      ).toResult
      _ <- messageQueue.send(change).toResult[Unit]
    } yield change

  def deleteRecordSet(
                       recordSetId: String,
                       zoneId: String,
                       auth: AuthPrincipal
                     ): Result[ZoneCommandResult] =
    for {
      zone <- getZone(zoneId)
      existing <- getRecordSet(recordSetId)
      _ <- isNotHighValueDomain(existing, zone, highValueDomainConfig).toResult
      _ <- canDeleteRecordSet(auth, existing.name, existing.typ, zone, existing.ownerGroupId).toResult
      _ <- notPending(existing).toResult
      _ <- typeSpecificDeleteValidations(existing, zone).toResult
      change <- RecordSetChangeGenerator.forDelete(existing, zone, Some(auth)).toResult
      _ <- messageQueue.send(change).toResult[Unit]
    } yield change

  def getRecordSet(
                    recordSetId: String,
                    authPrincipal: AuthPrincipal
                  ): Result[RecordSetInfo] =
    for {
      recordSet <- getRecordSet(recordSetId)
      groupName <- getGroupName(recordSet.ownerGroupId)
    } yield RecordSetInfo(recordSet, groupName)

  def getRecordSetByZone(
                          recordSetId: String,
                          zoneId: String,
                          authPrincipal: AuthPrincipal
                        ): Result[RecordSetInfo] =
    for {
      zone <- getZone(zoneId)
      recordSet <- getRecordSet(recordSetId)
      _ <- canViewRecordSet(
        authPrincipal,
        recordSet.name,
        recordSet.typ,
        zone,
        recordSet.ownerGroupId
      ).toResult
      groupName <- getGroupName(recordSet.ownerGroupId)
    } yield RecordSetInfo(recordSet, groupName)

  def listRecordSets(
                      startFrom: Option[String],
                      maxItems: Option[Int],
                      recordNameFilter: String,
                      recordTypeFilter: Option[Set[RecordType]],
                      recordOwnerGroupFilter: Option[String],
                      nameSort: NameSort,
                      authPrincipal: AuthPrincipal,
                      recordTypeSort: RecordTypeSort
                    ): Result[ListGlobalRecordSetsResponse] =
    for {
      _ <- validRecordNameFilterLength(recordNameFilter).toResult
      formattedRecordNameFilter <- formatRecordNameFilter(recordNameFilter)
      recordSetResults <- recordSetRepository
        .listRecordSets(
          None,
          startFrom,
          maxItems,
          Some(formattedRecordNameFilter),
          recordTypeFilter,
          recordOwnerGroupFilter,
          nameSort,
          recordTypeSort
        )
        .toResult[ListRecordSetResults]
      rsOwnerGroupIds = recordSetResults.recordSets.flatMap(_.ownerGroupId).toSet
      rsZoneIds = recordSetResults.recordSets.map(_.zoneId).toSet
      rsGroups <- groupRepository.getGroups(rsOwnerGroupIds).toResult[Set[Group]]
      rsZones <- zoneRepository.getZones(rsZoneIds).toResult[Set[Zone]]
      setsWithSupplementalInfo = getSupplementalInfo(recordSetResults.recordSets, rsGroups, rsZones)
    } yield ListGlobalRecordSetsResponse(
      setsWithSupplementalInfo,
      recordSetResults.startFrom,
      recordSetResults.nextId,
      recordSetResults.maxItems,
      recordNameFilter,
      recordSetResults.recordTypeFilter,
      recordSetResults.recordOwnerGroupFilter,
      recordSetResults.nameSort
    )

  /**
   * Searches recordsets, optionally using the recordset cache (controlled by the 'use-recordset-cache' setting)
   *
   * @param startFrom          The starting record
   * @param maxItems           The maximum number of items
   * @param recordNameFilter   The record name filter
   * @param recordTypeFilter   The record type filter
   * @param recordOwnerGroupId The owner group identifier
   * @param nameSort           The sort direction
   * @param authPrincipal      The authenticated principal
   * @return A {@link ListGlobalRecordSetsResponse}
   */
  def searchRecordSets(
                        startFrom: Option[String],
                        maxItems: Option[Int],
                        recordNameFilter: String,
                        recordTypeFilter: Option[Set[RecordType]],
                        recordOwnerGroupFilter: Option[String],
                        nameSort: NameSort,
                        authPrincipal: AuthPrincipal,
                        recordTypeSort: RecordTypeSort
                      ): Result[ListGlobalRecordSetsResponse] = {
    for {
      _ <- validRecordNameFilterLength(recordNameFilter).toResult
      formattedRecordNameFilter <- formatRecordNameFilter(recordNameFilter)
      recordSetResults <- if (useRecordSetCache) {
        // Search the cache
        recordSetCacheRepository.listRecordSetData(
          None,
          startFrom,
          maxItems,
          Some(formattedRecordNameFilter),
          recordTypeFilter,
          recordOwnerGroupFilter,
          nameSort
        ).toResult[ListRecordSetResults]
      } else {
        // Search the record table directly
        recordSetRepository.listRecordSets(
          None,
          startFrom,
          maxItems,
          Some(formattedRecordNameFilter),
          recordTypeFilter,
          recordOwnerGroupFilter,
          nameSort,
          recordTypeSort
        ).toResult[ListRecordSetResults]
      }
      rsOwnerGroupIds = recordSetResults.recordSets.flatMap(_.ownerGroupId).toSet
      rsZoneIds = recordSetResults.recordSets.map(_.zoneId).toSet
      rsGroups <- groupRepository.getGroups(rsOwnerGroupIds).toResult[Set[Group]]
      rsZones <- zoneRepository.getZones(rsZoneIds).toResult[Set[Zone]]
      setsWithSupplementalInfo = getSupplementalInfo(recordSetResults.recordSets, rsGroups, rsZones)
    } yield ListGlobalRecordSetsResponse(
      setsWithSupplementalInfo,
      recordSetResults.startFrom,
      recordSetResults.nextId,
      recordSetResults.maxItems,
      recordNameFilter,
      recordSetResults.recordTypeFilter,
      recordSetResults.recordOwnerGroupFilter,
      recordSetResults.nameSort
    )
  }

  def listRecordSetsByZone(
                            zoneId: String,
                            startFrom: Option[String],
                            maxItems: Option[Int],
                            recordNameFilter: Option[String],
                            recordTypeFilter: Option[Set[RecordType]],
                            recordOwnerGroupFilter: Option[String],
                            nameSort: NameSort,
                            authPrincipal: AuthPrincipal,
                            recordTypeSort: RecordTypeSort
                          ): Result[ListRecordSetsByZoneResponse] =
    for {
      zone <- getZone(zoneId)
      _ <- canSeeZone(authPrincipal, zone).toResult
      recordSetResults <- recordSetRepository
        .listRecordSets(
          Some(zoneId),
          startFrom,
          maxItems,
          recordNameFilter,
          recordTypeFilter,
          recordOwnerGroupFilter,
          nameSort,
          recordTypeSort
        )
        .toResult[ListRecordSetResults]
      rsOwnerGroupIds = recordSetResults.recordSets.flatMap(_.ownerGroupId).toSet
      rsGroups <- groupRepository.getGroups(rsOwnerGroupIds).toResult[Set[Group]]
      setsWithGroupName = getListWithGroupNames(recordSetResults.recordSets, rsGroups)
      setsWithAccess <- getListAccessLevels(authPrincipal, setsWithGroupName, zone).toResult
    } yield ListRecordSetsByZoneResponse(
      setsWithAccess,
      recordSetResults.startFrom,
      recordSetResults.nextId,
      recordSetResults.maxItems,
      recordSetResults.recordNameFilter,
      recordSetResults.recordTypeFilter,
      recordSetResults.recordOwnerGroupFilter,
      recordSetResults.nameSort,
      recordTypeSort
    )

  def getRecordSetChange(
                          zoneId: String,
                          changeId: String,
                          authPrincipal: AuthPrincipal
                        ): Result[RecordSetChange] =
    for {
      zone <- getZone(zoneId)
      change <- recordChangeRepository
        .getRecordSetChange(zone.id, changeId)
        .orFail(
          RecordSetChangeNotFoundError(
            s"Unable to find record set change with id $changeId in zone ${zone.name}"
          )
        )
        .toResult[RecordSetChange]
      _ <- canViewRecordSet(
        authPrincipal,
        change.recordSet.name,
        change.recordSet.typ,
        zone,
        change.recordSet.ownerGroupId
      ).toResult
    } yield change

  def listRecordSetChanges(
                            zoneId: String,
                            startFrom: Option[String] = None,
                            maxItems: Int = 100,
                            authPrincipal: AuthPrincipal
                          ): Result[ListRecordSetChangesResponse] =
    for {
      zone <- getZone(zoneId)
      _ <- canSeeZone(authPrincipal, zone).toResult
      recordSetChangesResults <- recordChangeRepository
        .listRecordSetChanges(zone.id, startFrom, maxItems)
        .toResult[ListRecordSetChangesResults]
      recordSetChangesInfo <- buildRecordSetChangeInfo(recordSetChangesResults.items)
    } yield ListRecordSetChangesResponse(zoneId, recordSetChangesResults, recordSetChangesInfo)

  def getZone(zoneId: String): Result[Zone] =
    zoneRepository
      .getZone(zoneId)
      .orFail(ZoneNotFoundError(s"Zone with id $zoneId does not exists"))
      .toResult[Zone]

  def getRecordSet(recordsetId: String): Result[RecordSet] =
    recordSetRepository
      .getRecordSet(recordsetId)
      .orFail(
        RecordSetNotFoundError(
          s"RecordSet with id $recordsetId does not exist."
        )
      )
      .toResult[RecordSet]

  def recordSetDoesNotExistInDatabase(recordSet: RecordSet, zone: Zone): Result[Unit] =
    recordSetRepository
      .getRecordSets(zone.id, recordSet.name, recordSet.typ)
      .map {
        case Nil => Right(())
        case _ =>
          Left(
            RecordSetAlreadyExists(
              s"RecordSet with name ${recordSet.name} and type ${recordSet.typ} already " +
                s"exists in zone ${zone.name}"
            )
          )
      }
      .toResult

  def buildRecordSetChangeInfo(
                                changes: List[RecordSetChange]
                              ): Result[List[RecordSetChangeInfo]] = {
    val userIds = changes.map(_.userId).toSet
    for {
      users <- userRepository.getUsers(userIds, None, None).map(_.users).toResult[Seq[User]]
      userMap = users.map(u => (u.id, u.userName)).toMap
      recordSetChangesInfo = changes.map(
        change => RecordSetChangeInfo(change, userMap.get(change.userId))
      )
    } yield recordSetChangesInfo
  }

  def getListWithGroupNames(recordsets: List[RecordSet], groups: Set[Group]): List[RecordSetInfo] =
    recordsets.map { rs =>
      val ownerGroupName =
        rs.ownerGroupId.flatMap(groupId => groups.find(_.id == groupId).map(_.name))
      RecordSetInfo(rs, ownerGroupName)
    }

  def getSupplementalInfo(
                           recordsets: List[RecordSet],
                           groups: Set[Group],
                           zones: Set[Zone]
                         ): List[RecordSetGlobalInfo] =
    recordsets.map { rs =>
      val ownerGroupName =
        rs.ownerGroupId.flatMap(groupId => groups.find(_.id == groupId).map(_.name))
      val (zoneName, zoneShared) = zones.find(_.id == rs.zoneId) match {
        case Some(zone) => (zone.name, zone.shared)
        case None => ("Unknown zone name", false)
      }
      RecordSetGlobalInfo(rs, zoneName, zoneShared, ownerGroupName)
    }

  def getGroupName(groupId: Option[String]): Result[Option[String]] = {
    val groupName = for {
      input <- OptionT.fromOption[IO](groupId)
      dbGet <- OptionT(groupRepository.getGroup(input))
    } yield dbGet.name
    groupName.value.toResult[Option[String]]
  }

  def getGroupIfProvided(groupId: Option[String]): Result[Option[Group]] = {
    val ownerGroup = for {
      id <- OptionT.fromOption[IO](groupId)
      group <- OptionT(groupRepository.getGroup(id))
    } yield group
    ownerGroup.value.toResult
  }

  def recordSetDoesNotExist(
                             backendConnection: Zone => Backend,
                             zone: Zone,
                             recordSet: RecordSet,
                             validateRecordLookupAgainstDnsBackend: Boolean
                           ): Result[Unit] =
    recordSetDoesNotExistInDatabase(recordSet, zone).value.flatMap {
      case Left(recordSetAlreadyExists: RecordSetAlreadyExists)
        if validateRecordLookupAgainstDnsBackend =>
        backendConnection(zone)
          .resolve(recordSet.name, zone.name, recordSet.typ)
          .attempt
          .map {
            case Right(existingRecords) =>
              if (existingRecords.isEmpty) Right(())
              else Left(recordSetAlreadyExists)
            case error => error
          }
      case result => IO(result)
    }.toResult

  def isUniqueUpdate(
                      backendConnection: Zone => Backend,
                      newRecordSet: RecordSet,
                      existingRecordsWithName: List[RecordSet],
                      zone: Zone,
                      validateRecordLookupAgainstDnsBackend: Boolean
                    ): Result[Unit] =
    RecordSetValidations
      .recordSetDoesNotExist(newRecordSet, existingRecordsWithName, zone) match {
      case Left(recordSetAlreadyExists: RecordSetAlreadyExists)
        if validateRecordLookupAgainstDnsBackend =>
        backendConnection(zone)
          .resolve(newRecordSet.name, zone.name, newRecordSet.typ)
          .attempt
          .map {
            case Right(existingRecords) =>
              if (existingRecords.isEmpty) Right(())
              else Left(recordSetAlreadyExists)
            case error => error
          }
          .toResult
      case result => IO(result).toResult
    }

  def formatRecordNameFilter(recordNameFilter: String): Result[String] = {
    if (validateIpv4Address(recordNameFilter).isValid || validateIpv6Address(recordNameFilter).isValid) {
      ReverseMap.fromAddress(recordNameFilter).toString
    } else {
      ensureTrailingDot(recordNameFilter)
    }
  }.toResult
}
