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

package controllers.datastores

import controllers.UserAccountStore
import models.UserAccount

import scala.collection.mutable
import scala.util.Try

class InMemoryUserAccountStore extends UserAccountStore {
  val users = new mutable.HashMap[String, UserAccount]()
  val usersByNameIndex = new mutable.HashMap[String, String]()
  val usersByKeyIndex = new mutable.HashMap[String, String]()

  def getUserById(userId: String): Try[Option[UserAccount]] =
    Try(users.get(userId))

  def getUserByName(username: String): Try[Option[UserAccount]] =
    Try(usersByNameIndex.get(username)).flatMap {
      case Some(userId) => getUserById(userId)
      case None => Try(None)
    }

  def getUserByKey(key: String): Try[Option[UserAccount]] =
    Try(usersByKeyIndex.get(key)).flatMap {
      case Some(userId) => getUserById(userId)
      case None => Try(None)
    }

  def storeUser(user: UserAccount): Try[UserAccount] =
    Try {
      users.put(user.userId, user)
      usersByNameIndex.put(user.username, user.userId)
      usersByKeyIndex.put(user.accessKey, user.userId)
      user
    }
}
