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
import vinyldns.core.domain.auth.AuthPrincipal
import java.time.temporal.ChronoUnit

object GroupChangeType extends Enumeration {
  type GroupChangeType = Value
  val Create, Update, Delete = Value
}

import vinyldns.core.domain.membership.GroupChangeType._

case class GroupChange(
    newGroup: Group,
    changeType: GroupChangeType,
    userId: String,
    oldGroup: Option[Group] = None,
    id: String = UUID.randomUUID().toString,
    created: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    userName: Option[String] = None,
    groupChangeMessage: Option[String] = None
)

object GroupChange {
  def forAdd(group: Group, authPrincipal: AuthPrincipal): GroupChange =
    GroupChange(group, GroupChangeType.Create, authPrincipal.userId)

  def forUpdate(newGroup: Group, oldGroup: Group, authPrincipal: AuthPrincipal): GroupChange =
    GroupChange(newGroup, GroupChangeType.Update, authPrincipal.userId, Some(oldGroup))

  def forDelete(group: Group, authPrincipal: AuthPrincipal): GroupChange =
    GroupChange(group, GroupChangeType.Delete, authPrincipal.userId)
}
