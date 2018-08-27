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

import com.amazonaws.auth.BasicAWSCredentials
import models.UserAccount
import org.joda.time.DateTime
import org.junit.runner._
import org.specs2.mock.Mockito
import org.specs2.mutable._
import org.specs2.runner._
import play.api.{Application, Configuration, Environment, Mode}
import play.api.libs.json.{JsObject, JsValue, Json, OWrites}
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.core.server.{Server, ServerConfig}

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
import play.api.inject.guice.GuiceApplicationBuilder

import scala.util.{Failure, Success}

/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  * For more information, consult the wiki.
  */
@RunWith(classOf[JUnitRunner])
class VinylDNSSpec extends Specification with Mockito {

  val simulatedBackendPort = 9001

  // Important: order is critical, put the override after loading the default config from application-test.conf
  val testConfig: Configuration =
    Configuration.load(Environment.simple()) ++ Configuration.from(
      Map("portal.vinyldns.backend.url" -> s"http://localhost:$simulatedBackendPort"))

  val app: Application = GuiceApplicationBuilder()
    .configure(testConfig)
    .build()

  val components: ControllerComponents = Helpers.stubControllerComponents()
  val defaultActionBuilder = DefaultActionBuilder(Helpers.stubBodyParser())

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
        userAccessor.get(anyString).returns(Success(Some(frodoAccount)))
        userAccessor.get("frodo").returns(Success(Some(frodoAccount)))

        val vinyldnsPortal =
          new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
        val result = vinyldnsPortal
          .getAuthenticatedUserData()
          .apply(
            FakeRequest(GET, "/api/users/currentuser").withSession(("username", "frodo"))
          )

        status(result) must beEqualTo(200)
        val userInfo: JsValue = contentAsJson(result)

