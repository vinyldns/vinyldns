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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import cats.effect.IO
import fs2.concurrent.SignallingRef
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.api.Interfaces.{EitherImprovements, Result, ensuring}
import vinyldns.api.config.ServerConfig
import vinyldns.api.domain.zone.NotAuthorizedError
import vinyldns.core.domain.auth.AuthPrincipal

import scala.concurrent.duration._

final case class CurrentStatus(
    processingDisabled: Boolean,
    color: String,
    keyName: String,
    version: String
)

class StatusRoute(
  serverConfig: ServerConfig,
  val vinylDNSAuthenticator: VinylDNSAuthenticator,
  val processingDisabled: SignallingRef[IO, Boolean]
) extends VinylDNSJsonProtocol
  with VinylDNSDirectives[Throwable] {

  def getRoutes: Route = statusRoute

  implicit val timeout: Timeout = Timeout(10.seconds)

  def logger: Logger = LoggerFactory.getLogger(classOf[StatusRoute])

  def handleErrors(e: Throwable): PartialFunction[Throwable, Route] = {
    case NotAuthorizedError(msg) => complete(StatusCodes.Forbidden, msg)
  }

  def postStatus(isProcessingDisabled: Boolean, authPrincipal: AuthPrincipal): Result[Boolean] = {
    for {
      _ <- isAdmin(authPrincipal).toResult
      isDisabled = isProcessingDisabled
    } yield isDisabled
  }

  def isAdmin(authPrincipal: AuthPrincipal): Either[Throwable, Unit] =
    ensuring(NotAuthorizedError(s"Not authorized. User '${authPrincipal.signedInUser.userName}' cannot make the requested change.")) {
      authPrincipal.isSystemAdmin
    }

  val statusRoute: Route =
    (get & path("status")) {
      onSuccess(processingDisabled.get.unsafeToFuture()) { isProcessingDisabled =>
        complete(
          StatusCodes.OK,
          CurrentStatus(
            isProcessingDisabled,
            serverConfig.color,
            serverConfig.keyName,
            serverConfig.version
          )
        )
      }
    } ~
      (post & path("status")) {
        parameters("processingDisabled".as[Boolean]) { isProcessingDisabled =>
          authenticateAndExecute(postStatus(isProcessingDisabled, _)){ isProcessingDisabled =>
            onSuccess(processingDisabled.set(isProcessingDisabled).unsafeToFuture()) {
              complete(
                StatusCodes.OK,
                CurrentStatus(
                  isProcessingDisabled,
                  serverConfig.color,
                  serverConfig.keyName,
                  serverConfig.version
                )
              )
            }
          }
        }
      }
}
