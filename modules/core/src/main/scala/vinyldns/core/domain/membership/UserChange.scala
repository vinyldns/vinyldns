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

import org.joda.time.DateTime
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.UserChangeType.UserChangeType

object UserChangeType extends Enumeration {
  type UserChangeType = Value

  // Note: we do not have Delete yet, Update would be for locking and unlocking users
  val Create, Update = Value
}

final case class UserChange(
    newUser: User,
    changeType: UserChangeType,
    madeByUserId: String,
    oldUser: Option[User],
    id: String,
    created: DateTime)

object UserChange {
  def forAdd(user: User, authPrincipal: AuthPrincipal): UserChange =
    UserChange(
      user,
      UserChangeType.Create,
      authPrincipal.userId,
      None,
      UUID.randomUUID().toString,
      DateTime.now)

  def forUpdate(newUser: User, oldUser: User, authPrincipal: AuthPrincipal): UserChange =
    UserChange(
      newUser,
      UserChangeType.Update,
      authPrincipal.userId,
      Some(oldUser),
      UUID.randomUUID().toString,
      DateTime.now)
}
