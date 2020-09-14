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

import akka.event.Logging.{ErrorLevel, InfoLevel}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server.directives.{BasicDirectives, LogEntry}
import akka.http.scaladsl.server.{Directive0, ExceptionHandler}
import cats.effect.IO
import vinyldns.core.logging.RequestTracing

import scala.util.Try
import scala.util.control.NonFatal

/** Utilities for request logging  */
trait RequestLogging {
  import BasicDirectives._

  /** Extension class for HttpRequest. Adds utility methods. */
  implicit class HttpRequestExtension(req: HttpRequest) {

    /** Returns the request headers as a map. See [[HttpRequestExtension]] */
    def headerMap: Map[String, String] = req.headers.map(x => x.name() -> x.value()).toMap
  }

  /** Transforms an incoming request by adding an HTTP header with a tracking ID (if one is not already present).
    * Should be called before the request is handled by the routes.
    */
  def injectTrackingId: Directive0 = mapRequest { request: HttpRequest =>
    if (request.headers.exists(_.name.equalsIgnoreCase(RequestTracing.requestIdHeaderName))) request
    else request.addHeader(new VinylDnsRequestIdHeader(RequestTracing.generateTraceId))
  }

  /** A custom [[ExceptionHandler]] which logs the exception with the request context. */
  def loggingExceptionHandler: ExceptionHandler = ExceptionHandler {
    // $COVERAGE-OFF$
    case NonFatal(e) =>
      ctx => {
        val message = Option(e.getMessage).getOrElse("(No error message supplied)")
        val traceId = RequestTracing.extractTraceId(ctx.request.headerMap).getOrElse("(unknown)")

        val context = s"Exception during request - $message | trace.id=$traceId"
        ctx.log.error(e, context)
        ctx.request.discardEntityBytes(ctx.materializer)
        ctx.complete(InternalServerError)
      }
    // $COVERAGE-ON$
  }

  /** Performs request logging.  Should be used with the [[akka.http.scaladsl.server.directives.DebuggingDirectives.logRequestResult logRequestResult]] */
  def requestLogger(doNotLog: Seq[Uri.Path]): HttpRequest => (Any => Option[LogEntry]) = {
    req: HttpRequest =>
      {
        val startTime = System.currentTimeMillis()
        result: Any => {
          val endTime = System.currentTimeMillis()
          val duration = endTime - startTime
          if (doNotLog.contains(req.uri.path)) {
            None
          } else {
            result match {
              case rawResponse: HttpResponse =>
                Some(LogEntry(buildLogMessage(req, Some(rawResponse), duration), InfoLevel))
              case completeResult: Complete =>
                Some(
                  LogEntry(buildLogMessage(req, Some(completeResult.response), duration), InfoLevel)
                )
              case _: Rejected =>
                Some(LogEntry(buildLogMessage(req, None, duration), ErrorLevel))
              case x => // this can happen if sealRoute below cannot convert into a response.
                val res = HttpResponse(
                  status = StatusCodes.InternalServerError,
                  entity = HttpEntity(x.toString)
                )
                Some(LogEntry(buildLogMessage(req, Some(res), duration), ErrorLevel))
            }
          }
        }
      }
  }

  private[route] def buildLogMessage(
      request: HttpRequest,
      responseOption: Option[HttpResponse],
      duration: Long
  ): String = {
    val requestHeadersNoAuth = request.headers
      .filter(_.name.toLowerCase != "authorization")
      .map(h => s"${h.name}='${h.value}'")
      .mkString(", ")

    // rejections have their response entity discarded by default
    val response = responseOption match {
      case Some(res) =>
        val errorResponse = if (res.status.intValue() > 202) {
          s", entity=${res.entity}"
        } else ""
        s"Response: status_code=${res.status.intValue}$errorResponse"
      case None => ""
    }

    val traceId = RequestTracing.extractTraceId(request.headerMap).getOrElse("(unknown)")
    s"Request: method=${request.method.value}, path=${request.uri} | " +
      s"trace.id=$traceId | " +
      s"Headers: [$requestHeadersNoAuth] | " +
      s"$response | " +
      s"Duration: request_duration=$duration"
  }

  private[route] final class VinylDnsRequestIdHeader(requestId: IO[String])
      extends ModeledCustomHeader[VinylDnsRequestIdHeader] {
    override def renderInRequests = true
    override def renderInResponses = true
    override val companion = VinylDnsRequestIdHeader
    override def value: String = requestId.unsafeRunSync()
  }

  private[route] object VinylDnsRequestIdHeader
      extends ModeledCustomHeaderCompanion[VinylDnsRequestIdHeader] {
    override val name = RequestTracing.requestIdHeaderName
    override def parse(value: String) = Try(new VinylDnsRequestIdHeader(IO.pure(value)))
  }
}
