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
import vinyldns.api.domain.auth.AuthPrincipal

object GroupStatus extends Enumeration {
  type GroupStatus = Value
  val Active, Deleted = Value
}

import vinyldns.api.domain.membership.GroupStatus._
case class Group(
    name: String,
    email: String,
    description: Option[String] = None,
    id: String = UUID.randomUUID().toString,
    created: DateTime = DateTime.now,
    status: GroupStatus = GroupStatus.Active,
    memberIds: Set[String] = Set.empty,
    adminUserIds: Set[String] = Set.empty
) {
  def addMember(user: User): Group =
    this.copy(memberIds = memberIds + user.id)

  // If the user can be removed remove it, otherwise do nothing
  def removeMember(user: User): Group =
    if (isNotLastMember(user)) {
      this.copy(memberIds = memberIds - user.id)
    } else {
      this
    }

  def isNotLastMember(user: User): Boolean =
    !(memberIds.size == 1 && memberIds.contains(user.id))

  def addAdminUser(user: User): Group =
    this.copy(memberIds = memberIds + user.id, adminUserIds = adminUserIds + user.id)

  // If the user can be removed remove it, otherwise do nothing
  def removeAdminUser(user: User): Group =
    if (isNotLastAdminUser(user)) {
      this.copy(adminUserIds = adminUserIds - user.id)
    } else {
      this
    }

  def isNotLastAdminUser(user: User): Boolean =
    !(adminUserIds.size == 1 && adminUserIds.contains(user.id))

  def withUpdates(
      nameUpdate: String,
      emailUpdate: String,
      descriptionUpdate: Option[String],
      memberIdsUpdate: Set[String],
      adminUserIdsUpdate: Set[String]): Group =
    this.copy(
      name = nameUpdate,
      email = emailUpdate,
      description = descriptionUpdate,
      memberIds = memberIdsUpdate ++ adminUserIdsUpdate,
      adminUserIds = adminUserIdsUpdate)
}

object Group {
  import cats.data.ValidatedNel
  import cats.implicits._
  import cats.syntax.either._

  def build(
      name: String,
      email: String,
      description: Option[String],
      memberIds: Set[UserInfo],
      adminUserIds: Set[UserInfo]): ValidatedNel[String, Group] =
    Group(
      name,
      email,
      description,
      memberIds = memberIds.map(_.id) ++ adminUserIds.map(_.id),
      adminUserIds = adminUserIds.map(_.id)).validNel[String]

  def build(
      id: String,
      name: String,
      email: String,
      description: Option[String],
      memberIds: Set[UserInfo],
      adminUserIds: Set[UserInfo]): ValidatedNel[String, Group] =
    Group(
      name,
      email,
      description,
      id,
      memberIds = memberIds.map(_.id) ++ adminUserIds.map(_.id),
      adminUserIds = adminUserIds.map(_.id)).validNel[String]
}

object GroupChangeType extends Enumeration {
  type GroupChangeType = Value
  val Create, Update, Delete = Value
}

import vinyldns.api.domain.membership.GroupChangeType._
case class GroupChange(
    newGroup: Group,
    changeType: GroupChangeType,
    userId: String,
    oldGroup: Option[Group] = None,
    id: String = UUID.randomUUID().toString,
    created: DateTime = DateTime.now
)

object GroupChange {
  def forAdd(group: Group, authPrincipal: AuthPrincipal): GroupChange =
    GroupChange(group, GroupChangeType.Create, authPrincipal.userId)

  def forUpdate(newGroup: Group, oldGroup: Group, authPrincipal: AuthPrincipal): GroupChange =
    GroupChange(newGroup, GroupChangeType.Update, authPrincipal.userId, Some(oldGroup))

  def forDelete(group: Group, authPrincipal: AuthPrincipal): GroupChange =
    GroupChange(group, GroupChangeType.Delete, authPrincipal.userId)
}
