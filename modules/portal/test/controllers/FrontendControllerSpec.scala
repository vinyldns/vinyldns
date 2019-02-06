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

import cats.effect.IO
import org.junit.runner.RunWith
import org.pac4j.play.scala.SecurityComponents
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.Configuration
import play.api.test.CSRFTokenHelper._
import play.api.test.Helpers._
import play.api.test._
import vinyldns.core.crypto.{CryptoAlgebra, NoOpCrypto}
import vinyldns.core.domain.membership.User

@RunWith(classOf[JUnitRunner])
class FrontendControllerSpec extends Specification with Mockito with TestApplicationData {

  val components: SecurityComponents = mockControllerComponents
  val config: Configuration = testConfigLdap
  val crypto: CryptoAlgebra = new NoOpCrypto()
  val userAccessor: UserAccountAccessor = buildMockUserAccountAccessor
  val underTest = new FrontendController(
    components,
    config,
    userAccessor,
    new NoOpCrypto()
  )
  val lockedUserAccessor: UserAccountAccessor = buildMockLockedkUserAccountAccessor
  val lockedUserUnderTest = new FrontendController(
    components,
    config,
    lockedUserAccessor,
    crypto
  )

  "FrontendController" should {
    "send 404 on a bad request" in new WithApplication(app) {
      route(app, FakeRequest(GET, "/boum")) must beSome.which(status(_) == NOT_FOUND)
    }

    "Get for '/'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the index page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.index()(FakeRequest(GET, "/").withSession("username" -> "frodo").withCSRFToken)
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain(s"Are you sure you want to log out")
      }
    }

    "Get for '/index'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/index")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the zone page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.index()(
            FakeRequest(GET, "/index").withSession("username" -> "frodo").withCSRFToken)
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Are you sure you want to log out")
        contentAsString(result) must contain("Zones | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.index()(
            FakeRequest(GET, "/index").withSession("username" -> "lockedFbaggins").withCSRFToken)
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for '/groups'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/groups")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the groups view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewAllGroups()(
            FakeRequest(GET, "/groups").withSession("username" -> "frodo").withCSRFToken)
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Groups | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewAllGroups()(
            FakeRequest(GET, "/groups").withSession("username" -> "lockedFbaggins").withCSRFToken)
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for '/groups/id'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/groups/some-id")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the groups view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewGroup("some-id")(
            FakeRequest(GET, "/groups").withSession("username" -> "frodo").withCSRFToken)
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Group | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewGroup("some-id")(
            FakeRequest(GET, "/groups").withSession("username" -> "lockedFbaggins").withCSRFToken)
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for '/zones'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/zones")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the zone view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewAllZones()(
            FakeRequest(GET, "/zones").withSession("username" -> "frodo").withCSRFToken)
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Zones | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewAllZones()(
            FakeRequest(GET, "/zones").withSession("username" -> "lockedFbaggins").withCSRFToken)
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for '/zones/id'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/zones/some-id")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the zones view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewZone("some-id")(
            FakeRequest(GET, "/zones/some-id").withSession("username" -> "frodo").withCSRFToken)
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Zone | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewZone("some-id")(
            FakeRequest(GET, "/zones/some-id")
              .withSession("username" -> "lockedFbaggins")
              .withCSRFToken)
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for login" should {
      "render the login page when the user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/login")).get
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Username")
        contentAsString(result) must contain("Password")
      }
      "redirect to the index page when the user is logged in" in new WithApplication(app) {
        val username = "LoggedInUser"
        val result = route(
          app,
          FakeRequest(GET, "/login")
            .withSession(("username", username))).get
        status(result) must beEqualTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/index")
      }
      "redirect to the index page when a user is locked out" in new WithApplication(app) {
        // redirects to the index page because the user is logged in, then locked status is checked there
        val result =
          lockedUserUnderTest.loginPage()(
            FakeRequest(GET, "/login").withSession("username" -> "lockedFbaggins").withCSRFToken)
        headers(result) must contain("Location" -> "/index")
      }
    }

    "Get for logout" should {
      "redirect to the login page" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/logout")).get

        status(result) must beEqualTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "clear the session cookie" in new WithApplication(app) {
        // TODO: cookie behavior is radically different in play 2.6, so we cannot look for a Set-Cookie header
        val username = "LoggedInUser"
        val result = route(
          app,
          FakeRequest(GET, "/logout")
            .withSession(("username", username))).get
        headers(result) must contain("Location" -> "/login")
      }
    }

    "Get for '/noaccess'" should {
      "render the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.noAccess()(
            FakeRequest(GET, "/noaccess").withSession("username" -> "lockedFbaggins").withCSRFToken)
        status(result) must beEqualTo(UNAUTHORIZED)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Account locked.")
        contentAsString(result) must contain("No Access to VinylDNS")
      }
    }

    "Get for '/batchchanges'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/zones")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the batch changes view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewAllBatchChanges()(
            FakeRequest(GET, "/batchchanges").withSession("username" -> "frodo").withCSRFToken)
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Batch Changes | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewAllBatchChanges()(
            FakeRequest(GET, "/batchchanges")
              .withSession("username" -> "lockedFbaggins")
              .withCSRFToken)
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for '/batchchanges/id'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/batchchanges/some-id")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the batch change view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewBatchChange("some-id")(
            FakeRequest(GET, "/batchchanges/some-id")
              .withSession("username" -> "frodo")
              .withCSRFToken)
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Batch Change | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewBatchChange("some-id")(
            FakeRequest(GET, "/batchchanges/some-id")
              .withSession("username" -> "lockedFbaggins")
              .withCSRFToken)
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for '/batchchanges/new'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/batchchanges/new")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the new batch change view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewNewBatchChange()(
            FakeRequest(GET, "/batchchanges/new").withSession("username" -> "frodo").withCSRFToken)
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("New Batch Change | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewNewBatchChange()(
            FakeRequest(GET, "/batchchanges/new")
              .withSession("username" -> "lockedFbaggins")
              .withCSRFToken)
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "CustomLinks" should {
      "be displayed on login screen if login screen flag is true" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/login")).get
        contentType(result) must beSome.which(_ == "text/html")
        status(result) must equalTo(OK)
        contentAsString(result) must contain("test link login")
        contentAsString(result) must not(contain("test link sidebar"))
      }

      "be displayed on the logged-in view if sidebar flag is true" in new WithApplication(app) {
        val result =
          underTest.viewAllZones()(
            FakeRequest(GET, "/zones").withSession("username" -> "frodo").withCSRFToken)
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("test link sidebar")
        contentAsString(result) must not(contain("test link login"))
      }
    }
