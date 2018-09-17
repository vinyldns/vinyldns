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
import vinyldns.core.domain.membership.LockStatus.LockStatus

object LockStatus extends Enumeration {
  type LockStatus = Value
  val Locked, Unlocked = Value
}

case class User(
    userName: String,
    accessKey: String,
    secretKey: String,
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    email: Option[String] = None,
    created: DateTime = DateTime.now,
    id: String = UUID.randomUUID().toString,
    isSuper: Boolean = false,
    lockStatus: LockStatus = LockStatus.Unlocked
) {

  def updateUserLockStatus(lockStatus: LockStatus): User =
    this.copy(lockStatus = lockStatus)
}
