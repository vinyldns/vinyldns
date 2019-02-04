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

import java.math.BigInteger
import java.security.spec.RSAPublicKeySpec
import java.security.{KeyFactory, PublicKey}

import controllers.VinylDNS.UserDetails
import javax.inject.{Inject, Singleton}
import org.apache.commons.codec.binary.Base64
import pdi.jwt.{Jwt, JwtAlgorithm}
import play.api.Configuration
import play.api.libs.json.{JsPath, Json, JsonConfiguration, Reads}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.libs.json.JsonNaming.SnakeCase
import scodec.bits.ByteVector

import scala.concurrent.{ExecutionContext, Future}

object OidcAuthenticator {
  case class OidcConfig(
      authorizationEndpoint: String,
      tokenEndpoint: String,
      jwksEndpoint: String,
      clientId: String,
      secret: String,
      jwtUsernameField: String,
      jwtEmailField: Option[String],
      jwtFirstNameField: Option[String],
      jwtLastNameField: Option[String])

  case class OidcResponse(
      accessToken: String,
      tokenType: String,
      expiresIn: Int,
      scope: String,
      idToken: String)

  case class OidcUserDetails(
      username: String,
      email: Option[String],
      firstName: Option[String],
      lastName: Option[String])
      extends UserDetails

  case class OidcIdTokenHeader(
      typ: String,
      alg: String,
      kid: String
  )

  case class JwksKey(
      kty: String,
      use: String,
      kid: String,
      x5t: String,
      n: String,
      e: String,
      x5c: Seq[String]
  )

  case class JwksResponse(
      keys: Seq[JwksKey]
  )

  implicit val oidcIdTokenHeaderReads = Json.reads[OidcIdTokenHeader]
  implicit val jwksKeyReads = Json.reads[JwksKey]
  implicit val jwksResponseReads = Json.reads[JwksResponse]

  def getKidFromToken(oidcResponse: OidcResponse): String = {
    val header = oidcResponse.idToken.split("\\.")(0)
    val decoded = new String(ByteVector.fromBase64(header).get.toArray, "UTF-8").trim()
    Json.parse(decoded).as[OidcIdTokenHeader].kid
  }

  def getJwksKeyFromKid(kid: String, jwksResponse: JwksResponse): Option[JwksKey] =
    jwksResponse.keys.find(k => k.kid.equals(kid))

  def jwksKeyToPublicKey(jwksKey: JwksKey): PublicKey = {
    val keyType = jwksKey.kty
    val modulusBase64 = jwksKey.n
    val exponentBase64 = jwksKey.e
    val modulus = new BigInteger(1, Base64.decodeBase64(modulusBase64))
    val exponent = new BigInteger(1, Base64.decodeBase64(exponentBase64))
    val kf = KeyFactory.getInstance(keyType)
    kf.generatePublic(new RSAPublicKeySpec(modulus, exponent))
  }
}
@Singleton
class OidcAuthenticator @Inject()(wsClient: WSClient, configuration: Configuration) {

  import OidcAuthenticator._

  lazy val oidcInfo: OidcConfig = pureconfig.loadConfigOrThrow[OidcConfig]("oidc")
  implicit lazy val oidcUserReads: Reads[OidcUserDetails] =
    (JsPath \ oidcInfo.jwtUsernameField)
      .read[String]
      .and((JsPath \ oidcInfo.jwtEmailField.getOrElse("email")).readNullable[String])
      .and((JsPath \ oidcInfo.jwtFirstNameField.getOrElse("firstname")).readNullable[String])
      .and((JsPath \ oidcInfo.jwtLastNameField.getOrElse("lastname")).readNullable[String])(
        OidcUserDetails.apply _)

  val oidcScope = "openid profile email"
  val grantType = "authorization_code"

  implicit val jsonConfig = JsonConfiguration(SnakeCase)
  implicit val oidcReads: Reads[OidcResponse] = Json.reads[OidcResponse]

  def oidcGetCode(redirectUri: String): WSRequest = {
    // TODO here should 1st check if the user info is already in session (and not expired and such)

    val responseType = "code"

    val queryParams = (
      "client_id" -> oidcInfo.clientId,
      "response_type" -> responseType,
      "redirect_uri" -> redirectUri,
      "scope" -> "openid profile email")

    val call = wsClient
      .url(oidcInfo.authorizationEndpoint)
      .withQueryStringParameters(
        "client_id" -> oidcInfo.clientId,
        "response_type" -> responseType,
        "redirect_uri" -> redirectUri,
        "scope" -> "openid profile email")

    (call.url, call.queryString)
    call
  }

  def oidcCallback(code: String, redirectUri: String)(
      implicit executionContext: ExecutionContext): Future[OidcUserDetails] = {

    // TODO error handling here
    val tokenResponse =
      wsClient
        .url(oidcInfo.tokenEndpoint)
        .post(
          Map(
            "client_id" -> Seq(oidcInfo.clientId),
            "grant_type" -> Seq(grantType),
            "redirect_uri" -> Seq(redirectUri),
            "scope" -> Seq(oidcScope),
            "code" -> Seq(code),
            "client_secret" -> Seq(oidcInfo.secret)
          )
        )
        .map { resp =>
          resp.json.as[OidcResponse]
        }

    val keyStoreResponse =
      wsClient
        .url(oidcInfo.jwksEndpoint)
        .get()
        .map { resp =>
          resp.json.as[JwksResponse]
        }

    for {
      token <- tokenResponse
      jwksKeys <- keyStoreResponse
      kid = getKidFromToken(token)
      jwksKey = getJwksKeyFromKid(kid, jwksKeys).get
      publicKey = jwksKeyToPublicKey(jwksKey)
      idDecoded = Jwt.decodeRawAll(token.idToken, publicKey, Seq(JwtAlgorithm.RS256)).get
    } yield Json.parse(idDecoded._2).as[OidcUserDetails]
  }
}
