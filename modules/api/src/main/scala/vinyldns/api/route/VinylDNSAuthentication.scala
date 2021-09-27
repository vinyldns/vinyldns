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

package vinyldns.api.route

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.RequestContext
import cats.effect._
import vinyldns.api.domain.auth.AuthPrincipalProvider
import vinyldns.core.Messages._
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.LockStatus
import vinyldns.core.route.Monitored

import scala.util.matching.Regex

sealed abstract class VinylDNSAuthenticationError(msg: String) extends Throwable(msg)
final case class AuthMissing(msg: String) extends VinylDNSAuthenticationError(msg)
final case class AuthRejected(reason: String) extends VinylDNSAuthenticationError(reason)
final case class AccountLocked(reason: String) extends VinylDNSAuthenticationError(reason)

trait VinylDNSAuthenticator {
  def authenticate(
      ctx: RequestContext,
      content: String
  ): IO[Either[VinylDNSAuthenticationError, AuthPrincipal]]
}

class ProductionVinylDNSAuthenticator(
    val authenticator: Aws4Authenticator,
    val authPrincipalProvider: AuthPrincipalProvider,
    val crypto: CryptoAlgebra
) extends VinylDNSAuthenticator
    with Monitored {

  def authenticate(
      ctx: RequestContext,
      content: String
  ): IO[Either[VinylDNSAuthenticationError, AuthPrincipal]] =
    // Need to refactor getAuthPrincipal to be an IO[Either[E, A]] instead of how it is implemented.
    getAuthPrincipal(ctx, content, crypto).attempt.flatMap {
      case Left(e: VinylDNSAuthenticationError) => IO.pure(Left(e))
      case Right(ok) => IO.pure(Right(ok))
      case Left(e) => IO.raiseError(e)
    }

  /**
    * Gets the auth header from the request.  If the auth header is not found then the
    * AuthMissing is thrown, which yields a CredentialsMissing
    *
    * @return A Future containing the value of the auth header
    */
  def getAuthHeader(ctx: RequestContext): IO[String] =
    ctx.request.headers
      .find { header =>
        header.name.compareToIgnoreCase("Authorization") == 0
      }
      .map(header => IO.pure(header.value))
      .getOrElse(IO.raiseError(AuthMissing(AuthMissingErrorMsg)))

  /**
    * Parses the auth header into an Aws Regex.Match.  If the auth header cannot be parsed, an
    * AuthRejected is thrown which will result in a CredentialsRejected
    *
    * @return A Future containing a Regex.Match on the auth header
    */
  def parseAuthHeader(header: String): IO[Regex.Match] =
    Aws4Authenticator
      .parseAuthHeader(header)
      .map(IO.pure)
      .getOrElse(IO.raiseError(AuthRejected(AuthRejectedErrorMsg)))

  /**
    * Gets the access key from the request.  Normalizes the exceptions coming out of the authenticator
    *
    * @return A Future with the access key in the Authorization Header
    */
  def getAccessKey(header: String): IO[String] =
    IO(authenticator.extractAccessKey(header))
      .handleErrorWith {
        case mt: MissingAuthenticationTokenException =>
          IO.raiseError(AuthMissing(mt.msg))
        case e: Throwable =>
          IO.raiseError(AuthRejected(e.getMessage))
      }

  /**
    * Validates the signature on the request
    *
    * @return Successful future if ok; Failure with an AuthRejected otherwise
    */
  def validateRequestSignature(
      req: HttpRequest,
      secretKey: String,
      authHeaderRegex: Regex.Match,
      content: String
  ): IO[Unit] =
    authHeaderRegex match {
      case auth if authenticator.authenticateReq(req, auth.subgroups, secretKey, content) =>
        IO.unit
      case _ =>
        IO.raiseError(AuthRejected(RequestSignatureErrorMsg))
    }

  /**
    * Authenticates the request:
    * - gets the Authorization Http Header from the request
    * - parse the Http Header into a RegEx
    * - extracts the access key from the Authorization Http Header
    * - looks up the account based on the access key
    * - validates the signature of the request
    * - looks up the authorized accounts for the signed in user
    * - builds the auth principal
    *
    * If any validations fail that we expect, will yield a Failure with an AuthMissing or AuthRejected; otherwise
    * unanticipated exceptions will simply bubble out and result as 500s or 503s
    *
    * @param ctx The Http Request Context
    * @return A Future containing the AuthPrincipal for the request.
    */
  def getAuthPrincipal(
      ctx: RequestContext,
      content: String,
      crypto: CryptoAlgebra
  ): IO[AuthPrincipal] =
    for {
      authHeader <- getAuthHeader(ctx)
      regexMatch <- parseAuthHeader(authHeader)
      accessKey <- getAccessKey(authHeader)
      authPrincipal <- getAuthPrincipal(accessKey)
      _ <- validateRequestSignature(
        ctx.request,
        decryptSecret(authPrincipal.secretKey, crypto),
        regexMatch,
        content
      )
    } yield authPrincipal

  def decryptSecret(str: String, crypto: CryptoAlgebra): String =
    crypto.decrypt(str)

  def getAuthPrincipal(accessKey: String): IO[AuthPrincipal] =
    authPrincipalProvider.getAuthPrincipal(accessKey).flatMap {
      case Some(ok) =>
        if (ok.signedInUser.lockStatus == LockStatus.Locked) {
          IO.raiseError(
            AccountLocked(AccountLockedErrorMsg.format(ok.signedInUser.userName))
          )
        } else IO.pure(ok)
      case None =>
        IO.raiseError(AuthRejected(AccountAccessKeyErrorMsg.format(accessKey)))
    }
}
