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
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import cats.data.Validated.{Invalid, Valid}
import cats.data.{EitherT, ValidatedNel}
import cats.effect.IO
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.route.Monitor

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.control.NonFatal

trait VinylDNSDirectives[E] extends Directives {

  val vinylDNSAuthenticator: VinylDNSAuthenticator

  def getRoutes: Route

  def authenticate: Directive1[AuthPrincipal] = extractRequestContext.flatMap { ctx =>
    extractStrictEntity(10.seconds).flatMap { strictEntity =>
      onSuccess(
        vinylDNSAuthenticator.authenticate(ctx, strictEntity.data.utf8String).unsafeToFuture()
      ).flatMap {
          case Right(authPrincipal) ⇒
            provide(authPrincipal)
          case Left(e) ⇒
            complete(handleAuthenticateError(e))
        }
    }
  }

  def handleAuthenticateError(error: VinylDNSAuthenticationError): HttpResponse =
    error match {
      case AccountLocked(err) =>
        HttpResponse(
          status = StatusCodes.Forbidden,
          entity = HttpEntity(s"Authentication Failed: $err")
        )
      case e =>
        HttpResponse(
          status = StatusCodes.Unauthorized,
          entity = HttpEntity(s"Authentication Failed: ${e.getMessage}")
        )
    }

  /* Adds monitoring to an Endpoint.  The name will be surfaced in JMX */
  def monitor(name: String): Directive0 =
    extractExecutionContext.flatMap { implicit ec ⇒
      BasicDirectives.mapInnerRoute { inner => ctx =>
        val startTime = System.currentTimeMillis()
        try {
          inner(ctx)
            .map { result =>
              record(getMonitor(name), startTime)(result)
              result
            }
            .recoverWith {
              case nf @ NonFatal(e) =>
                record(getMonitor(name), startTime)(Failure(e))
                ctx.fail(nf)
            }
        } catch {
          case nf @ NonFatal(e) =>
            record(getMonitor(name), startTime)(Failure(e))
            ctx.fail(nf)
        }
      }
    }

  // used to record stats about an http request / response
  def record(monitor: Monitor, startTime: Long): Any => Any = {
    case res: Complete =>
      monitor.capture(monitor.duration(startTime), res.response.status.intValue < 500)
      res

    case rej: Rejected =>
      monitor.capture(monitor.duration(startTime), success = true)
      rej

    case resp: HttpResponse =>
      monitor.capture(monitor.duration(startTime), resp.status.intValue < 500)
      resp

    case Failure(t) =>
      monitor.fail(monitor.duration(startTime))
      throw t

    case f: akka.actor.Status.Failure =>
      monitor.fail(monitor.duration(startTime))
      f

    case e: Throwable =>
      monitor.fail(monitor.duration(startTime))
      throw e

    case x =>
      x
  }

  private[route] def getMonitor(name: String) = Monitor(name)

  def ifValid[A](validation: => ValidatedNel[String, A]): Directive1[A] =
    validation match {
      case Valid(a) => provide(a)
      case Invalid(errors) =>
        reject(ValidationRejection(compact(render("errors" -> errors.toList.toSet))))
    }

  /**
    * Helpers to handle route authentication flow for routing. Implementing classes/objects
    * must provide handleErrors implementation.
    */
  def handleErrors(e: E): PartialFunction[E, Route]

  /**
    * Authenticate user and execute service call without request entity
    *
    * Flow:
    * - Authenticate user. Proceed if successful; otherwise return unauthorized error to user.
    * - Invoke service call, f, and return the response to the user.
    */
  def authenticateAndExecute[A](f: AuthPrincipal => EitherT[IO, E, A])(g: A => Route): Route =
    authenticate { authPrincipal =>
      onSuccess(f(authPrincipal).value.unsafeToFuture()) {
        case Right(a) => g(a)
        case Left(e) => handleErrors(e).applyOrElse(e, failWith)
      }
    }

  /**
    * Authenticate user and execute service call using request entity
    *
    * Flow:
    * - Authenticate user. Proceed if successful; otherwise return unauthorized error to user.
    * - Deserialize request entity into expected data structure. Proceed if successful; otherwise
    * return error to user.
    * - Invoke service call, f, and return the response to the user.
    */
  def authenticateAndExecuteWithEntity[A, B](
      f: (AuthPrincipal, B) => EitherT[IO, E, A]
  )(g: A => Route)(implicit um: FromRequestUnmarshaller[B]): Route =
    authenticate { authPrincipal =>
      entity(as[B]) { deserializedEntity =>
        onSuccess(f(authPrincipal, deserializedEntity).value.unsafeToFuture()) {
          case Right(a) => g(a)
          case Left(e) => handleErrors(e).applyOrElse(e, failWith)
        }
      }
    }

  private def failWith(error: E): StandardRoute = error match {
    case error: Throwable => StandardRoute(_.fail(error))
  }
}
