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

import org.joda.time.DateTime
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
    created: DateTime = DateTime.now,
    status: GroupStatus = GroupStatus.Active,
    members: Set[UserInfo] = Set.empty,
    admins: Set[UserInfo] = Set.empty
)
object GroupInfo {
  def apply(group: Group): GroupInfo = GroupInfo(
    id = group.id,
    name = group.name,
    email = group.email,
    description = group.description,
    created = group.created,
    status = group.status,
    members = group.memberIds.map(UserInfo(_)),
    admins = group.adminUserIds.map(UserInfo(_))
  )
}

final case class GroupChangeInfo(
    newGroup: GroupInfo,
    changeType: GroupChangeType,
    userId: String,
    oldGroup: Option[GroupInfo] = None,
    id: String = UUID.randomUUID().toString,
    created: String = DateTime.now.getMillis.toString
)

object GroupChangeInfo {
  def apply(groupChange: GroupChange): GroupChangeInfo = GroupChangeInfo(
    newGroup = GroupInfo(groupChange.newGroup),
    changeType = groupChange.changeType,
    userId = groupChange.userId,
    oldGroup = groupChange.oldGroup.map(GroupInfo.apply),
    id = groupChange.id,
    created = groupChange.created.getMillis.toString
  )
}

case class UserInfo(
    id: String,
    userName: Option[String] = None,
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    email: Option[String] = None,
    created: Option[DateTime] = None,
    lockStatus: LockStatus = LockStatus.Unlocked
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
      lockStatus = user.lockStatus
    )
}

case class MemberInfo(
    id: String,
    userName: Option[String] = None,
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    email: Option[String] = None,
    created: Option[DateTime] = None,
    isAdmin: Boolean = false
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
      isAdmin = group.adminUserIds.contains(user.id)
    )
}

final case class ListMembersResponse(
    members: Seq[MemberInfo],
    startFrom: Option[String] = None,
    nextId: Option[String] = None,
    maxItems: Int)

final case class ListUsersResponse(
    members: Seq[UserInfo],
    startFrom: Option[String] = None,
    nextId: Option[String] = None,
    maxItems: Int)

final case class ListAdminsResponse(admins: Seq[UserInfo])

final case class ListGroupChangesResponse(
    changes: Seq[GroupChangeInfo],
    startFrom: Option[String] = None,
    nextId: Option[String] = None,
    maxItems: Int)

final case class ListMyGroupsResponse(
    groups: Seq[GroupInfo],
    groupNameFilter: Option[String] = None,
    startFrom: Option[String] = None,
    nextId: Option[String] = None,
    maxItems: Int)

final case class GroupNotFoundError(msg: String) extends Throwable(msg)

final case class GroupAlreadyExistsError(msg: String) extends Throwable(msg)

final case class UserNotFoundError(msg: String) extends Throwable(msg)

final case class InvalidGroupError(msg: String) extends Throwable(msg)

final case class UnableToRemoveLastMemberFromGroupError(msg: String) extends Throwable(msg)

final case class UnableToRemoveLastAdminUserFromGroupError(msg: String) extends Throwable(msg)

final case class InvalidGroupRequestError(msg: String) extends Throwable(msg)
