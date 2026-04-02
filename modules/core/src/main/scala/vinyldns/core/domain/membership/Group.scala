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

import vinyldns.core.domain.auth.AuthPrincipal

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
    membershipAccessStatus: Option[MembershipAccessStatus] = None

) {

  def addMember(user: User): Group =
    this.copy(memberIds = memberIds + user.id)

  def pendingReviewMember(user: User, description: Option[String] = None, authPrincipal: AuthPrincipal): Group = {
    val updatedMembershipAction = MembershipAccess(
      userId = user.id,
      submittedBy = authPrincipal.userId,
      description = description,
      status = MemberStatus.PendingReview.toString
    )

    val updatedMembershipStatus = membershipAccessStatus match {
      case Some(status) =>
        status.copy(pendingReviewMember = status.pendingReviewMember + updatedMembershipAction)
      case None =>
        MembershipAccessStatus(pendingReviewMember = Set(updatedMembershipAction))
    }

    this.copy(membershipAccessStatus = Some(updatedMembershipStatus))
  }

  def approvedMember(user: User, description: Option[String] = None, authPrincipal: AuthPrincipal): Group = {
    val updatedMembershipAction = MembershipAccess(
      userId = user.id,
      submittedBy = authPrincipal.userId,
      description = description,
      status = MemberStatus.Approved.toString
    )
    val updatedMembershipStatus = membershipAccessStatus match {
      case Some(status) =>
        status.copy(
          pendingReviewMember = status.pendingReviewMember.filterNot(_.userId == user.id),
          approvedMember = status.approvedMember + updatedMembershipAction)
      case None =>
        MembershipAccessStatus(approvedMember = Set(updatedMembershipAction))
    }
    this.copy(membershipAccessStatus = Some(updatedMembershipStatus)).addMember(user)
  }

  def rejectedMember(user: User, description: Option[String] = None, authPrincipal: AuthPrincipal): Group = {
    val updatedMembershipAction = MembershipAccess(
      userId = user.id,
      submittedBy = authPrincipal.userId,
      description = description,
      status = MemberStatus.Rejected.toString
    )
    val updatedMembershipStatus = membershipAccessStatus match {
      case Some(status) =>
        status.copy(
          pendingReviewMember = status.pendingReviewMember.filterNot(_.userId == user.id),
          rejectedMember = status.rejectedMember + updatedMembershipAction
        )
      case None =>
        MembershipAccessStatus(rejectedMember = Set(updatedMembershipAction))
    }
    this.copy(membershipAccessStatus = Some(updatedMembershipStatus))
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
      membershipStatusUpdate: Option[MembershipAccessStatus],
  ): Group =
    this.copy(
      name = nameUpdate,
      email = emailUpdate,
      description = descriptionUpdate,
      memberIds = memberIdsUpdate ++ adminUserIdsUpdate,
      adminUserIds = adminUserIdsUpdate,
      membershipAccessStatus = membershipStatusUpdate
    )
}
case class MembershipAccessStatus(
                              pendingReviewMember: Set[MembershipAccess] = Set.empty,
                              rejectedMember: Set[MembershipAccess] = Set.empty,
                              approvedMember: Set[MembershipAccess] = Set.empty
                            )


case class MembershipAccess(
                         userId: String,
                         created: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS),
                         submittedBy: String = "System",
                         description: Option[String] = None,
                         status: String = "System"
                       )
