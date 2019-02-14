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

import java.net.{URI, URL}
import java.util.Date

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.oauth2.sdk.AuthorizationCode
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant
import com.nimbusds.jose.jwk.source.RemoteJWKSet
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SimpleSecurityContext}
import com.nimbusds.jwt.proc.{DefaultJWTProcessor, JWTProcessor}
import com.nimbusds.jwt._
import com.nimbusds.oauth2.sdk.TokenRequest
import com.nimbusds.oauth2.sdk.auth.{ClientSecretBasic, Secret}
import com.nimbusds.oauth2.sdk.id.ClientID
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse
import controllers.VinylDNS.UserDetails
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.ws.{WSClient, WSRequest}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object OidcAuthenticator {
  case class OidcConfig(
      authorizationEndpoint: String,
      tokenEndpoint: String,
      jwksEndpoint: String,
      tenantId: String,
      clientId: String,
      secret: String,
      jwtUsernameField: String,
      redirectUri: String,
      scope: String = "openid profile email")

  case class OidcUserDetails(
      username: String,
      email: Option[String],
      firstName: Option[String],
      lastName: Option[String])
      extends UserDetails
}
@Singleton
class OidcAuthenticator @Inject()(wsClient: WSClient, configuration: Configuration) {

  import OidcAuthenticator._

  lazy val oidcInfo: OidcConfig = pureconfig.loadConfigOrThrow[OidcConfig]("oidc")

  val clientID = new ClientID(oidcInfo.clientId)
  val clientSecret = new Secret(oidcInfo.secret)
  val clientAuth = new ClientSecretBasic(clientID, clientSecret)
  val tokenEndpoint = new URI(oidcInfo.tokenEndpoint)
  val redirectUri: String = oidcInfo.redirectUri + "/callback"

  val sc = new SimpleSecurityContext()

  val jwtProcessor: JWTProcessor[SimpleSecurityContext] = {
    val processor = new DefaultJWTProcessor[SimpleSecurityContext]()

    val keySource =
      new RemoteJWKSet[SimpleSecurityContext](new URL(oidcInfo.jwksEndpoint))

    val expectedJWSAlg = JWSAlgorithm.RS256

    val keySelector = new JWSVerificationKeySelector(expectedJWSAlg, keySource)
    processor.setJWSKeySelector(keySelector)
    processor
  }

  def oidcGetCode(): WSRequest = {
    val responseType = "code"

    val call = wsClient
      .url(oidcInfo.authorizationEndpoint)
      .withQueryStringParameters(
        "client_id" -> oidcInfo.clientId,
        "response_type" -> responseType,
        "redirect_uri" -> redirectUri,
        "scope" -> "openid profile email")

    call
  }

  def isNotExpired(claims: JWTClaimsSet): Boolean = {
    val now = new Date()

    now.before(claims.getExpirationTime) &&
    now.after(claims.getNotBeforeTime) &&
    now.after(claims.getIssueTime)
  }

  def validateIdToken(claimsSet: JWTClaimsSet): Boolean = {
    val tid = claimsSet.getStringClaim("tid")
    val appId = claimsSet.getStringListClaim("aud").get(0)

    isNotExpired(claimsSet) && tid == oidcInfo.tenantId && oidcInfo.clientId == appId
  }

  def getUserFromClaims(claimsSet: JWTClaimsSet): Option[OidcUserDetails] =
    for {
      username <- Try(claimsSet.getStringClaim(oidcInfo.jwtUsernameField)).toOption
      email = Try(claimsSet.getStringClaim("email")).toOption
      firstname = Try(claimsSet.getStringClaim("givenname")).toOption
      lastname = Try(claimsSet.getStringClaim("surname")).toOption
    } yield OidcUserDetails(username, email, firstname, lastname)

  def oidcCallback(codeIn: Option[String])(
      implicit executionContext: ExecutionContext): Future[Option[JWTClaimsSet]] = {

    val code = new AuthorizationCode(codeIn.get)
    val callback = new URI(redirectUri)
    val codeGrant = new AuthorizationCodeGrant(code, callback)

    val request = new TokenRequest(tokenEndpoint, clientAuth, codeGrant)
    val tokenResponse = OIDCTokenResponseParser.parse(request.toHTTPRequest.send())

    // TODO handle error
    if (!tokenResponse.indicatesSuccess) { // We got an error response...
      val errorResponse = tokenResponse.toErrorResponse
    }

    val successResponse = tokenResponse.toSuccessResponse.asInstanceOf[OIDCTokenResponse]
    // Get the ID and access token
    val idToken = successResponse.getOIDCTokens.getIDToken
    //val accessToken = successResponse.getOIDCTokens.getAccessToken

    val claimsSet = jwtProcessor.process(idToken, sc)
    if (validateIdToken(claimsSet)) {
      Future.successful(Some(claimsSet))
    } else {
      Future.successful(None)
    }

  }
}
