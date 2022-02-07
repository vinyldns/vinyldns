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

import java.util.Date

import com.nimbusds.jose.proc.SimpleSecurityContext
import com.nimbusds.jwt.proc.JWTProcessor
import com.nimbusds.jwt._
import com.nimbusds.oauth2.sdk.AuthorizationCode
import com.nimbusds.oauth2.sdk.http.HTTPResponse
import controllers.OidcAuthenticator.{ErrorResponse, OidcUserDetails}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.libs.ws.WSClient
import play.api.test.FakeRequest
import play.api.Configuration

class OidcAuthenticatorSpec extends Specification with Mockito {

  val ws: WSClient = mock[WSClient]

  val oidcConfigMap: Map[String, Any] = Map(
    "logout-endpoint" -> "http://test.logout.url",
    "authorization-endpoint" -> "http://test.authorization.url",
    "jwks-endpoint" -> "http://test.jwks.url",
    "token-endpoint" -> "http://test.token.url",
    "tenant-id" -> "test-tenant-id",
    "client-name" -> "test-client-name",
    "client-id" -> "test-client-id",
    "redirect-uri" -> "http://localhost:9001",
    "secret" -> "test-secret",
    "scope" -> "openid profile email",
    "jwt-username-field" -> "username",
    "jwt-firstname-field" -> "firstname",
    "jwt-lastname-field" -> "lastname",
    "enabled" -> true
  )

  val oidcConfig: Configuration = Configuration.from(Map("oidc" -> oidcConfigMap))

  val currentTime: Long = new Date().getTime / 1000
  val futureTime: Long = currentTime + 5000
  val pastTime: Long = currentTime - 5000

  val goodToken: String =
    s"""{"username":"un",
       |"firstname":"First",
       |"lastname":"Last",
       |"email":"test@test.com",
       |"tid":"test-tenant-id",
       |"aud":"test-client-id",
       |"exp": $futureTime,
       |"iat": $pastTime,
       |"nbf": $pastTime}""".stripMargin

  val jwtClaims: JWTClaimsSet = JWTClaimsSet.parse(goodToken)

  val testOidcAuthenticator: OidcAuthenticator = new OidcAuthenticator(ws, oidcConfig) {
    val mockJwtProcessor: JWTProcessor[SimpleSecurityContext] =
      mock[JWTProcessor[SimpleSecurityContext]]
    mockJwtProcessor.process(any[JWT], any[SimpleSecurityContext]).returns(jwtClaims)

    override lazy val jwtProcessor: JWTProcessor[SimpleSecurityContext] = mockJwtProcessor
  }

  val oidcConfigNoTenantId: Configuration =
    Configuration.from(Map("oidc" -> (oidcConfigMap - "tenant-id")))
  val testOidcAuthenticatorNoTenantId: OidcAuthenticator =
    new OidcAuthenticator(ws, oidcConfigNoTenantId) {
      val mockJwtProcessor: JWTProcessor[SimpleSecurityContext] =
        mock[JWTProcessor[SimpleSecurityContext]]
      mockJwtProcessor.process(any[JWT], any[SimpleSecurityContext]).returns(jwtClaims)

      override lazy val jwtProcessor: JWTProcessor[SimpleSecurityContext] = mockJwtProcessor
    }

