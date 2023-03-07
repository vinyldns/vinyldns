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

import vinyldns.api.Interfaces.Result
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.LockStatus.LockStatus
import vinyldns.core.domain.membership._

trait MembershipServiceAlgebra {

  def createGroup(inputGroup: Group, authPrincipal: AuthPrincipal): Result[Group]

  def updateGroup(
      groupId: String,
      name: String,
      email: String,
      description: Option[String],
      memberIds: Set[String],
      adminUserIds: Set[String],
      authPrincipal: AuthPrincipal
  ): Result[Group]

  def deleteGroup(groupId: String, authPrincipal: AuthPrincipal): Result[Group]

  def getGroup(id: String, authPrincipal: AuthPrincipal): Result[Group]

  def getGroupChange(id: String, authPrincipal: AuthPrincipal): Result[GroupChangeInfo]

  def listMyGroups(
      groupNameFilter: Option[String],
      startFrom: Option[String],
      maxItems: Int,
      authPrincipal: AuthPrincipal,
      ignoreAccess: Boolean,
      abridged: Boolean = false
  ): Result[ListMyGroupsResponse]

  def listMembers(
      groupId: String,
      startFrom: Option[String],
      maxItems: Int,
      authPrincipal: AuthPrincipal
  ): Result[ListMembersResponse]

  def listAdmins(groupId: String, authPrincipal: AuthPrincipal): Result[ListAdminsResponse]

  def getGroupActivity(
      groupId: String,
      startFrom: Option[String],
      maxItems: Int,
      authPrincipal: AuthPrincipal
  ): Result[ListGroupChangesResponse]

  def updateUserLockStatus(
      userId: String,
      lockStatus: LockStatus,
      authPrincipal: AuthPrincipal
  ): Result[User]

  def getUser(
      userIdentifier: String,
      authPrincipal: AuthPrincipal
  ): Result[User]
}