//    ".serveCredFile" should {
//      "return a csv file with the new style credentials" in new WithApplication(app) {
//        import play.api.mvc.Result
//
//        val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
//        userAccessor.get(frodoUser.userName).returns(IO.pure(Some(frodoUser)))
//        val underTest =
//          new VinylDNS(config, mockLdapAuthenticator, userAccessor, ws, components, crypto)
//
//        val result: Future[Result] = underTest.serveCredsFile("credsfile.csv")(
//          FakeRequest(GET, s"/download-creds-file/credsfile.csv")
//            .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))
//        val content: String = contentAsString(result)
//        content must contain("NT ID")
//        content must contain(frodoUser.userName)
//        content must contain(frodoUser.accessKey)
//        content must contain(frodoUser.secretKey)
//        there.was(one(crypto).decrypt(frodoUser.secretKey))
//      }
//      "redirect to login if user is not logged in" in new WithApplication(app) {
//        import play.api.mvc.Result
//
//        val underTest =
//          new VinylDNS(config, mockLdapAuthenticator, mockUserAccessor, ws, components, crypto)
//
//        val result: Future[Result] = underTest.serveCredsFile("credsfile.csv")(
//          FakeRequest(GET, s"/download-creds-file/credsfile.csv"))
//
//        status(result) mustEqual 303
//        redirectLocation(result) must beSome("/login")
//        flash(result).get("alertType") must beSome("danger")
//        flash(result).get("alertMessage") must beSome(
//          "You are not logged in. Please login to continue.")
//      }
//      "redirect to login if user account is locked" in new WithApplication(app) {
//        import play.api.mvc.Result
//
//        val underTest =
//          new VinylDNS(
//            config,
//            mockLdapAuthenticator,
//            mockLockedUserAccessor,
//            ws,
//            components,
//            crypto)
//
//        val result: Future[Result] = underTest.serveCredsFile("credsfile.csv")(
//          FakeRequest(GET, s"/download-creds-file/credsfile.csv")
//            .withSession(
//              "username" -> lockedFrodoUser.userName,
//              "accessKey" -> lockedFrodoUser.accessKey))
//
//        status(result) mustEqual 303
//        redirectLocation(result) must beSome("/login")
//        flash(result).get("alertType") must beSome("danger")
//        flash(result).get("alertMessage") must beSome("Account locked")
//      }
//      "redirect to login if user account is not found" in new WithApplication(app) {
//        import play.api.mvc.Result
//
//        val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
//        userAccessor.get(frodoUser.userName).returns(IO.pure(None))
//        val underTest =
//          new VinylDNS(config, mockLdapAuthenticator, userAccessor, ws, components, crypto)
//
//        val result: Future[Result] = underTest.serveCredsFile("credsfile.csv")(
//          FakeRequest(GET, s"/download-creds-file/credsfile.csv")
//            .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))
//
//        status(result) mustEqual 303
//        redirectLocation(result) must beSome("/login")
//        flash(result).get("alertType") must beSome("danger")
//        flash(result).get("alertMessage") must beSome(
//          s"Unable to find user account for user name '${frodoUser.userName}'")
//      }
//    }

  }

  def buildMockUserAccountAccessor: UserAccountAccessor = {
    val accessor = mock[UserAccountAccessor]
    accessor.get(anyString).returns(IO.pure(Some(frodoUser)))
    accessor.create(any[User]).returns(IO.pure(frodoUser))
    accessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
    accessor
  }

  def buildMockLockedkUserAccountAccessor: UserAccountAccessor = {
    val accessor = mock[UserAccountAccessor]
    accessor.get(anyString).returns(IO.pure(Some(lockedFrodoUser)))
    accessor.create(any[User]).returns(IO.pure(lockedFrodoUser))
    accessor.getUserByKey(anyString).returns(IO.pure(Some(lockedFrodoUser)))
    accessor
  }
}
