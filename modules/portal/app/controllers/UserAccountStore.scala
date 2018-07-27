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

package controllers

import models.UserAccount

import scala.util.Try

// $COVERAGE-OFF$

trait UserAccountStore {
  def getUserById(userId: String): Try[Option[UserAccount]]
  def getUserByName(username: String): Try[Option[UserAccount]]
  def getUserByKey(accessKey: String): Try[Option[UserAccount]]
  def storeUser(user: UserAccount): Try[UserAccount]
}

sealed trait UserChangeType
final case object Created extends UserChangeType
final case object Updated extends UserChangeType
final case object Deleted extends UserChangeType
// $COVERAGE-ON$
