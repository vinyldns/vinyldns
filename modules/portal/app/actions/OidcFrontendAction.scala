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
import controllers.{CacheHeader, VinylDNS}
import play.api.mvc.Result
import play.api.mvc.Results.Redirect
import vinyldns.core.domain.membership.User

import scala.concurrent.{ExecutionContext, Future}

/**
  * Custom action that will take a request and do an account lookup for the user
  * If the user is not in session, redirect to the login screen
  * If the user is in session, but the account is not found, redirect to the login screen with a different message
  * If the user is locked out, redirect to login screen
  * Otherwise, load the account into a custom UserAccountRequest and pass into the action
  */
class OidcFrontendAction(val userLookup: String => IO[Option[User]])(
    implicit val executionContext: ExecutionContext)
    extends VinylDnsAction
    with CacheHeader {

  def notLoggedInResult: Future[Result] =
    Future.successful(
      Redirect("/testopen")
        .withHeaders(cacheHeaders: _*))

  def cantFindAccountResult(un: String): Future[Result] =
    Future.successful(
      Redirect("/testopen")
        .withHeaders(cacheHeaders: _*))

  // TODO need new screen for this
  def lockedUserResult: Future[Result] =
    Future.successful(
      Redirect("/login")
        .flashing(VinylDNS.Alerts.error(s"Account locked"))
        .withNewSession
        .withHeaders(cacheHeaders: _*))
}
