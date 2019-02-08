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
import org.specs2.matcher.ResultMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.mvc.Results._
import play.api.test.Helpers._
import play.api.mvc.{AnyContent, Request, RequestHeader, Result}
import vinyldns.core.domain.membership.{LockStatus, User}

import scala.concurrent.Future

@RunWith(classOf[JUnitRunner])
class OidcFrontendActionSpec extends Specification with Mockito with ResultMatchers {

  val frodoUser = User(
    "fbaggins",
    "key",
    "secret",
    Some("Frodo"),
    Some("Baggins"),
    Some("fbaggins@hobbitmail.me"),
    DateTime.now,
    "frodo-uuid")

  val oidcUsernameField = "username"

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

  val mockProfile: CommonProfile = {
    val profile = mock[CommonProfile]
    doReturn(frodoUser.firstName.get).when(profile).getFirstName
    doReturn(frodoUser.lastName.get).when(profile).getFamilyName
    doReturn(frodoUser.email.get).when(profile).getEmail
    doReturn(false).when(profile).isExpired

    profile
  }

  val mockPac4jScalaTemplateHelper: Pac4jScalaTemplateHelper[CommonProfile] = {
    val template = mock[Pac4jScalaTemplateHelper[CommonProfile]]

    template
  }

  val mockOidcFrontendAction: OidcFrontendAction = {
    new OidcFrontendAction(
      mockUserAccountAccessor.get,
      mockUserAccountAccessor.create,
      oidcUsernameField)(scala.concurrent.ExecutionContext.global, mockPac4jScalaTemplateHelper)
  }

  val block: UserRequest[AnyContent] => Future[Result] = { request: UserRequest[AnyContent] =>
    Future.successful(Redirect("/index"))
  }

  "OidcFrontendAction" should {
    "succeed and redirect to index if the user is found" in {
      doReturn(frodoUser.userName).when(mockProfile).getAttribute(oidcUsernameField)
      doReturn(Some(mockProfile))
        .when(mockPac4jScalaTemplateHelper)
        .getCurrentProfile(any[RequestHeader])

      val result: Future[Result] =
        mockOidcFrontendAction.invokeBlock(mock[Request[AnyContent]], block)
      status(result) mustEqual 303
      redirectLocation(result) must beSome("/index")
    }

    "redirect if the user is not logged in" in {
      doReturn(None)
        .when(mockPac4jScalaTemplateHelper)
        .getCurrentProfile(any[RequestHeader])

      val result: Future[Result] =
        mockOidcFrontendAction.invokeBlock(mock[Request[AnyContent]], block)

      status(result) mustEqual 303
      redirectLocation(result) must beSome("/")
    }

    "redirect if the session is expired" in {
      doReturn(Some(mockProfile))
        .when(mockPac4jScalaTemplateHelper)
        .getCurrentProfile(any[RequestHeader])
      doReturn(true).when(mockProfile).isExpired

      val result: Future[Result] =
        mockOidcFrontendAction.invokeBlock(mock[Request[AnyContent]], block)

      status(result) mustEqual 303
      redirectLocation(result) must beSome("/")
    }

    "create user if not found in database and redirect to index" in {
      doReturn(Some(mockProfile))
        .when(mockPac4jScalaTemplateHelper)
        .getCurrentProfile(any[RequestHeader])
      doReturn(false).when(mockProfile).isExpired
      doReturn("no-existo").when(mockProfile).getAttribute(oidcUsernameField)

      val result: Future[Result] =
        mockOidcFrontendAction.invokeBlock(mock[Request[AnyContent]], block)

      status(result) mustEqual 303
      redirectLocation(result) must beSome("/index")
    }

    "redirect to noaccess if user is locked" in {
      doReturn(Some(mockProfile))
        .when(mockPac4jScalaTemplateHelper)
        .getCurrentProfile(any[RequestHeader])
      doReturn("locked-user").when(mockProfile).getAttribute(oidcUsernameField)

      val result: Future[Result] =
        mockOidcFrontendAction.invokeBlock(mock[Request[AnyContent]], block)

      status(result) mustEqual 303
      redirectLocation(result) must beSome("/noaccess")
    }
  }
}