  "OidcAuthenticator" should {
    "Initial code call" should {
      "properly generate the code call" in {
        val codeCall = testOidcAuthenticator.getCodeCall("/abcd")
        val query = codeCall.queryString().get

        codeCall.toString must startWith("http://test.authorization.url")
        query must contain("client_id=test-client-id")
        query must contain("response_type=code")
        query must contain("redirect_uri=http://localhost:9001/callback")
        query must contain("scope=openid+profile+email")
        query must contain("nonce")
      }
      "properly handle code call response" in {
        val request = FakeRequest("GET", "/callback?code=asdasdasdasd")

        val out = testOidcAuthenticator.getCodeFromAuthResponse(request)
        out must beRight(new AuthorizationCode("asdasdasdasd"))
      }
      "fail if no code in code call response" in {
        val request = FakeRequest("GET", "/callback?boo=asdasdasdasd")

        val out = testOidcAuthenticator.getCodeFromAuthResponse(request)
        out must beLeft(ErrorResponse(500, "No code value in getCodeFromAuthResponse"))
      }
      "fail if there is some other parse error in the code response" in {
        val request = FakeRequest("GET", "/callback?code=")

        val out = testOidcAuthenticator.getCodeFromAuthResponse(request)
        out must beLeft.like {
          case e: ErrorResponse => e.code == 500
        }
      }
      "fail if some other error in code call response" in {
        val request =
          FakeRequest("GET", "/callback?error=invalid_request&error_description=something-bad")

        val out = testOidcAuthenticator.getCodeFromAuthResponse(request)
        out must beLeft(ErrorResponse(302, "Sign in error: something-bad"))
      }
    }
    "getUserFromClaims" should {
      "generate a user from valid claims" in {
        val user = OidcUserDetails("un", Some("test@test.com"), Some("First"), Some("Last"))
        testOidcAuthenticator.getUserFromClaims(jwtClaims) must beRight(user)
      }
      "generate a user from valid claims with no email/fname/lname" in {
        val user = OidcUserDetails("un", None, None, None)
        val tokenInfo =
          s"""{"username":"un"}""".stripMargin

        val jwt = JWTClaimsSet.parse(tokenInfo)
        testOidcAuthenticator.getUserFromClaims(jwt) must beRight(user)
      }
      "fail if no username exists in claims" in {
        val tokenInfo =
          s"""{"firstname":"First",
             |"lastname":"Last",
             |"email":"test@test.com"}""".stripMargin

        val jwt = JWTClaimsSet.parse(tokenInfo)
        testOidcAuthenticator.getUserFromClaims(jwt) must beLeft(
          ErrorResponse(500, "Username field not included in token from from OIDC provider")
        )
      }
    }
    "getValidUsernameFromToken" should {

      "Succeed with valid fields" in {
        testOidcAuthenticator.getValidUsernameFromToken(goodToken) must beSome("un")
      }
      "Fail with expired token" in {
        val tokenInfo =
          s"""{"username":"un",
             |"tid":"test-tenant-id",
             |"aud":"test-client-id",
             |"firstname":"First",
             |"lastname":"Last",
             |"exp": $pastTime,
             |"iat": $pastTime,
             |"nbf": $pastTime,
             |"email":"test@test.com"}""".stripMargin

        testOidcAuthenticator.getValidUsernameFromToken(tokenInfo) must beNone
      }
      "Fail if tid is invalid" in {
        val tokenInfo =
          s"""{"username":"un",
             |"tid":"bad-tid",
             |"aud":"test-client-id",
             |"firstname":"First",
             |"lastname":"Last",
             |"exp": $futureTime,
             |"iat": $pastTime,
             |"nbf": $pastTime,
             |"email":"test@test.com"}""".stripMargin

        testOidcAuthenticator.getValidUsernameFromToken(tokenInfo) must beNone
      }
      "succeed if tid is not configured" in {
        val tokenInfo =
          s"""{"username":"un",
             |"tid":"bad-tid",
             |"aud":"test-client-id",
             |"firstname":"First",
             |"lastname":"Last",
             |"exp": $futureTime,
             |"iat": $pastTime,
             |"nbf": $pastTime,
             |"email":"test@test.com"}""".stripMargin

        testOidcAuthenticatorNoTenantId.getValidUsernameFromToken(tokenInfo) must beSome("un")
      }
      "succeed if the tid is not returned and it is not configured" in {
        val tokenInfo =
          s"""{"username":"un",
             |"aud":"test-client-id",
             |"firstname":"First",
             |"lastname":"Last",
             |"exp": $futureTime,
             |"iat": $pastTime,
             |"nbf": $pastTime,
             |"email":"test@test.com"}""".stripMargin

        testOidcAuthenticatorNoTenantId.getValidUsernameFromToken(tokenInfo) must beSome("un")
      }
      "fail if aud is invalid" in {
        val tokenInfo =
          s"""{"username":"un",
             |"tid":"test-tenant-id",
             |"aud":"bad-aud",
             |"firstname":"First",
             |"lastname":"Last",
             |"exp": $futureTime,
             |"iat": $pastTime,
             |"nbf": $pastTime,
             |"email":"test@test.com"}""".stripMargin

        testOidcAuthenticator.getValidUsernameFromToken(tokenInfo) must beNone
      }
    }
    "handleCallbackResponse" should {
      val unsignedTestKey = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJ0aWQiOiJ0ZXN0LXRlbmFudC1pZCIsImF1ZCI6InRlc3QtY2xpZW50LWlkIiwidXNl" +
        "cm5hbWUiOiJ1biIsImZpcnN0bmFtZSI6IkZpcnN0IiwibGFzdG5hbWUiOiJMYXN0IiwiZW1haWwiOiJ0ZXN0QHRlc3QuY29tIn0."

      "succeed with good response" in {
        val testResponse = new HTTPResponse(200)
        testResponse.setHeader("Content-Type", "application/json", "charset=utf-8")
        val body =
          s"""{
              "access_token": "$unsignedTestKey",
              "token_type": "Bearer",
              "id_token": "$unsignedTestKey"
              }
          """
        testResponse.setContent(body)

        testOidcAuthenticator.handleCallbackResponse(testResponse) must beRight(jwtClaims)
      }
      "respond with errors if given" in {
        val testResponse = new HTTPResponse(400)
        testResponse.setHeader("Content-Type", "application/json", "charset=utf-8")
        val body =
          s"""{
              "error": "some error",
              "error_description": "some error description"
              }
          """
        testResponse.setContent(body)

        testOidcAuthenticator.handleCallbackResponse(testResponse) must beLeft(
          ErrorResponse(400, "Sign in token error: some error description")
        )
      }
    }
  }
}
