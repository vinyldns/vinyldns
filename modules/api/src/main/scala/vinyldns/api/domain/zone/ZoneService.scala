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
import vinyldns.api.Interfaces._
import vinyldns.api.domain.AccessValidationAlgebra
import vinyldns.api.domain.auth.AuthPrincipal
import vinyldns.api.domain.engine.EngineCommandBus
import vinyldns.api.domain.membership.{Group, GroupRepository, User, UserRepository}

import scala.concurrent.ExecutionContext

class ZoneService(
    zoneRepository: ZoneRepository,
    groupRepository: GroupRepository,
    userRepository: UserRepository,
    zoneChangeRepository: ZoneChangeRepository,
    connectionValidator: ZoneConnectionValidatorAlgebra,
    commandBus: EngineCommandBus,
    zoneValidations: ZoneValidations,
    accessValidation: AccessValidationAlgebra)(implicit ec: ExecutionContext)
    extends ZoneServiceAlgebra {

  import accessValidation._
  import zoneValidations._

  def connectToZone(zone: Zone, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      _ <- isValidZoneAcl(zone.acl).toResult
      _ <- connectionValidator.validateZoneConnections(zone)
      _ <- zoneDoesNotExist(zone)
      _ <- adminGroupExists(zone.adminGroupId)
      _ <- userIsMemberOfGroup(zone.adminGroupId, auth).toResult
      createZoneChange <- ZoneChange.forAdd(zone, auth).toResult
      send <- commandBus.sendZoneCommand(createZoneChange)
    } yield send

  def updateZone(newZone: Zone, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      _ <- isValidZoneAcl(newZone.acl).toResult
      existingZone <- getZoneOrFail(newZone.id)
      _ <- validateZoneConnectionIfChanged(newZone, existingZone)
      _ <- canChangeZone(auth, existingZone).toResult
      _ <- adminGroupExists(newZone.adminGroupId)
      _ <- userIsMemberOfGroup(newZone.adminGroupId, auth).toResult
      updateZoneChange <- ZoneChange.forUpdate(newZone, existingZone, auth).toResult
      send <- commandBus.sendZoneCommand(updateZoneChange)
    } yield send

  def deleteZone(zoneId: String, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canChangeZone(auth, zone).toResult
      deleteZoneChange <- ZoneChange.forDelete(zone, auth).toResult
      send <- commandBus.sendZoneCommand(deleteZoneChange)
    } yield send

  def syncZone(zoneId: String, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canChangeZone(auth, zone).toResult
      _ <- outsideSyncDelay(zone).toResult
      syncZoneChange <- ZoneChange.forSync(zone, auth).toResult
      send <- commandBus.sendZoneCommand(syncZoneChange)
    } yield send

  def getZone(zoneId: String, auth: AuthPrincipal): Result[ZoneInfo] =
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canSeeZone(auth, zone).toResult
      aclInfo <- getZoneAclDisplay(zone.acl)
      groupName <- getGroupName(zone.adminGroupId)
    } yield ZoneInfo(zone, aclInfo, groupName)

  def listZones(
      authPrincipal: AuthPrincipal,
      nameFilter: Option[String] = None,
      startFrom: Option[Int] = None,
      maxItems: Int = 100): Result[ListZonesResponse] = {
    for {
      zones <- zoneRepository.listZones(authPrincipal, nameFilter, startFrom, maxItems)
      groupIds = zones.map(_.adminGroupId).toSet
      groups <- groupRepository.getGroups(groupIds)
      zoneSummaryInfos = zoneAdminGroupMapping(zones, groups)
    } yield
      ListZonesResponse(
        zones = zoneSummaryInfos,
        nameFilter = nameFilter,
        startFrom = startFrom,
        nextId = if (zones.size < maxItems) None else startFrom.orElse(Some(0)).map(_ + zones.size),
        maxItems = maxItems
      )
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
      _ <- canChangeZone(authPrincipal, zone).toResult
      _ <- isValidAclRule(newRule).toResult
      zoneChange <- ZoneChange
        .forUpdate(
          newZone = zone.addACLRule(newRule),
          oldZone = zone,
          authPrincipal = authPrincipal
        )
        .toResult
      send <- commandBus.sendZoneCommand(zoneChange)
    } yield send
  }

  def deleteACLRule(
      zoneId: String,
      aclRuleInfo: ACLRuleInfo,
      authPrincipal: AuthPrincipal): Result[ZoneCommandResult] = {
    val newRule = ACLRule(aclRuleInfo)
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canChangeZone(authPrincipal, zone).toResult
      zoneChange <- ZoneChange
        .forUpdate(
          newZone = zone.deleteACLRule(newRule),
          oldZone = zone,
          authPrincipal = authPrincipal
        )
        .toResult
      send <- commandBus.sendZoneCommand(zoneChange)
    } yield send
  }

  def zoneDoesNotExist(zone: Zone): Result[Unit] =
    zoneRepository
      .getZoneByName(zone.name)
      .map {
        case Some(existingZone) if existingZone.status != ZoneStatus.Deleted =>
          ZoneAlreadyExistsError(s"Zone with name ${zone.name} already exists").asLeft
        case _ => ().asRight
      }
      .toResult

  def adminGroupExists(groupId: String): Result[Unit] =
    groupRepository
      .getGroup(groupId)
      .map {
        case Some(_) => ().asRight
        case None => InvalidZoneAdminError(s"Admin group with ID $groupId does not exist").asLeft
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
      .orFail(ZoneNotFoundError(s"Zone with id ${zoneId} does not exists"))
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
