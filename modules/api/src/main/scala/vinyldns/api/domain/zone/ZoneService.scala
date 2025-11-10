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

import cats.effect.IO
import cats.implicits._
import vinyldns.api.domain.access.AccessValidationsAlgebra
import vinyldns.api.Interfaces
import vinyldns.api.domain.membership.MembershipValidations.isSuperAdmin
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.api.repository.ApiDataAccessor
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.membership.{Group, GroupRepository, ListUsersResults, User, UserRepository}
import vinyldns.core.domain.zone._
import vinyldns.core.queue.MessageQueue
import vinyldns.core.domain.DomainHelpers.ensureTrailingDot
import vinyldns.core.domain.backend.BackendResolver
import com.cronutils.model.definition.CronDefinition
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import com.cronutils.model.CronType
import vinyldns.api.domain.membership.MembershipService

object ZoneService {
  def apply(
      dataAccessor: ApiDataAccessor,
      connectionValidator: ZoneConnectionValidatorAlgebra,
      messageQueue: MessageQueue,
      zoneValidations: ZoneValidations,
      accessValidation: AccessValidationsAlgebra,
      backendResolver: BackendResolver,
      crypto: CryptoAlgebra,
      membershipService:MembershipService
  ): ZoneService =
    new ZoneService(
      dataAccessor.zoneRepository,
      dataAccessor.groupRepository,
      dataAccessor.userRepository,
      dataAccessor.zoneChangeRepository,
      connectionValidator,
      messageQueue,
      zoneValidations,
      accessValidation,
      backendResolver,
      crypto,
      membershipService
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
    accessValidation: AccessValidationsAlgebra,
    backendResolver: BackendResolver,
    crypto: CryptoAlgebra,
    membershipService:MembershipService
) extends ZoneServiceAlgebra {

  import accessValidation._
  import zoneValidations._
  import Interfaces._

  def connectToZone(
      createZoneInput: CreateZoneInput,
      auth: AuthPrincipal
  ): Result[ZoneCommandResult] =
    for {
      createAclDottedHosts <- createZoneAcl(createZoneInput, auth)
      allowDottedHosts <- allowDottedHostsCreateZones(auth, createZoneInput)
      _ <- isValidZoneAcl(createZoneInput.acl).toResult
      _ <- membershipService.emailValidation(createZoneInput.email)
      _ <- connectionValidator.isValidBackendId(createZoneInput.backendId).toResult
      _ <- validateSharedZoneAuthorized(createZoneInput.shared, auth.signedInUser).toResult
      _ <- zoneDoesNotExist(createZoneInput.name)
      _ <- adminGroupExists(createZoneInput.adminGroupId)
      _ <- if(createZoneInput.recurrenceSchedule.isDefined) canScheduleZoneSync(auth).toResult else IO.unit.toResult
      isCronStringValid = if(createZoneInput.recurrenceSchedule.isDefined) isValidCronString(createZoneInput.recurrenceSchedule.get) else true
      _ <- validateCronString(isCronStringValid).toResult
      _ <- canChangeZone(auth, createZoneInput.name, createZoneInput.adminGroupId).toResult
      zoneToCreate = Zone(createZoneInput.copy(allowDottedHosts=allowDottedHosts, acl = createAclDottedHosts), auth.isTestUser)
      createdZoneInput = if(createZoneInput.recurrenceSchedule.isDefined) createZoneInput.copy(scheduleRequestor = Some(auth.signedInUser.userName)) else createZoneInput
      _ <- connectionValidator.validateZoneConnections(zoneToCreate)
      createZoneChange <- ZoneChangeGenerator.forAdd(zoneToCreate, auth).toResult
      _ <- messageQueue.send(createZoneChange).toResult[Unit]
    } yield createZoneChange

  def updateZone(updateZoneInput: UpdateZoneInput, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      updateAclDottedHosts <- updateZoneAcl(updateZoneInput, auth)
      allowDottedHosts <- allowDottedHostsUpdateZones(auth, updateZoneInput)
      _ <- isValidZoneAcl(updateZoneInput.acl).toResult
      _ <- membershipService.emailValidation(updateZoneInput.email)
      _ <- connectionValidator.isValidBackendId(updateZoneInput.backendId).toResult
      existingZone <- getZoneOrFail(updateZoneInput.id)
      _ <- validateSharedZoneAuthorized(
        existingZone.shared,
        updateZoneInput.shared,
        auth.signedInUser
      ).toResult
      _ <- canChangeZone(auth, existingZone.name, existingZone.adminGroupId).toResult
      _ <- if(updateZoneInput.recurrenceSchedule.isDefined) canScheduleZoneSync(auth).toResult else IO.unit.toResult
      isCronStringValid = if(updateZoneInput.recurrenceSchedule.isDefined) isValidCronString(updateZoneInput.recurrenceSchedule.get) else true
      _ <- validateCronString(isCronStringValid).toResult
      _ <- adminGroupExists(updateZoneInput.adminGroupId)
      // if admin group changes, this confirms user has access to new group
      _ <- canChangeZone(auth, updateZoneInput.name, updateZoneInput.adminGroupId).toResult
      zoneWithUpdates = Zone(updateZoneInput.copy(allowDottedHosts = allowDottedHosts, acl = updateAclDottedHosts), existingZone)
      updatedZoneInput = if(updateZoneInput.recurrenceSchedule.isDefined) updateZoneInput.copy(scheduleRequestor = Some(auth.signedInUser.userName)) else updateZoneInput
      _ <- validateZoneConnectionIfChanged(zoneWithUpdates, existingZone)
      updateZoneChange <- ZoneChangeGenerator
        .forUpdate(zoneWithUpdates, existingZone, auth, crypto)
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
      accessLevel = getZoneAccess(auth, zone)
    } yield ZoneInfo(zone, aclInfo, groupName, accessLevel)

  def getCommonZoneDetails(zoneId: String, auth: AuthPrincipal): Result[ZoneDetails] =
    for {
      zone <- getZoneOrFail(zoneId)
      groupName <- getGroupName(zone.adminGroupId)
    } yield ZoneDetails(zone, groupName)

  def getZoneByName(zoneName: String, auth: AuthPrincipal): Result[ZoneInfo] =
    for {
      zone <- getZoneByNameOrFail(ensureTrailingDot(zoneName))
      aclInfo <- getZoneAclDisplay(zone.acl)
      groupName <- getGroupName(zone.adminGroupId)
      accessLevel = getZoneAccess(auth, zone)
    } yield ZoneInfo(zone, aclInfo, groupName, accessLevel)

  // List zones. Uses zone name as default while using search to list zones or by admin group name if selected.
  def listZones(
      authPrincipal: AuthPrincipal,
      nameFilter: Option[String] = None,
      startFrom: Option[String] = None,
      maxItems: Int = 100,
      searchByAdminGroup: Boolean = false,
      ignoreAccess: Boolean = false,
      includeReverse: Boolean = true
  ): Result[ListZonesResponse] = {
    if(!searchByAdminGroup || nameFilter.isEmpty){
      for {
        listZonesResult <- zoneRepository.listZones(
          authPrincipal,
          nameFilter,
          startFrom,
          maxItems,
          ignoreAccess,
          includeReverse
      )
      zones = listZonesResult.zones
      groupIds = zones.map(_.adminGroupId).toSet
      groups <- groupRepository.getGroups(groupIds)
      zoneSummaryInfos = zoneSummaryInfoMapping(zones, authPrincipal, groups)
    } yield ListZonesResponse(
      zoneSummaryInfos,
      listZonesResult.zonesFilter,
      listZonesResult.startFrom,
      listZonesResult.nextId,
      listZonesResult.maxItems,
      listZonesResult.ignoreAccess,
      listZonesResult.includeReverse
    )}
    else {
      for {
        groupIds <- getGroupsIdsByName(nameFilter.get)
        listZonesResult <- zoneRepository.listZonesByAdminGroupIds(
          authPrincipal,
          startFrom,
          maxItems,
          groupIds,
          ignoreAccess,
          includeReverse
        )
        zones = listZonesResult.zones
        groups <- groupRepository.getGroups(groupIds)
        zoneSummaryInfos = zoneSummaryInfoMapping(zones, authPrincipal, groups)
      } yield ListZonesResponse(
        zoneSummaryInfos,
        nameFilter,
        listZonesResult.startFrom,
        listZonesResult.nextId,
        listZonesResult.maxItems,
        listZonesResult.ignoreAccess,
        listZonesResult.includeReverse
      )
    }
  }.toResult

  def listDeletedZones(
                        authPrincipal: AuthPrincipal,
                        nameFilter: Option[String] = None,
                        startFrom: Option[String] = None,
                        maxItems: Int = 100,
                        ignoreAccess: Boolean = false
                      ): Result[ListDeletedZoneChangesResponse] = {
    for {
      listZonesChangeResult <- zoneChangeRepository.listDeletedZones(
        authPrincipal,
        nameFilter,
        startFrom,
        maxItems,
        ignoreAccess
      )
      zoneChanges = listZonesChangeResult.zoneDeleted
      groupIds = zoneChanges.map(_.zone.adminGroupId).toSet
      groups <- groupRepository.getGroups(groupIds)
      userId = zoneChanges.map(_.userId).toSet
      users <- userRepository.getUsers(userId,None,None)
      zoneDeleteSummaryInfos = ZoneChangeDeletedInfoMapping(zoneChanges, authPrincipal, groups, users)
    } yield {
      ListDeletedZoneChangesResponse(
        zoneDeleteSummaryInfos,
        listZonesChangeResult.zoneChangeFilter,
        listZonesChangeResult.nextId,
        listZonesChangeResult.startFrom,
        listZonesChangeResult.maxItems,
        listZonesChangeResult.ignoreAccess
      )
    }
  }.toResult

  private def ZoneChangeDeletedInfoMapping(
                                            zoneChange: List[ZoneChange],
                                            auth: AuthPrincipal,
                                            groups: Set[Group],
                                            users: ListUsersResults
                                          ): List[ZoneChangeDeletedInfo] =
    zoneChange.map { zc =>
      val groupName = groups.find(_.id == zc.zone.adminGroupId) match {
        case Some(group) => group.name
        case None => "Unknown group name"
      }
      val userName = users.users.find(_.id == zc.userId) match {
        case Some(user) => user.userName
        case None => "Unknown user name"
      }
      val zoneAccess = getZoneAccess(auth, zc.zone)
      ZoneChangeDeletedInfo(zc, groupName,userName, zoneAccess)
    }

  def zoneSummaryInfoMapping(
      zones: List[Zone],
      auth: AuthPrincipal,
      groups: Set[Group]
  ): List[ZoneSummaryInfo] =
    zones.map { zn =>
      val groupName = groups.find(_.id == zn.adminGroupId) match {
        case Some(group) => group.name
        case None => "Unknown group name"
      }
      val zoneAccess = getZoneAccess(auth, zn)
      ZoneSummaryInfo(zn, groupName, zoneAccess)
    }

  def listZoneChanges(
      zoneId: String,
      authPrincipal: AuthPrincipal,
      startFrom: Option[String] = None,
      maxItems: Int = 100
  ): Result[ListZoneChangesResponse] =
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canSeeZoneChange(authPrincipal, zone).toResult
      zoneChangesResults <- zoneChangeRepository
        .listZoneChanges(zone.id, startFrom, maxItems)
        .toResult[ListZoneChangesResults]
    } yield ListZoneChangesResponse(zone.id, zoneChangesResults)

