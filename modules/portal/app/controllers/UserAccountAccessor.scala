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

import javax.inject.{Inject, Singleton}

import models.UserAccount
import org.joda.time.DateTime

import scala.util.{Failure, Success, Try}

case class UserChangeRecord(
    changeId: Long,
    userId: String,
    user: String,
    timeStamp: DateTime,
    changeType: UserChangeType,
    newUser: UserAccount,
    oldUser: UserAccount)

@Singleton
class UserAccountAccessor @Inject()(store: UserAccountStore) {

  /**
    * Lookup a user in the store. Using identifier as the user id and/or name
    *
    * @param identifier
    * @return Success(Some(user account)) on success, Success(None) if the user does not exist and Failure when there
    *         was an error.
    */
  def get(identifier: String): Try[Option[UserAccount]] =
    store.getUserById(identifier) match {
      case Success(None) => store.getUserByName(identifier)
      case Success(Some(user)) => Success(Some(user))
      case Failure(ex) => Failure(ex)
    }

  def put(user: UserAccount): Try[UserAccount] =
    store.storeUser(user)

  def getUserByKey(key: String): Try[Option[UserAccount]] =
    store.getUserByKey(key)
}
