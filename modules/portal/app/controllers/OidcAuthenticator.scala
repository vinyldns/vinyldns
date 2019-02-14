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
import org.slf4j.{Logger, LoggerFactory}
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

  private val logger: Logger = LoggerFactory.getLogger(classOf[OidcAuthenticator])
  val oidcEnabled: Boolean = configuration.getOptional[Boolean]("oidc.enabled").getOrElse(false)

  lazy val oidcInfo: OidcConfig = pureconfig.loadConfigOrThrow[OidcConfig]("oidc")

  lazy val clientID = new ClientID(oidcInfo.clientId)
  lazy val clientSecret = new Secret(oidcInfo.secret)
  lazy val clientAuth = new ClientSecretBasic(clientID, clientSecret)
  lazy val tokenEndpoint = new URI(oidcInfo.tokenEndpoint)
  lazy val redirectUri: String = oidcInfo.redirectUri + "/callback"

  lazy val sc = new SimpleSecurityContext()

  lazy val jwtProcessor: JWTProcessor[SimpleSecurityContext] = {
    val processor = new DefaultJWTProcessor[SimpleSecurityContext]()

    val keySource =
      new RemoteJWKSet[SimpleSecurityContext](new URL(oidcInfo.jwksEndpoint))

    val expectedJWSAlg = JWSAlgorithm.RS256

    val keySelector = new JWSVerificationKeySelector(expectedJWSAlg, keySource)
    processor.setJWSKeySelector(keySelector)
    processor
  }

  lazy val (oidcGetCodeUrl, oidcGetCodeQueryString) = {
    val responseType = "code"

    val call = wsClient
      .url(oidcInfo.authorizationEndpoint)
      .withQueryStringParameters(
        "client_id" -> oidcInfo.clientId,
        "response_type" -> responseType,
        "redirect_uri" -> redirectUri,
        "scope" -> "openid profile email")

    (call.url, call.queryString)
  }

  def isNotExpired(claimsSet: JWTClaimsSet): Boolean = {
    val now = new Date()
    val expirationTime = claimsSet.getExpirationTime

    val user = getUsernameFromClaims(claimsSet)
    logger.debug(s"Current time: $now, token for $user will expire at: $expirationTime")

    val idTokenStillValid = now.before(expirationTime) &&
      now.after(claimsSet.getNotBeforeTime) &&
      now.after(claimsSet.getIssueTime)

    if (!idTokenStillValid) {
      logger.info(s"Token for $user is expired")
    }

    idTokenStillValid
  }

  def validateIdToken(claimsSet: JWTClaimsSet): Boolean = {
    val isValidTenantId =
      Try(claimsSet.getStringClaim("tid") == oidcInfo.tenantId).toOption.getOrElse(false)
    val isValidAppId =
      Try(claimsSet.getStringListClaim("aud").get(0) == oidcInfo.clientId).toOption.getOrElse(false)

    if (isValidAppId && isValidTenantId) {
      isNotExpired(claimsSet)
    } else {
      val user = getUsernameFromClaims(claimsSet)
      logger.error(
        s"Token issue for user $user; isValidTenantId = $isValidTenantId, isValidAppId = $isValidAppId")
      false
    }
  }

  private def getUsernameFromClaims(claimsSet: JWTClaimsSet): Option[String] =
    Try(claimsSet.getStringClaim(oidcInfo.jwtUsernameField)).toOption

  def getUsernameFromToken(jwtClaimsSetString: String): Option[String] = {
    val claimsSet = JWTClaimsSet.parse(jwtClaimsSetString)
    val isValid = validateIdToken(claimsSet)
    if (isValid) {
      // only return username if the token is valid
      getUsernameFromClaims(claimsSet)
    } else {
      None
    }
  }

  def getUserFromClaims(claimsSet: JWTClaimsSet): Option[OidcUserDetails] =
    for {
      username <- getUsernameFromClaims(claimsSet)
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
    val idToken = successResponse.getOIDCTokens.getIDToken

    val claimsSet = jwtProcessor.process(idToken, sc)
    if (validateIdToken(claimsSet)) {
      Future.successful(Some(claimsSet))
    } else {
      Future.successful(None)
    }

  }
}