  def listFailedZoneChanges(
                             authPrincipal: AuthPrincipal,
                             startFrom: Int= 0,
                             maxItems: Int = 100
                           ): Result[ListFailedZoneChangesResponse] =
    for {
      zoneChangesFailedResults <- zoneChangeRepository
        .listFailedZoneChanges(maxItems, startFrom)
        .toResult[ListFailedZoneChangesResults]
      _ <- zoneAccess(zoneChangesFailedResults.items, authPrincipal).toResult
    } yield
      ListFailedZoneChangesResponse(
        zoneChangesFailedResults.items,
        zoneChangesFailedResults.nextId,
        startFrom,
        maxItems
      )

  def zoneAccess(
                  zoneCh: List[ZoneChange],
                  auth: AuthPrincipal
                ): List[Result[Unit]] =
    zoneCh.map { zn =>
      canSeeZone(auth, zn.zone).toResult
    }

  def addACLRule(
      zoneId: String,
      aclRuleInfo: ACLRuleInfo,
      authPrincipal: AuthPrincipal
  ): Result[ZoneCommandResult] = {
    val newRule = ACLRule(aclRuleInfo)
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canChangeZone(authPrincipal, zone.name, zone.adminGroupId).toResult
      _ <- isValidAclRule(newRule).toResult
      zoneChange <- ZoneChangeGenerator
        .forUpdate(
          newZone = zone.addACLRule(newRule),
          oldZone = zone,
          authPrincipal = authPrincipal,
          crypto
        )
        .toResult
      _ <- messageQueue.send(zoneChange).toResult[Unit]
    } yield zoneChange
  }

  def deleteACLRule(
      zoneId: String,
      aclRuleInfo: ACLRuleInfo,
      authPrincipal: AuthPrincipal
  ): Result[ZoneCommandResult] = {
    val newRule = ACLRule(aclRuleInfo)
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canChangeZone(authPrincipal, zone.name, zone.adminGroupId).toResult
      zoneChange <- ZoneChangeGenerator
        .forUpdate(
          newZone = zone.deleteACLRule(newRule),
          oldZone = zone,
          authPrincipal = authPrincipal,
          crypto
        )
        .toResult
      _ <- messageQueue.send(zoneChange).toResult[Unit]
    } yield zoneChange
  }

  def getGroupsIdsByName(groupName: String): IO[Set[String]] = {
    groupRepository.getGroupsByName(groupName).map(x => x.map(_.id))
  }

  def getBackendIds(): Result[List[String]] =
    backendResolver.ids.toList.toResult

  def isValidCronString(maybeString: String): Boolean = {
    val isValid = try {
      val cronDefinition: CronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
      val parser: CronParser = new CronParser(cronDefinition)
      val quartzCron = parser.parse(maybeString)
      quartzCron.validate
      true
    }
    catch {
      case _: Exception =>
        false
    }
    isValid
  }

  def validateCronString(isValid: Boolean): Either[Throwable, Unit] =
    ensuring(
      InvalidRequest("Invalid cron expression. Please enter a valid cron expression in 'recurrenceSchedule'.")
    )(
      isValid
    )

  def zoneDoesNotExist(zoneName: String): Result[Unit] =
    zoneRepository
      .getZoneByName(zoneName)
      .map {
        case Some(existingZone) if existingZone.status != ZoneStatus.Deleted =>
          ZoneAlreadyExistsError(
            s"Zone with name $zoneName already exists. " +
              s"Please contact ${existingZone.email} to request access to the zone."
          ).asLeft
        case _ => ().asRight
      }
      .toResult

  def allowDottedHostsUpdateZones(authPrincipal: AuthPrincipal, updateZoneInput: UpdateZoneInput): Result[Boolean] =
    if (updateZoneInput.allowDottedHosts == true || updateZoneInput.allowDottedLimits>0){
    isSuperAdmin(authPrincipal).isRight match {
      case true => updateZoneInput.allowDottedHosts.toResult
      case false => NotAuthorizedError("Not Authorised: User is not VinylDNS Admin").asLeft.toResult
    }} else updateZoneInput.allowDottedHosts.toResult

  def allowDottedHostsCreateZones(authPrincipal: AuthPrincipal, createZoneInput: CreateZoneInput): Result[Boolean] =
    if (createZoneInput.allowDottedHosts == true || createZoneInput.allowDottedLimits>0){
      isSuperAdmin(authPrincipal).isRight match {
      case true => createZoneInput.allowDottedHosts.toResult
      case false => NotAuthorizedError("Not Authorised: User is not VinylDNS Admin").asLeft.toResult
      }} else createZoneInput.allowDottedHosts.toResult

  def updateZoneAcl(updateZoneInput: UpdateZoneInput, authPrincipal: AuthPrincipal): Result[ZoneACL] = {
    updateZoneInput.allowDottedHosts match {
      case true =>
        if (isSuperAdmin(authPrincipal).isRight){ZoneACL(updateZoneInput.acl.rules)}.toResult
      else   NotAuthorizedError("Not Authorised: User is not VinylDNS Admin").asLeft.toResult
      case false => ZoneACL(updateZoneInput.acl.rules.map(_.copy(allowDottedHosts = false))).toResult}
  }

  def createZoneAcl(createZoneInput: CreateZoneInput, authPrincipal: AuthPrincipal): Result[ZoneACL] = {
    createZoneInput.allowDottedHosts match {
      case true => if (isSuperAdmin(authPrincipal).isRight){ZoneACL(createZoneInput.acl.rules)}.toResult
      else   NotAuthorizedError("Not Authorised: User is not VinylDNS Admin").asLeft.toResult
      case false => ZoneACL(createZoneInput.acl.rules.map(_.copy(allowDottedHosts = false))).toResult}
  }
  def canScheduleZoneSync(auth: AuthPrincipal): Either[Throwable, Unit] =
    ensuring(
      NotAuthorizedError(s"User '${auth.signedInUser.userName}' is not authorized to schedule zone sync in this zone.")
    )(
      auth.isSystemAdmin
    )

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
