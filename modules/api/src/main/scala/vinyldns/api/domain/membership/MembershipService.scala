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

import cats.effect.IO
import cats.implicits._
import scalikejdbc.DB
import vinyldns.api.Interfaces._
import vinyldns.api.config.ValidEmailConfig
import vinyldns.api.repository.ApiDataAccessor
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.LockStatus.LockStatus
import vinyldns.core.domain.zone.ZoneRepository
import vinyldns.core.domain.membership._
import vinyldns.core.domain.record.RecordSetRepository
import vinyldns.core.Messages._
import vinyldns.mysql.TransactionProvider

object MembershipService {
  def apply(dataAccessor: ApiDataAccessor,emailConfig:ValidEmailConfig): MembershipService =
    new MembershipService(
      dataAccessor.groupRepository,
      dataAccessor.userRepository,
      dataAccessor.membershipRepository,
      dataAccessor.zoneRepository,
      dataAccessor.groupChangeRepository,
      dataAccessor.recordSetRepository,
      emailConfig
    )
}

class MembershipService(
    groupRepo: GroupRepository,
    userRepo: UserRepository,
    membershipRepo: MembershipRepository,
    zoneRepo: ZoneRepository,
    groupChangeRepo: GroupChangeRepository,
    recordSetRepo: RecordSetRepository,
    validDomains: ValidEmailConfig
) extends MembershipServiceAlgebra with TransactionProvider {

  import MembershipValidations._

  def createGroup(inputGroup: Group, authPrincipal: AuthPrincipal): Result[Group] = {
    val newGroup = inputGroup.addAdminUser(authPrincipal.signedInUser)
    val adminMembers = inputGroup.adminUserIds
    val nonAdminMembers = inputGroup.memberIds.diff(adminMembers)
    for {
      _ <- groupValidation(newGroup)
      _ <- emailValidation(newGroup.email)
      _ <- hasMembersAndAdmins(newGroup).toResult
      _ <- groupWithSameNameDoesNotExist(newGroup.name)
      _ <- usersExist(newGroup.memberIds)
      _ <- createGroupData(GroupChange.forAdd(newGroup, authPrincipal), newGroup, adminMembers, nonAdminMembers).toResult[Unit]
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
      _ <- groupValidation(newGroup)
      _ <- emailValidation(newGroup.email)
      _ <- canEditGroup(existingGroup, authPrincipal).toResult
      addedAdmins = newGroup.adminUserIds.diff(existingGroup.adminUserIds)
      // new non-admin members ++ admins converted to non-admins
      addedNonAdmins = newGroup.memberIds.diff(existingGroup.memberIds).diff(addedAdmins) ++
        existingGroup.adminUserIds.diff(newGroup.adminUserIds).intersect(newGroup.memberIds)
      removedMembers = existingGroup.memberIds.diff(newGroup.memberIds)
      _ <- hasMembersAndAdmins(newGroup).toResult
      _ <- usersExist(addedNonAdmins)
      _ <- differentGroupWithSameNameDoesNotExist(newGroup.name, existingGroup.id)
      _ <- updateGroupData(GroupChange.forUpdate(newGroup, existingGroup, authPrincipal), newGroup, existingGroup, addedAdmins, addedNonAdmins, removedMembers).toResult[Unit]
    } yield newGroup

  def deleteGroup(groupId: String, authPrincipal: AuthPrincipal): Result[Group] =
    for {
      existingGroup <- getExistingGroup(groupId)
      _ <- canEditGroup(existingGroup, authPrincipal).toResult
      _ <- isNotZoneAdmin(existingGroup)
      _ <- isNotRecordOwnerGroup(existingGroup)
      _ <- isNotInZoneAclRule(existingGroup)
      deletedGroup <- deleteGroupData(GroupChange.forDelete(existingGroup, authPrincipal), existingGroup).toResult[Group]
    } yield deletedGroup

  def createGroupData(
   groupChangeData: GroupChange,
   newGroup: Group,
   adminMembers: Set[String],
   nonAdminMembers: Set[String]
  ): IO[Unit] =
    executeWithinTransaction { db: DB =>
      for {
        _ <- groupChangeRepo.save(db, groupChangeData)
        _ <- groupRepo.save(db, newGroup)
        // save admin and non-admin members separately
        _ <- membershipRepo
          .saveMembers(db, newGroup.id, adminMembers, isAdmin = true)
        _ <- membershipRepo
          .saveMembers(db, newGroup.id, nonAdminMembers, isAdmin = false)
      } yield ()
    }

  def updateGroupData(
   groupChangeData: GroupChange,
   newGroup: Group,
   existingGroup: Group,
   addedAdmins: Set[String],
   addedNonAdmins: Set[String],
   removedMembers: Set[String]
  ): IO[Unit] =
    executeWithinTransaction { db: DB =>
      for {
        _ <- groupChangeRepo
          .save(db, groupChangeData)
        _ <- groupRepo.save(db, newGroup)
        // save admin and non-admin members separately
        _ <- membershipRepo
          .saveMembers(db, existingGroup.id, addedAdmins, isAdmin = true)
        _ <- membershipRepo
          .saveMembers(db, existingGroup.id, addedNonAdmins, isAdmin = false)
        _ <- membershipRepo.removeMembers(db, existingGroup.id, removedMembers)
      } yield ()
    }

  def deleteGroupData(
   groupChangeData: GroupChange,
   existingGroup: Group,
  ): IO[Group] =
    executeWithinTransaction { db: DB =>
      for {
        _ <- groupChangeRepo
          .save(db, groupChangeData)
        _ <- membershipRepo
          .removeMembers(db, existingGroup.id, existingGroup.memberIds)
        deletedGroup = existingGroup.copy(status = GroupStatus.Deleted)
        _ <- groupRepo.delete(deletedGroup)
      } yield deletedGroup
    }

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
      ignoreAccess: Boolean,
      abridged: Boolean = false
  ): Result[ListMyGroupsResponse] = {
    val groupsCall =
      if (authPrincipal.isSystemAdmin || ignoreAccess) {
        groupRepo.getAllGroups()
      } else {
        groupRepo.getGroups(authPrincipal.memberGroupIds.toSet)
      }

    groupsCall.map { grp =>
      pageListGroupsResponse(grp.toList, groupNameFilter, startFrom, maxItems, ignoreAccess, abridged, authPrincipal)
    }
  }.toResult

  def pageListGroupsResponse(
      allGroups: Seq[Group],
      groupNameFilter: Option[String],
      startFrom: Option[String],
      maxItems: Int,
      ignoreAccess: Boolean,
      abridged: Boolean = false,
      authPrincipal: AuthPrincipal
    ): ListMyGroupsResponse = {
    val allMyGroups = allGroups
      .filter(_.status == GroupStatus.Active)
      .sortBy(_.name.toLowerCase)
      .map(x => GroupInfo.fromGroup(x, abridged, Some(authPrincipal)))

    val filtered = if(startFrom.isDefined){
      val prevPageGroup = allMyGroups.filter(_.id == startFrom.get).head.name
      allMyGroups
        .filter(grp => groupNameFilter.map(_.toLowerCase).forall(grp.name.toLowerCase.contains(_)))
        .filter(grp => grp.name.toLowerCase > prevPageGroup.toLowerCase)
    } else {
      allMyGroups
        .filter(grp => groupNameFilter.map(_.toLowerCase).forall(grp.name.toLowerCase.contains(_)))
    }

    val nextId = if (filtered.length > maxItems) Some(filtered(maxItems - 1).id) else None
    val groups = filtered.take(maxItems)

    ListMyGroupsResponse(groups, groupNameFilter, startFrom, nextId, maxItems, ignoreAccess)
  }

  def getGroupChange(
    groupChangeId: String,
    authPrincipal: AuthPrincipal
  ): Result[GroupChangeInfo] =
    for {
      result <- groupChangeRepo
        .getGroupChange(groupChangeId)
        .toResult[Option[GroupChange]]
      _ <- isGroupChangePresent(result).toResult
      _ <- canSeeGroup(result.get.newGroup.id, authPrincipal).toResult
      allUserIds = getGroupUserIds(Seq(result.get))
      allUserMap <- getUsers(allUserIds).map(_.users.map(x => x.id -> x.userName).toMap)
      groupChangeMessage <- determineGroupDifference(Seq(result.get), allUserMap)
      groupChanges = (groupChangeMessage, Seq(result.get)).zipped.map{ (a, b) => b.copy(groupChangeMessage = Some(a)) }
      userIds = Seq(result.get).map(_.userId).toSet
      users <- getUsers(userIds).map(_.users)
      userMap = users.map(u => (u.id, u.userName)).toMap
    } yield groupChanges.map(change => GroupChangeInfo.apply(change.copy(userName = userMap.get(change.userId)))).head

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
      allUserIds = getGroupUserIds(result.changes)
      allUserMap <- getUsers(allUserIds).map(_.users.map(x => x.id -> x.userName).toMap)
      groupChangeMessage <- determineGroupDifference(result.changes, allUserMap)
      groupChanges = (groupChangeMessage, result.changes).zipped.map{ (a, b) => b.copy(groupChangeMessage = Some(a)) }
      userIds = result.changes.map(_.userId).toSet
      users <- getUsers(userIds).map(_.users)
      userMap = users.map(u => (u.id, u.userName)).toMap
    } yield ListGroupChangesResponse(
      groupChanges.map(change => GroupChangeInfo.apply(change.copy(userName = userMap.get(change.userId)))),
      startFrom,
      result.lastEvaluatedTimeStamp,
      maxItems
    )

  def getGroupUserIds(groupChange: Seq[GroupChange]): Set[String] = {
    var userIds: Set[String] = Set.empty[String]
    for (change <- groupChange) {
      if (change.oldGroup.isDefined) {
        val adminAddDifference = change.newGroup.adminUserIds.diff(change.oldGroup.get.adminUserIds)
        val adminRemoveDifference = change.oldGroup.get.adminUserIds.diff(change.newGroup.adminUserIds)
        val memberAddDifference = change.newGroup.memberIds.diff(change.oldGroup.get.memberIds)
        val memberRemoveDifference = change.oldGroup.get.memberIds.diff(change.newGroup.memberIds)
        userIds = userIds ++ adminAddDifference ++ adminRemoveDifference ++ memberAddDifference ++ memberRemoveDifference
      }
    }
    userIds
  }

  def determineGroupDifference(groupChange: Seq[GroupChange],  allUserMap: Map[String, String]): Result[Seq[String]] = {
    var groupChangeMessage: Seq[String] = Seq.empty[String]

    for (change <- groupChange) {
      val sb = new StringBuilder
      if (change.oldGroup.isDefined) {
        if (change.oldGroup.get.name != change.newGroup.name) {
          sb.append(s"Group name changed to '${change.newGroup.name}'. ")
        }
        if (change.oldGroup.get.email != change.newGroup.email) {
          sb.append(s"Group email changed to '${change.newGroup.email}'. ")
        }
        if (change.oldGroup.get.description != change.newGroup.description) {
          sb.append(s"Group description changed to '${change.newGroup.description.get}'. ")
        }
        val adminAddDifference = change.newGroup.adminUserIds.diff(change.oldGroup.get.adminUserIds)
        if (adminAddDifference.nonEmpty) {
          sb.append(s"Group admin/s with user name/s '${adminAddDifference.map(x => allUserMap(x)).mkString("','")}' added. ")
        }
        val adminRemoveDifference = change.oldGroup.get.adminUserIds.diff(change.newGroup.adminUserIds)
        if (adminRemoveDifference.nonEmpty) {
          sb.append(s"Group admin/s with user name/s '${adminRemoveDifference.map(x => allUserMap(x)).mkString("','")}' removed. ")
        }
        val memberAddDifference = change.newGroup.memberIds.diff(change.oldGroup.get.memberIds)
        if (memberAddDifference.nonEmpty) {
          sb.append(s"Group member/s with user name/s '${memberAddDifference.map(x => allUserMap(x)).mkString("','")}' added. ")
        }
        val memberRemoveDifference = change.oldGroup.get.memberIds.diff(change.newGroup.memberIds)
        if (memberRemoveDifference.nonEmpty) {
          sb.append(s"Group member/s with user name/s '${memberRemoveDifference.map(x => allUserMap(x)).mkString("','")}' removed. ")
        }
        groupChangeMessage = groupChangeMessage :+ sb.toString().trim
      }
      // It'll be in else statement if the group was created or deleted
      else {
        if (change.changeType == GroupChangeType.Create) {
          sb.append("Group Created.")
        }
        else if (change.changeType == GroupChangeType.Delete){
          sb.append("Group Deleted.")
        }
        groupChangeMessage = groupChangeMessage :+ sb.toString()
      }
    }
    groupChangeMessage
  }.toResult

  /**
   * Retrieves the requested User from the given userIdentifier, which can be a userId or username
   * @param userIdentifier The userId or username
   * @return The found User
   */
  def getUser(userIdentifier: String, authPrincipal: AuthPrincipal): Result[User] =
    userRepo
      .getUserByIdOrName(userIdentifier)
      .orFail(UserNotFoundError(s"User $userIdentifier was not found"))
      .toResult[User]

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
      .orFail(UserNotFoundError(s"User with ID $userId was not found"))
      .toResult[User]

  def getExistingGroup(groupId: String): Result[Group] =
    groupRepo
      .getGroup(groupId)
      .orFail(GroupNotFoundError(s"Group with ID $groupId was not found"))
      .toResult[Group]

  // Validate group details. Group name and email cannot be empty
  def groupValidation(group: Group): Result[Unit] = {
    Option(group) match {
      case Some(value) if Option(value.name).forall(_.trim.isEmpty) || Option(value.email).forall(_.trim.isEmpty) =>
        GroupValidationError(GroupValidationErrorMsg).asLeft
      case _ =>
        ().asRight
    }
  }.toResult
   // Validate email details.Email domains details are fetched from the config file.
  def emailValidation(email: String): Result[Unit] = {
    val emailDomains = validDomains.valid_domains
    val numberOfDots=  validDomains.number_of_dots
    val splitEmailDomains = emailDomains.mkString(",")
    val emailRegex ="""^(?!\.)(?!.*\.$)(?!.*\.\.)[a-zA-Z0-9._]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r
    val index = email.indexOf('@');
    val emailSplit = if(index != -1){
      email.substring(index+1,email.length)}
    val wildcardEmailDomains=if(splitEmailDomains.contains("*")){
      emailDomains.map(x=>x.replaceAllLiterally("*",""))}
    else emailDomains

    Option(email) match {
      case Some(value) if (emailRegex.findFirstIn(value) != None)=>

        if ((emailDomains.contains(emailSplit) || emailDomains.isEmpty || wildcardEmailDomains.exists(x => emailSplit.toString.endsWith(x)))&&
              emailSplit.toString.count(_ == '.')<=numberOfDots)
        ().asRight
        else {
          if(emailSplit.toString.count(_ == '.')>numberOfDots){
            EmailValidationError(DotsValidationErrorMsg + " " + numberOfDots).asLeft
          }
          else {
            EmailValidationError(EmailValidationErrorMsg + " " + wildcardEmailDomains.mkString(",")).asLeft
          }
        }
      case _ =>
        EmailValidationError(InvalidEmailValidationErrorMsg).asLeft
    }}.toResult


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

  def usersExist(userIds: Set[String]): Result[Unit] = {
    userRepo.getUsers(userIds, None, None).map { results =>
      val delta = userIds.diff(results.users.map(_.id).toSet)
      if (delta.isEmpty)
        ().asRight
      else
        UserNotFoundError(s"Users [ ${delta.mkString(",")} ] were not found").asLeft
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

  def isNotZoneAdmin(group: Group): Result[Unit] =
    zoneRepo
      .getZonesByAdminGroupId(group.id)
      .map { zones =>
        ensuring(InvalidGroupRequestError(ZoneAdminError.format(group.name)))(
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
            RecordSetOwnerError.format(group.name, rsId)
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
            ACLRuleError.format(group.name, zId)
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
