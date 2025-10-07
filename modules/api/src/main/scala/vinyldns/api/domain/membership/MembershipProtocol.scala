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

import java.util.UUID
import java.time.Instant
import java.time.temporal.ChronoUnit
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.GroupChangeType.GroupChangeType
import vinyldns.core.domain.membership.GroupStatus.GroupStatus
import vinyldns.core.domain.membership.LockStatus.LockStatus
import vinyldns.core.domain.membership._

/* This is the new View model for Groups, do not surface the Group model directly any more */
final case class GroupInfo(
    id: String,
    name: String,
    email: String,
    description: Option[String] = None,
    created: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    status: GroupStatus = GroupStatus.Active,
    members: Set[UserId] = Set.empty,
    admins: Set[UserId] = Set.empty
)
object GroupInfo {
  def apply(group: Group): GroupInfo = fromGroup(group, abridged = false, None)

  def fromGroup(group: Group, abridged: Boolean = false,
                authPrincipal: Option[AuthPrincipal]): GroupInfo = GroupInfo(
    id = group.id,
    name = group.name,
    email = group.email,
    description = group.description,
    created = if (abridged) null else group.created,
    status = if (abridged) null else group.status,
    members = (if (abridged && authPrincipal.isDefined) group.memberIds.filter(x => authPrincipal.get.userId == x && authPrincipal.get.isGroupMember(group.id))
              else group.memberIds).map(UserId),
    admins = (if (abridged && authPrincipal.isDefined) group.adminUserIds.filter(x => authPrincipal.get.userId == x && authPrincipal.get.isGroupAdmin(group))
              else group.adminUserIds).map(UserId)
  )
}

final case class GroupChangeInfo(
    newGroup: GroupInfo,
    changeType: GroupChangeType,
    userId: String,
    oldGroup: Option[GroupInfo] = None,
    id: String = UUID.randomUUID().toString,
    created: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    userName: String,
    groupChangeMessage: String
)

object GroupChangeInfo {
  def apply(groupChange: GroupChange): GroupChangeInfo = GroupChangeInfo(
    newGroup = GroupInfo(groupChange.newGroup),
    changeType = groupChange.changeType,
    userId = groupChange.userId,
    oldGroup = groupChange.oldGroup.map(GroupInfo.apply),
    id = groupChange.id,
    created = groupChange.created,
    userName = groupChange.userName.getOrElse("unknown user"),
    groupChangeMessage = groupChange.groupChangeMessage.getOrElse("")
  )
}

case class UserId(id: String)

case class UserInfo(
    id: String,
    userName: Option[String] = None,
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    email: Option[String] = None,
    created: Option[Instant] = None,
    isSuper: Option[Boolean] = None,
    isSupport: Option[Boolean] = None,
    lockStatus: LockStatus
)
object UserInfo {
  def apply(user: User): UserInfo =
    UserInfo(
      id = user.id,
      userName = Some(user.userName),
      firstName = user.firstName,
      lastName = user.lastName,
      email = user.email,
      created = Some(user.created),
      isSuper = Some(user.isSuper),
      isSupport = Some(user.isSupport),
      lockStatus = user.lockStatus
    )
}

case class UserResponseInfo(
   id: String,
   userName: Option[String] = None,
   groupId: Set[String] = Set.empty
 )

object UserResponseInfo {
  def apply(user: User , group: Group): UserResponseInfo =
    UserResponseInfo(
      id = user.id,
      userName = Some(user.userName),
      groupId = Set(group.id)
    )
}

case class MemberInfo(
    id: String,
    userName: Option[String] = None,
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    email: Option[String] = None,
    created: Option[Instant] = None,
    isAdmin: Boolean = false,
    lockStatus: LockStatus
)

object MemberInfo {
  def apply(user: User, group: Group): MemberInfo =
    MemberInfo(
      id = user.id,
      userName = Some(user.userName),
      firstName = user.firstName,
      lastName = user.lastName,
      email = user.email,
      created = Some(user.created),
      isAdmin = group.adminUserIds.contains(user.id),
      lockStatus = user.lockStatus
    )
}

final case class ListMembersResponse(
    members: Seq[MemberInfo],
    startFrom: Option[String] = None,
    nextId: Option[String] = None,
    maxItems: Int
)

final case class ListUsersResponse(
    members: Seq[UserInfo],
    startFrom: Option[String] = None,
    nextId: Option[String] = None,
    maxItems: Int
)

final case class ListAdminsResponse(admins: Seq[UserInfo])

final case class ListGroupChangesResponse(
    changes: Seq[GroupChangeInfo],
    startFrom: Option[Int] = None,
    nextId: Option[Int] = None,
    maxItems: Int
)

final case class ListMyGroupsResponse(
    groups: Seq[GroupInfo],
    groupNameFilter: Option[String] = None,
    startFrom: Option[String] = None,
    nextId: Option[String] = None,
    maxItems: Int,
    ignoreAccess: Boolean
)

final case class GroupNotFoundError(msg: String) extends Throwable(msg)

final case class GroupAlreadyExistsError(msg: String) extends Throwable(msg)

final case class GroupValidationError(msg: String) extends Throwable(msg)

final case class EmailValidationError(msg: String) extends Throwable(msg)

final case class UserNotFoundError(msg: String) extends Throwable(msg)

final case class InvalidGroupError(msg: String) extends Throwable(msg)

final case class UnableToRemoveLastMemberFromGroupError(msg: String) extends Throwable(msg)

final case class UnableToRemoveLastAdminUserFromGroupError(msg: String) extends Throwable(msg)

final case class InvalidGroupRequestError(msg: String) extends Throwable(msg)
