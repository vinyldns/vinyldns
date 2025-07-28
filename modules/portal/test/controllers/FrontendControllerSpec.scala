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

import actions.{LegacySecuritySupport, SecuritySupport}
import akka.http.scaladsl.model.Uri
import cats.effect.IO
import org.junit.runner.RunWith
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.mvc.{AnyContent, BodyParser, ControllerComponents}
import play.api.test.CSRFTokenHelper._
import play.api.test.Helpers._
import play.api.test._
import play.api.{Configuration, Environment}
import vinyldns.core.domain.membership.User

import scala.concurrent.ExecutionContext

@RunWith(classOf[JUnitRunner])
class FrontendControllerSpec extends Specification with Mockito with TestApplicationData {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  val components: ControllerComponents = Helpers.stubControllerComponents()
  val parser: BodyParser[AnyContent] = Helpers.stubBodyParser()
  val config: Configuration = Configuration.load(Environment.simple())
  val oidcConfig: Configuration = config ++ Configuration.from(Map("oidc.enabled" -> true))
  val userAccessor: UserAccountAccessor = buildMockUserAccountAccessor
  val disabledOidcAuthenticator: OidcAuthenticator = mock[OidcAuthenticator]
  val mockOidcAuthenticator: OidcAuthenticator = mock[OidcAuthenticator]
  val enabledOidcAuthenticator: OidcAuthenticator = mock[OidcAuthenticator]
  enabledOidcAuthenticator.oidcEnabled.returns(true)
  enabledOidcAuthenticator.getCodeCall(anyString).returns(Uri("http://test.com"))
  enabledOidcAuthenticator.oidcLogoutUrl.returns("http://logout-test.com")
  enabledOidcAuthenticator.getValidUsernameFromToken(any[String]).returns(Some("test"))

  val lockedUserAccessor: UserAccountAccessor = buildMockLockedkUserAccountAccessor
  val superUserAccessor: UserAccountAccessor = buildMockSuperUserAccountAccessor
  val disabledOidcSec: SecuritySupport =
    new LegacySecuritySupport(components, userAccessor, config, disabledOidcAuthenticator)
  val enabledOidcSec: SecuritySupport =
    new LegacySecuritySupport(components, userAccessor, config, enabledOidcAuthenticator)
  val lockedSec: SecuritySupport =
    new LegacySecuritySupport(components, lockedUserAccessor, config, disabledOidcAuthenticator)
  val superSec: SecuritySupport =
    new LegacySecuritySupport(components, superUserAccessor, config, disabledOidcAuthenticator)

  val underTest = new FrontendController(
    components,
    config,
    disabledOidcSec
  )

  val lockedUserUnderTest = new FrontendController(
    components,
    config,
    lockedSec
  )

  val superUserUnderTest = new FrontendController(
    components,
    config,
    superSec
  )

  val oidcUnderTest = new FrontendController(
    components,
    oidcConfig,
    enabledOidcSec
  )

  "FrontendController" should {
    "send 404 on a bad request" in new WithApplication(app) {
      route(app, FakeRequest(GET, "/boum")) must beSome.which(status(_) == NOT_FOUND)
    }

    "Get for '/'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = underTest.index()(FakeRequest(GET, "/"))
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login?target=/")
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
      "with ldap enabled" should {
        "redirect to the login page when a user is not logged in" in new WithApplication(app) {
          val result = underTest.index()(FakeRequest(GET, "/index"))
          status(result) must equalTo(SEE_OTHER)
          headers(result) must contain("Location" -> "/login?target=/index")
        }
        "render the DNS Changes page when the user is logged in" in new WithApplication(app) {
          val result =
            underTest.index()(
              FakeRequest(GET, "/index").withSession("username" -> "frodo").withCSRFToken
            )
          status(result) must beEqualTo(OK)
          contentType(result) must beSome.which(_ == "text/html")
          contentAsString(result) must contain("Are you sure you want to log out")
          contentAsString(result) must contain("DNS Changes | VinylDNS")
        }
        "redirect to the no access page when a user is locked out" in new WithApplication(app) {
          val result =
            lockedUserUnderTest.index()(
              FakeRequest(GET, "/index").withSession("username" -> "lockedFbaggins").withCSRFToken
            )
          headers(result) must contain("Location" -> "/noaccess")
        }
      }
      "with oidc enabled" should {
        "redirect to the login page when a user is not logged in" in new WithApplication(app) {
          val result = oidcUnderTest.index()(FakeRequest(GET, "/index").withCSRFToken)
          status(result) must equalTo(SEE_OTHER)
          headers(result) must contain("Location" -> "/login?target=/index")
        }
        "render the DNS Changes page when the user is logged in" in new WithApplication(app) {
          val result =
            oidcUnderTest.index()(
              FakeRequest(GET, "/index").withSession(VinylDNS.ID_TOKEN -> "test").withCSRFToken
            )

          status(result) must beEqualTo(OK)
          contentType(result) must beSome.which(_ == "text/html")
          contentAsString(result) must contain("Are you sure you want to log out")
          contentAsString(result) must contain("DNS Changes | VinylDNS")
        }
      }
    }