        (userInfo \ "id").as[String] must beEqualTo(frodoAccount.userId)
        (userInfo \ "userName").as[String] must beEqualTo(frodoAccount.username)
        Some((userInfo \ "firstName").as[String]) must beEqualTo(frodoAccount.firstName)
        Some((userInfo \ "lastName").as[String]) must beEqualTo(frodoAccount.lastName)
        Some((userInfo \ "email").as[String]) must beEqualTo(frodoAccount.email)
        (userInfo \ "isSuper").as[Boolean] must beFalse
      }
      "return Not found if the current logged in user was not found" in new WithApplication(app) {
        val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
        val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
        val config: Configuration = Configuration.load(Environment.simple())
        val ws: WSClient = mock[WSClient]

        authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
        userAccessor.get(anyString).returns(Success(Some(frodoAccount)))
        userAccessor.get("frodo").returns(Success(None))

        val vinyldnsPortal =
          new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
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
        userAccessor.get(anyString).returns(Success(Some(frodoAccount)))
        userAccessor.get("frodo").returns(Success(Some(frodoAccount)))

        val vinyldnsPortal =
          new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
        val result = vinyldnsPortal
          .regenerateCreds()
          .apply(
            FakeRequest(POST, "/regenerate-creds").withSession(
              "username" -> frodoAccount.username,
              "accessKey" -> frodoAccount.accessKey))

        status(result) must beEqualTo(200)

        session(result).get("username") must beSome(frodoAccount.username)
        (session(result).get("accessKey") must not).beSome(frodoAccount.accessKey)
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
          userAccessor.get(frodoDetails.username).returns(Success(Some(frodoAccount)))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
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
          userAccessor.get(frodoDetails.username).returns(Success(Some(frodoAccount)))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
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
          userAccessor.get(anyString).returns(Success(None))
          userAccessor.put(any[UserAccount]).returns(Success(frodoAccount))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "frodo", "password" -> "secondbreakfast")
            )
          there.was(one(userAccessor).get("frodo"))
          there.was(one(userAccessor).put(_: UserAccount))
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
          userAccessor.get(frodoDetails.username).returns(Success(None))
          userAccessor.put(any[UserAccount]).returns(Success(frodoAccount))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest().withFormUrlEncodedBody(
                "username" -> frodoDetails.username,
                "password" -> "secondbreakfast")
            )
          there.was(one(userAccessor).get(frodoDetails.username))
          there.was(one(userAccessor).put(_: UserAccount))
        }

        "do not call the user accessor to create the new user account if it is found" in new WithApplication(
          app) {
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = buildMockUserAccountAccessor
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
          userAccessor.get(any[String]).returns(Success(Some(frodoAccount)))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "frodo", "password" -> "secondbreakfast")
            )
          there.was(one(userAccessor).get("frodo"))
          there.was(no(userAccessor).put(_: UserAccount))
        }

        "set the username, and key for the new style membership" in new WithApplication(app) {
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator
            .authenticate(frodoDetails.username, "secondbreakfast")
            .returns(Success(frodoDetails))
          userAccessor.get(frodoDetails.username).returns(Success(Some(frodoAccount)))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest().withFormUrlEncodedBody(
                "username" -> frodoDetails.username,
                "password" -> "secondbreakfast")
            )
          session(response).get("username") must beSome(frodoAccount.username)
          session(response).get("accessKey") must beSome(frodoAccount.accessKey)
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
          userAccessor.get(frodoDetails.username).returns(Success(Some(frodoAccount)))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
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
          userAccessor.get(anyString).returns(Success(None))
          userAccessor.put(any[UserAccount]).returns(Success(frodoAccount))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
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
          userAccessor.get(anyString).returns(Success(None))
          userAccessor.put(any[UserAccount]).returns(Success(frodoAccount))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "frodo", "password" -> "secondbreakfast")
            )
          session(response).get("username") must beSome(frodoAccount.username)
          session(response).get("accessKey") must beSome(frodoAccount.accessKey)
        }
        "redirect to index" in new WithApplication(app) {
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
          userAccessor.get(anyString).returns(Success(None))
          userAccessor.put(any[UserAccount]).returns(Success(frodoAccount))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
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
          userAccessor.get(anyString).returns(Success(None))
          userAccessor.put(any[UserAccount]).returns(Success(serviceAccount))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
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
          userAccessor.get(anyString).returns(Success(None))
          userAccessor.put(any[UserAccount]).returns(Success(serviceAccount))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
          val response = vinyldnsPortal
            .login()
            .apply(
              FakeRequest()
                .withFormUrlEncodedBody("username" -> "service", "password" -> "password")
            )
          session(response).get("username") must beSome(serviceAccount.username)
          session(response).get("accessKey") must beSome(serviceAccount.accessKey)
        }
        "redirect to index" in new WithApplication(app) {
          val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
          val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
          val config: Configuration = Configuration.load(Environment.simple())
          val ws: WSClient = mock[WSClient]
          authenticator.authenticate("service", "password").returns(Success(serviceAccountDetails))
          userAccessor.get(anyString).returns(Success(None))
          userAccessor.put(any[UserAccount]).returns(Success(serviceAccount))

          val vinyldnsPortal =
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
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
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
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
            new VinylDNS(
              config,
              authenticator,
              mockUserAccountAccessor,
              mockAuditLog,
              ws,
              components)
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
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
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
            new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.newGroup()(
              FakeRequest(POST, "/groups")
                .withJsonBody(hobbitGroupRequest)
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(OK)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.newGroup()(
              FakeRequest(POST, "/groups")
                .withJsonBody(invalidHobbitGroup)
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(BAD_REQUEST)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.newGroup()(
              FakeRequest(POST, s"/groups")
                .withJsonBody(hobbitGroupRequest)
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(UNAUTHORIZED)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.newGroup()(
              FakeRequest(POST, "/groups")
                .withJsonBody(hobbitGroupRequest)
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(CONFLICT)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.getGroup(hobbitGroupId)(
              FakeRequest(GET, s"/groups/$hobbitGroupId")
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(OK)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.getGroup(hobbitGroupId)(
              FakeRequest(GET, s"/groups/$hobbitGroupId")
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(UNAUTHORIZED)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.getGroup("not-hobbits")(
              FakeRequest(GET, "/groups/not-hobbits")
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(NOT_FOUND)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.deleteGroup(hobbitGroupId)(
              FakeRequest(DELETE, s"/groups/$hobbitGroupId")
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(NO_CONTENT)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.deleteGroup(hobbitGroupId)(
              FakeRequest(DELETE, s"/groups/$hobbitGroupId")
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(UNAUTHORIZED)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))

            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.deleteGroup(hobbitGroupId)(
              FakeRequest(DELETE, s"/groups/$hobbitGroupId")
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(FORBIDDEN)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.deleteGroup("not-hobbits")(
              FakeRequest(DELETE, "/groups/not-hobbits")
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(NOT_FOUND)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.updateGroup(hobbitGroupId)(
              FakeRequest(PUT, s"/groups/$hobbitGroupId")
                .withJsonBody(hobbitGroup)
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(OK)
            contentAsJson(result) must beEqualTo(hobbitGroup)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.updateGroup(hobbitGroupId)(
              FakeRequest(PUT, s"/groups/$hobbitGroupId")
                .withJsonBody(invalidHobbitGroup)
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(BAD_REQUEST)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.updateGroup(hobbitGroupId)(
              FakeRequest(PUT, s"/groups/$hobbitGroupId")
                .withJsonBody(hobbitGroup)
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(UNAUTHORIZED)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.updateGroup(hobbitGroupId)(
              FakeRequest(PUT, s"/groups/$hobbitGroupId")
                .withJsonBody(hobbitGroup)
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(FORBIDDEN)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.updateGroup("not-hobbits")(
              FakeRequest(PUT, "/groups/not-hobbits")
                .withJsonBody(hobbitGroup)
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(NOT_FOUND)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.getMemberList(hobbitGroupId)(
              FakeRequest(GET, s"/data/groups/$hobbitGroupId/members")
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(OK)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.getMemberList(hobbitGroupId)(
              FakeRequest(GET, s"/groups/$hobbitGroupId/members?maxItems=0")
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(BAD_REQUEST)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.getMemberList(hobbitGroupId)(
              FakeRequest(GET, s"/groups/$hobbitGroupId/members")
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(UNAUTHORIZED)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.getMemberList(hobbitGroupId)(
              FakeRequest(GET, s"/groups/$hobbitGroupId/members")
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(NOT_FOUND)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.getMyGroups()(
              FakeRequest(GET, s"/api/groups")
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(OK)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
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
            mockUserAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
            val underTest = new VinylDNS(
              testConfig,
              mockLdapAuthenticator,
              mockUserAccessor,
              mockAuditLog,
              client,
              components)
            val result = underTest.getMyGroups()(
              FakeRequest(GET, s"/api/groups")
                .withSession(
                  "username" -> frodoAccount.username,
                  "accessKey" -> frodoAccount.accessKey))

            status(result) must beEqualTo(UNAUTHORIZED)
            header("Pragma", result) must beSome("no-cache")
            header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
            header("Expires", result) must beSome("0")
          }
        }
      }
    }
    ".getUserCreds" should {
      "return credentials for a user using the new style" in new WithApplication(app) {
        val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
        val config: Configuration = Configuration.load(Environment.simple())
        val ws: WSClient = mock[WSClient]
        mockUserAccountAccessor.getUserByKey(anyString).returns(Success(Some(frodoAccount)))
        val underTest = new VinylDNS(
          config,
          mockLdapAuthenticator,
          mockUserAccountAccessor,
          mockAuditLog,
          ws,
          components)

        val result: BasicAWSCredentials = underTest.getUserCreds(Some(frodoAccount.accessKey))
        there.was(one(mockUserAccountAccessor).getUserByKey(frodoAccount.accessKey))
        result.getAWSAccessKeyId must beEqualTo(frodoAccount.accessKey)
        result.getAWSSecretKey must beEqualTo(frodoAccount.accessSecret)
      }
      "fail when not supplied with a key using the new style" in new WithApplication(app) {
        val config: Configuration = Configuration.load(Environment.simple())
        val ws: WSClient = mock[WSClient]
        val underTest = new VinylDNS(
          config,
          mockLdapAuthenticator,
          mockUserAccountAccessor,
          mockAuditLog,
          ws,
          components)

        underTest.getUserCreds(None) must throwAn[IllegalArgumentException]
      }
    }
    ".serveCredFile" should {
      "return a csv file with the new style credentials" in new WithApplication(app) {
        import play.api.mvc.Result

        val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
        val config: Configuration = Configuration.load(Environment.simple())
        val ws: WSClient = mock[WSClient]
        userAccessor.get(frodoAccount.username).returns(Success(Some(frodoAccount)))
        val underTest =
          new VinylDNS(config, mockLdapAuthenticator, userAccessor, mockAuditLog, ws, components)

        val result: Future[Result] = underTest.serveCredsFile("credsfile.csv")(
          FakeRequest(GET, s"/download-creds-file/credsfile.csv")
            .withSession(
              "username" -> frodoAccount.username,
              "accessKey" -> frodoAccount.accessKey))
        val content: String = contentAsString(result)
        content must contain("NT ID")
        content must contain(frodoAccount.username)
        content must contain(frodoAccount.accessKey)
        content must contain(frodoAccount.accessSecret)
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
        userAccessor.get(frodoDetails.username).returns(Success(Some(frodoAccount)))

        val expected = Json.toJson(VinylDNS.UserInfo.fromAccount(frodoAccount))

        val vinyldnsPortal =
          new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
        val result = vinyldnsPortal
          .getUserDataByUsername(lookupValue)
          .apply(
            FakeRequest(GET, s"/api/users/lookupuser/$lookupValue")
              .withSession("username" -> "frodo")
          )
        status(result) must beEqualTo(200)
        header("Pragma", result) must beSome("no-cache")
        header("Cache-Control", result) must beSome("no-cache, no-store, must-revalidate")
        header("Expires", result) must beSome("0")
        contentAsJson(result) must beEqualTo(expected)
      }

      "return a 404 if the account is not found" in new WithApplication(app) {
        val authenticator: LdapAuthenticator = mock[LdapAuthenticator]
        val userAccessor: UserAccountAccessor = mock[UserAccountAccessor]
        val config: Configuration = Configuration.load(Environment.simple())
        val ws: WSClient = mock[WSClient]
        userAccessor.get(frodoAccount.username).returns(Success(None))
        authenticator
          .lookup(frodoAccount.username)
          .returns(Failure(new UserDoesNotExistException("not found")))

        val vinyldnsPortal =
          new VinylDNS(config, authenticator, userAccessor, mockAuditLog, ws, components)
        val result = vinyldnsPortal
          .getUserDataByUsername(frodoAccount.username)
          .apply(
            FakeRequest(GET, s"/api/users/lookupuser/${frodoAccount.username}")
              .withSession("username" -> "frodo")
          )

        status(result) must beEqualTo(404)
        header("Pragma", result) must beNone
        header("Cache-Control", result) must beNone
        header("Expires", result) must beNone
      }
    }
  }

  val frodoDetails = UserDetails(
    "CN=frodo,OU=hobbits,DC=middle,DC=earth",
    "frodo",
    Some("fbaggins@hobbitmail.me"),
    Some("Frodo"),
    Some("Baggins"))
  val frodoAccount = UserAccount(
    "frodo-uuid",
    "fbaggins",
    Some("Frodo"),
    Some("Baggins"),
    Some("fbaggins@hobbitmail.me"),
    DateTime.now,
    "key",
    "secret")

  val serviceAccountDetails =
    UserDetails("CN=frodo,OU=hobbits,DC=middle,DC=earth", "service", None, None, None)
  val serviceAccount =
    UserAccount("service-uuid", "service", None, None, None, DateTime.now, "key", "secret")

  val frodoJsonString: String = s"""{
       |  "userName":  "${frodoAccount.username}",
       |  "firstName": "${frodoAccount.firstName}",
       |  "lastName":  "${frodoAccount.lastName}",
       |  "email":     "${frodoAccount.email}",
       |  "created":   "${frodoAccount.created}",
       |  "id":        "${frodoAccount.userId}"
       |}
     """.stripMargin

  val samAccount = UserAccount(
    "sam-uuid",
    "sgamgee",
    Some("Samwise"),
    Some("Gamgee"),
    Some("sgamgee@hobbitmail.me"),
    DateTime.now,
    "key",
    "secret")
  val samDetails = UserDetails(
    "CN=sam,OU=hobbits,DC=middle,DC=earth",
    "sam",
    Some("sgamgee@hobbitmail.me"),
    Some("Sam"),
    Some("Gamgee"))

  def buildMockUserAccountAccessor: UserAccountAccessor = {
    val accessor = mock[UserAccountAccessor]
    accessor.get(anyString).returns(Success(Some(mock[UserAccount])))
    accessor.put(any[UserAccount]).returns(Success(mock[UserAccount]))
    accessor.getUserByKey(anyString).returns(Success(Some(mock[UserAccount])))
    accessor
  }

  def buildMockChangeLogStore: ChangeLogStore = {
    val log = mock[ChangeLogStore]
    log.log(any[ChangeLogMessage]).defaultAnswer {
      case message: ChangeLogMessage => Success(message)
    }
    log
  }

  val mockUserAccountAccessor: UserAccountAccessor = buildMockUserAccountAccessor

  val mockAuditLog: ChangeLogStore = buildMockChangeLogStore

  val mockLdapAuthenticator: LdapAuthenticator = mock[LdapAuthenticator]

  val frodoJson: String =
    s"""{
        |"name": "${frodoAccount.username}"
        |}
     """.stripMargin

  val hobbitGroupId = "uuid-12345-abcdef"
  val hobbitGroup: JsValue = Json.parse(s"""{
        | "id":          "${hobbitGroupId}",
        | "name":        "hobbits",
        | "email":       "hobbitAdmin@shire.me",
        | "description": "Hobbits of the shire",
        | "members":     [ { "id": "${frodoAccount.userId}" },  { "id": "samwise-userId" } ],
        | "admins":      [ { "id": "${frodoAccount.userId}" } ]
        | }
    """.stripMargin)

  val ringbearerGroup: JsValue = Json.parse(
    s"""{
       |  "id":          "ringbearer-group-uuid",
       |  "name":        "ringbearers",
       |  "email":       "future-minions@mordor.me",
       |  "description": "Corruptable folk of middle-earth",
       |  "members":     [ { "id": "${frodoAccount.userId}" },  { "id": "sauron-userId" } ],
       |  "admins":      [ { "id": "sauron-userId" } ]
       |  }
     """.stripMargin
  )
  val hobbitGroupRequest: JsValue = Json.parse(s"""{
        | "name":        "hobbits",
        | "email":       "hobbitAdmin@shire.me",
        | "description": "Hobbits of the shire",
        | "members":     [ { "id": "${frodoAccount.userId}" },  { "id": "samwise-userId" } ],
        | "admins":      [ { "id": "${frodoAccount.userId}" } ]
        | }
    """.stripMargin)

  val invalidHobbitGroup: JsValue = Json.parse(s"""{
        | "name":        "hobbits",
        | "email":       "hobbitAdmin@shire.me",
        | "description": "Hobbits of the shire",
        | "members":     [ { "id": "${frodoAccount.userId}" },  { "id": "merlin-userId" } ],
        | "admins":      [ { "id": "${frodoAccount.userId}" } ]
        | }
    """.stripMargin)

  val hobbitGroupMembers: JsValue = Json.parse(
    s"""{
       | "members": [ ${frodoJsonString} ],
       | "maxItems": 100
       |}
     """.stripMargin
  )

  val groupList: JsObject = Json.obj("groups" -> Json.arr(hobbitGroup))
  val emptyGroupList: JsObject = Json.obj("groups" -> Json.arr())

  val frodoGroupList: JsObject = Json.obj("groups" -> Json.arr(hobbitGroup, ringbearerGroup))
}
