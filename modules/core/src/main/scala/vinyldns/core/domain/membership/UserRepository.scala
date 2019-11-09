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

import cats.effect._
import vinyldns.core.repository.Repository

trait UserRepository extends Repository {

  /*Looks up a user.  If the user is not found, or if the user's status is Deleted, will return None */
  def getUser(userId: String): IO[Option[User]]

  def getUsers(
      userIds: Set[String],
      startFrom: Option[String],
      maxItems: Option[Int]
  ): IO[ListUsersResults]

  def getAllUsers: IO[List[User]]

  def getUserByAccessKey(accessKey: String): IO[Option[User]]

  def getUserByName(userName: String): IO[Option[User]]

  def save(user: User): IO[User]

  def save(users: List[User]): IO[List[User]]
}
