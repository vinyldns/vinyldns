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

package vinyldns.core.domain.auth

import vinyldns.core.domain.membership.User
import vinyldns.core.domain.membership.Group

case class AuthPrincipal(signedInUser: User, memberGroupIds: Seq[String]) {
  def isSuper: Boolean =
    signedInUser.isSuper

  def isSystemAdmin: Boolean =
    signedInUser.isSuper || signedInUser.isSupport

  def isGroupAdmin(group: Group): Boolean =
    group.adminUserIds.contains(signedInUser.id)

  def isGroupMember(groupId: String): Boolean =
    memberGroupIds.contains(groupId)

  def isTestUser: Boolean = signedInUser.isTest

  val secretKey: String = signedInUser.secretKey.value

  val userId: String = signedInUser.id

  val userName: String = signedInUser.userName
}
