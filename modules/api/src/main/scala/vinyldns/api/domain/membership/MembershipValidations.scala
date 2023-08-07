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

import vinyldns.api.Interfaces.ensuring
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.api.domain.zone.NotAuthorizedError
import vinyldns.core.Messages._
import vinyldns.core.domain.membership.{Group, GroupChange}

object MembershipValidations {

  private val canViewGroupDetails = true

  def hasMembersAndAdmins(group: Group): Either[Throwable, Unit] =
    ensuring(InvalidGroupError(hasNoMembersAndAdminsErrorMsg)) {
      group.memberIds.nonEmpty && group.adminUserIds.nonEmpty
    }

  def canEditGroup(group: Group, authPrincipal: AuthPrincipal): Either[Throwable, Unit] =
    ensuring(NotAuthorizedError(NoAuthorizationErrorMsg)) {
      authPrincipal.isGroupAdmin(group) || authPrincipal.isSuper
    }

  def isSuperAdmin(authPrincipal: AuthPrincipal): Either[Throwable, Unit] =
    ensuring(NotAuthorizedError(NoAuthorizationErrorMsg)) {
      authPrincipal.isSuper
    }

  def canSeeGroup(groupId: String, authPrincipal: AuthPrincipal): Either[Throwable, Unit] =
    ensuring(NotAuthorizedError(NoAuthorizationErrorMsg)) {
      authPrincipal.isGroupMember(groupId) || authPrincipal.isSystemAdmin || canViewGroupDetails
    }

  def isGroupChangePresent(groupChange: Option[GroupChange]): Either[Throwable, Unit] =
    ensuring(InvalidGroupRequestError(InvalidGroupChangeIdErrorMsg)) {
      groupChange.isDefined
  }
}
