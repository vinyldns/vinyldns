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

import cats.effect.IO
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import vinyldns.core.domain.membership.{User, UserChange, UserChangeRepository, UserRepository}

@Singleton
class UserAccountAccessor @Inject()(users: UserRepository, changes: UserChangeRepository) {

  /**
    * Lookup a user in the store. Using identifier as the user id and/or name
    *
    * @param identifier
    * @return Success(Some(user account)) on success, Success(None) if the user does not exist and Failure when there
    *         was an error.
    */
  def get(identifier: String): IO[Option[User]] =
    users
      .getUser(identifier)
      .flatMap {
        case None => users.getUserByName(identifier)
        case found => IO(found)
      }

  def create(user: User): IO[User] =
    for {
      _ <- users.save(user)
      _ <- changes.save(UserChange.CreateUser(user, "system", DateTime.now))
    } yield user

  def update(user: User, oldUser: User): IO[User] =
    for {
      _ <- users.save(user)
      _ <- changes.save(UserChange.UpdateUser(user, "system", DateTime.now, oldUser))
    } yield user

  def getUserByKey(key: String): IO[Option[User]] =
    users.getUserByAccessKey(key)
}
