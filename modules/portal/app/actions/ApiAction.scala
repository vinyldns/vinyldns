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
import controllers.CacheHeader
import play.api.mvc.{ActionFunction, Request, Result}
import play.api.mvc.Results.{Forbidden, NotFound, Unauthorized}
import vinyldns.core.domain.membership.{LockStatus, User}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Custom action that will take a request and do an account lookup for the user
  * If the user is not in session, return Unauthorized message
  * If the user is in session, but the account is not found, return Not Found message
  * If the user is locked out, return Forbidden message
  * Otherwise, load the account into a custom UserAccountRequest and pass into the action
  */
class ApiAction(userLookup: String => IO[Option[User]])(
    implicit val executionContext: ExecutionContext)
    extends ActionFunction[Request, UserRequest]
    with CacheHeader {

  def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
    // if the user name is not in session, reject
    request.session.get("username") match {
      case None =>
        Future(
          Unauthorized("You are not logged in. Please login to continue.").withHeaders(
            cacheHeaders: _*))

      case Some(un) =>
        // user name in session, let's get it from the repo
        userLookup(un).unsafeToFuture().flatMap {
          case None =>
            // Odd case, but let's handle with a different error message
            Future.successful(
              NotFound(s"Unable to find user account for user name '$un'")
                .withHeaders(cacheHeaders: _*))

          case Some(user) if user.lockStatus == LockStatus.Locked =>
            Future.successful(Forbidden(s"Account is locked.").withHeaders(cacheHeaders: _*))

          case Some(user) =>
            block(new UserRequest(un, user, request))
        }
    }
}
