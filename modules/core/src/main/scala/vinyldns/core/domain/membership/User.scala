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

import org.apache.commons.lang3.RandomStringUtils
import java.time.Instant
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.{Encrypted, Encryption}
import vinyldns.core.domain.membership.LockStatus.LockStatus
import vinyldns.core.domain.membership.PermissionStatus.PermissionStatus
import java.time.temporal.ChronoUnit

object LockStatus extends Enumeration {
  type LockStatus = Value
  val Locked, Unlocked = Value
}

object PermissionStatus extends Enumeration {
  type PermissionStatus = Value
  val MakeSuper, RemoveSuper, MakeSupport, RemoveSupport = Value

  private val valueMap =
    PermissionStatus.values.map(v => v.toString.toLowerCase -> v).toMap

  def find(status: String): PermissionStatus =
    valueMap(status.toLowerCase)
}

final case class User(
    userName: String,
    accessKey: String,
    secretKey: Encrypted,
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    email: Option[String] = None,
    created: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    id: String = UUID.randomUUID().toString,
    isSuper: Boolean = false,
    lockStatus: LockStatus = LockStatus.Unlocked,
    isSupport: Boolean = false,
    isTest: Boolean = false
) {

  def updateUserLockStatus(lockStatus: LockStatus): User =
    this.copy(lockStatus = lockStatus)

  def updateUserPermissionStatus(permissionStatus: PermissionStatus): User = {
    permissionStatus match {
      case PermissionStatus.MakeSuper => this.copy(isSuper = true)
      case PermissionStatus.RemoveSuper => this.copy(isSuper = false)
      case PermissionStatus.MakeSupport => this.copy(isSupport = true)
      case PermissionStatus.RemoveSupport => this.copy(isSupport = false)
    }
  }

  def regenerateCredentials(): User =
    copy(accessKey = User.generateKey, secretKey = Encrypted(User.generateKey))

  def withEncryptedSecretKey(cryptoAlgebra: CryptoAlgebra): User =
    copy(secretKey = Encryption.apply(cryptoAlgebra, secretKey.value))

  override def toString: String = {
    val sb = new StringBuilder
    sb.append("User: [")
    sb.append("id=\"").append(id).append("\"; ")
    sb.append("userName=\"").append(userName).append("\"; ")
    sb.append("firstName=\"").append(firstName.toString).append("\"; ")
    sb.append("lastName=\"").append(lastName.toString).append("\"; ")
    sb.append("email=\"").append(email.toString).append("\"; ")
    sb.append("created=\"").append(created).append("\"; ")
    sb.append("isSuper=\"").append(isSuper).append("\"; ")
    sb.append("isSupport=\"").append(isSupport).append("\"; ")
    sb.append("isTest=\"").append(isTest).append("\"; ")
    sb.append("lockStatus=\"").append(lockStatus.toString).append("\"; ")
    sb.append("]")
    sb.toString
  }
}

object User {
  def generateKey: String = RandomStringUtils.randomAlphanumeric(20)
}
