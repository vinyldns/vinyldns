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

import akka.event.Logging._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server.directives.LogEntry
import cats.effect.IO
import fs2.concurrent.SignallingRef
import io.prometheus.client.CollectorRegistry
import vinyldns.api.domain.auth.AuthPrincipalProvider
import vinyldns.api.domain.batch.BatchChangeServiceAlgebra
import vinyldns.api.domain.membership.MembershipServiceAlgebra
import vinyldns.api.domain.record.RecordSetServiceAlgebra
import vinyldns.api.domain.zone.ZoneServiceAlgebra
import vinyldns.core.health.HealthService

import scala.util.matching.Regex

object VinylDNSService {
  val ZoneIdRegex: Regex = "(?i)(/?zones/)(?:[0-9a-f]-?)+(.*)".r
  val ZoneAndRecordIdRegex: Regex =
    "(?i)(/?zones/)(?:[0-9a-f]-?)+(/recordsets/)(?:[0-9a-f]-?)+(.*)".r
  val ZoneRecordAndChangesRegex: Regex =
    "(?i)(/?zones/)(?:[0-9a-f]-?)+(/recordsets/)(?:[0-9a-f]-?)+(/changes/)(?:[0-9a-f]-?)+(.*)".r
  val TsigKeyRegex: Regex = """(?is)(.*?\"key\"\s*:\s*\")(?:.+?)(\".*)""".r

  def logMessage(req: HttpRequest, resOption: Option[HttpResponse], duration: Long): String = {
    val requestHeadersNoAuth = req.headers
      .filter(_.name.toLowerCase != "authorization")
      .map(h => s"${h.name}='${h.value}'")
      .mkString(", ")
    // rejections have their response entity discarded by default
    val response =
      resOption match {
        case Some(res) =>
          val errorResponse = if (res.status.intValue() > 202) {
            s", entity=${res.entity}"
          } else ""
          s"Response: status_code=${res.status.intValue}$errorResponse"
        case None => ""
      }
    Seq(
      s"Headers: [$requestHeadersNoAuth]",
      s"Request: protocol=${req.protocol.value}, method=${req.method.value}, path=${sanitizePath(req.uri)}",
      s"$response",
      s"Duration: request_duration=$duration"
    ).mkString(" | ")
  }

  def sanitizePath(uri: Uri): String = {
    val p = uri.path.toString()
    p match {
      case ZoneRecordAndChangesRegex(z, rs, c, rest) =>
        s"$z<zone id>$rs<record set id>$c<change id>$rest"
      case ZoneAndRecordIdRegex(z, rs, rest) => s"$z<zone id>$rs<record set id>$rest"
      case ZoneIdRegex(z, rest) => s"$z<zone id>$rest"
      case _ => p
    }
  }

  def buildLogEntry(doNotLog: Seq[Uri.Path]): HttpRequest => Any => Option[LogEntry] = {
    req: HttpRequest =>
      {
        val startTime = System.currentTimeMillis()
        r: Any =>
          {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            doNotLog.contains(req.uri.path) match {
              case false => {
                r match {
                  case res: HttpResponse =>
                    Some(LogEntry(VinylDNSService.logMessage(req, Some(res), duration), InfoLevel))
                  case res: Complete =>
                    Some(
                      LogEntry(
                        VinylDNSService.logMessage(req, Some(res.response), duration),
                        InfoLevel))
                  case _: Rejected =>
                    Some(LogEntry(VinylDNSService.logMessage(req, None, duration), ErrorLevel))
                  case x => // this can happen if sealRoute below cannot convert into a response.
                    val res = HttpResponse(
                      status = StatusCodes.InternalServerError,
                      entity = HttpEntity(x.toString))
                    Some(LogEntry(VinylDNSService.logMessage(req, Some(res), duration), ErrorLevel))
                }
              }
              case true => None
            }
          }
      }
  }
}

// $COVERAGE-OFF$
class VinylDNSService(
    val membershipService: MembershipServiceAlgebra,
    val processingDisabled: SignallingRef[IO, Boolean],
    val zoneService: ZoneServiceAlgebra,
    val healthService: HealthService,
    val recordSetService: RecordSetServiceAlgebra,
    val batchChangeService: BatchChangeServiceAlgebra,
    val collectorRegistry: CollectorRegistry,
    authPrincipalProvider: AuthPrincipalProvider)
    extends VinylDNSDirectives
    with PingRoute
    with ZoneRoute
    with RecordSetRoute
    with HealthCheckRoute
    with BlueGreenRoute
    with MembershipRoute
    with StatusRoute
    with PrometheusRoute
    with BatchChangeRoute
    with VinylDNSJsonProtocol
    with JsonValidationRejection {

  val aws4Authenticator = new Aws4Authenticator
  val vinylDNSAuthenticator: VinylDNSAuthenticator =
    new ProductionVinylDNSAuthenticator(aws4Authenticator, authPrincipalProvider)

  // Authenticated routes must go first
  def authenticatedRoutes: server.Route =
    handleRejections(validationRejectionHandler)(authenticate { authPrincipal =>
      batchChangeRoute(authPrincipal) ~
        zoneRoute(authPrincipal) ~
        recordSetRoute(authPrincipal) ~
        membershipRoute(authPrincipal)
    })

  val unloggedUris = Seq(
    Uri.Path("/health"),
    Uri.Path("/color"),
    Uri.Path("/ping"),
    Uri.Path("/status"),
    Uri.Path("/metrics/prometheus"))
  val unloggedRoutes
    : Route = healthCheckRoute ~ pingRoute ~ colorRoute ~ statusRoute ~ prometheusRoute
  val vinyldnsRoutes: Route =
    logRequestResult(VinylDNSService.buildLogEntry(unloggedUris))(
      unloggedRoutes ~ authenticatedRoutes)
  val routes = vinyldnsRoutes
}
// $COVERAGE-ON$
