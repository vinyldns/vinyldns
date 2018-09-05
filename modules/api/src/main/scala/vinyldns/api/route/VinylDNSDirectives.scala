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

import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.AuthenticationFailedRejection.Cause
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives
import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.effect._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import vinyldns.core.domain.auth.AuthPrincipal

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.control.NonFatal

trait VinylDNSDirectives extends Directives {

  /**
    * Authenticator that takes a request context and yields an Authentication, which is an Either
    * that holds a Left - Rejection, or Right - AuthPrincipal.
    * @return an Authentication with the AuthPrincipal as looked up from the request, or a Left(Rejection)
    */
  def vinyldnsAuthenticator(
      ctx: RequestContext,
      content: String): IO[Either[Cause, AuthPrincipal]] =
    VinylDNSAuthenticator(ctx, content)

  def authenticate: Directive1[AuthPrincipal] =
    extractExecutionContext.flatMap { implicit ec ⇒
      extractRequestContext.flatMap { ctx =>
        extractStrictEntity(10.seconds).flatMap { strictEntity =>
          onSuccess(vinyldnsAuthenticator(ctx, strictEntity.data.utf8String).unsafeToFuture())
            .flatMap {
              case Right(authPrincipal) ⇒
                provide(authPrincipal)
              case Left(cause) ⇒
                // we need to finish the result, rejections will proceed and ultimately
                // we can fail with a different rejection
                complete(
                  HttpResponse(
                    status = StatusCodes.Unauthorized,
                    entity = HttpEntity(s"Authentication Failed: $cause")
                  ))
            }
        }
      }
    }

  /* Adds monitoring to an Endpoint.  The name will be surfaced in JMX */
  def monitor(name: String): Directive0 =
    extractExecutionContext.flatMap { implicit ec ⇒
      BasicDirectives.mapInnerRoute { inner => ctx =>
        val startTime = System.currentTimeMillis()
        try {
          inner(ctx)
            .map { result =>
              getMonitor(name).record(startTime)(result)
              result
            }
            .recoverWith {
              case nf @ NonFatal(e) =>
                getMonitor(name).record(startTime)(Failure(e))
                ctx.fail(nf)
            }
        } catch {
          case nf @ NonFatal(e) =>
            getMonitor(name).record(startTime)(Failure(e))
            ctx.fail(nf)
        }
      }
    }

  private[route] def getMonitor(name: String) = Monitor(name)

  def ifValid[A](validation: => ValidatedNel[String, A]): Directive1[A] =
    validation match {
      case Valid(a) => provide(a)
      case Invalid(errors) =>
        reject(ValidationRejection(compact(render("errors" -> errors.toList.toSet))))
    }
}
