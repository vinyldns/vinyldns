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

package vinyldns.api.domain.auth

import cats.effect._
import vinyldns.core.domain.membership.{MembershipRepository, User, UserRepository}
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.route.Monitored

trait AuthPrincipalProvider extends Monitored {
  def getAuthPrincipal(accessKey: String): IO[Option[AuthPrincipal]]
  def getAuthPrincipalByUserId(userId: String): IO[Option[AuthPrincipal]]
}

class MembershipAuthPrincipalProvider(
    userRepo: UserRepository,
    membershipRepo: MembershipRepository
) extends AuthPrincipalProvider {

  def getAuthPrincipal(accessKey: String): IO[Option[AuthPrincipal]] =
    getUserByAccessKey(accessKey).flatMap {
      case None => IO.pure(None)
      case Some(user) =>
        getGroupsForUser(user.id).map { memberships =>
          Option(AuthPrincipal(user, memberships.toSeq))
        }
    }

  def getAuthPrincipalByUserId(userId: String): IO[Option[AuthPrincipal]] =
    getUserById(userId).flatMap {
      case None => IO.pure(None)
      case Some(user) =>
        getGroupsForUser(userId).map { memberships =>
          Option(AuthPrincipal(user, memberships.toSeq))
        }
    }

  private def getUserByAccessKey(accessKey: String): IO[Option[User]] =
    monitor("user.getUserByAccessKey") {
      userRepo.getUserByAccessKey(accessKey)
    }

  private def getUserById(userId: String): IO[Option[User]] =
    monitor("user.getUserById") {
      userRepo.getUser(userId)
    }

  private def getGroupsForUser(userId: String): IO[Set[String]] =
    monitor("membership.getGroupsForUser") {
      membershipRepo.getGroupsForUser(userId)
    }
}
