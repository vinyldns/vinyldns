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

package vinyldns.api.domain.zone

import cats.implicits._
import vinyldns.api.{Interfaces, VinylDNSConfig}
import vinyldns.api.domain.AccessValidationAlgebra
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.api.repository.ApiDataAccessor
import vinyldns.core.domain.membership.{Group, GroupRepository, User, UserRepository}
import vinyldns.core.domain.zone._
import vinyldns.core.queue.MessageQueue
import vinyldns.core.domain.DomainHelpers.ensureTrailingDot

object ZoneService {
  def apply(
      dataAccessor: ApiDataAccessor,
      connectionValidator: ZoneConnectionValidatorAlgebra,
      messageQueue: MessageQueue,
      zoneValidations: ZoneValidations,
      accessValidation: AccessValidationAlgebra): ZoneService =
    new ZoneService(
      dataAccessor.zoneRepository,
      dataAccessor.groupRepository,
      dataAccessor.userRepository,
      dataAccessor.zoneChangeRepository,
      connectionValidator,
      messageQueue,
      zoneValidations,
      accessValidation
    )
}

class ZoneService(
    zoneRepository: ZoneRepository,
    groupRepository: GroupRepository,
    userRepository: UserRepository,
    zoneChangeRepository: ZoneChangeRepository,
    connectionValidator: ZoneConnectionValidatorAlgebra,
    messageQueue: MessageQueue,
    zoneValidations: ZoneValidations,
    accessValidation: AccessValidationAlgebra)
    extends ZoneServiceAlgebra {

  import accessValidation._
  import zoneValidations._
  import Interfaces._

  def connectToZone(
      createZoneInput: CreateZoneInput,
      auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      _ <- isValidZoneAcl(createZoneInput.acl).toResult
      _ <- connectionValidator.isValidBackendId(createZoneInput.backendId).toResult
      _ <- validateSharedZoneAuthorized(createZoneInput.shared, auth.signedInUser).toResult
      _ <- zoneDoesNotExist(createZoneInput.name)
      _ <- adminGroupExists(createZoneInput.adminGroupId)
      _ <- canChangeZone(auth, createZoneInput.name, createZoneInput.adminGroupId).toResult
      zoneToCreate = Zone(createZoneInput, auth.isTestUser)
      _ <- connectionValidator.validateZoneConnections(zoneToCreate)
      createZoneChange <- ZoneChangeGenerator.forAdd(zoneToCreate, auth).toResult
      _ <- messageQueue.send(createZoneChange).toResult[Unit]
    } yield createZoneChange

  def updateZone(updateZoneInput: UpdateZoneInput, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      _ <- isValidZoneAcl(updateZoneInput.acl).toResult
      _ <- connectionValidator.isValidBackendId(updateZoneInput.backendId).toResult
      existingZone <- getZoneOrFail(updateZoneInput.id)
      _ <- validateSharedZoneAuthorized(
        existingZone.shared,
        updateZoneInput.shared,
        auth.signedInUser).toResult
      _ <- canChangeZone(auth, existingZone.name, existingZone.adminGroupId).toResult
      _ <- adminGroupExists(updateZoneInput.adminGroupId)
      // if admin group changes, this confirms user has access to new group
      _ <- canChangeZone(auth, updateZoneInput.name, updateZoneInput.adminGroupId).toResult
      zoneWithUpdates = Zone(updateZoneInput, existingZone)
      _ <- validateZoneConnectionIfChanged(zoneWithUpdates, existingZone)
      updateZoneChange <- ZoneChangeGenerator
        .forUpdate(zoneWithUpdates, existingZone, auth)
        .toResult
      _ <- messageQueue.send(updateZoneChange).toResult[Unit]
    } yield updateZoneChange

  def deleteZone(zoneId: String, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canChangeZone(auth, zone.name, zone.adminGroupId).toResult
      deleteZoneChange <- ZoneChangeGenerator.forDelete(zone, auth).toResult
      _ <- messageQueue.send(deleteZoneChange).toResult[Unit]
    } yield deleteZoneChange

  def syncZone(zoneId: String, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canChangeZone(auth, zone.name, zone.adminGroupId).toResult
      _ <- outsideSyncDelay(zone).toResult
      syncZoneChange <- ZoneChangeGenerator.forSync(zone, auth).toResult
      _ <- messageQueue.send(syncZoneChange).toResult[Unit]
    } yield syncZoneChange

  def getZone(zoneId: String, auth: AuthPrincipal): Result[ZoneInfo] =
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canSeeZone(auth, zone).toResult
      aclInfo <- getZoneAclDisplay(zone.acl)
      groupName <- getGroupName(zone.adminGroupId)
    } yield ZoneInfo(zone, aclInfo, groupName)

  def getZoneByName(zoneName: String, auth: AuthPrincipal): Result[ZoneInfo] =
    for {
      zone <- getZoneByNameOrFail(ensureTrailingDot(zoneName))
      _ <- canSeeZone(auth, zone).toResult
      aclInfo <- getZoneAclDisplay(zone.acl)
      groupName <- getGroupName(zone.adminGroupId)
    } yield ZoneInfo(zone, aclInfo, groupName)

  def listZones(
      authPrincipal: AuthPrincipal,
      nameFilter: Option[String] = None,
      startFrom: Option[String] = None,
      maxItems: Int = 100): Result[ListZonesResponse] = {
    for {
      listZonesResult <- zoneRepository.listZones(authPrincipal, nameFilter, startFrom, maxItems)
      zones = listZonesResult.zones
      groupIds = zones.map(_.adminGroupId).toSet
      groups <- groupRepository.getGroups(groupIds)
      zoneSummaryInfos = zoneAdminGroupMapping(zones, groups)
    } yield
      ListZonesResponse(
        zoneSummaryInfos,
        listZonesResult.zonesFilter,
        listZonesResult.startFrom,
        listZonesResult.nextId,
        listZonesResult.maxItems)
  }.toResult

  def zoneAdminGroupMapping(zones: List[Zone], groups: Set[Group]): List[ZoneSummaryInfo] =
    zones.map { zn =>
      val groupName = groups.find(_.id == zn.adminGroupId) match {
        case Some(group) => group.name
        case None => "Unknown group name"
      }
      ZoneSummaryInfo(zn, groupName)
    }

  def listZoneChanges(
      zoneId: String,
      authPrincipal: AuthPrincipal,
      startFrom: Option[String] = None,
      maxItems: Int = 100): Result[ListZoneChangesResponse] =
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canSeeZone(authPrincipal, zone).toResult
      zoneChangesResults <- zoneChangeRepository
        .listZoneChanges(zone.id, startFrom, maxItems)
        .toResult[ListZoneChangesResults]
    } yield ListZoneChangesResponse(zone.id, zoneChangesResults)

  def addACLRule(
      zoneId: String,
      aclRuleInfo: ACLRuleInfo,
      authPrincipal: AuthPrincipal): Result[ZoneCommandResult] = {
    val newRule = ACLRule(aclRuleInfo)
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canChangeZone(authPrincipal, zone.name, zone.adminGroupId).toResult
      _ <- isValidAclRule(newRule).toResult
      zoneChange <- ZoneChangeGenerator
        .forUpdate(
          newZone = zone.addACLRule(newRule),
          oldZone = zone,
          authPrincipal = authPrincipal
        )
        .toResult
      _ <- messageQueue.send(zoneChange).toResult[Unit]
    } yield zoneChange
  }

  def deleteACLRule(
      zoneId: String,
      aclRuleInfo: ACLRuleInfo,
      authPrincipal: AuthPrincipal): Result[ZoneCommandResult] = {
    val newRule = ACLRule(aclRuleInfo)
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canChangeZone(authPrincipal, zone.name, zone.adminGroupId).toResult
      zoneChange <- ZoneChangeGenerator
        .forUpdate(
          newZone = zone.deleteACLRule(newRule),
          oldZone = zone,
          authPrincipal = authPrincipal
        )
        .toResult
      _ <- messageQueue.send(zoneChange).toResult[Unit]
    } yield zoneChange
  }

  def getBackendIds(): Result[List[String]] =
    VinylDNSConfig.configuredDnsConnections.dnsBackends.map(_.id).toResult

  def zoneDoesNotExist(zoneName: String): Result[Unit] =
    zoneRepository
      .getZoneByName(zoneName)
      .map {
        case Some(existingZone) if existingZone.status != ZoneStatus.Deleted =>
          ZoneAlreadyExistsError(s"Zone with name $zoneName already exists").asLeft
        case _ => ().asRight
      }
      .toResult

  def adminGroupExists(groupId: String): Result[Unit] =
    groupRepository
      .getGroup(groupId)
      .map {
        case Some(_) => ().asRight
        case None => InvalidGroupError(s"Admin group with ID $groupId does not exist").asLeft
      }
      .toResult

  def getGroupName(groupId: String): Result[String] = {
    groupRepository.getGroup(groupId).map {
      case Some(group) => group.name
      case None => "Unknown group name"
    }
  }.toResult

  def getZoneOrFail(zoneId: String): Result[Zone] =
    zoneRepository
      .getZone(zoneId)
      .orFail(ZoneNotFoundError(s"Zone with id $zoneId does not exists"))
      .toResult[Zone]

  def getZoneByNameOrFail(zoneName: String): Result[Zone] =
    zoneRepository
      .getZoneByName(zoneName)
      .orFail(ZoneNotFoundError(s"Zone with name $zoneName does not exists"))
      .toResult[Zone]

  def validateZoneConnectionIfChanged(newZone: Zone, existingZone: Zone): Result[Unit] =
    if (newZone.connection != existingZone.connection
      || newZone.transferConnection != existingZone.transferConnection) {
      connectionValidator.validateZoneConnections(newZone)
    } else {
      ().toResult
    }

  def getZoneAclDisplay(zoneAcl: ZoneACL): Result[ZoneACLInfo] = {
    val (withUserId, without) = zoneAcl.rules.partition(_.userId.isDefined)
    val (withGroupId, _) = without.partition(_.groupId.isDefined)

    val userIdsToFetch = withUserId.map(_.userId.get)
    val groupIdsToFetch = withGroupId.map(_.groupId.get)

    for {
      users <- userRepository.getUsers(userIdsToFetch, None, None).map(_.users).toResult[Seq[User]]
      groups <- groupRepository.getGroups(groupIdsToFetch).toResult[Set[Group]]
      groupMap = groups.map(g => (g.id, g.name)).toMap
      userMap = users.map(u => (u.id, u.userName)).toMap
      ruleInfos = zoneAcl.rules.map { rule =>
        (rule.userId, rule.groupId) match {
          case (Some(uid), _) => ACLRuleInfo(rule, userMap.get(uid))
          case (_, Some(gid)) => ACLRuleInfo(rule, groupMap.get(gid))
          case _ => ACLRuleInfo(rule, Some("All Users"))
        }
      }
    } yield ZoneACLInfo(ruleInfos.filter(_.displayName.isDefined))
  }
}
