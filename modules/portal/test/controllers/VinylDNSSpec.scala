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

  protected def before: Any = org.mockito.Mockito.reset(crypto)

  "VinylDNS" should {
    "send 404 on a bad request" in new WithApplication(app) {
      route(app, FakeRequest(GET, "/boum")) must beSome.which(status(_) == NOT_FOUND)
    }

    ".getUserData" should {
      "return the current logged in users information" in new WithApplication(app) {
        val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
        val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
        val config: Configuration = Configuration.load(Environment.simple())
        val ws: WSClient = mock[WSClient]

        authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
        userAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
        userAccessor.get("frodo").returns(IO.pure(Some(frodoUser)))

        val vinyldnsPortal =
          new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
        val result = vinyldnsPortal
          .getAuthenticatedUserData()
          .apply(
            FakeRequest(GET, "/api/users/currentuser").withSession(("username", "frodo"))
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
        val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
        val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
        val config: Configuration = Configuration.load(Environment.simple())
        val ws: WSClient = mock[WSClient]

        authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
        userAccessor.get(anyString).returns(IO.pure(Some(frodoUser)))
        userAccessor.get("frodo").returns(IO.pure(None))

        val vinyldnsPortal =
          new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
        val result = vinyldnsPortal
          .getAuthenticatedUserData()
          .apply(
            FakeRequest(GET, "/api/users/currentuser").withSession(("username", "frodo"))
          )

        status(result) must beEqualTo(404)
      }
    }

    ".regenerateCreds" should {
      "change the access key and secret for the current user" in new WithApplication(app) {
        val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
        val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
        val config: Configuration = Configuration.load(Environment.simple())
        val ws: WSClient = mock[WSClient]

        authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
        userAccessor.get("fbaggins").returns(IO.pure(Some(frodoUser)))
        userAccessor.update(any[User], any[User]).returns(IO.pure(frodoUser))

        val vinyldnsPortal =
          new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
        val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
        val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
        val config: Configuration = Configuration.load(Environment.simple())
        val ws: WSClient = mock[WSClient]

        userAccessor.get("fbaggins").returns(IO.pure(None))

        authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))

        val vinyldnsPortal =
          new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
        val result = vinyldnsPortal
          .regenerateCreds()
          .apply(FakeRequest(POST, "/regenerate-creds")
            .withSession("username" -> frodoUser.userName, "accessKey" -> frodoUser.accessKey))

        status(result) must beEqualTo(404)
        hasCacheHeaders(result)
      }
    }

    ".login" should {
      "if login is correct with a valid key" should {
        "call the authenticator and the account accessor" in new WithApplication(app) {
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]

          authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
          userAccessor.get(frodoDetails.username).returns(IO.pure(Some(frodoUser)))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator
            .authenticate(frodoDetails.username, "secondbreakfast")
            .returns(Success(frodoDetails))
          userAccessor.get(frodoDetails.username).returns(IO.pure(Some(frodoUser)))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
          userAccessor.get(anyString).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(frodoUser))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]

          authenticator
            .authenticate(frodoDetails.username, "secondbreakfast")
            .returns(Success(frodoDetails))
          userAccessor.get(frodoDetails.username).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(frodoUser))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = buildMockUserAccountAccessor
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
          userAccessor.get(any[String]).returns(IO.pure(Some(frodoUser)))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator
            .authenticate(frodoDetails.username, "secondbreakfast")
            .returns(Success(frodoDetails))
          userAccessor.get(frodoDetails.username).returns(IO.pure(Some(frodoUser)))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator
            .authenticate(frodoDetails.username, "secondbreakfast")
            .returns(Success(frodoDetails))
          userAccessor.get(frodoDetails.username).returns(IO.pure(Some(frodoUser)))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
          userAccessor.get(anyString).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(frodoUser))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
          userAccessor.get(anyString).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(frodoUser))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
          userAccessor.get(anyString).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(frodoUser))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator.authenticate("service", "password").returns(Success(serviceAccountDetails))
          userAccessor.get(anyString).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(serviceAccount))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator.authenticate("service", "password").returns(Success(serviceAccountDetails))
          userAccessor.get(anyString).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(serviceAccount))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator.authenticate("service", "password").returns(Success(serviceAccountDetails))
          userAccessor.get(anyString).returns(IO.pure(None))
          userAccessor.create(any[User]).returns(IO.pure(serviceAccount))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator
            .authenticate("frodo", "secondbreakfast")
            .returns(Failure(new RuntimeException("login failed")))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator
            .authenticate("frodo", "secondbreakfast")
            .returns(Failure(new RuntimeException("login failed")))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, mockUserAccountAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator
            .authenticate("frodo", "secondbreakfast")
            .returns(Failure(new RuntimeException("login failed")))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator
            .authenticate("frodo", "secondbreakfast")
            .returns(Failure(new RuntimeException("login failed")))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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
              new VinylDNS(
                testConfig,
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

        val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
        val config: Configuration = Configuration.load(Environment.simple())
        val ws: WSClient = mock[WSClient]
        userAccessor.get(frodoUser.userName).returns(IO.pure(Some(frodoUser)))
        val underTest =
          new VinylDNS(config, mockLdapAuthenticator, userAccessor, ws, components, crypto)

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
    }

    ".lookupUserAccount" should {
      implicit val userInfoWrites: OWrites[VinylDNS.UserInfo] = Json.writes[VinylDNS.UserInfo]

      "return a list of users from a list of usernames" in new WithApplication(app) {
        val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
        val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
        val config: Configuration = Configuration.load(Environment.simple())
        val ws: WSClient = mock[WSClient]
        val lookupValue = "someNTID"
        authenticator.lookup(lookupValue).returns(Success(frodoDetails))
        userAccessor.get(frodoDetails.username).returns(IO.pure(Some(frodoUser)))

        val expected = Json.toJson(VinylDNS.UserInfo.fromUser(frodoUser))

        val vinyldnsPortal =
          new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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

      "return a 404 if the account is not found" in new WithApplication(app) {
        val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
        val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
        val config: Configuration = Configuration.load(Environment.simple())
        val ws: WSClient = mock[WSClient]
        userAccessor.get(any[String]).returns(IO.pure(None))
        authenticator
          .lookup(frodoUser.userName)
          .returns(Failure(new UserDoesNotExistException("not found")))

        val vinyldnsPortal =
          new VinylDNS(config, authenticator, userAccessor, ws, components, crypto)
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
  }

  def buildMockUserAccountAccessor: UserAccountAccessor = {
    val accessor = mock[UserAccountAccessor]
    accessor.get(anyString).returns(IO.pure(Some(frodoUser)))
    accessor.create(any[User]).returns(IO.pure(frodoUser))
    accessor.getUserByKey(anyString).returns(IO.pure(Some(frodoUser)))
    accessor
  }

  private def hasCacheHeaders(result: Future[play.api.mvc.Result]) = {
    header("Pragma", result) must beSome("no-cache")
    header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
    header("Expires", result) must beSome("0")
  }

  val mockUserAccountAccessor: UserAccountAccessor = buildMockUserAccountAccessor
  val mockLdapAuthenticator: LdapAuthenticator = mock[LdapAuthenticator]
}
