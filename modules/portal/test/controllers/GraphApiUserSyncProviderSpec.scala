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
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.libs.json._
import vinyldns.core.domain.Encrypted
import vinyldns.core.domain.membership.User

class GraphApiUserSyncProviderSpec extends Specification with Mockito {

  val testUser: User = User("testuser", "accessKey", Encrypted("secretKey"))
  val disabledUser: User = User("disableduser", "accessKey", Encrypted("secretKey"))
  val missingUser: User = User("missinguser", "accessKey", Encrypted("secretKey"))
  val errorUser: User = User("erroruser", "accessKey", Encrypted("secretKey"))

  /**
    * Testable subclass that overrides HTTP calls to avoid real network I/O.
    * This follows the same sync-wrapped-in-IO pattern used by the real provider.
    */
  class TestableGraphApiProvider(
      userResponses: Map[String, (Int, String)] = Map.empty,
      tokenResponse: Either[Throwable, String] = Right("test-token")
  ) extends GraphApiUserSyncProvider("test-tenant", "test-client", "test-secret", "onPremisesSamAccountName") {

    override private[controllers] def getAccessToken(): IO[String] =
      tokenResponse match {
        case Right(token) => IO.pure(token)
        case Left(err) => IO.raiseError(err)
      }

    override private[controllers] def checkUser(token: String, user: User): IO[Option[User]] =
      IO {
        userResponses.get(user.userName) match {
          case Some((200, body)) =>
            val json = Json.parse(body)
            val values = (json \ "value").as[JsArray].value
            if (values.isEmpty) {
              Some(user)
            } else {
              val accountEnabled = (values.head \ "accountEnabled").asOpt[Boolean].getOrElse(true)
              if (!accountEnabled) Some(user) else None
            }
          case Some((404, _)) =>
            Some(user)
          case Some((_, _)) =>
            None // HTTP error → treat as active (safe default)
          case None =>
            None // No response configured → treat as active
        }
      }
  }

  "GraphApiUserSyncProvider" should {

    "return empty list when user is found and enabled" in {
      val responses = Map(
        "testuser" -> (200, """{"value": [{"accountEnabled": true}]}""")
      )
      val provider = new TestableGraphApiProvider(userResponses = responses)

      val result = provider.getStaleUsers(List(testUser)).unsafeRunSync()
      result must beEmpty
    }

    "return user when user is found but disabled" in {
      val responses = Map(
        "disableduser" -> (200, """{"value": [{"accountEnabled": false}]}""")
      )
      val provider = new TestableGraphApiProvider(userResponses = responses)

      val result = provider.getStaleUsers(List(disabledUser)).unsafeRunSync()
      result must beEqualTo(List(disabledUser))
    }

    "return user when user is not found (empty result)" in {
      val responses = Map(
        "missinguser" -> (200, """{"value": []}""")
      )
      val provider = new TestableGraphApiProvider(userResponses = responses)

      val result = provider.getStaleUsers(List(missingUser)).unsafeRunSync()
      result must beEqualTo(List(missingUser))
    }

    "return user when Graph API returns 404" in {
      val responses = Map(
        "missinguser" -> (404, "")
      )
      val provider = new TestableGraphApiProvider(userResponses = responses)

      val result = provider.getStaleUsers(List(missingUser)).unsafeRunSync()
      result must beEqualTo(List(missingUser))
    }

    "treat user as active on HTTP error (safe default)" in {
      val responses = Map(
        "erroruser" -> (500, "Internal Server Error")
      )
      val provider = new TestableGraphApiProvider(userResponses = responses)

      val result = provider.getStaleUsers(List(errorUser)).unsafeRunSync()
      result must beEmpty
    }

    "handle multiple users with mixed results" in {
      val responses = Map(
        "testuser" -> (200, """{"value": [{"accountEnabled": true}]}"""),
        "disableduser" -> (200, """{"value": [{"accountEnabled": false}]}"""),
        "missinguser" -> (200, """{"value": []}"""),
        "erroruser" -> (500, "error")
      )
      val provider = new TestableGraphApiProvider(userResponses = responses)

      val result = provider
        .getStaleUsers(List(testUser, disabledUser, missingUser, errorUser))
        .unsafeRunSync()

      result must contain(exactly(disabledUser, missingUser))
    }

    "propagate token acquisition failure" in {
      val provider = new TestableGraphApiProvider(
        tokenResponse = Left(new RuntimeException("Token request failed with status 401"))
      )

      provider.getStaleUsers(List(testUser)).unsafeRunSync() must throwA[RuntimeException](
        "Token request failed with status 401"
      )
    }
  }

  "NoOpUserSyncProvider" should {
    "return empty list" in {
      NoOpUserSyncProvider.getStaleUsers(List(testUser)).unsafeRunSync() must beEmpty
    }
  }

  "LdapUserSyncProvider" should {
    "delegate to authenticator.getUsersNotInLdap" in {
      val mockAuth = mock[Authenticator]
      mockAuth.getUsersNotInLdap(List(testUser)).returns(IO(List(testUser)))

      val provider = new LdapUserSyncProvider(mockAuth)
      val result = provider.getStaleUsers(List(testUser)).unsafeRunSync()

      result must beEqualTo(List(testUser))
      there.was(one(mockAuth).getUsersNotInLdap(List(testUser)))
    }

    "return empty list when no users are stale" in {
      val mockAuth = mock[Authenticator]
      mockAuth.getUsersNotInLdap(List(testUser)).returns(IO(Nil))

      val provider = new LdapUserSyncProvider(mockAuth)
      val result = provider.getStaleUsers(List(testUser)).unsafeRunSync()

      result must beEmpty
    }
  }

  "NoOpAuthenticator" should {
    val auth = new NoOpAuthenticator

    "return LdapServiceException for authenticate" in {
      auth.authenticate("user", "pass") must beLeft(LdapServiceException("LDAP not configured"))
    }

    "return LdapServiceException for lookup" in {
      auth.lookup("user") must beLeft(LdapServiceException("LDAP not configured"))
    }

    "return empty list for getUsersNotInLdap" in {
      auth.getUsersNotInLdap(List(testUser)).unsafeRunSync() must beEmpty
    }

    "return a passing health check" in {
      auth.healthCheck().unsafeRunSync() must beRight
    }
  }
}
