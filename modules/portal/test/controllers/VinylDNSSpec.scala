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
import controllers.VinylDNS.Alert
import org.junit.runner._
import org.specs2.mock.Mockito
import org.specs2.mutable._
import org.specs2.runner._
import org.specs2.specification.BeforeEach
import play.api.libs.json.{JsValue, Json, OWrites}
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.api.{Configuration, Environment, Mode}
import play.core.server.{Server, ServerConfig}
import vinyldns.core.crypto.{CryptoAlgebra, NoOpCrypto}
import vinyldns.core.domain.membership._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/* these verbs are renamed to avoid collisions with the verb identifiers in the standard values library file */
import play.api.routing.sird.{
  DELETE => backendDELETE,
  GET => backendGET,
  POST => backendPOST,
  PUT => backendPUT,
  _
}

import scala.util.{Failure, Success}

@RunWith(classOf[JUnitRunner])
class VinylDNSSpec extends Specification with Mockito with TestApplicationData with BeforeEach {

  val components: ControllerComponents = Helpers.stubControllerComponents()
  val defaultActionBuilder = DefaultActionBuilder(Helpers.stubBodyParser())
  val crypto: CryptoAlgebra = spy(new NoOpCrypto())
  val config: Configuration = Configuration.load(Environment.simple())
  val mockOidcAuth: OidcAuthenticator = mock[OidcAuthenticator]
  val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
  val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]

  protected def before: Any = org.mockito.Mockito.reset(crypto, authenticator, userAccessor)

  def TestVinylDNS(
      configuration: Configuration = config,
      authenticator: Authenticator = authenticator,
      userAccountAccessor: UserAccountAccessor = userAccessor,
      wsClient: WSClient = ws,
      components: ControllerComponents = components,
      crypto: CryptoAlgebra = crypto,
      oidcAuthenticator: OidcAuthenticator = mockOidcAuth): VinylDNS =
    new VinylDNS(
      configuration,
      authenticator,
      userAccountAccessor,
      wsClient,
      components,
      crypto,
      oidcAuthenticator)

  val vinyldnsPortal: VinylDNS = TestVinylDNS()

  "VinylDNS.Alerts" should {
    "return alertType and alertMessage are given" in {
      VinylDNS.Alerts.fromFlash(
        Flash(
          Map(
            "alertType" -> "danger",
            "alertMessage" -> "Authentication failed, please try again"))) must
        beEqualTo(Some(Alert("danger", "Authentication failed, please try again")))
    }

    "return None if no alertType and alertMessage are given" in {
      VinylDNS.Alerts.fromFlash(Flash(Map())) must beEqualTo(None)
    }
  }

  "VinylDNS" should {
    "send 404 on a bad request" in new WithApplication(app) {
      route(app, FakeRequest(GET, "/boum")) must beSome.which(status(_) == NOT_FOUND)
    }

    ".getUserData" should {
      "return the current logged in users information" in new WithApplication(app) {
        val vinyldnsPortal =
          TestVinylDNS(config, mockLdapAuthenticator, mockUserAccessor, ws, components, crypto)
        val result = vinyldnsPortal
          .getAuthenticatedUserData()
          .apply(
            FakeRequest(GET, "/api/users/currentuser").withSession(("username", "fbaggins"))
          )

        status(result) must beEqualTo(200)
        val userInfo: JsValue = contentAsJson(result)

        (userInfo \ "id").as[String] must beEqualTo(frodoUser.id)
        (userInfo \ "userName").as[String] must beEqualTo(frodoUser.userName)
        Some((userInfo \ "firstName").as[String]) must beEqualTo(frodoUser.firstName)
        Some((userInfo \ "lastName").as[String]) must beEqualTo(frodoUser.lastName)
        Some((userInfo \ "email").as[String]) must beEqualTo(frodoUser.email)
        (userInfo \ "isSuper").as[Boolean] must beFalse
      }
      "return Not found if the current logged in user was not found" in new WithApplication(app) {
        userAccessor.get(frodoUser.userName).returns(IO.pure(None))

        val vinyldnsPortal =
          TestVinylDNS(config, mockLdapAuthenticator, userAccessor, ws, components, crypto)
        val result = vinyldnsPortal
          .getAuthenticatedUserData()
          .apply(
            FakeRequest(GET, "/api/users/currentuser").withSession(("username", frodoUser.userName))
          )

        status(result) must beEqualTo(404)
      }
      "return Forbidden if the current user account is locked" in new WithApplication(app) {
        authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
        userAccessor.get(anyString).returns(IO.pure(Some(lockedFrodoUser)))

        val result = vinyldnsPortal
          .getAuthenticatedUserData()
          .apply(
            FakeRequest(GET, "/api/users/currentuser").withSession(("username", "frodo"))
          )

        status(result) must beEqualTo(403)
        contentAsString(result) must beEqualTo("User account for `frodo` is locked.")
      }
      "return unauthorized (401) if the current user account is locked" in new WithApplication(app) {
        authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
        userAccessor.get(anyString).returns(IO.pure(Some(lockedFrodoUser)))

        val result = vinyldnsPortal
          .getAuthenticatedUserData()
          .apply(FakeRequest(GET, "/api/users/currentuser"))

        status(result) must beEqualTo(401)
        contentAsString(result) must beEqualTo("You are not logged in. Please login to continue.")
      }
    }

    ".regenerateCreds" should {
      "change the access key and secret for the current user" in new WithApplication(app) {
        authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
        userAccessor.get("fbaggins").returns(IO.pure(Some(frodoUser)))
        userAccessor.update(any[User], any[User]).returns(IO.pure(frodoUser))

        val result = vinyldnsPortal
          .regenerateCreds()
          .apply(FakeRequest(POST, "/regenerate-creds")
            .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

        status(result) must beEqualTo(200)
        header("Pragma", result) must beSome("no-cache")
        header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
        header("Expires", result) must beSome("0")

        session(result).get("username") must beSome(frodoUser.userName)
        (session(result).get("accessKey") must not).beSome(frodoUser.accessKey)
      }

      "fail if user is not found" in new WithApplication(app) {
        userAccessor.get("fbaggins").returns(IO.pure(None))
        authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))

        val result = vinyldnsPortal
          .regenerateCreds()
          .apply(FakeRequest(POST, "/regenerate-creds")
            .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

        status(result) must beEqualTo(404)
        hasCacheHeaders(result)
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
        userAccessor.get(lockedFrodoUser.userName).returns(IO.pure(Some(lockedFrodoUser)))

        val result = vinyldnsPortal
          .regenerateCreds()
          .apply(
            FakeRequest(POST, "/regenerate-creds")
              .withSession(
                "username" -> lockedFrodoUser.userName,
                "accessKey" -> lockedFrodoUser.accessKey))

        status(result) must beEqualTo(403)
        contentAsString(result) must beEqualTo(
          s"User account for `${lockedFrodoUser.userName}` is locked.")
        hasCacheHeaders(result)
      }
      "return unauthorized (401) if user account is locked" in new WithApplication(app) {
        authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
        userAccessor.get(lockedFrodoUser.userName).returns(IO.pure(Some(lockedFrodoUser)))

        val result = vinyldnsPortal
          .regenerateCreds()
          .apply(FakeRequest(POST, "/regenerate-creds"))

        status(result) must beEqualTo(401)
        contentAsString(result) must beEqualTo("You are not logged in. Please login to continue.")
        hasCacheHeaders(result)
      }
    }

    ".login" should {
      "if login is correct with a valid key" should {
        "call the authenticator and the account accessor" in new WithApplication(app) {
          authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
          userAccessor.get(frodoDetails.username).returns(IO.pure(Some(frodoUser)))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "frodo", "password" -> "secondbreakfast")
            )
          there.was(one(authenticator).authenticate("frodo", "secondbreakfast"))
          there.was(one(userAccessor).get("frodo"))
        }
        "call the new user account accessor and return the new style account" in new WithApplication(
          app) {
          authenticator
            .authenticate(frodoDetails.username, "secondbreakfast")
            .returns(Success(frodoDetails))
          userAccessor.get(frodoDetails.username).returns(IO.pure(Some(frodoUser)))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest().withFormUrlEncodedBody(
                "username" -> frodoDetails.username,
                "password" -> "secondbreakfast")
            )
          there.was(one(authenticator).authenticate("frodo", "secondbreakfast"))
          there.was(one(userAccessor).get("frodo"))
        }
        "call the user accessor to create the new user account if it is not found" in new WithApplication(
          app) {
          authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
          userAccessor.get(anyString).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(frodoUser))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "frodo", "password" -> "secondbreakfast")
            )
          there.was(one(userAccessor).get("frodo"))
          there.was(one(userAccessor).create(_: User))
        }
        "call the user accessor to create the new style user account if it is not found" in new WithApplication(
          app) {
          authenticator
            .authenticate(frodoDetails.username, "secondbreakfast")
            .returns(Success(frodoDetails))
          userAccessor.get(frodoDetails.username).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(frodoUser))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest().withFormUrlEncodedBody(
                "username" -> frodoDetails.username,
                "password" -> "secondbreakfast")
            )
          there.was(one(userAccessor).get(frodoDetails.username))
          there.was(one(userAccessor).create(_: User))
        }

        "do not call the user accessor to create the new user account if it is found" in new WithApplication(
          app) {
          authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
          userAccessor.get(any[String]).returns(IO.pure(Some(frodoUser)))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "frodo", "password" -> "secondbreakfast")
            )
          there.was(one(userAccessor).get("frodo"))
          there.was(no(userAccessor).create(_: User))
        }

        "set the username, and key for the new style membership" in new WithApplication(app) {
          authenticator
            .authenticate(frodoDetails.username, "secondbreakfast")
            .returns(Success(frodoDetails))
          userAccessor.get(frodoDetails.username).returns(IO.pure(Some(frodoUser)))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest().withFormUrlEncodedBody(
                "username" -> frodoDetails.username,
                "password" -> "secondbreakfast")
            )
          session(response).get("username") must beSome(frodoUser.userName)
          session(response).get("accessKey") must beSome(frodoUser.accessKey)
          session(response).get("rootAccount") must beNone

        }
        "redirect to index using the new style accounts" in new WithApplication(app) {
          authenticator
            .authenticate(frodoDetails.username, "secondbreakfast")
            .returns(Success(frodoDetails))
          userAccessor.get(frodoDetails.username).returns(IO.pure(Some(frodoUser)))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest().withFormUrlEncodedBody(
                "username" -> frodoDetails.username,
                "password" -> "secondbreakfast")
            )
          status(response) mustEqual 303
          redirectLocation(response) must beSome("/index")
        }
      }

      "if login is correct but no account is found" should {
        "call the authenticator and the user account accessor" in new WithApplication(app) {
          authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
          userAccessor.get(anyString).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(frodoUser))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "frodo", "password" -> "secondbreakfast")
            )
          there.was(one(authenticator).authenticate("frodo", "secondbreakfast"))
          there.was(one(userAccessor).get("frodo"))
        }
        "set the username and the key" in new WithApplication(app) {
          authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
          userAccessor.get(anyString).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(frodoUser))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "frodo", "password" -> "secondbreakfast")
            )
          session(response).get("username") must beSome(frodoUser.userName)
          session(response).get("accessKey") must beSome(frodoUser.accessKey)
        }
        "redirect to index" in new WithApplication(app) {
          authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
          userAccessor.get(anyString).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(frodoUser))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "frodo", "password" -> "secondbreakfast")
            )
          status(response) mustEqual 303
          redirectLocation(response) must beSome("/index")
        }
      }

      "if service account login is correct but no account is found" should {
        "call the authenticator and the user account accessor" in new WithApplication(app) {
          authenticator.authenticate("service", "password").returns(Success(serviceAccountDetails))
          userAccessor.get(anyString).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(serviceAccount))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "service", "password" -> "password")
            )
          there.was(one(authenticator).authenticate("service", "password"))
          there.was(one(userAccessor).get("service"))
        }
        "set the username and the key" in new WithApplication(app) {
          authenticator.authenticate("service", "password").returns(Success(serviceAccountDetails))
          userAccessor.get(anyString).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(serviceAccount))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "service", "password" -> "password")
            )
          session(response).get("username") must beSome(serviceAccount.userName)
          session(response).get("accessKey") must beSome(serviceAccount.accessKey)
        }
        "redirect to index" in new WithApplication(app) {
          authenticator.authenticate("service", "password").returns(Success(serviceAccountDetails))
          userAccessor.get(anyString).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(serviceAccount))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "service", "password" -> "password")
            )
          status(response) mustEqual 303
          redirectLocation(response) must beSome("/index")
        }
      }

      "if login is not correct" should {
        "call the authenticator not the account accessor" in new WithApplication(app) {
          authenticator
            .authenticate("frodo", "secondbreakfast")
            .returns(Failure(new RuntimeException("login failed")))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "frodo", "password" -> "secondbreakfast")
            )
          there.was(one(authenticator).authenticate("frodo", "secondbreakfast"))
          there.was(no(userAccessor).get(anyString))
        }
        "do not set the username and key" in new WithApplication(app) {
          authenticator
            .authenticate("frodo", "secondbreakfast")
            .returns(Failure(new RuntimeException("login failed")))

          val vinyldnsPortal =
            TestVinylDNS(config, authenticator, mockUserAccessor, ws, components, crypto)
          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "frodo", "password" -> "secondbreakfast")
            )
          session(response).get("username") must beNone
          session(response).get("accessKey") must beNone
        }
        "redirect to login" in new WithApplication(app) {
          authenticator
            .authenticate("frodo", "secondbreakfast")
            .returns(Failure(new RuntimeException("login failed")))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "frodo", "password" -> "secondbreakfast")
            )
          status(response) mustEqual 303
          redirectLocation(response) must beSome("/login")
        }
        "set the flash with an error message" in new WithApplication(app) {
          authenticator
            .authenticate("frodo", "secondbreakfast")
            .returns(Failure(new RuntimeException("login failed")))

          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "frodo", "password" -> "secondbreakfast")
            )
          flash(response).get("alertType") must beSome("danger")
          flash(response).get("alertMessage") must beSome("Authentication failed, please try again")
        }
      }
    }

    ".oidcCallback" should {
      "redirect to set session view" in new WithApplication(app) {
        val response = vinyldnsPortal
          .oidcCallback("id")
          .apply(FakeRequest("GET", "id?query=q"))

        status(response) mustEqual 200
        contentAsString(response) must contain("/public/lib/oidc-finish.js")
      }
    }

    ".newGroup" should {
      "return the group description on create - status ok (200)" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPOST(p"/groups") =>
            defaultActionBuilder {
              Results.Ok(hobbitGroup)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.newGroup()(
              FakeRequest(POST, "/groups")
                .withJsonBody(hobbitGroupRequest)
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(OK)
            hasCacheHeaders(result)
            contentAsJson(result) must beEqualTo(hobbitGroup)
          }
        }
      }
      "return bad request (400) if the request is not properly made" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPOST(p"/groups") =>
            defaultActionBuilder {
              Results.BadRequest("user id not found")
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.newGroup()(
              FakeRequest(POST, "/groups")
                .withJsonBody(invalidHobbitGroup)
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(BAD_REQUEST)
            hasCacheHeaders(result)
          }
        }
      }
      "return authentication failed (401) when auth fails in the backend" in new WithApplication(
        app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPOST(p"/groups") =>
            defaultActionBuilder {
              Results.Unauthorized("Invalid credentials")
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.newGroup()(
              FakeRequest(POST, s"/groups")
                .withJsonBody(hobbitGroupRequest)
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(UNAUTHORIZED)
            hasCacheHeaders(result)
          }
        }
      }
      "return conflict (409) when the group exists already" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPOST(p"/groups") =>
            defaultActionBuilder {
              Results.Conflict("A group named 'hobbits' already exists")
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.newGroup()(
              FakeRequest(POST, "/groups")
                .withJsonBody(hobbitGroupRequest)
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(CONFLICT)
            hasCacheHeaders(result)
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPOST(p"/groups") =>
            defaultActionBuilder {
              Results.Ok(hobbitGroup)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockLockedUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.newGroup()(
              FakeRequest(POST, "/groups")
                .withJsonBody(hobbitGroupRequest)
                .withSession(
                  "username" -> lockedFrodoUser.userName,
                  "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
            hasCacheHeaders(result)
          }
        }
      }
      "return unauthorized (401) if user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPOST(p"/groups") =>
            defaultActionBuilder {
              Results.Ok(hobbitGroup)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockLockedUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.newGroup()(FakeRequest(POST, "/groups")
              .withJsonBody(hobbitGroupRequest))

            status(result) must beEqualTo(401)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
            hasCacheHeaders(result)
          }
        }
      }
    }

    ".getGroup" should {
      "return the group description if it is found - status ok (200)" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups/${hobbitGroupId}") =>
            defaultActionBuilder {
              Results.Ok(hobbitGroup)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.getGroup(hobbitGroupId)(FakeRequest(GET, s"/groups/$hobbitGroupId")
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(OK)
            hasCacheHeaders(result)
            contentAsJson(result) must beEqualTo(hobbitGroup)
          }
        }
      }
      "return authentication failed (401) when auth fails in the backend" in new WithApplication(
        app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups/$hobbitGroupId") =>
            defaultActionBuilder {
              Results.Unauthorized("Invalid credentials")
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.getGroup(hobbitGroupId)(FakeRequest(GET, s"/groups/$hobbitGroupId")
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(UNAUTHORIZED)
            hasCacheHeaders(result)
          }
        }
      }
      "return a not found (404) if the group does not exist" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups/not-hobbits") =>
            defaultActionBuilder {
              Results.NotFound
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.getGroup("not-hobbits")(FakeRequest(GET, "/groups/not-hobbits")
              .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(NOT_FOUND)
            hasCacheHeaders(result)
          }
        }
      }
      "return status forbidden (403) if the user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups/${hobbitGroupId}") =>
            defaultActionBuilder {
              Results.Ok(hobbitGroup)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockLockedUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.getGroup(hobbitGroupId)(
                FakeRequest(GET, s"/groups/$hobbitGroupId")
                  .withSession(
                    "username" -> lockedFrodoUser.userName,
                    "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
      "return unauthorized (401) if user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups/${hobbitGroupId}") =>
            defaultActionBuilder {
              Results.Ok(hobbitGroup)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.getGroup(hobbitGroupId)(FakeRequest(GET, s"/groups/$hobbitGroupId"))

            status(result) must beEqualTo(401)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
            hasCacheHeaders(result)
          }
        }
      }
    }

    ".deleteGroup" should {
      "return ok with no content (204) when delete is successful" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendDELETE(p"/groups/$hobbitGroupId") =>
            defaultActionBuilder {
              Results.NoContent
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.deleteGroup(hobbitGroupId)(FakeRequest(DELETE, s"/groups/$hobbitGroupId")
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(NO_CONTENT)
            hasCacheHeaders(result)
          }
        }
      }
      "return unauthorized (401) when user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendDELETE(p"/groups/$hobbitGroupId") =>
            defaultActionBuilder {
              Results.NoContent
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.deleteGroup(hobbitGroupId)(FakeRequest(DELETE, s"/groups/$hobbitGroupId"))

            status(result) mustEqual 401
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
            hasCacheHeaders(result)
          }
        }
      }
      "return forbidden (403) when user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendDELETE(p"/groups/$hobbitGroupId") =>
            defaultActionBuilder {
              Results.NoContent
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockLockedUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.deleteGroup(hobbitGroupId)(
                FakeRequest(DELETE, s"/groups/$hobbitGroupId")
                  .withSession(
                    "username" -> lockedFrodoUser.userName,
                    "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
      "return authentication failed (401) when authentication fails in the backend" in new WithApplication(
        app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendDELETE(p"/groups/$hobbitGroupId") =>
            defaultActionBuilder {
              Results.Unauthorized("Invalid credentials")
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.deleteGroup(hobbitGroupId)(FakeRequest(DELETE, s"/groups/$hobbitGroupId")
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(UNAUTHORIZED)
            hasCacheHeaders(result)
          }
        }
      }
      "return forbidden (403) when authorization fails in the backend" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendDELETE(p"/groups/$hobbitGroupId") =>
            defaultActionBuilder {
              Results.Forbidden("You do not have access to delete this group")
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))

            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.deleteGroup(hobbitGroupId)(FakeRequest(DELETE, s"/groups/$hobbitGroupId")
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(FORBIDDEN)
            hasCacheHeaders(result)
          }
        }
      }
      "return a not found (404) if the group does not exist" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendDELETE(p"/groups/not-hobbits") =>
            defaultActionBuilder {
              Results.NotFound
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.deleteGroup("not-hobbits")(FakeRequest(DELETE, "/groups/not-hobbits")
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(NOT_FOUND)
            hasCacheHeaders(result)
          }
        }
      }
    }

    ".updateGroup" should {
      "return the new group description if it is saved successfully - Ok (200)" in new WithApplication(
        app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/groups/$hobbitGroupId") =>
            defaultActionBuilder {
              Results.Ok(hobbitGroup)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.updateGroup(hobbitGroupId)(
              FakeRequest(PUT, s"/groups/$hobbitGroupId")
                .withJsonBody(hobbitGroup)
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(OK)
            contentAsJson(result) must beEqualTo(hobbitGroup)
            hasCacheHeaders(result)
          }
        }
      }
      "return unauthorized (401) if the user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/groups/$hobbitGroupId") =>
            defaultActionBuilder {
              Results.Ok(hobbitGroup)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.updateGroup(hobbitGroupId)(FakeRequest(PUT, s"/groups/$hobbitGroupId")
                .withJsonBody(hobbitGroup))

            status(result) mustEqual 401
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
            hasCacheHeaders(result)
          }
        }
      }
      "return forbidden (403) if the user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/groups/$hobbitGroupId") =>
            defaultActionBuilder {
              Results.Ok(hobbitGroup)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockLockedUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.updateGroup(hobbitGroupId)(
              FakeRequest(PUT, s"/groups/$hobbitGroupId")
                .withJsonBody(hobbitGroup)
                .withSession(
                  "username" -> lockedFrodoUser.userName,
                  "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
            hasCacheHeaders(result)
          }
        }
      }
      "return bad request (400) when the request is rejected by the backend" in new WithApplication(
        app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/groups/$hobbitGroupId") =>
            defaultActionBuilder {
              Results.BadRequest("Unknown user")
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.updateGroup(hobbitGroupId)(
              FakeRequest(PUT, s"/groups/$hobbitGroupId")
                .withJsonBody(invalidHobbitGroup)
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(BAD_REQUEST)
            hasCacheHeaders(result)
          }
        }
      }
      "return unauthorized (401) when request fails authentication" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/groups/$hobbitGroupId") =>
            defaultActionBuilder {
              Results.Unauthorized("Authentication failed, bad signature")
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.updateGroup(hobbitGroupId)(
              FakeRequest(PUT, s"/groups/$hobbitGroupId")
                .withJsonBody(hobbitGroup)
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(UNAUTHORIZED)
            hasCacheHeaders(result)
          }
        }
      }
      "return forbidden (403) when request fails permissions in the backend" in new WithApplication(
        app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/groups/${hobbitGroupId}") =>
            defaultActionBuilder {
              Results.Forbidden("Authentication failed, bad signature")
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.updateGroup(hobbitGroupId)(
              FakeRequest(PUT, s"/groups/$hobbitGroupId")
                .withJsonBody(hobbitGroup)
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(FORBIDDEN)
            hasCacheHeaders(result)
          }
        }
      }
      "return not found (404) when the group is not found in the backend" in new WithApplication(
        app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/groups/not-hobbits") =>
            defaultActionBuilder {
              Results.NotFound
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.updateGroup("not-hobbits")(
              FakeRequest(PUT, "/groups/not-hobbits")
                .withJsonBody(hobbitGroup)
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(NOT_FOUND)
            hasCacheHeaders(result)
          }
        }
      }
    }

    ".getMemberList" should {
      "return a list of members of the group when requested - Ok (200)" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups/$hobbitGroupId/members") =>
            defaultActionBuilder {
              Results.Ok(hobbitGroupMembers)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.getMemberList(hobbitGroupId)(
              FakeRequest(GET, s"/data/groups/$hobbitGroupId/members")
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(OK)
            hasCacheHeaders(result)
            contentAsJson(result) must beEqualTo(hobbitGroupMembers)
          }
        }
      }
      "return unauthorized (401) if the user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups/$hobbitGroupId/members") =>
            defaultActionBuilder {
              Results.Ok(hobbitGroupMembers)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockLockedUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.getMemberList(hobbitGroupId)(
              FakeRequest(GET, s"/data/groups/$hobbitGroupId/members"))

            status(result) mustEqual 401
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
            hasCacheHeaders(result)
          }
        }
      }
      "return forbidden (403) if the user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups/$hobbitGroupId/members") =>
            defaultActionBuilder {
              Results.Ok(hobbitGroupMembers)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockLockedUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.getMemberList(hobbitGroupId)(
              FakeRequest(GET, s"/data/groups/$hobbitGroupId/members")
                .withSession(
                  "username" -> lockedFrodoUser.userName,
                  "accessKey" -> lockedFrodoUser.accessKey))

            status(result) must beEqualTo(FORBIDDEN)
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
      "return bad request (400) when the request is rejected by the back end" in new WithApplication(
        app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups/$hobbitGroupId/members") =>
            defaultActionBuilder {
              Results.BadRequest("Invalid maxItems")
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.getMemberList(hobbitGroupId)(
              FakeRequest(GET, s"/groups/$hobbitGroupId/members?maxItems=0")
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(BAD_REQUEST)
            hasCacheHeaders(result)
          }
        }
      }
      "return unauthorized (401) when request fails authentication" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups/$hobbitGroupId/members") =>
            defaultActionBuilder {
              Results.Unauthorized("The supplied authentication is invalid")
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.getMemberList(hobbitGroupId)(
              FakeRequest(GET, s"/groups/$hobbitGroupId/members")
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(UNAUTHORIZED)
            hasCacheHeaders(result)
          }
        }
      }
      "return not found (404) when the group is not found in the backend" in new WithApplication(
        app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups/$hobbitGroupId/members") =>
            defaultActionBuilder {
              Results.NotFound
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.getMemberList(hobbitGroupId)(
              FakeRequest(GET, s"/groups/$hobbitGroupId/members")
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(NOT_FOUND)
            hasCacheHeaders(result)
          }
        }
      }
    }

    ".myGroups" should {
      "return the list of groups when requested - Ok(200)" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups") =>
            defaultActionBuilder {
              Results.Ok(frodoGroupList)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.getMyGroups()(FakeRequest(GET, s"/api/groups")
              .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(OK)
            hasCacheHeaders(result)
            contentAsJson(result) must beEqualTo(frodoGroupList)
          }
        }
      }
      "return unauthorized (401) when user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups") =>
            defaultActionBuilder {
              Results.Ok(frodoGroupList)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.getMyGroups()(FakeRequest(GET, s"/api/groups"))

            status(result) mustEqual 401
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
            hasCacheHeaders(result)
          }
        }
      }
      "return forbidden (403) when user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups") =>
            defaultActionBuilder {
              Results.Ok(frodoGroupList)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockLockedUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.getMyGroups()(
              FakeRequest(GET, s"/api/groups")
                .withSession(
                  "username" -> lockedFrodoUser.userName,
                  "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
      "return unauthorized (401) when request fails authentication" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/groups") =>
            defaultActionBuilder {
              Results.Unauthorized("The supplied authentication is invalid")
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.getMyGroups()(FakeRequest(GET, s"/api/groups")
              .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(UNAUTHORIZED)
            hasCacheHeaders(result)
          }
        }
      }
    }

    ".serveCredFile" should {
      "return a csv file with the new style credentials" in new WithApplication(app) {
        import play.api.mvc.Result
        userAccessor.get(frodoUser.userName).returns(IO.pure(Some(frodoUser)))
        val underTest =
          TestVinylDNS(config, mockLdapAuthenticator, userAccessor, ws, components, crypto)

        val result: Future[Result] = underTest.serveCredsFile("credsfile.csv")(
          FakeRequest(GET, s"/download-creds-file/credsfile.csv")
            .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))
        val content: String = contentAsString(result)
        content must contain("NT ID")
        content must contain(frodoUser.userName)
        content must contain(frodoUser.accessKey)
        content must contain(frodoUser.secretKey)
        there.was(one(crypto).decrypt(frodoUser.secretKey))
      }
      "redirect to login if user is not logged in" in new WithApplication(app) {
        import play.api.mvc.Result

        val underTest =
          TestVinylDNS(config, mockLdapAuthenticator, mockUserAccessor, ws, components, crypto)

        val result: Future[Result] = underTest.serveCredsFile("credsfile.csv")(
          FakeRequest(GET, s"/download-creds-file/credsfile.csv"))

        status(result) mustEqual 303
        redirectLocation(result) must beSome("/login")
        flash(result).get("alertType") must beSome("danger")
        flash(result).get("alertMessage") must beSome(
          "You are not logged in. Please login to continue.")
      }
      "redirect to login if user account is locked" in new WithApplication(app) {
        import play.api.mvc.Result

        val underTest =
          TestVinylDNS(
            config,
            mockLdapAuthenticator,
            mockLockedUserAccessor,
            ws,
            components,
            crypto)

        val result: Future[Result] = underTest.serveCredsFile("credsfile.csv")(
          FakeRequest(GET, s"/download-creds-file/credsfile.csv")
            .withSession(
              "username" -> lockedFrodoUser.userName,
              "accessKey" -> lockedFrodoUser.accessKey))

        status(result) mustEqual 303
        redirectLocation(result) must beSome("/noaccess")
      }
      "redirect to login if user account is not found" in new WithApplication(app) {
        import play.api.mvc.Result
        userAccessor.get(frodoUser.userName).returns(IO.pure(None))
        val underTest =
          TestVinylDNS(config, mockLdapAuthenticator, userAccessor, ws, components, crypto)

        val result: Future[Result] = underTest.serveCredsFile("credsfile.csv")(
          FakeRequest(GET, s"/download-creds-file/credsfile.csv")
            .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

        status(result) mustEqual 303
        redirectLocation(result) must beSome("/login")
        flash(result).get("alertType") must beSome("danger")
        flash(result).get("alertMessage") must beSome(
          s"Unable to find user account for user name '${frodoUser.userName}'")
      }
    }

    ".lookupUserAccount" should {
      implicit val userInfoWrites: OWrites[VinylDNS.UserInfo] = Json.writes[VinylDNS.UserInfo]

      "return a list of users from a list of usernames" in new WithApplication(app) {
        val lookupValue = "someNTID"
        authenticator.lookup(lookupValue).returns(Success(frodoDetails))
        userAccessor.get(frodoDetails.username).returns(IO.pure(Some(frodoUser)))

        val expected = Json.toJson(VinylDNS.UserInfo.fromUser(frodoUser))

        val result = vinyldnsPortal
          .getUserDataByUsername(lookupValue)
          .apply(
            FakeRequest(GET, s"/api/users/lookupuser/$lookupValue")
              .withSession("username" -> "frodo")
          )
        status(result) must beEqualTo(200)
        hasCacheHeaders(result)
        contentAsJson(result) must beEqualTo(expected)
      }
      "return unauthorized (401) if user is not logged in" in new WithApplication(app) {
        val lookupValue = "someNTID"
        authenticator.lookup(lookupValue).returns(Success(frodoDetails))

        val vinyldnsPortal =
          TestVinylDNS(config, authenticator, mockLockedUserAccessor, ws, components, crypto)
        val result = vinyldnsPortal
          .getUserDataByUsername(lookupValue)
          .apply(FakeRequest(GET, s"/api/users/lookupuser/$lookupValue"))

        status(result) mustEqual 401
        contentAsString(result) must beEqualTo("You are not logged in. Please login to continue.")
        hasCacheHeaders(result)
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {

        val lookupValue = "someNTID"
        authenticator.lookup(lookupValue).returns(Success(frodoDetails))

        val vinyldnsPortal =
          TestVinylDNS(config, authenticator, mockLockedUserAccessor, ws, components, crypto)
        val result = vinyldnsPortal
          .getUserDataByUsername(lookupValue)
          .apply(
            FakeRequest(GET, s"/api/users/lookupuser/$lookupValue")
              .withSession(
                "username" -> lockedFrodoUser.userName,
                "accessKey" -> lockedFrodoUser.accessKey))

        status(result) must beEqualTo(403)
        hasCacheHeaders(result)
        contentAsString(result) must beEqualTo(
          s"User account for `${lockedFrodoUser.userName}` is locked.")
      }
      "return a 404 if the account is not found" in new WithApplication(app) {
        userAccessor.get(any[String]).returns(IO.pure(None))
        authenticator
          .lookup(frodoUser.userName)
          .returns(Failure(new UserDoesNotExistException("not found")))

        val result = vinyldnsPortal
          .getUserDataByUsername(frodoUser.userName)
          .apply(
            FakeRequest(GET, s"/api/users/lookupuser/${frodoUser.userName}")
              .withSession("username" -> "frodo")
          )

        status(result) must beEqualTo(404)
        hasCacheHeaders(result)
      }
    }

    ".getZones" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.getZones()(FakeRequest(GET, s"/api/zones"))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.getZones()(
              FakeRequest(GET, s"/api/zones").withSession(
                "username" -> lockedFrodoUser.userName,
                "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".getZone" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/$hobbitZoneId") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.getZone(hobbitZoneId)(FakeRequest(GET, s"/api/zones/$hobbitZoneId"))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/$hobbitZoneId") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.getZone(hobbitZoneId)(
              FakeRequest(GET, s"/api/zones/$hobbitZoneId").withSession(
                "username" -> lockedFrodoUser.userName,
                "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".getZoneByName" should {
      "return ok (200) if the zone is found" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/name/$hobbitZoneName") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.getZoneByName(hobbitZoneName)(
                FakeRequest(GET, s"/zones/name/$hobbitZoneName")
                  .withSession(
                    "username" -> frodoUser.userName,
                    "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(OK)
            hasCacheHeaders(result)
            contentAsJson(result) must beEqualTo(hobbitZone)
          }
        }
      }
      "return a not found (404) if the zone does not exist" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/name/not-hobbits") =>
            defaultActionBuilder {
              Results.NotFound
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val mockUserAccessor = mock[UserAccountAccessor]
            mockUserAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
            mockUserAccessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.getZoneByName("not-hobbits")(FakeRequest(GET, "/zones/name/not-hobbits")
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(NOT_FOUND)
            hasCacheHeaders(result)
          }
        }
      }
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/name/$hobbitZoneName") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.getZoneByName(hobbitZoneName)(
                FakeRequest(GET, s"/api/zones/name/$hobbitZoneName"))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/name/$hobbitZoneName") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.getZoneByName(hobbitZoneName)(
              FakeRequest(GET, s"/api/zones/name/$hobbitZoneName").withSession(
                "username" -> lockedFrodoUser.userName,
                "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".syncZone" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPOST(p"/zones/$hobbitZoneId/sync") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.syncZone(hobbitZoneId)(FakeRequest(POST, s"/api/zones/$hobbitZoneId/sync"))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/$hobbitZoneId/sync") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.syncZone(hobbitZoneId)(
              FakeRequest(POST, s"/api/zones/$hobbitZoneId/sync").withSession(
                "username" -> lockedFrodoUser.userName,
                "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".getRecordSets" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/$hobbitZoneId/recordsets") =>
            defaultActionBuilder {
              Results.Ok(hobbitRecordSet)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.getRecordSets(hobbitZoneId)(
                FakeRequest(GET, s"/api/zones/$hobbitZoneId/recordsets"))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/$hobbitZoneId/recordsets") =>
            defaultActionBuilder {
              Results.Ok(hobbitRecordSet)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.getRecordSets(hobbitZoneId)(
              FakeRequest(GET, s"/api/zones/$hobbitZoneId/recordsets").withSession(
                "username" -> lockedFrodoUser.userName,
                "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".listRecordSetChanges" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/$hobbitZoneId/recordsetchanges") =>
            defaultActionBuilder {
              Results.Ok(hobbitRecordSet)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.listRecordSetChanges(hobbitZoneId)(
                FakeRequest(GET, s"/api/zones/$hobbitZoneId/recordsets"))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/$hobbitZoneId/recordsetchanges") =>
            defaultActionBuilder {
              Results.Ok(hobbitRecordSet)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.listRecordSetChanges(hobbitZoneId)(
              FakeRequest(GET, s"/api/zones/$hobbitZoneId/recordsets").withSession(
                "username" -> lockedFrodoUser.userName,
                "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".addZone" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPOST(p"/zones") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.addZone()(FakeRequest(POST, s"/api/zones").withJsonBody(hobbitZoneRequest))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPOST(p"/zones") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.addZone()(
              FakeRequest(POST, s"/api/zones")
                .withJsonBody(hobbitZoneRequest)
                .withSession(
                  "username" -> lockedFrodoUser.userName,
                  "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".updateZone" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/zones/$hobbitZoneId") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.updateZone(hobbitZoneId)(
                FakeRequest(PUT, s"/api/zones/$hobbitZoneId").withJsonBody(hobbitZoneRequest))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/zones/$hobbitZoneId") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.updateZone(hobbitZoneId)(
              FakeRequest(PUT, s"/api/zones/$hobbitZoneId")
                .withJsonBody(hobbitZoneRequest)
                .withSession(
                  "username" -> lockedFrodoUser.userName,
                  "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".addRecordSet" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPOST(p"/zones/$hobbitZoneId/recordsets") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.addRecordSet(hobbitRecordSetId)(
                FakeRequest(POST, s"/api/zones/$hobbitZoneId/recordsets")
                  .withJsonBody(hobbitZoneRequest))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/zones/$hobbitZoneId") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.addRecordSet(hobbitRecordSetId)(
              FakeRequest(POST, s"/api/zones/$hobbitZoneId/recordsets")
                .withJsonBody(hobbitZoneRequest)
                .withSession(
                  "username" -> lockedFrodoUser.userName,
                  "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".deleteZone" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendDELETE(p"/zones/$hobbitZoneId") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.deleteZone(hobbitZoneId)(
                FakeRequest(DELETE, s"/api/zones/$hobbitZoneId").withJsonBody(hobbitZoneRequest))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendDELETE(p"/zones/$hobbitZoneId") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.deleteZone(hobbitZoneId)(
              FakeRequest(DELETE, s"/api/zones/$hobbitZoneId")
                .withJsonBody(hobbitZoneRequest)
                .withSession(
                  "username" -> lockedFrodoUser.userName,
                  "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".updateRecordSet" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/zones/$hobbitZoneId/recordsets/$hobbitRecordSetId") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.updateRecordSet(hobbitZoneId, hobbitRecordSetId)(
                FakeRequest(PUT, s"/api/zones/$hobbitZoneId/recordsets/$hobbitRecordSetId")
                  .withJsonBody(hobbitZoneRequest))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/zones/$hobbitZoneId/recordsets/$hobbitRecordSetId") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.updateRecordSet(hobbitZoneId, hobbitRecordSetId)(
              FakeRequest(PUT, s"/api/zones/$hobbitZoneId/recordsets/$hobbitRecordSetId")
                .withJsonBody(hobbitZoneRequest)
                .withSession(
                  "username" -> lockedFrodoUser.userName,
                  "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".deleteRecordSet" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendDELETE(p"/zones/$hobbitZoneId/recordsets/$hobbitRecordSetId") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.deleteRecordSet(hobbitZoneId, hobbitRecordSetId)(
                FakeRequest(DELETE, s"/api/zones/$hobbitZoneId").withJsonBody(hobbitZoneRequest))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendDELETE(p"/zones/$hobbitZoneId/recordsets/$hobbitRecordSetId") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.deleteRecordSet(hobbitZoneId, hobbitRecordSetId)(
              FakeRequest(DELETE, s"/api/zones/$hobbitZoneId/recordsets/$hobbitRecordSetId")
                .withJsonBody(hobbitZoneRequest)
                .withSession(
                  "username" -> lockedFrodoUser.userName,
                  "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".getBatchChange" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/batchrecordchanges/$hobbitZoneId") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.getBatchChange(hobbitZoneId)(
                FakeRequest(GET, s"/api/batchchanges/$hobbitZoneId")
                  .withJsonBody(hobbitZoneRequest))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/batchrecordchanges/$hobbitZoneId") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.getBatchChange(hobbitZoneId)(
              FakeRequest(GET, s"/api/batchchanges/$hobbitZoneId")
                .withJsonBody(hobbitZoneRequest)
                .withSession(
                  "username" -> lockedFrodoUser.userName,
                  "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".newBatchChange" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPOST(p"/batchrecordchanges") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.newBatchChange()(
                FakeRequest(POST, s"/api/batchchanges").withJsonBody(hobbitZoneRequest))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPOST(p"/batchrecordchanges") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.newBatchChange()(
              FakeRequest(POST, s"/api/batchchanges")
                .withJsonBody(hobbitZoneRequest)
                .withSession(
                  "username" -> lockedFrodoUser.userName,
                  "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".listBatchChanges" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/batchrecordchanges") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.listBatchChanges()(
                FakeRequest(GET, s"/api/batchchanges").withJsonBody(hobbitZoneRequest))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/batchrecordchanges") =>
            defaultActionBuilder {
              Results.Ok(hobbitZone)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.listBatchChanges()(
              FakeRequest(GET, s"/api/batchchanges")
                .withJsonBody(hobbitZoneRequest)
                .withSession(
                  "username" -> lockedFrodoUser.userName,
                  "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }

    ".lockUser" should {
      "return successful if requesting user is a super user" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/users/${frodoUser.id}/lock") =>
            defaultActionBuilder {
              Results.Ok(userJson)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockMultiUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.lockUser(frodoUser.id)(
              FakeRequest(PUT, s"/users/${frodoUser.id}/lock")
                .withSession(
                  "username" -> superFrodoUser.userName,
                  "accessKey" -> superFrodoUser.accessKey))

            status(result) must beEqualTo(OK)
            contentAsJson(result) must beEqualTo(userJson)
            hasCacheHeaders(result)
          }
        }
      }
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/users/${frodoUser.id}/lock") =>
            defaultActionBuilder {
              Results.Ok(userJson)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockMultiUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.lockUser(frodoUser.id)(FakeRequest(PUT, s"/users/${frodoUser.id}/lock"))

            status(result) mustEqual 401
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
            hasCacheHeaders(result)
          }
        }
      }
      "return Forbidden if requesting user is not a super user" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/users/${frodoUser.id}/lock") =>
            defaultActionBuilder {
              Results.Ok(userJson)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.lockUser(frodoUser.id)(FakeRequest(PUT, s"/users/${frodoUser.id}/lock")
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) must beEqualTo(403)
            contentAsString(result) must beEqualTo("Request restricted to super users only.")
            hasCacheHeaders(result)
          }
        }
      }
    }

    ".unlockUser" should {
      "return successful if requesting user is a super user" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/users/${lockedFrodoUser.id}/unlock") =>
            defaultActionBuilder {
              Results.Ok(userJson)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockMultiUserAccessor,
                client,
                components,
                crypto)
            val result = underTest.unlockUser(lockedFrodoUser.id)(
              FakeRequest(PUT, s"/users/${lockedFrodoUser.id}/unlock")
                .withSession(
                  "username" -> superFrodoUser.userName,
                  "accessKey" -> superFrodoUser.accessKey))

            status(result) must beEqualTo(OK)
            contentAsJson(result) must beEqualTo(userJson)
            hasCacheHeaders(result)
          }
        }
      }
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/users/${frodoUser.id}/unlock") =>
            defaultActionBuilder {
              Results.Ok(userJson)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.lockUser(frodoUser.id)(FakeRequest(PUT, s"/users/${frodoUser.id}/unlock"))

            status(result) mustEqual 401
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
            hasCacheHeaders(result)
          }
        }
      }
      "return forbidden (403) if requesting user is not a super user" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendPUT(p"/users/${frodoUser.id}/unlock") =>
            defaultActionBuilder {
              Results.Ok(userJson)
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest =
              TestVinylDNS(
                testConfigLdap,
                mockLdapAuthenticator,
                mockUserAccessor,
                client,
                components,
                crypto)
            val result =
              underTest.unlockUser(frodoUser.id)(FakeRequest(PUT, s"/users/${frodoUser.id}/unlock")
                .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

            status(result) mustEqual 403
            contentAsString(result) must beEqualTo("Request restricted to super users only.")
            hasCacheHeaders(result)
          }
        }
      }
    }

    "getBackendIds" should {
      "return unauthorized (401) if requesting user is not logged in" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/backendids") =>
            defaultActionBuilder {
              Results.Ok(Json.parse("['backend-1', 'backend-2']"))
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockUserAccessor,
              client,
              components,
              crypto)
            val result =
              underTest.getBackendIds()(FakeRequest(GET, "/zones/backendids"))

            status(result) mustEqual 401
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              "You are not logged in. Please login to continue.")
          }
        }
      }
      "return forbidden (403) if user account is locked" in new WithApplication(app) {
        Server.withRouter(ServerConfig(port = Some(simulatedBackendPort), mode = Mode.Test)) {
          case backendGET(p"/zones/backendids") =>
            defaultActionBuilder {
              Results.Ok(Json.parse("['backend-1', 'backend-2']"))
            }
        } { implicit port =>
          WsTestClient.withClient { client =>
            val underTest = TestVinylDNS(
              testConfigLdap,
              mockLdapAuthenticator,
              mockLockedUserAccessor,
              client,
              components,
              crypto)
            val result = underTest.getBackendIds()(
              FakeRequest(GET, "/zones/backendids")
                .withSession(
                  "username" -> lockedFrodoUser.userName,
                  "accessKey" -> lockedFrodoUser.accessKey))

            status(result) mustEqual 403
            hasCacheHeaders(result)
            contentAsString(result) must beEqualTo(
              s"User account for `${lockedFrodoUser.userName}` is locked.")
          }
        }
      }
    }
  }

  def buildmockUserAccessor: UserAccountAccessor = {
    val accessor = mock[UserAccountAccessor]
    accessor.get(frodoUser.userName).returns(IO.pure(Some(frodoUser)))
    accessor.getUserByKey(frodoUser.accessKey).returns(IO.pure(Some(frodoUser)))
    accessor
  }

  def buildMockMultiUserAccountAccessor: UserAccountAccessor = {
    val accessor = mock[UserAccountAccessor]
    accessor.get(frodoUser.userName).returns(IO.pure(Some(frodoUser)))
    accessor.getUserByKey(frodoUser.accessKey).returns(IO.pure(Some(frodoUser)))
    accessor.get(superFrodoUser.userName).returns(IO.pure(Some(superFrodoUser)))
    accessor.get(lockedFrodoUser.userName).returns(IO.pure(Some(lockedFrodoUser)))
    accessor.create(any[User]).returns(IO.pure(frodoUser))
    accessor
  }

  def buildmockLockedUserAccessor: UserAccountAccessor = {
    val accessor = mock[UserAccountAccessor]
    accessor.get(lockedFrodoUser.userName).returns(IO.pure(Some(lockedFrodoUser)))
    accessor
  }

  private def hasCacheHeaders(result: Future[play.api.mvc.Result]) = {
    header("Pragma", result) must beSome("no-cache")
    header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
    header("Expires", result) must beSome("0")
  }

  val mockUserAccessor: UserAccountAccessor = buildmockUserAccessor
  val mockMultiUserAccessor: UserAccountAccessor = buildMockMultiUserAccountAccessor
  val mockLockedUserAccessor: UserAccountAccessor = buildmockLockedUserAccessor
  val mockLdapAuthenticator: LdapAuthenticator = mock[LdapAuthenticator]
  val ws: WSClient = mock[WSClient]
}
