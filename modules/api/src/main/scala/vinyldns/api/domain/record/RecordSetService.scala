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
import vinyldns.api.domain.AccessValidationAlgebra
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.{Group, GroupRepository, User, UserRepository}
import vinyldns.api.domain.zone._
import vinyldns.api.repository.ApiDataAccessor
import vinyldns.api.route.ListRecordSetsResponse
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.{Zone, ZoneCommandResult, ZoneRepository}
import vinyldns.core.queue.MessageQueue
import cats.data._
import cats.effect.IO

object RecordSetService {
  def apply(
      dataAccessor: ApiDataAccessor,
      messageQueue: MessageQueue,
      accessValidation: AccessValidationAlgebra): RecordSetService =
    new RecordSetService(
      dataAccessor.zoneRepository,
      dataAccessor.groupRepository,
      dataAccessor.recordSetRepository,
      dataAccessor.recordChangeRepository,
      dataAccessor.userRepository,
      messageQueue,
      accessValidation
    )
}

class RecordSetService(
    zoneRepository: ZoneRepository,
    groupRepository: GroupRepository,
    recordSetRepository: RecordSetRepository,
    recordChangeRepository: RecordChangeRepository,
    userRepository: UserRepository,
    messageQueue: MessageQueue,
    accessValidation: AccessValidationAlgebra)
    extends RecordSetServiceAlgebra {

  import RecordSetValidations._
  import accessValidation._

  def addRecordSet(recordSet: RecordSet, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      zone <- getZone(recordSet.zoneId)
      change <- RecordSetChangeGenerator.forAdd(recordSet, zone, Some(auth)).toResult
      // because changes happen to the RS in forAdd itself, converting 1st and validating on that
      rsForValidations = change.recordSet
      _ <- isNotHighValueDomain(recordSet, zone).toResult
      _ <- recordSetDoesNotExist(rsForValidations, zone)
      _ <- validRecordTypes(rsForValidations, zone).toResult
      _ <- validRecordNameLength(rsForValidations, zone).toResult
      _ <- canAddRecordSet(auth, rsForValidations.name, rsForValidations.typ, zone).toResult
      existingRecordsWithName <- recordSetRepository
        .getRecordSetsByName(zone.id, rsForValidations.name)
        .toResult[List[RecordSet]]
      ownerGroup <- getGroupIfProvided(rsForValidations.ownerGroupId)
      _ <- canUseOwnerGroup(rsForValidations.ownerGroupId, ownerGroup, auth).toResult
      _ <- noCnameWithNewName(rsForValidations, existingRecordsWithName, zone).toResult
      _ <- typeSpecificAddValidations(rsForValidations, existingRecordsWithName, zone).toResult
      _ <- messageQueue.send(change).toResult[Unit]
    } yield change

  def updateRecordSet(recordSet: RecordSet, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      zone <- getZone(recordSet.zoneId)
      existing <- getRecordSet(recordSet.id, zone)
      change <- RecordSetChangeGenerator.forUpdate(existing, recordSet, zone, Some(auth)).toResult
      // because changes happen to the RS in forUpdate itself, converting 1st and validating on that
      rsForValidations = change.recordSet
      _ <- isNotHighValueDomain(recordSet, zone).toResult
      _ <- canUpdateRecordSet(auth, existing.name, existing.typ, zone, existing.ownerGroupId).toResult
      ownerGroup <- getGroupIfProvided(rsForValidations.ownerGroupId)
      _ <- canUseOwnerGroup(rsForValidations.ownerGroupId, ownerGroup, auth).toResult
      _ <- notPending(existing).toResult
      _ <- validRecordTypes(rsForValidations, zone).toResult
      _ <- validRecordNameLength(rsForValidations, zone).toResult
      existingRecordsWithName <- recordSetRepository
        .getRecordSetsByName(zone.id, rsForValidations.name)
        .toResult[List[RecordSet]]
      _ <- isUniqueUpdate(rsForValidations, existingRecordsWithName, zone).toResult
      _ <- noCnameWithNewName(rsForValidations, existingRecordsWithName, zone).toResult
      _ <- typeSpecificEditValidations(rsForValidations, existing, existingRecordsWithName, zone).toResult
      _ <- messageQueue.send(change).toResult[Unit]
    } yield change

  def deleteRecordSet(
      recordSetId: String,
      zoneId: String,
      auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      zone <- getZone(zoneId)
      existing <- getRecordSet(recordSetId, zone)
      _ <- isNotHighValueDomain(existing, zone).toResult
      _ <- canDeleteRecordSet(auth, existing.name, existing.typ, zone, existing.ownerGroupId).toResult
      _ <- notPending(existing).toResult
      _ <- typeSpecificDeleteValidations(existing, zone).toResult
      change <- RecordSetChangeGenerator.forDelete(existing, zone, Some(auth)).toResult
      _ <- messageQueue.send(change).toResult[Unit]
    } yield change

  def getRecordSet(
      recordSetId: String,
      zoneId: String,
      authPrincipal: AuthPrincipal): Result[RecordSetInfo] =
    for {
      zone <- getZone(zoneId)
      recordSet <- getRecordSet(recordSetId, zone)
      _ <- canViewRecordSet(
        authPrincipal,
        recordSet.name,
        recordSet.typ,
        zone,
        recordSet.ownerGroupId).toResult
      groupName <- getGroupName(recordSet.ownerGroupId)
    } yield RecordSetInfo(recordSet, groupName)

  def listRecordSets(
      zoneId: String,
      startFrom: Option[String],
      maxItems: Option[Int],
      recordNameFilter: Option[String],
      authPrincipal: AuthPrincipal): Result[ListRecordSetsResponse] =
    for {
      zone <- getZone(zoneId)
      _ <- canSeeZone(authPrincipal, zone).toResult
      recordSetResults <- recordSetRepository
        .listRecordSets(zoneId, startFrom, maxItems, recordNameFilter)
        .toResult[ListRecordSetResults]
      rsOwnerGroupIds = recordSetResults.recordSets.flatMap(_.ownerGroupId).toSet
      rsGroups <- groupRepository.getGroups(rsOwnerGroupIds).toResult[Set[Group]]
      setsWithGroupName = getListWithGroupNames(recordSetResults.recordSets, rsGroups)
      setsWithAccess <- getListAccessLevels(authPrincipal, setsWithGroupName, zone).toResult
    } yield
      ListRecordSetsResponse(
        setsWithAccess,
        recordSetResults.startFrom,
        recordSetResults.nextId,
        recordSetResults.maxItems,
        recordSetResults.recordNameFilter)

  def getRecordSetChange(
      zoneId: String,
      changeId: String,
      authPrincipal: AuthPrincipal): Result[RecordSetChange] =
    for {
      zone <- getZone(zoneId)
      change <- recordChangeRepository
        .getRecordSetChange(zone.id, changeId)
        .orFail(
          RecordSetChangeNotFoundError(
            s"Unable to find record set change with id $changeId in zone ${zone.name}"))
        .toResult[RecordSetChange]
      _ <- canViewRecordSet(
        authPrincipal,
        change.recordSet.name,
        change.recordSet.typ,
        zone,
        change.recordSet.ownerGroupId).toResult
    } yield change

  def listRecordSetChanges(
      zoneId: String,
      startFrom: Option[String] = None,
      maxItems: Int = 100,
      authPrincipal: AuthPrincipal): Result[ListRecordSetChangesResponse] =
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

  def getRecordSet(recordsetId: String, zone: Zone): Result[RecordSet] =
    recordSetRepository
      .getRecordSet(zone.id, recordsetId)
      .orFail(
        RecordSetNotFoundError(
          s"RecordSet with id $recordsetId does not exist in zone ${zone.name}"))
      .toResult[RecordSet]

  def recordSetDoesNotExist(recordSet: RecordSet, zone: Zone): Result[Unit] =
    recordSetRepository
      .getRecordSets(zone.id, recordSet.name, recordSet.typ)
      .map {
        case Nil => Right(())
        case _ =>
          Left(
            RecordSetAlreadyExists(
              s"RecordSet with name ${recordSet.name} and type ${recordSet.typ} already " +
                s"exists in zone ${zone.name}"))
      }
      .toResult

  def buildRecordSetChangeInfo(
      changes: List[RecordSetChange]): Result[List[RecordSetChangeInfo]] = {
    val userIds = changes.map(_.userId).toSet
    for {
      users <- userRepository.getUsers(userIds, None, None).map(_.users).toResult[Seq[User]]
      userMap = users.map(u => (u.id, u.userName)).toMap
      recordSetChangesInfo = changes.map(change =>
        RecordSetChangeInfo(change, userMap.get(change.userId)))
    } yield recordSetChangesInfo
  }

  def getListWithGroupNames(recordsets: List[RecordSet], groups: Set[Group]): List[RecordSetInfo] =
    recordsets.map { rs =>
      val ownerGroupName =
        rs.ownerGroupId.flatMap(groupId => groups.find(_.id == groupId).map(_.name))
      RecordSetInfo(rs, ownerGroupName)
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
}
