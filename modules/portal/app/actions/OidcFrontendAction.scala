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
import controllers.{CacheHeader, UserAccountAccessor, UserDetails, VinylDNS}
import javax.inject.Inject
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{Pac4jScalaTemplateHelper, SecurityComponents}
import play.api.{Configuration, Logger}
import play.api.mvc.Result
import vinyldns.core.domain.membership.User

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Custom action that will take a request and do an account lookup for the user
  * If the user is not in session, redirect to the login screen
  * If the user is in session, but the account is not found, redirect to the login screen with a different message
  * If the user is locked out, redirect to login screen
  * Otherwise, load the account into a custom UserAccountRequest and pass into the action
  */
class OidcFrontendAction @Inject()(
    configuration: Configuration,
    val userLookup: String => IO[Option[User]],
    val controllerComponents: SecurityComponents,
    val userAccountAccessor: UserAccountAccessor)(
    implicit val executionContext: ExecutionContext,
    pac4jTemplateHelper: Pac4jScalaTemplateHelper[CommonProfile])
    extends VinylDnsAction(configuration)
    with CacheHeader {

  def notLoggedInResult: Future[Result] =
    Future.successful(
      Redirect("/")
        .withHeaders(cacheHeaders: _*))

  def cantFindAccountResult(un: String): Future[Result] = {
    Future.successful(
      Redirect("/")
        .withHeaders(cacheHeaders: _*))
  }

  // TODO need new screen for this
  def lockedUserResult: Future[Result] =
    Future.successful(
      Redirect("/")
        .withNewSession
        .withHeaders(cacheHeaders: _*))

  override def createUser(
      un: String,
      fname: Option[String],
      lname: Option[String],
      email: Option[String]): Future[Option[User]] = {
    val user = userAccountAccessor
      .get(un)
      .flatMap {
        case None =>
          Logger.info(s"Creating user account for ${un}")
          createNewUser(un, fname, lname, email).map { u: User =>
            Logger.info(s"User account for ${u.userName} created with id ${u.id}")
            u
          }
        case Some(u) =>
          Logger.info(s"User account for ${u.userName} exists with id ${u.id}")
          IO.pure(u)
      }

    user.map(Some(_)).unsafeToFuture()
  }

  private def createNewUser(
      un: String,
      fname: Option[String],
      lname: Option[String],
      email: Option[String]): IO[User] = {
    val newUser =
      User(un, User.generateKey, User.generateKey, fname, lname, email)
    userAccountAccessor.create(newUser)
  }
}
