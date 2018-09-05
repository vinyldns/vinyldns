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
import controllers.VinylDNS.Alerts
import javax.inject.Inject
import models.UserAccount
import play.api.mvc.Results.Redirect
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

// Because.  Play.  *sigh*
class UserAccountRequest[A](val userName: String, val account: UserAccount, request: Request[A])
    extends WrappedRequest(request)

/**
  * Custom action that will take a request and do an account lookup for the user
  * If the user is not in session, redirect to the login screen
  * If the user is in session, but the account is not found, redirect to the login screen with a different message
  * If the user is locked out, redirect to login screen
  * Otherwise, load the account into a custom UserAccountRequest and pass into the action
  */
class UserAccountAction(userLookup: String => Try[Option[UserAccount]])(
    implicit val executionContext: ExecutionContext)
    extends ActionFunction[Request, UserAccountRequest] {
  def invokeBlock[A](
      request: Request[A],
      block: UserAccountRequest[A] => Future[Result]): Future[Result] =
    // if the user name is not in session, kick out to the login screen
    request.session.get("username") match {
      case None =>
        Future(
          Redirect("/login").flashing(
            Alerts.warning("You are not logged in. Please login to continue.")))

      case Some(un) =>
        // user name in session, let's get it from the repo
        Future.fromTry(userLookup(un)).flatMap {
          case None =>
            // Odd case, but let's redirect to login with a different error message
            Future.successful(
              Redirect("/login").flashing(
                Alerts.warning(s"Unable to find user account for user name '$un'")))

          case Some(acc) =>
            // TODO: Check if the account is locked, if so then Redirect to login; else run the block
            block(new UserAccountRequest(un, acc, request))
        }
    }
}
