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

package models

import java.util.UUID

import org.apache.commons.lang3.RandomStringUtils
import org.joda.time.DateTime
import vinyldns.core.domain.membership.LockStatus
import vinyldns.core.domain.membership.LockStatus.LockStatus

class UserDoesNotExistException(message: String) extends Exception(message: String)

case class UserAccount(
    userId: String,
    username: String,
    firstName: Option[String],
    lastName: Option[String],
    email: Option[String],
    created: DateTime,
    accessKey: String,
    accessSecret: String,
    isSuper: Boolean = false,
    lockStatus: LockStatus = LockStatus.Unlocked) {

  private def generateKey: String = RandomStringUtils.randomAlphanumeric(20)

  override def toString() = {
    val sb = new StringBuilder
    sb.append("UserAccount: [")
    sb.append("id=\"").append(userId).append("\"; ")
    sb.append("username=\"").append(username).append("\"; ")
    sb.append("firstName=\"").append(firstName).append("\"; ")
    sb.append("lastName=\"").append(lastName).append("\"; ")
    sb.append("email=\"").append(email).append("\"; ")
    sb.append("accessKey=\"").append(accessKey).append("\"; ")
    sb.append("]")
    sb.toString
  }

  def regenerateCredentials(): UserAccount =
    copy(accessKey = generateKey, accessSecret = generateKey)
}

object UserAccount {
  private def generateKey: String = RandomStringUtils.randomAlphanumeric(20)

  def apply(
      username: String,
      firstName: Option[String],
      lastName: Option[String],
      email: Option[String]): UserAccount = {
    val userId = UUID.randomUUID().toString
    val createdTime = DateTime.now()
    val key = generateKey
    val secret = generateKey

    UserAccount(
      userId,
      username,
      firstName,
      lastName,
      email,
      createdTime,
      key,
      secret,
      false,
      LockStatus.Unlocked)
  }
}
