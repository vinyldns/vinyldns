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

package vinyldns.core.domain.membership

import java.util.UUID

import java.time.Instant
import java.time.temporal.ChronoUnit

object GroupStatus extends Enumeration {
  type GroupStatus = Value
  val Active, Deleted = Value
}

import vinyldns.core.domain.membership.GroupStatus._
case class Group(
    name: String,
    email: String,
    description: Option[String] = None,
    id: String = UUID.randomUUID().toString,
    created: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    status: GroupStatus = GroupStatus.Active,
    memberIds: Set[String] = Set.empty,
    adminUserIds: Set[String] = Set.empty,
    customMessage: Option[String] = None
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
      adminUserIdsUpdate: Set[String],
      customMessageUpdate: Option[String]
  ): Group =
    this.copy(
      name = nameUpdate,
      email = emailUpdate,
      description = descriptionUpdate,
      customMessage = customMessageUpdate,
      memberIds = memberIdsUpdate ++ adminUserIdsUpdate,
      adminUserIds = adminUserIdsUpdate
    )
}
