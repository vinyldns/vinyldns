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
import controllers.{CacheHeader, OidcAuthenticator}
import javax.inject.Inject
import org.slf4j.LoggerFactory
import play.api.mvc.Results.{Forbidden, NotFound, Unauthorized}
import play.api.mvc.{ActionFunction, AnyContent, BodyParser, ControllerComponents, Request, Result}
import vinyldns.core.domain.membership.User

import scala.concurrent.{ExecutionContext, Future}

/**
  * Custom action that will take a request and do an account lookup for the user
  * If the user is not in session, return Unauthorized message
  * If the user is in session, but the account is not found, return Not Found message
  * If the user is locked out, return Forbidden message
  * Otherwise, load the account into a custom UserAccountRequest and pass into the action
  */
class LegacyApiAction @Inject()(
    val userLookup: String => IO[Option[User]],
    val oidcAuthenticator: OidcAuthenticator,
    val parser: BodyParser[AnyContent])
    extends VinylDnsAction
    with ApiActionBuilder
    with CacheHeader {

  override val logger = LoggerFactory.getLogger(classOf[LegacyApiAction])
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global

  def notLoggedInResult: Future[Result] =
    Future.successful(
      Unauthorized("You are not logged in. Please login to continue.").withHeaders(
        cacheHeaders: _*))

  def cantFindAccountResult(un: String): Future[Result] =
    Future.successful(
      NotFound(s"Unable to find user account for user name '$un'")
        .withHeaders(cacheHeaders: _*))

  def lockedUserResult(un: String): Future[Result] =
    Future.successful(Forbidden(s"User account for `$un` is locked.").withHeaders(cacheHeaders: _*))
}
