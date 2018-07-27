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

import vinyldns.api.domain.membership.{MembershipRepository, User, UserRepository}
import vinyldns.api.route.Monitored

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AuthPrincipalProvider extends Monitored {
  def getAuthPrincipal(accessKey: String): Future[Option[AuthPrincipal]]
}

class MembershipAuthPrincipalProvider(
    userRepo: UserRepository,
    membershipRepo: MembershipRepository)
    extends AuthPrincipalProvider {

  def getAuthPrincipal(accessKey: String): Future[Option[AuthPrincipal]] =
    getUserByAccessKey(accessKey).flatMap {
      case None => Future.successful(None)
      case Some(user) =>
        getGroupsForUser(user.id).map { memberships =>
          Option(AuthPrincipal(user, memberships.toSeq))
        }
    }

  private def getUserByAccessKey(accessKey: String): Future[Option[User]] =
    monitor("user.getUserByAccessKey") {
      userRepo.getUserByAccessKey(accessKey)
    }

  private def getGroupsForUser(userId: String): Future[Set[String]] =
    monitor("membership.getGroupsForUser") {
      membershipRepo.getGroupsForUser(userId)
    }
}

object MembershipAuthPrincipalProvider {
  val userRepository: UserRepository = UserRepository()
  val membershipRepository: MembershipRepository = MembershipRepository()

  def apply(): MembershipAuthPrincipalProvider =
    new MembershipAuthPrincipalProvider(userRepository, membershipRepository)
}
