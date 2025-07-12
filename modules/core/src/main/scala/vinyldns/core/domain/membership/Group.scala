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

object MemberStatus extends Enumeration {
  type MemberStatus = Value
  val Request, PendingReview, Rejected, Approved = Value
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
    memberStatus: Option[MembershipStatus] = None

) {

  def addMember(user: User): Group =
    this.copy(memberIds = memberIds + user.id)

  def pendingReviewMember(user: User): Group = {
    val updatedMembershipStatus = memberStatus match {
      case Some(status) =>
        status.copy(pendingReviewMember = status.pendingReviewMember + user.id)
      case None =>
        MembershipStatus(pendingReviewMember = Set(user.id))
    }
    this.copy(memberStatus = Some(updatedMembershipStatus))
  }

  def approvedMember(user: User): Group = {
    val updatedMembershipStatus = memberStatus match {
      case Some(status) =>
        status.copy(
          pendingReviewMember = status.pendingReviewMember - user.id,
          approvedMember = status.approvedMember + user.id)
      case None =>
        MembershipStatus(approvedMember = Set(user.id))
    }
    this.copy(memberStatus = Some(updatedMembershipStatus))
    addMember(user)
  }

  def rejectedMember(user: User): Group = {
    val updatedMembershipStatus = memberStatus match {
      case Some(status) =>
        status.copy(
          pendingReviewMember = status.pendingReviewMember - user.id,
          rejectedMember = status.rejectedMember + user.id)
      case None =>
        MembershipStatus(rejectedMember = Set(user.id))
    }
    this.copy(memberStatus = Some(updatedMembershipStatus))
  }

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
      membershipStatusUpdate: Option[MembershipStatus],
  ): Group =
    this.copy(
      name = nameUpdate,
      email = emailUpdate,
      description = descriptionUpdate,
      memberIds = memberIdsUpdate ++ adminUserIdsUpdate,
      adminUserIds = adminUserIdsUpdate,
      memberStatus = membershipStatusUpdate
    )
}
case class MembershipStatus(
                              pendingReviewMember: Set[String] = Set.empty,
                              rejectedMember: Set[String] = Set.empty,
                              approvedMember: Set[String] = Set.empty
                            )
