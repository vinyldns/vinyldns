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

package actions

import cats.effect.IO
import controllers.{OidcAuthenticator, VinylDNS}
import org.slf4j.Logger
import play.api.mvc.{ActionFunction, Request, Result, Session}
import vinyldns.core.domain.membership.{LockStatus, User}
import vinyldns.core.logging.StructuredArgs._

import scala.concurrent.{ExecutionContext, Future}

trait VinylDnsAction extends ActionFunction[Request, UserRequest] {

  val userLookup: String => IO[Option[User]]
  val oidcAuthenticator: OidcAuthenticator
  val oidcEnabled: Boolean = oidcAuthenticator.oidcEnabled

  val logger: Logger

  implicit val executionContext: ExecutionContext

  def notLoggedInResult: Future[Result]

  def cantFindAccountResult(un: String): Future[Result]

  def lockedUserResult(un: String): Future[Result]

  def getValidUsernameLdap(session: Session): Option[String] =
    session.get("username")

  def getValidUsernameOidc(session: Session): Option[String] =
    session.get(VinylDNS.ID_TOKEN).flatMap {
      oidcAuthenticator.getValidUsernameFromToken
    }

  def invokeBlock[A](
      request: Request[A],
      block: UserRequest[A] => Future[Result]): Future[Result] = {
    // if the user name is not in session, or token is invalid reject
    val userName = if (oidcEnabled) {
      getValidUsernameOidc(request.session)
    } else {
      getValidUsernameLdap(request.session)
    }

    userName match {
      case None =>
        logger.info(
          "User is not logged in or token expired; redirecting to login screen",
          entries(event("login-redirect", Id("None", "user"), Map("reason" -> "expired-token")))
        )
        notLoggedInResult

      case Some(un) =>
        // user name in session, let's get it from the repo
        userLookup(un).unsafeToFuture().flatMap {
          // Odd case, but let's handle with a different error message
          case None =>
            logger.error(
              "Cant find account for user with username",
              entries(event("notFound", Id(un, "user"))))
            cantFindAccountResult(un)

          case Some(user) if user.lockStatus == LockStatus.Locked =>
            logger.error(
              "User account is locked; redirecting to lock screen",
              entries(event("login-redirect", user, Map("reason" -> "locked"))))
            lockedUserResult(un)

          case Some(user) =>
            logger.debug(s"User logged in", entries(event("login-success", user)))
            block(new UserRequest(un, user, request))
        }
    }
  }
}
