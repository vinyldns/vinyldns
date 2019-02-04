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

import controllers.VinylDNS.UserDetails
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.{JsPath, Json, JsonConfiguration, Reads}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.mvc._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.libs.json.JsonNaming.SnakeCase
import scodec.bits.ByteVector

import scala.concurrent.{ExecutionContext, Future}

object OidcAuthenticator {
  case class OidcConfig(
      authorizationEndpoint: String,
      tokenEndpoint: String,
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
      implicit executionContext: ExecutionContext): Future[OidcUserDetails] =
    // TODO error handling here
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
        val response = resp.json.as[OidcResponse]

        //TODO need to be validating these tokens
        val idDecoded = new String(
          ByteVector.fromBase64(response.idToken.split("\\.")(1)).get.toArray,
          "UTF-8").trim

        Json.parse(idDecoded).as[OidcUserDetails]
      }

}