    "Get for '/groups'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = underTest.viewAllGroups()(FakeRequest(GET, "/groups"))
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login?target=/groups")
      }
      "render the groups view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewAllGroups()(
            FakeRequest(GET, "/groups").withSession("username" -> "frodo").withCSRFToken
          )
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Groups | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewAllGroups()(
            FakeRequest(GET, "/groups").withSession("username" -> "lockedFbaggins").withCSRFToken
          )
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for '/groups/id'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = underTest.viewGroup("some-id")(FakeRequest(GET, "/groups/some-id"))
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login?target=/groups/some-id")
      }
      "render the groups view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewGroup("some-id")(
            FakeRequest(GET, "/groups").withSession("username" -> "frodo").withCSRFToken
          )
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Group | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewGroup("some-id")(
            FakeRequest(GET, "/groups").withSession("username" -> "lockedFbaggins").withCSRFToken
          )
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for '/zones'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = underTest.viewAllZones()(FakeRequest(GET, "/zones"))
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login?target=/zones")
      }
      "render the zone view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewAllZones()(
            FakeRequest(GET, "/zones").withSession("username" -> "frodo").withCSRFToken
          )
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Zones | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewAllZones()(
            FakeRequest(GET, "/zones").withSession("username" -> "lockedFbaggins").withCSRFToken
          )
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for '/zones/id'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = underTest.viewZone("some-id")(FakeRequest(GET, "/zones/some-id"))
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login?target=/zones/some-id")
      }
      "render the zones view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewZone("some-id")(
            FakeRequest(GET, "/zones/some-id").withSession("username" -> "frodo").withCSRFToken
          )
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Zone | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewZone("some-id")(
            FakeRequest(GET, "/zones/some-id")
              .withSession("username" -> "lockedFbaggins")
              .withCSRFToken
          )
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for '/recordsets'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = underTest.viewRecordSets()(FakeRequest(GET, "/recordsets"))
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login?target=/recordsets")
      }
      "render the recordset view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewRecordSets()(
            FakeRequest(GET, "/recordsets").withSession("username" -> "frodo").withCSRFToken
          )
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("RecordSets | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewRecordSets()(
            FakeRequest(GET, "/recordsets")
              .withSession("username" -> "lockedFbaggins")
              .withCSRFToken
          )
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for login" should {
      "with ldap enabled" should {
        "render the login page when the user is not logged in" in new WithApplication(app) {
          val result = underTest.loginPage()(FakeRequest(GET, "/login").withCSRFToken)
          contentType(result) must beSome.which(_ == "text/html")
          contentAsString(result) must contain("Username")
          contentAsString(result) must contain("Password")
        }
        "redirect to the index page when the user is logged in" in new WithApplication(app) {
          val username = "LoggedInUser"
          val result = underTest.loginPage()(
            FakeRequest(GET, "/login")
              .withSession(("username", username))
          )
          status(result) must beEqualTo(SEE_OTHER)
          headers(result) must contain("Location" -> "/index")
        }
        "redirect to the index page when a user is locked out" in new WithApplication(app) {
          // redirects to the index page because the user is logged in, then locked status is checked there
          val result =
            lockedUserUnderTest.loginPage()(
              FakeRequest(GET, "/login").withSession("username" -> "lockedFbaggins").withCSRFToken
            )
          headers(result) must contain("Location" -> "/index")
        }
      }
      "with oidc enabled" should {
        "redirect to code call with no session" in new WithApplication(app) {
          val result =
            oidcUnderTest.loginPage()(FakeRequest(GET, "/login").withCSRFToken)

          status(result) must equalTo(302)
          headers(result) must contain("Location" -> "http://test.com")
        }
        "redirect to the index page when a user is logged in" in new WithApplication(app) {
          val result =
            oidcUnderTest.loginPage()(
              FakeRequest(GET, "/login")
                .withSession(VinylDNS.ID_TOKEN -> "test")
                .withCSRFToken
            )

          status(result) must equalTo(SEE_OTHER)
          headers(result) must contain("Location" -> "/index")
        }
      }
    }

    "Get for logout" should {
      "with ldap enabled" should {
        "redirect to the login page" in new WithApplication(app) {
          val result = underTest.logout()(FakeRequest(GET, "/logout"))

          status(result) must beEqualTo(SEE_OTHER)
          headers(result) must contain("Location" -> "/login")
        }
        "clear the session cookie" in new WithApplication(app) {
          // TODO: cookie behavior is radically different in play 2.6, so we cannot look for a Set-Cookie header
          val username = "LoggedInUser"
          val result = underTest.logout()(
            FakeRequest(GET, "/logout")
              .withSession(("username", username))
          )
          headers(result) must contain("Location" -> "/login")
        }
      }
      "with oidc enabled" should {
        "redirect to the logout url" in new WithApplication(app) {
          val result =
            oidcUnderTest.logout()(
              FakeRequest(GET, "/logout")
                .withSession(VinylDNS.ID_TOKEN -> "test")
                .withCSRFToken
            )

          status(result) must equalTo(SEE_OTHER)
          headers(result) must contain("Location" -> "http://logout-test.com")
        }
      }
    }

    "Get for '/noaccess'" should {
      "render the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.noAccess()(
            FakeRequest(GET, "/noaccess").withSession("username" -> "lockedFbaggins").withCSRFToken
          )
        status(result) must beEqualTo(UNAUTHORIZED)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Account locked.")
        contentAsString(result) must contain("No Access to VinylDNS")
      }
    }

    "Get for '/dnschanges'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = underTest.viewAllBatchChanges()(FakeRequest(GET, "/dnschanges"))
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login?target=/dnschanges")
      }
      "render the batch changes view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewAllBatchChanges()(
            FakeRequest(GET, "/dnschanges").withSession("username" -> "frodo").withCSRFToken
          )
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("DNS Changes | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewAllBatchChanges()(
            FakeRequest(GET, "/dnschanges")
              .withSession("username" -> "lockedFbaggins")
              .withCSRFToken
          )
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for '/settings'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = superUserUnderTest.viewSettings()(FakeRequest(GET, "/settings"))
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login?target=/settings")
      }
      "render the settings view page when the user is logged in and the user is a super or support user" in new WithApplication(app) {
        val result =
          superUserUnderTest.viewSettings()(
            FakeRequest(GET, "/settings").withSession("username" -> "sgamgee").withCSRFToken
          )
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Settings | VinylDNS")
      }
      "redirect to the forbidden page when a user is not a super or support user" in new WithApplication(app) {
        val result =
          underTest.viewSettings()(
            FakeRequest(GET, "/settings").withSession("username" -> "frodo").withCSRFToken
          )
        status(result) mustEqual FORBIDDEN
        contentAsString(result) mustEqual "You are not authorized to access this page."
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewSettings()(
            FakeRequest(GET, "/settings")
              .withSession("username" -> "lockedFbaggins")
              .withCSRFToken
          )
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for '/dnschanges/id'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = underTest.viewBatchChange("some-id")(FakeRequest(GET, "/dnschanges/some-id"))
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login?target=/dnschanges/some-id")
      }
      "render the DNS change view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewBatchChange("some-id")(
            FakeRequest(GET, "/dnschanges/some-id")
              .withSession("username" -> "frodo")
              .withCSRFToken
          )
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("DNS Change | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewBatchChange("some-id")(
            FakeRequest(GET, "/dnschanges/some-id")
              .withSession("username" -> "lockedFbaggins")
              .withCSRFToken
          )
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "Get for '/dnschanges/new'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = underTest.viewNewBatchChange()(FakeRequest(GET, "/dnschanges/new"))
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login?target=/dnschanges/new")
      }
      "render the new batch change view page when the user is logged in" in new WithApplication(app) {
        val result =
          underTest.viewNewBatchChange()(
            FakeRequest(GET, "/dnschanges/new").withSession("username" -> "frodo").withCSRFToken
          )
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("New DNS Change | VinylDNS")
      }
      "redirect to the no access page when a user is locked out" in new WithApplication(app) {
        val result =
          lockedUserUnderTest.viewNewBatchChange()(
            FakeRequest(GET, "/dnschanges/new")
              .withSession("username" -> "lockedFbaggins")
              .withCSRFToken
          )
        headers(result) must contain("Location" -> "/noaccess")
      }
    }

    "CustomLinks" should {
      "be displayed on login screen if login screen flag is true" in new WithApplication(app) {
        val result = underTest.loginPage()(FakeRequest(GET, "/login").withCSRFToken)
        contentType(result) must beSome.which(_ == "text/html")
        status(result) must equalTo(OK)
        contentAsString(result) must contain("test link login")
        contentAsString(result) must not(contain("test link sidebar"))
      }

      "be displayed on the logged-in view if sidebar flag is true" in new WithApplication(app) {
        val result =
          underTest.viewAllZones()(
            FakeRequest(GET, "/zones").withSession("username" -> "frodo").withCSRFToken
          )
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("test link sidebar")
        contentAsString(result) must not(contain("test link login"))
      }
    }
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

  def buildMockSuperUserAccountAccessor: UserAccountAccessor = {
    val accessor = mock[UserAccountAccessor]
    accessor.get(anyString).returns(IO.pure(Some(superSamAccount)))
    accessor.create(any[User]).returns(IO.pure(superSamAccount))
    accessor.getUserByKey(anyString).returns(IO.pure(Some(superSamAccount)))
    accessor
  }
}
