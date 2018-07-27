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

import scalaz.Disjunction
import scalaz.syntax.ToEitherOps
import vinyldns.api.Interfaces.ensuring
import vinyldns.api.domain.auth.AuthPrincipal
import vinyldns.api.domain.zone.NotAuthorizedError

object MembershipValidations extends ToEitherOps {

  def hasMembersAndAdmins(group: Group): Disjunction[Throwable, Unit] =
    ensuring(InvalidGroupError("Group must have at least one member and one admin")) {
      group.memberIds.nonEmpty && group.adminUserIds.nonEmpty
    }

  def isAdmin(group: Group, authPrincipal: AuthPrincipal): Disjunction[Throwable, Unit] =
    ensuring(NotAuthorizedError("Not authorized")) {
      group.adminUserIds.contains(authPrincipal.userId) || authPrincipal.signedInUser.isSuper
    }

  def canSeeGroup(groupId: String, authPrincipal: AuthPrincipal): Disjunction[Throwable, Unit] =
    ensuring(NotAuthorizedError("Not authorized")) {
      authPrincipal.isAuthorized(groupId)
    }
}
