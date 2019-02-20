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

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.oauth2.sdk._
import com.nimbusds.jose.jwk.source.RemoteJWKSet
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SimpleSecurityContext}
import com.nimbusds.jwt.proc.{DefaultJWTProcessor, JWTProcessor}
import com.nimbusds.jwt._
import com.nimbusds.oauth2.sdk.auth.{ClientSecretBasic, Secret}
import com.nimbusds.oauth2.sdk.http.HTTPResponse
import com.nimbusds.oauth2.sdk.id.ClientID
import com.nimbusds.openid.connect.sdk._
import controllers.VinylDNS.UserDetails
import javax.inject.{Inject, Singleton}
import org.slf4j.{Logger, LoggerFactory}
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.collection.JavaConverters._

object OidcAuthenticator {
  final case class OidcConfig(
      authorizationEndpoint: String,
      tokenEndpoint: String,
      jwksEndpoint: String,
      logoutEndpoint: String,
      tenantId: String,
      clientId: String,
      secret: String,
      redirectUri: String,
      jwtUsernameField: String,
      jwtFirstnameField: String,
      jwtLastnameField: String,
      jwtEmailField: String = "email",
      scope: String = "openid profile email")

  final case class OidcUserDetails(
      username: String,
      email: Option[String],
      firstName: Option[String],
      lastName: Option[String])
      extends UserDetails

  final case class ErrorResponse(code: Int, message: String)
}
@Singleton
class OidcAuthenticator @Inject()(wsClient: WSClient, configuration: Configuration) {

  import OidcAuthenticator._

  private val logger: Logger = LoggerFactory.getLogger(classOf[OidcAuthenticator])
  val oidcEnabled: Boolean = configuration.getOptional[Boolean]("oidc.enabled").getOrElse(false)
  lazy val oidcInfo: OidcConfig =
    pureconfig.loadConfigOrThrow[OidcConfig](configuration.underlying, "oidc")

  lazy val clientID = new ClientID(oidcInfo.clientId)
  lazy val clientSecret = new Secret(oidcInfo.secret)
  lazy val clientAuth = new ClientSecretBasic(clientID, clientSecret)
  lazy val tokenEndpoint = new URI(oidcInfo.tokenEndpoint)
  lazy val redirectUriString: String = oidcInfo.redirectUri + "/callback"
  lazy val oidcLogoutUrl: String = oidcInfo.logoutEndpoint
  lazy val redirectUri = new URI(redirectUriString)

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

  def getCodeCall: Uri = {
    val nonce = new Nonce()

    val query = Query(
      "client_id" -> oidcInfo.clientId,
      "response_type" -> "code",
      "redirect_uri" -> redirectUriString,
      "scope" -> oidcInfo.scope,
      "nonce" -> nonce.toString
    )

    Uri(s"${oidcInfo.authorizationEndpoint}").withQuery(query)
  }

  def getCodeFromAuthResponse(request: RequestHeader): Either[ErrorResponse, AuthorizationCode] =
    Try(AuthenticationResponseParser.parse(new URI(request.uri))).toEither
      .leftMap { err =>
        logger.error(s"Unexpected parse error in getCodeFromAuthResponse: ${err.getMessage}")
        ErrorResponse(500, err.getMessage)
      }
      .flatMap {
        case s: AuthenticationSuccessResponse =>
          val code = Option(s.getAuthorizationCode)
          code match {
            case Some(c) => Right(c)
            case None =>
              Left(ErrorResponse(500, "No code value in getCodeFromAuthResponse"))
          }
        case err: AuthorizationErrorResponse =>
          val errorMessage = s"Sign in error: ${err.getErrorObject.getDescription}"
          Left(ErrorResponse(err.toHTTPResponse.getStatusCode, errorMessage))
      }

  def isNotExpired(claimsSet: JWTClaimsSet): Boolean = {
    val now = new Date()
    val expirationTime = Option(claimsSet.getExpirationTime)
    val notBeforeTime = Option(claimsSet.getNotBeforeTime)
    val issueTime = Option(claimsSet.getIssueTime)

    val user = getStringFieldOption(claimsSet, oidcInfo.jwtUsernameField)
    logger.debug(s"Current time: $now, token for $user will expire at: $expirationTime")

    val idTokenStillValid = expirationTime.forall(now.before) &&
      notBeforeTime.forall(now.after) &&
      issueTime.forall(now.after)

    if (!idTokenStillValid) {
      logger.info(s"Token for $user is expired")
    }

    idTokenStillValid
  }

