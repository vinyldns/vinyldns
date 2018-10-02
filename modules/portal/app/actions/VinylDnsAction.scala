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
import play.api.mvc.{ActionFunction, Request, Result}
import vinyldns.core.domain.membership.{LockStatus, User}
import scala.concurrent.{ExecutionContext, Future}

trait VinylDnsAction extends ActionFunction[Request, UserRequest] {
  val userLookup: String => IO[Option[User]]
  implicit val executionContext: ExecutionContext
  def notLoggedInResult: Future[Result]
  def cantFindAccountResult(un: String): Future[Result]
  def lockedUserResult: Future[Result]

  def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
    // if the user name is not in session, reject
    request.session.get("username") match {
      case None => notLoggedInResult

      case Some(un) =>
        // user name in session, let's get it from the repo
        userLookup(un).unsafeToFuture().flatMap {
          // Odd case, but let's handle with a different error message
          case None => cantFindAccountResult(un)

          case Some(user) if user.lockStatus == LockStatus.Locked => lockedUserResult

          case Some(user) => block(new UserRequest(un, user, request))
        }
    }
}
