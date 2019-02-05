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

import cats.data.OptionT
import cats.implicits._
import cats.effect.IO
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.Pac4jScalaTemplateHelper

import play.api.mvc.{ActionFunction, Request, Result}
import vinyldns.core.domain.membership.{LockStatus, User}

import scala.concurrent.{ExecutionContext, Future}

abstract class VinylDnsAction(
    implicit val pac4jTemplateHelper: Pac4jScalaTemplateHelper[CommonProfile])
    extends ActionFunction[Request, UserRequest] {

  val userLookup: String => IO[Option[User]]
  val oidcEnabled: Boolean
  val oidcUsernameField: String

  implicit val executionContext: ExecutionContext

  def notLoggedInResult: Future[Result]

  def cantFindAccountResult(un: String): Future[Result]
  
  def lockedUserResult(un: String): Future[Result]


  def createUser(
      un: String,
      fname: Option[String],
      lname: Option[String],
      email: Option[String]): Future[Option[User]] = Future.successful(None)

  def invokeBlock[A](
      request: Request[A],
      block: UserRequest[A] => Future[Result]): Future[Result] = {
    // Get user from session.

    val oidcProfile = if (oidcEnabled) {
      pac4jTemplateHelper
        .getCurrentProfile(request)
    } else {
      None
    }

    val expired = oidcProfile.exists(_.isExpired)

    val username = if (oidcEnabled) {
      oidcProfile.map(_.getAttribute(oidcUsernameField).toString)
    } else {
      request.session.get("username")
    }

    username match {
      case None => notLoggedInResult

      case Some(un) if oidcEnabled && expired => notLoggedInResult

      case Some(un) =>
        // user name in session, let's get it from the repo
        userLookup(un).unsafeToFuture().flatMap {
          // At this point, will create the user if this is coming from a frontend action
          case None if oidcEnabled => {
            val userCreation = for {
              prof <- OptionT.fromOption[Future](oidcProfile)
              user <- OptionT(
                createUser(
                  un,
                  Some(prof.getFirstName),
                  Some(prof.getFamilyName),
                  Some(prof.getEmail)))
              blk <- OptionT.liftF(block(new UserRequest(un, user, request)))
            } yield blk

            userCreation.value.flatMap {
              case Some(res) => Future.successful(res)
              case None => cantFindAccountResult(un)
            }
          }

          case None => cantFindAccountResult(un)

          case Some(user) if user.lockStatus == LockStatus.Locked => lockedUserResult(un)

          case Some(user) =>
            block(new UserRequest(un, user, request))
        }
    }
  }

}
