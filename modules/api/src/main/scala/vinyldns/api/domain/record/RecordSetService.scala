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
import org.slf4j.{Logger, LoggerFactory}
import org.xbill.DNS.ReverseMap
import vinyldns.api.config.{DottedHostsConfig, HighValueDomainConfig, ZoneAuthConfigs}
import vinyldns.api.domain.DomainValidations.{validateIpv4Address, validateIpv6Address}
import vinyldns.api.domain.access.AccessValidationsAlgebra
import vinyldns.core.domain.record.NameSort.NameSort
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.DomainHelpers.ensureTrailingDot
import vinyldns.core.domain.backend.{Backend, BackendResolver}
import vinyldns.core.domain.record.RecordTypeSort.RecordTypeSort
import vinyldns.core.notifier.{AllNotifiers, Notification}

import scala.util.matching.Regex

object RecordSetService {
  def apply(
             dataAccessor: ApiDataAccessor,
             messageQueue: MessageQueue,
             accessValidation: AccessValidationsAlgebra,
             backendResolver: BackendResolver,
             validateRecordLookupAgainstDnsBackend: Boolean,
             highValueDomainConfig: HighValueDomainConfig,
             dottedHostsConfig: DottedHostsConfig,
             approvedNameServers: List[Regex],
             useRecordSetCache: Boolean,
             notifiers: AllNotifiers
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
      dottedHostsConfig,
      approvedNameServers,
      useRecordSetCache,
      notifiers

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
                        dottedHostsConfig: DottedHostsConfig,
                        approvedNameServers: List[Regex],
                        useRecordSetCache: Boolean,
                        notifiers: AllNotifiers
                      ) extends RecordSetServiceAlgebra {

  import RecordSetValidations._
  import accessValidation._

  val logger: Logger = LoggerFactory.getLogger(classOf[RecordSetService])

  val approverOwnerShipTransferStatus = List(OwnerShipTransferStatus.ManuallyApproved , OwnerShipTransferStatus.AutoApproved, OwnerShipTransferStatus.ManuallyRejected)
  val requestorOwnerShipTransferStatus = List(OwnerShipTransferStatus.Cancelled , OwnerShipTransferStatus.Requested, OwnerShipTransferStatus.PendingReview)

  def addRecordSet(recordSet: RecordSet, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      zone <- getZone(recordSet.zoneId)
      authZones = dottedHostsConfig.zoneAuthConfigs.map(x => x.zone)
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
      allowedZoneList <- getAllowedZones(authZones).toResult[Set[String]]
      isInAllowedUsers = checkIfInAllowedUsers(zone, dottedHostsConfig, auth)
      isUserInAllowedGroups <- checkIfInAllowedGroups(zone, dottedHostsConfig, auth).toResult[Boolean]
      isAllowedUser = isInAllowedUsers || isUserInAllowedGroups
      isRecordTypeAllowed = checkIfInAllowedRecordType(zone, dottedHostsConfig, rsForValidations)
      isRecordTypeAndUserAllowed = isAllowedUser && isRecordTypeAllowed
      allowedDotsLimit = getAllowedDotsLimit(zone, dottedHostsConfig)
      recordFqdnDoesNotAlreadyExist <- recordFQDNDoesNotExist(rsForValidations, zone).toResult[Boolean]
      _ <- typeSpecificValidations(
        rsForValidations,
        existingRecordsWithName,
        zone,
        None,
        approvedNameServers,
        recordFqdnDoesNotAlreadyExist,
        allowedZoneList,
        isRecordTypeAndUserAllowed,
        allowedDotsLimit
      ).toResult
      _ <- if(allowedZoneList.contains(zone.name)) checkAllowedDots(allowedDotsLimit, rsForValidations, zone).toResult else ().toResult
      _ <- if(allowedZoneList.contains(zone.name)) isNotApexEndsWithDot(rsForValidations, zone).toResult else ().toResult
      _ <- messageQueue.send(change).toResult[Unit]
    } yield change

  def updateRecordSet(recordSet: RecordSet, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      zone <- getZone(recordSet.zoneId)
      existing <- getRecordSet(recordSet.id)
      _ <- unchangedRecordName(existing, recordSet, zone).toResult
      _ <- unchangedRecordType(existing, recordSet).toResult
      _ <- unchangedZoneId(existing, recordSet).toResult
      _ <- if(requestorOwnerShipTransferStatus.contains(recordSet.recordSetGroupChange.map(_.ownerShipTransferStatus).getOrElse("<none>"))
        && !auth.isSuper && !auth.isGroupMember(existing.ownerGroupId.getOrElse("None"))) {
        unchangedRecordSet (existing, recordSet).toResult} else ().toResult
      _ <- if(existing.recordSetGroupChange.exists(_.ownerShipTransferStatus == OwnerShipTransferStatus.Cancelled)
        && !auth.isSuper) {
        recordSetOwnerShipApproveStatus(recordSet).toResult
      } else ().toResult
      _ = logger.info(s"updated recordsetgroupchange: ${recordSet.recordSetGroupChange}")
      _ = logger.info(s"existing recordsetgroupchange: ${existing.recordSetGroupChange}")
      recordSet <- updateRecordSetGroupChangeStatus(recordSet, existing, zone, auth)
      change <- RecordSetChangeGenerator.forUpdate(existing, recordSet, zone, Some(auth)).toResult
      // because changes happen to the RS in forUpdate itself, converting 1st and validating on that
      rsForValidations = change.recordSet
      superUserCanUpdateOwnerGroup = canSuperUserUpdateOwnerGroup(existing, recordSet, zone, auth)
      _ <- isNotHighValueDomain(recordSet, zone, highValueDomainConfig).toResult
      _ <- if(requestorOwnerShipTransferStatus.contains(recordSet.recordSetGroupChange.map(_.ownerShipTransferStatus).getOrElse("<none>"))
        && !auth.isSuper && !auth.isGroupMember(existing.ownerGroupId.getOrElse("None"))) ().toResult
      else canUpdateRecordSet(auth, existing.name, existing.typ, zone, existing.ownerGroupId, superUserCanUpdateOwnerGroup).toResult
      ownerGroup <- getGroupIfProvided(rsForValidations.ownerGroupId)
      ownerTransferGroup <- getGroupInfo(rsForValidations.recordSetGroupChange.map(_.requestedOwnerGroupId.getOrElse("None")))
      _ <- if(requestorOwnerShipTransferStatus.contains(recordSet.recordSetGroupChange.map(_.ownerShipTransferStatus).getOrElse("<none>"))
        && !auth.isSuper && !auth.isGroupMember(existing.ownerGroupId.getOrElse("None")))
        canUseOwnerGroup(rsForValidations.recordSetGroupChange.map(_.requestedOwnerGroupId.getOrElse("None")), ownerTransferGroup, auth).toResult
      else if(approverOwnerShipTransferStatus.contains(recordSet.recordSetGroupChange.map(_.ownerShipTransferStatus).getOrElse("<none>"))
        && !auth.isSuper) canUseOwnerGroup(existing.ownerGroupId, ownerGroup, auth).toResult
      else canUseOwnerGroup(rsForValidations.ownerGroupId, ownerGroup, auth).toResult
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
      authZones = dottedHostsConfig.zoneAuthConfigs.map(x => x.zone)
      allowedZoneList <- getAllowedZones(authZones).toResult[Set[String]]
      isInAllowedUsers = checkIfInAllowedUsers(zone, dottedHostsConfig, auth)
      isUserInAllowedGroups <- checkIfInAllowedGroups(zone, dottedHostsConfig, auth).toResult[Boolean]
      isAllowedUser = isInAllowedUsers || isUserInAllowedGroups
      isRecordTypeAllowed = checkIfInAllowedRecordType(zone, dottedHostsConfig, rsForValidations)
      isRecordTypeAndUserAllowed = isAllowedUser && isRecordTypeAllowed
      allowedDotsLimit = getAllowedDotsLimit(zone, dottedHostsConfig)
      _ <- typeSpecificValidations(
        rsForValidations,
        existingRecordsWithName,
        zone,
        Some(existing),
        approvedNameServers,
        true,
        allowedZoneList,
        isRecordTypeAndUserAllowed,
        allowedDotsLimit
      ).toResult
      _ <- if(existing.name == rsForValidations.name) ().toResult else if(allowedZoneList.contains(zone.name)) checkAllowedDots(allowedDotsLimit, rsForValidations, zone).toResult else ().toResult
      _ <- if(allowedZoneList.contains(zone.name)) isNotApexEndsWithDot(rsForValidations, zone).toResult else ().toResult
      _ <- messageQueue.send(change).toResult[Unit]
      _ <- if(recordSet.recordSetGroupChange.isDefined &&
        recordSet.recordSetGroupChange.exists(rsgc =>
          rsgc.ownerShipTransferStatus != OwnerShipTransferStatus.None &&
            rsgc.ownerShipTransferStatus != OwnerShipTransferStatus.AutoApproved))
        notifiers.notify(Notification(change)).toResult
      else ().toResult
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

  def getGroupInfo(ids: Option[String]): Result[Option[Group]]= {
    ids match {
      case Some("None") => EitherT.pure[IO, Throwable](None)
      case Some(value) => getGroupIfProvided(Some(value))
      case None => EitherT.pure[IO, Throwable](None)
    }

  }

  //update ownership transfer is zone is shared
  def updateRecordSetGroupChangeStatus(recordSet: RecordSet, existing: RecordSet, zone: Zone, authPrincipal: AuthPrincipal): Result[RecordSet] = {
    val existingOwnerShipTransfer = existing.recordSetGroupChange.getOrElse(OwnerShipTransfer.apply(OwnerShipTransferStatus.None, Some("none")))
    val ownerShipTransfer = recordSet.recordSetGroupChange.getOrElse(OwnerShipTransfer.apply(OwnerShipTransferStatus.None, Some("none")))
    if (recordSet.recordSetGroupChange.isDefined &&
      ownerShipTransfer.ownerShipTransferStatus != OwnerShipTransferStatus.None)
      if (zone.shared){
        if (approverOwnerShipTransferStatus.contains(ownerShipTransfer.ownerShipTransferStatus)) {
          val recordSetOwnerApproval =
            ownerShipTransfer.ownerShipTransferStatus match {
              case OwnerShipTransferStatus.ManuallyApproved =>
                recordSet.copy(ownerGroupId = existingOwnerShipTransfer.requestedOwnerGroupId,
                  recordSetGroupChange = Some(ownerShipTransfer.copy(ownerShipTransferStatus = OwnerShipTransferStatus.ManuallyApproved,
                    requestedOwnerGroupId = existingOwnerShipTransfer.requestedOwnerGroupId)))
              case OwnerShipTransferStatus.ManuallyRejected =>
                recordSet.copy(
                  recordSetGroupChange = Some(ownerShipTransfer.copy(ownerShipTransferStatus = OwnerShipTransferStatus.ManuallyRejected,
                    requestedOwnerGroupId = existingOwnerShipTransfer.requestedOwnerGroupId)))
              case OwnerShipTransferStatus.AutoApproved =>
                recordSet.copy(
                  ownerGroupId = ownerShipTransfer.requestedOwnerGroupId,
                  recordSetGroupChange = Some(ownerShipTransfer.copy(ownerShipTransferStatus = OwnerShipTransferStatus.AutoApproved,
                    requestedOwnerGroupId = ownerShipTransfer.requestedOwnerGroupId)))

              case _ => recordSet.copy(
                recordSetGroupChange = Some(ownerShipTransfer.copy(
                  ownerShipTransferStatus = OwnerShipTransferStatus.None,
                  requestedOwnerGroupId = None)))
            }
          for {
            _ <- if(existingOwnerShipTransfer != ownerShipTransfer && !(authPrincipal.isSuper && recordSet.ownerGroupId == existingOwnerShipTransfer.requestedOwnerGroupId)) {
              canChangeFromPendingReview(existingOwnerShipTransfer.ownerShipTransferStatus, ownerShipTransfer.ownerShipTransferStatus).toResult}
            else ().toResult
            recordSet <- recordSetOwnerApproval.toResult
          } yield recordSet
        }
        else {
          val recordSetOwnerRequest =
            ownerShipTransfer.ownerShipTransferStatus match {
              case OwnerShipTransferStatus.Cancelled =>
                recordSet.copy(recordSetGroupChange = Some(ownerShipTransfer.copy(
                  ownerShipTransferStatus = OwnerShipTransferStatus.Cancelled,
                  requestedOwnerGroupId = existingOwnerShipTransfer.requestedOwnerGroupId)))
              case OwnerShipTransferStatus.PendingReview => recordSet
              case OwnerShipTransferStatus.Requested | OwnerShipTransferStatus.PendingReview if existing.ownerGroupId.isDefined =>
                recordSet.copy(
                recordSetGroupChange = Some(ownerShipTransfer.copy(ownerShipTransferStatus = OwnerShipTransferStatus.PendingReview)))
              case OwnerShipTransferStatus.Requested if existing.ownerGroupId.isEmpty => recordSet.copy(
                ownerGroupId = ownerShipTransfer.requestedOwnerGroupId,
                recordSetGroupChange = Some(ownerShipTransfer.copy(
                  ownerShipTransferStatus = OwnerShipTransferStatus.AutoApproved,
                  requestedOwnerGroupId = ownerShipTransfer.requestedOwnerGroupId)))
            }
          for {
            _ <- if (existingOwnerShipTransfer != ownerShipTransfer) {
              for {
                _ <- isValidOwnerShipTransferStatus(recordSet.recordSetGroupChange).toResult
                _ <- isAlreadyOwnerGroupMember(existing, recordSet).toResult
                _ <- isValidCancelOwnerShipTransferStatus(
                  existingOwnerShipTransfer.ownerShipTransferStatus,
                  ownerShipTransfer.ownerShipTransferStatus).toResult
                _ <- canCancelOwnershipTransfer(recordSet,authPrincipal).toResult
              } yield ()
            } else ().toResult
            recordSet <- recordSetOwnerRequest.toResult
          } yield recordSet
        }
      } else for {
        _ <- unchangedRecordSetOwnershipStatus(recordSet, existing).toResult
      } yield recordSet.copy(
        ownerGroupId = recordSet.ownerGroupId,
        recordSetGroupChange = existing.recordSetGroupChange)
    else recordSet.copy(
      ownerGroupId = recordSet.ownerGroupId,
      recordSetGroupChange = existing.recordSetGroupChange).toResult
  }

  // For dotted hosts. Check if a record that may conflict with dotted host exist or not
  def recordFQDNDoesNotExist(newRecordSet: RecordSet, zone: Zone): IO[Boolean] = {
    // Use fqdn for searching through `recordset` mysql table to see if it already exist
    val newRecordFqdn = if(newRecordSet.name != zone.name) newRecordSet.name + "." + zone.name else newRecordSet.name

    for {
      record <- recordSetRepository.getRecordSetsByFQDNs(Set(newRecordFqdn))
      isRecordAlreadyExist = doesRecordWithSameTypeExist(record, newRecordSet)
      doesNotExist = if(isRecordAlreadyExist) false else true
    } yield doesNotExist
  }

  // Check if a record with same type already exist in 'recordset' mysql table
  def doesRecordWithSameTypeExist(oldRecord: List[RecordSet], newRecord: RecordSet): Boolean = {
    if(oldRecord.nonEmpty) {
      val typeExists = oldRecord.map(x => x.typ == newRecord.typ)
      if (typeExists.contains(true)) true else false
    }
    else {
      false
    }
  }

  // Get zones that are allowed to create dotted hosts using the zones present in dotted hosts config
  def  getAllowedZones(zones: List[String]): IO[Set[String]] = {
    if(zones.isEmpty){
      val noZones: IO[Set[String]] = IO(Set.empty)
      noZones
    }
    else {
      // Wildcard zones needs to be passed to a separate method
      val wildcardZones = zones.filter(_.contains("*")).map(_.replace("*", "%"))
      // Zones without wildcard character are passed to a separate function
      val namedZones = zones.filter(zone => !zone.contains("*"))
      for{
        namedZoneResult <- zoneRepository.getZonesByNames(namedZones.toSet)
        wildcardZoneResult <- zoneRepository.getZonesByFilters(wildcardZones.toSet)
        zoneResult = namedZoneResult ++ wildcardZoneResult // Combine the zones
      } yield zoneResult.map(x => x.name)
    }
  }

  // Check if user is allowed to create dotted hosts using the users present in dotted hosts config
  def getAllowedDotsLimit(zone: Zone, config: DottedHostsConfig): Int = {
    val configZones = config.zoneAuthConfigs.map(x => x.zone)
    val zoneName = if(zone.name.takeRight(1) != ".") zone.name + "." else zone.name
    val dottedZoneConfig = configZones.filter(_.contains("*")).map(_.replace("*", "[A-Za-z0-9.]*"))
    val isContainWildcardZone = dottedZoneConfig.exists(x => zoneName.matches(x))
    val isContainNormalZone = configZones.contains(zoneName)
    if(isContainNormalZone){
      config.zoneAuthConfigs.filter(x => x.zone == zoneName).head.dotsLimit
    }
    else if(isContainWildcardZone){
      config.zoneAuthConfigs.filter(x => zoneName.matches(x.zone.replace("*", "[A-Za-z0-9.]*"))).head.dotsLimit
    }
    else {
      0
    }
  }

  // Check if user is allowed to create dotted hosts using the users present in dotted hosts config
  def checkIfInAllowedUsers(zone: Zone, config: DottedHostsConfig, auth: AuthPrincipal): Boolean = {
    val configZones = config.zoneAuthConfigs.map(x => x.zone)
    val zoneName = if(zone.name.takeRight(1) != ".") zone.name + "." else zone.name
    val dottedZoneConfig = configZones.filter(_.contains("*")).map(_.replace("*", "[A-Za-z0-9.]*"))
    val isContainWildcardZone = dottedZoneConfig.exists(x => zoneName.matches(x))
    val isContainNormalZone = configZones.contains(zoneName)
    if(isContainNormalZone){
      val users = config.zoneAuthConfigs.flatMap {
        x: ZoneAuthConfigs =>
          if (x.zone == zoneName) x.userList else List.empty
      }
      if(users.contains(auth.signedInUser.userName)){
        true
      }
      else {
        false
      }
    }
    else if(isContainWildcardZone){
      val users = config.zoneAuthConfigs.flatMap {
        x: ZoneAuthConfigs =>
          if (x.zone.contains("*")) {
            val wildcardZone = x.zone.replace("*", "[A-Za-z0-9.]*")
            if (zoneName.matches(wildcardZone)) x.userList else List.empty
          } else List.empty
      }
      if(users.contains(auth.signedInUser.userName)){
        true
      }
      else {
        false
      }
    }
    else {
      false
    }
  }

  // Check if user is allowed to create dotted hosts using the record types present in dotted hosts config
  def checkIfInAllowedRecordType(zone: Zone, config: DottedHostsConfig, rs: RecordSet): Boolean = {
    val configZones = config.zoneAuthConfigs.map(x => x.zone)
    val zoneName = if(zone.name.takeRight(1) != ".") zone.name + "." else zone.name
    val dottedZoneConfig = configZones.filter(_.contains("*")).map(_.replace("*", "[A-Za-z0-9.]*"))
    val isContainWildcardZone = dottedZoneConfig.exists(x => zoneName.matches(x))
    val isContainNormalZone = configZones.contains(zoneName)
    if(isContainNormalZone){
      val rType = config.zoneAuthConfigs.flatMap {
        x: ZoneAuthConfigs =>
          if (x.zone == zoneName) x.recordTypes else List.empty
      }
      if(rType.contains(rs.typ.toString)){
        true
      }
      else {
        false
      }
    }
    else if(isContainWildcardZone){
      val rType = config.zoneAuthConfigs.flatMap {
        x: ZoneAuthConfigs =>
          if (x.zone.contains("*")) {
            val wildcardZone = x.zone.replace("*", "[A-Za-z0-9.]*")
            if (zoneName.matches(wildcardZone)) x.recordTypes else List.empty
          } else List.empty
      }
      if(rType.contains(rs.typ.toString)){
        true
      }
      else {
        false
      }
    }
    else {
      false
    }
  }

  // Check if user is allowed to create dotted hosts using the groups present in dotted hosts config
  def checkIfInAllowedGroups(zone: Zone, config: DottedHostsConfig, auth: AuthPrincipal): IO[Boolean] = {
    val configZones = config.zoneAuthConfigs.map(x => x.zone)
    val zoneName = if(zone.name.takeRight(1) != ".") zone.name + "." else zone.name
    val dottedZoneConfig = configZones.filter(_.contains("*")).map(_.replace("*", "[A-Za-z0-9.]*"))
    val isContainWildcardZone = dottedZoneConfig.exists(x => zoneName.matches(x))
    val isContainNormalZone = configZones.contains(zoneName)
    val groups = if(isContainNormalZone){
      config.zoneAuthConfigs.flatMap {
        x: ZoneAuthConfigs =>
          if (x.zone == zoneName) x.groupList else List.empty
      }
    }
    else if(isContainWildcardZone){
      config.zoneAuthConfigs.flatMap {
        x: ZoneAuthConfigs =>
          if (x.zone.contains("*")) {
            val wildcardZone = x.zone.replace("*", "[A-Za-z0-9.]*")
            if (zoneName.matches(wildcardZone)) x.groupList else List.empty
          } else List.empty
      }
    }
    else {
      List.empty
    }
    for{
      groupsInConfig <- groupRepository.getGroupsByName(groups.toSet)
      members = groupsInConfig.flatMap(x => x.memberIds)
      usersList <- if(members.isEmpty) IO(Seq.empty) else userRepository.getUsers(members, None, None).map(x => x.users)
      users = if(usersList.isEmpty) Seq.empty else usersList.map(x => x.userName)
      isPresent = users.contains(auth.signedInUser.userName)
    } yield isPresent
  }

  def getRecordSet(
                    recordSetId: String,
                    authPrincipal: AuthPrincipal
                  ): Result[RecordSetInfo] =
    for {
      recordSet <- getRecordSet(recordSetId)
      groupName <- getGroupName(recordSet.ownerGroupId)
    } yield RecordSetInfo(recordSet, groupName)

  def getRecordSetCount(zoneId: String, authPrincipal: AuthPrincipal): Result[RecordSetCount] = {
    for {
      zone <- getZone(zoneId)
      _ <- canSeeZone(authPrincipal, zone).toResult
      count  <- recordSetRepository.getRecordSetCount(zoneId).toResult
    } yield RecordSetCount(count)
  }

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
      recordSetResults.recordTypeSort
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
                            startFrom: Option[Int] = None,
                            maxItems: Int = 100,
                            authPrincipal: AuthPrincipal
                          ): Result[ListRecordSetChangesResponse] =
      for {
        zone <- getZone(zoneId)
        _ <- canSeeZone(authPrincipal, zone).toResult
        recordSetChangesResults <- recordChangeRepository
          .listRecordSetChanges(Some(zone.id), startFrom, maxItems, None, None)
          .toResult[ListRecordSetChangesResults]
        recordSetChangesInfo <- buildRecordSetChangeInfo(recordSetChangesResults.items)
      } yield ListRecordSetChangesResponse(zoneId, recordSetChangesResults, recordSetChangesInfo)

  def listRecordSetChangeHistory(
                            zoneId: Option[String] = None,
                            startFrom: Option[Int] = None,
                            maxItems: Int = 100,
                            fqdn: Option[String] = None,
                            recordType: Option[RecordType] = None,
                            authPrincipal: AuthPrincipal
                          ): Result[ListRecordSetHistoryResponse] =
    for {
      zone <- getZone(zoneId.get)
      _ <- canSeeZone(authPrincipal, zone).toResult
      recordSetChangesResults <- recordChangeRepository
        .listRecordSetChanges(zoneId, startFrom, maxItems, fqdn, recordType)
        .toResult[ListRecordSetChangesResults]
      recordSetChangesInfo <- buildRecordSetChangeInfo(recordSetChangesResults.items)
    } yield ListRecordSetHistoryResponse(zoneId, recordSetChangesResults, recordSetChangesInfo)

  def listFailedRecordSetChanges(
                                  authPrincipal: AuthPrincipal,
                                  zoneId: Option[String] = None,
                                  startFrom: Int= 0,
                                  maxItems: Int = 100
                                ): Result[ListFailedRecordSetChangesResponse] =
    for {
      recordSetChangesFailedResults <- recordChangeRepository
        .listFailedRecordSetChanges(zoneId, maxItems, startFrom)
        .toResult[ListFailedRecordSetChangesResults]
      _ <- zoneAccess(recordSetChangesFailedResults.items, authPrincipal).toResult
    } yield
      ListFailedRecordSetChangesResponse(
        recordSetChangesFailedResults.items,
        recordSetChangesFailedResults.nextId,
        startFrom,
        maxItems)

  def zoneAccess(
                  RecordSetCh: List[RecordSetChange],
                  auth: AuthPrincipal
                ): List[Result[Unit]] =
    RecordSetCh.map { zn =>
      canSeeZone(auth, zn.zone).toResult
    }

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
