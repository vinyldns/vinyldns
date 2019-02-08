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
import controllers.UserAccountAccessor
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.Pac4jScalaTemplateHelper
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.mvc.Results.Redirect
import play.api.mvc.{AnyContent, Request, Result, Session}
import play.api.test.Helpers.{redirectLocation, status, _}
import vinyldns.core.domain.membership.{LockStatus, User}

import scala.concurrent.Future

@RunWith(classOf[JUnitRunner])
class LdapFrontendActionSpec extends Specification with Mockito {

  val frodoUser = User(
    "fbaggins",
    "key",
    "secret",
    Some("Frodo"),
    Some("Baggins"),
    Some("fbaggins@hobbitmail.me"),
    DateTime.now,
    "frodo-uuid")

  val mockUserAccountAccessor: UserAccountAccessor = {
    val accessor = mock[UserAccountAccessor]
    doReturn(IO.pure(None)).when(accessor).get("no-existo")
    doReturn(IO.pure(Some(frodoUser))).when(accessor).get(frodoUser.userName)
    doReturn(IO.pure(Some(frodoUser.copy(lockStatus = LockStatus.Locked))))
      .when(accessor)
      .get("locked-user")

    doReturn(IO.pure(frodoUser)).when(accessor).create(any[User])

    accessor
  }

  val block: UserRequest[AnyContent] => Future[Result] = { _: UserRequest[AnyContent] =>
    Future.successful(Redirect("/index"))
  }

  val mockLdapFrontendAction: LdapFrontendAction = {
    new LdapFrontendAction(mockUserAccountAccessor.get)(
      scala.concurrent.ExecutionContext.global,
      mock[Pac4jScalaTemplateHelper[CommonProfile]])
  }

  val mockSession: Session = {
    val session = mock[Session]
    doReturn(Some(frodoUser.userName)).when(session).get("username")

    session
  }

  val mockRequest: Request[AnyContent] = {
    val request = mock[Request[AnyContent]]
    doReturn(mockSession).when(request).session

    request
  }

  "LdapFrontendActionSpec" should {
    "succeed and redirect to index if the user is found" in {
      val result: Future[Result] =
        mockLdapFrontendAction.invokeBlock(mockRequest, block)

      status(result) mustEqual 303
      redirectLocation(result) must beSome("/index")
    }

    "redirect to login if the user is not logged in" in {
      doReturn(None).when(mockSession).get("username")

      val result: Future[Result] =
        mockLdapFrontendAction.invokeBlock(mockRequest, block)

      status(result) mustEqual 303
      redirectLocation(result) must beSome("/login")
    }

    "redirect to login if user is not found in database" in {
      doReturn(Some("no-existo")).when(mockSession).get("username")

      val result: Future[Result] =
        mockLdapFrontendAction.invokeBlock(mockRequest, block)

      status(result) mustEqual 303
      redirectLocation(result) must beSome("/login")
    }

    "redirect to noaccess if user is locked" in {
      doReturn(Some("locked-user")).when(mockSession).get("username")

      val result: Future[Result] =
        mockLdapFrontendAction.invokeBlock(mockRequest, block)

      status(result) mustEqual 303
      redirectLocation(result) must beSome("/noaccess")
    }
  }
}
