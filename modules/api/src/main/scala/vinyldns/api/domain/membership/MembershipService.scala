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

package vinyldns.api.domain.membership

import cats.implicits._
import vinyldns.api.Interfaces._
import vinyldns.api.repository.ApiDataAccessor
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.LockStatus.LockStatus
import vinyldns.core.domain.zone.ZoneRepository
import vinyldns.core.domain.membership._
import vinyldns.core.domain.record.RecordSetRepository
import vinyldns.core.Messages._
import vinyldns.api.config.GroupConfig

object MembershipService {
  def apply(dataAccessor: ApiDataAccessor, config: GroupConfig): MembershipService =
    new MembershipService(
      dataAccessor.groupRepository,
      dataAccessor.userRepository,
      dataAccessor.membershipRepository,
      dataAccessor.zoneRepository,
      dataAccessor.groupChangeRepository,
      dataAccessor.recordSetRepository,
      config
    )
}

class MembershipService(
    groupRepo: GroupRepository,
    userRepo: UserRepository,
    membershipRepo: MembershipRepository,
    zoneRepo: ZoneRepository,
    groupChangeRepo: GroupChangeRepository,
    recordSetRepo: RecordSetRepository,
    groupConfig: GroupConfig
) extends MembershipServiceAlgebra {

  import MembershipValidations._

  def createGroup(inputGroup: Group, authPrincipal: AuthPrincipal): Result[Group] = {
    val newGroup = inputGroup.addAdminUser(authPrincipal.signedInUser)
    val adminMembers = inputGroup.adminUserIds
    val nonAdminMembers = inputGroup.memberIds.diff(adminMembers)
    for {
      _ <- hasMembersAndAdmins(newGroup).toResult
      _ <- groupWithSameNameDoesNotExist(newGroup.name)
      _ <- groupWithSameEmailIdDoesNotExist(newGroup.email, groupConfig.enforceUniqueEmailId)
      _ <- usersExist(newGroup.memberIds)
      _ <- groupChangeRepo.save(GroupChange.forAdd(newGroup, authPrincipal)).toResult[GroupChange]
      _ <- groupRepo.save(newGroup).toResult[Group]
      // save admin and non-admin members separately
      _ <- membershipRepo
        .saveMembers(newGroup.id, adminMembers, isAdmin = true)
        .toResult[Set[String]]
      _ <- membershipRepo
        .saveMembers(newGroup.id, nonAdminMembers, isAdmin = false)
        .toResult[Set[String]]
    } yield newGroup
  }

  def updateGroup(
      groupId: String,
      name: String,
      email: String,
      description: Option[String],
      memberIds: Set[String],
      adminUserIds: Set[String],
      authPrincipal: AuthPrincipal
  ): Result[Group] =
    for {
      existingGroup <- getExistingGroup(groupId)
      newGroup = existingGroup.withUpdates(name, email, description, memberIds, adminUserIds)
      _ <- canEditGroup(existingGroup, authPrincipal).toResult
      addedAdmins = newGroup.adminUserIds.diff(existingGroup.adminUserIds)
      // new non-admin members ++ admins converted to non-admins
      addedNonAdmins = newGroup.memberIds.diff(existingGroup.memberIds).diff(addedAdmins) ++
        existingGroup.adminUserIds.diff(newGroup.adminUserIds).intersect(newGroup.memberIds)
      removedMembers = existingGroup.memberIds.diff(newGroup.memberIds)
      _ <- hasMembersAndAdmins(newGroup).toResult
      _ <- usersExist(addedNonAdmins)
      _ <- differentGroupWithSameNameDoesNotExist(newGroup.name, existingGroup.id)
      _ <- differentGroupWithSameEmailIdDoesNotExist(
        newGroup.email,
        existingGroup.id,
        groupConfig.enforceUniqueEmailId
      )
      _ <- groupChangeRepo
        .save(GroupChange.forUpdate(newGroup, existingGroup, authPrincipal))
        .toResult[GroupChange]
      _ <- groupRepo.save(newGroup).toResult[Group]
      // save admin and non-admin members separately
      _ <- membershipRepo
        .saveMembers(existingGroup.id, addedAdmins, isAdmin = true)
        .toResult[Set[String]]
      _ <- membershipRepo
        .saveMembers(existingGroup.id, addedNonAdmins, isAdmin = false)
        .toResult[Set[String]]
      _ <- membershipRepo.removeMembers(existingGroup.id, removedMembers).toResult[Set[String]]
    } yield newGroup

  def deleteGroup(groupId: String, authPrincipal: AuthPrincipal): Result[Group] =
    for {
      existingGroup <- getExistingGroup(groupId)
      _ <- canEditGroup(existingGroup, authPrincipal).toResult
      _ <- isNotZoneAdmin(existingGroup)
      _ <- isNotRecordOwnerGroup(existingGroup)
      _ <- isNotInZoneAclRule(existingGroup)
      _ <- groupChangeRepo
        .save(GroupChange.forDelete(existingGroup, authPrincipal))
        .toResult[GroupChange]
      _ <- membershipRepo
        .removeMembers(existingGroup.id, existingGroup.memberIds)
        .toResult[Set[String]]
      deletedGroup = existingGroup.copy(status = GroupStatus.Deleted)
      _ <- groupRepo.delete(deletedGroup).toResult[Group]
    } yield deletedGroup

  def getGroup(id: String, authPrincipal: AuthPrincipal): Result[Group] =
    for {
      group <- getExistingGroup(id)
      _ <- canSeeGroup(id, authPrincipal).toResult
    } yield group

  def listMembers(
      groupId: String,
      startFrom: Option[String],
      maxItems: Int,
      authPrincipal: AuthPrincipal
  ): Result[ListMembersResponse] =
    for {
      group <- getExistingGroup(groupId)
      _ <- canSeeGroup(groupId, authPrincipal).toResult
      result <- getUsers(group.memberIds, startFrom, Some(maxItems))
    } yield ListMembersResponse(
      result.users.map(MemberInfo(_, group)),
      startFrom,
      result.lastEvaluatedId,
      maxItems
    )

  def listAdmins(groupId: String, authPrincipal: AuthPrincipal): Result[ListAdminsResponse] =
    for {
      group <- getExistingGroup(groupId)
      _ <- canSeeGroup(groupId, authPrincipal).toResult
      result <- getUsers(group.adminUserIds, None, None)
    } yield ListAdminsResponse(result.users.map(UserInfo(_)))

  def listMyGroups(
      groupNameFilter: Option[String],
      startFrom: Option[String],
      maxItems: Int,
      authPrincipal: AuthPrincipal,
      ignoreAccess: Boolean
  ): Result[ListMyGroupsResponse] = {
    val groupsCall =
      if (authPrincipal.isSystemAdmin || ignoreAccess) {
        groupRepo.getAllGroups()
      } else {
        groupRepo.getGroups(authPrincipal.memberGroupIds.toSet)
      }

    groupsCall.map { grp =>
      pageListGroupsResponse(grp.toList, groupNameFilter, startFrom, maxItems, ignoreAccess)
    }
  }.toResult

  def pageListGroupsResponse(
      allGroups: Seq[Group],
      groupNameFilter: Option[String],
      startFrom: Option[String],
      maxItems: Int,
      ignoreAccess: Boolean
  ): ListMyGroupsResponse = {
    val allMyGroups = allGroups
      .filter(_.status == GroupStatus.Active)
      .sortBy(_.id)
      .map(GroupInfo.apply)

    val filtered = allMyGroups
      .filter(grp => groupNameFilter.forall(grp.name.contains(_)))
      .filter(grp => startFrom.forall(grp.id > _))

    val nextId = if (filtered.length > maxItems) Some(filtered(maxItems - 1).id) else None
    val groups = filtered.take(maxItems)

    ListMyGroupsResponse(groups, groupNameFilter, startFrom, nextId, maxItems, ignoreAccess)
  }

  def getGroupActivity(
      groupId: String,
      startFrom: Option[String],
      maxItems: Int,
      authPrincipal: AuthPrincipal
  ): Result[ListGroupChangesResponse] =
    for {
      _ <- canSeeGroup(groupId, authPrincipal).toResult
      result <- groupChangeRepo
        .getGroupChanges(groupId, startFrom, maxItems)
        .toResult[ListGroupChangesResults]
    } yield ListGroupChangesResponse(
      result.changes.map(GroupChangeInfo.apply),
      startFrom,
      result.lastEvaluatedTimeStamp,
      maxItems
    )

  def getUsers(
      userIds: Set[String],
      startFrom: Option[String] = None,
      pageSize: Option[Int] = None
  ): Result[ListUsersResults] =
    userRepo
      .getUsers(userIds, startFrom, pageSize)
      .toResult[ListUsersResults]

  def getExistingUser(userId: String): Result[User] =
    userRepo
      .getUser(userId)
      .orFail(UserNotFoundError(UserNotFoundErrorMsg.format(userId)))
      .toResult[User]

  def getExistingGroup(groupId: String): Result[Group] =
    groupRepo
      .getGroup(groupId)
      .orFail(GroupNotFoundError(GroupNotFoundErrorMsg.format(groupId)))
      .toResult[Group]

  def groupWithSameNameDoesNotExist(name: String): Result[Unit] =
    groupRepo
      .getGroupByName(name)
      .map {
        case Some(existingGroup) if existingGroup.status != GroupStatus.Deleted =>
          GroupAlreadyExistsError(GroupAlreadyExistsErrorMsg.format(name, existingGroup.email)).asLeft
        case _ =>
          ().asRight
      }
      .toResult

  def groupWithSameEmailIdDoesNotExist(emailAddress: String, enforceUniqueEmailId: Boolean): Result[Unit] =
    if (enforceUniqueEmailId) {
      groupRepo
        .getGroupByEmailAddress(emailAddress)
        .map {
          case Some(existingGroup) if existingGroup.status != GroupStatus.Deleted =>
            GroupAlreadyExistsError(
              GroupEmailExistsErrorMsg.format(existingGroup.name, emailAddress, existingGroup.email)
            ).asLeft
          case _ =>
            ().asRight
        }
        .toResult
    } else {
      ().toResult
    }

  def usersExist(userIds: Set[String]): Result[Unit] = {
    userRepo.getUsers(userIds, None, None).map { results =>
      val delta = userIds.diff(results.users.map(_.id).toSet)
      if (delta.isEmpty)
        ().asRight
      else
        UserNotFoundError(UsersNotFoundErrorMsg.format(delta.mkString(","))).asLeft
    }
  }.toResult

  def differentGroupWithSameNameDoesNotExist(name: String, groupId: String): Result[Unit] =
    groupRepo
      .getGroupByName(name)
      .map {
        case Some(existingGroup)
            if existingGroup.status != GroupStatus.Deleted && existingGroup.id != groupId =>
          GroupAlreadyExistsError(GroupAlreadyExistsErrorMsg.format(name, existingGroup.email)).asLeft
        case _ =>
          ().asRight
      }
      .toResult

  def differentGroupWithSameEmailIdDoesNotExist(emailAddress: String, groupId: String, enforceUniqueEmailId: Boolean): Result[Unit] =
    if (enforceUniqueEmailId) {
      groupRepo
        .getGroupByEmailAddress(emailAddress)
        .map {
          case Some(existingGroup)
              if existingGroup.status != GroupStatus.Deleted && existingGroup.id != groupId =>
            GroupAlreadyExistsError(
              GroupEmailExistsUpdateErrorMsg.format(existingGroup.name, emailAddress, existingGroup.email)
            ).asLeft
          case _ =>
            ().asRight
        }
        .toResult
    } else {
      ().toResult
    }

  def isNotZoneAdmin(group: Group): Result[Unit] =
    zoneRepo
      .getZonesByAdminGroupId(group.id)
      .map { zones =>
        ensuring(InvalidGroupRequestError(ZoneAdminErrorMsg.format(group.name)))(
          zones.isEmpty
        )
      }
      .toResult

  def isNotRecordOwnerGroup(group: Group): Result[Unit] =
    recordSetRepo
      .getFirstOwnedRecordByGroup(group.id)
      .map { rsId =>
        ensuring(
          InvalidGroupRequestError(
            RecordSetOwnerErrorMsg.format(group.name, rsId)
          )
        )(rsId.isEmpty)
      }
      .toResult

  def isNotInZoneAclRule(group: Group): Result[Unit] =
    zoneRepo
      .getFirstOwnedZoneAclGroupId(group.id)
      .map { zId =>
        ensuring(
          InvalidGroupRequestError(
            ACLRuleErrorMsg.format(group.name, zId)
          )
        )(zId.isEmpty)
      }
      .toResult

  def updateUserLockStatus(
      userId: String,
      lockStatus: LockStatus,
      authPrincipal: AuthPrincipal
  ): Result[User] =
    for {
      _ <- isSuperAdmin(authPrincipal).toResult
      existingUser <- getExistingUser(userId)
      newUser = existingUser.updateUserLockStatus(lockStatus)
      _ <- userRepo.save(newUser).toResult[User]
    } yield newUser
}