  def getStringFieldOption(claimsSet: JWTClaimsSet, field: String): Option[String] =
    Try(Option(claimsSet.getStringClaim(field))).toOption.flatten

  def isValidIdToken(claimsSet: JWTClaimsSet): Boolean = {
    val tid = getStringFieldOption(claimsSet, "tid")
    val aid = Try(List(claimsSet.getStringListClaim("aud").asScala).flatten).getOrElse(List())

    val isValidTenantId = tid.contains(oidcInfo.tenantId)
    val isValidAppId = aid.contains(oidcInfo.clientId)

    if (isValidAppId && isValidTenantId) {
      isNotExpired(claimsSet)
    } else {
      val user = getStringFieldOption(claimsSet, oidcInfo.jwtUsernameField)
      logger.error(s"Token issue for user $user; tenantId = $tid, appId = $aid")
      false
    }
  }

  def getValidUsernameFromToken(jwtClaimsSetString: String): Option[String] = {
    val claimsSet = Try(JWTClaimsSet.parse(jwtClaimsSetString)).toOption

    val isValid = claimsSet.exists(isValidIdToken)
    if (isValid) {
      // only return username if the token is valid
      claimsSet.flatMap(getStringFieldOption(_, oidcInfo.jwtUsernameField))
    } else {
      None
    }
  }

  def getUserFromClaims(claimsSet: JWTClaimsSet): Either[ErrorResponse, OidcUserDetails] =
    for {
      username <- getStringFieldOption(claimsSet, oidcInfo.jwtUsernameField)
        .toRight[ErrorResponse](
          ErrorResponse(500, "Username field not included in token from from OIDC provider"))
      email = getStringFieldOption(claimsSet, oidcInfo.jwtEmailField)
      firstname = getStringFieldOption(claimsSet, oidcInfo.jwtFirstnameField)
      lastname = getStringFieldOption(claimsSet, oidcInfo.jwtLastnameField)
    } yield OidcUserDetails(username, email, firstname, lastname)

  def handleCallbackResponse(response: HTTPResponse): Either[ErrorResponse, JWTClaimsSet] = {
    def matchTokenResponse(token: TokenResponse) = token match {
      case success: OIDCTokenResponse => Right(success)
      case err: TokenErrorResponse =>
        val errorMessage = s"Sign in token error: ${err.getErrorObject.getDescription}"
        Left(ErrorResponse(err.toHTTPResponse.getStatusCode, errorMessage))
      case _ => Left(ErrorResponse(500, "Unable to parse OIDC token response"))
    }

    def getClaimSet(oidcTokenResposne: OIDCTokenResponse): Either[ErrorResponse, JWTClaimsSet] = {
      val idToken = oidcTokenResposne.getOIDCTokens.getIDToken

      oidcTry(jwtProcessor.process(idToken, sc)).flatMap { claims =>
        if (isValidIdToken(claims)) {
          Right(claims)
        } else {
          Left(ErrorResponse(500, "Invalid ID token response received from from OIDC provider"))
        }
      }
    }

    for {
      asTokenResponse <- oidcTry(OIDCTokenResponseParser.parse(response))
      asOidcToken <- matchTokenResponse(asTokenResponse)
      claims <- getClaimSet(asOidcToken)
    } yield claims
  }

  def oidcCallback(code: AuthorizationCode)(
      implicit executionContext: ExecutionContext): EitherT[IO, ErrorResponse, JWTClaimsSet] =
    EitherT {
      val codeGrant = new AuthorizationCodeGrant(code, redirectUri)
      val request = new TokenRequest(tokenEndpoint, clientAuth, codeGrant)

      IO(request.toHTTPRequest.send()).map(handleCallbackResponse)
    }

  private def oidcTry[A](t: => A): Either[ErrorResponse, A] =
    Either
      .fromTry(Try(t))
      .leftMap { err =>
        logger.error(s"Unexpected error in OIDC flow: ${err.getMessage}")
        ErrorResponse(500, err.getMessage)
      }
}
