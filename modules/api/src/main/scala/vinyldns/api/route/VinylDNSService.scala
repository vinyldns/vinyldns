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

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`User-Agent`
import akka.http.scaladsl.server
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server.directives.LoggingMagnet
import akka.http.scaladsl.server.{Route, RouteResult}
import cats.effect.IO
import fs2.concurrent.SignallingRef
import io.prometheus.client.CollectorRegistry
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.api.domain.auth.MembershipAuthPrincipalProvider
import vinyldns.api.domain.batch.BatchChangeServiceAlgebra
import vinyldns.api.domain.membership.MembershipServiceAlgebra
import vinyldns.api.domain.record.RecordSetServiceAlgebra
import vinyldns.api.domain.zone.ZoneServiceAlgebra
import vinyldns.core.domain.membership.{MembershipRepository, UserRepository}
import vinyldns.core.health.HealthService

import scala.collection.JavaConverters._
import scala.util.matching.Regex

object VinylDNSService {

  val logger: Logger = LoggerFactory.getLogger(classOf[VinylDNSService])

  val ZoneIdRegex: Regex = "(?i)(/?zones/)(?:[0-9a-f]-?)+(.*)".r
  val ZoneAndRecordIdRegex: Regex =
    "(?i)(/?zones/)(?:[0-9a-f]-?)+(/recordsets/)(?:[0-9a-f]-?)+(.*)".r
  val ZoneRecordAndChangesRegex: Regex =
    "(?i)(/?zones/)(?:[0-9a-f]-?)+(/recordsets/)(?:[0-9a-f]-?)+(/changes/)(?:[0-9a-f]-?)+(.*)".r
  val TsigKeyRegex: Regex = """(?is)(.*?\"key\"\s*:\s*\")(?:.+?)(\".*)""".r

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

  def logMessage(
      req: HttpRequest,
      resOption: Option[HttpResponse],
      duration: Long): Map[String, Any] = {

    val headers = Map(
      "headers" ->
        req.headers
          .filter(_.name.toLowerCase != "authorization")
          .foldLeft(Map.empty[String, String])((z, h) => z + (h.name() -> h.value())))

    val ua = req.header[`User-Agent`].fold("-")(_.value())

    val emptyMap = Map.empty[String, Any]

    val resp = resOption match {
      case Some(res) if res.status.isSuccess() =>
        Map("status_code" -> res.status.intValue())
      case Some(res) =>
        val errResp: Map[String, Any] = Map(
          "body" -> Map("content" -> res.entity.withSizeLimit(1000L).toString),
          "status_code" -> res.status.intValue())
        errResp
      case None =>
        emptyMap
    }

    val url = Map("path" -> sanitizePath(req.uri)) ++
      req.uri.rawQueryString.fold(emptyMap)(q => Map("query" -> q))

    Map(
      "http" ->
        Map(
          "url" -> url,
          "request" -> (Map("method" -> req.method.value) ++ headers),
          "response" -> (Map("duration" -> duration) ++ resp),
          "user_agent" -> Map("original" -> ua),
          "version" -> req.protocol.value
        ))
  }

  def captureAccessLog(doNotLog: Seq[Uri.Path]): HttpRequest => RouteResult => Unit = {
    req: HttpRequest =>
      {
        val startTime = System.currentTimeMillis()
        result: RouteResult =>
          {
            if (!doNotLog.contains(req.uri.path)) {
              val endTime = System.currentTimeMillis()
              val duration = endTime - startTime
              val response = result match {
                case res: Complete =>
                  Some(res.response)
                case _: Rejected =>
                  Option.empty[HttpResponse]
                case x => // this can happen if sealRoute below cannot convert into a response.
                  Some(
                    HttpResponse(
                      status = StatusCodes.InternalServerError,
                      entity = HttpEntity(x.toString)))
              }

              val logEntries = logMessage(req, response, duration)
              response match {
                case Some(r) if r.status.isSuccess() =>
                  logger.info("request processed", StructuredArguments.entries(convert(logEntries)))
                case _ =>
                  logger.error("request failed", StructuredArguments.entries(convert(logEntries)))
              }
            } else {
              ()
            }
          }
      }
  }

  /**
    * Converts scala Map to Java Map required for structured logging library.
    *
    * @param m scala map
    * @return a java map
    */
  def convert(m: Map[_, _]): java.util.Map[_, _] =
    m.map { e =>
      e._2 match {
        case mm: Map[_, _] => e._1 -> convert(mm)
        case _ => e
      }
    }.asJava
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
    userRepository: UserRepository,
    membershipRepository: MembershipRepository)
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
  val authPrincipalProvider =
    new MembershipAuthPrincipalProvider(userRepository, membershipRepository)
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

  val unloggedUris: Seq[Path] = Seq(
    Uri.Path("/health"),
    Uri.Path("/color"),
    Uri.Path("/ping"),
    Uri.Path("/status"),
    Uri.Path("/metrics/prometheus"))

  val unloggedRoutes
    : Route = healthCheckRoute ~ pingRoute ~ colorRoute ~ statusRoute ~ prometheusRoute

  val vinyldnsRoutes: Route = logRequestResult(LoggingMagnet(_ =>
    VinylDNSService.captureAccessLog(unloggedUris)))(unloggedRoutes ~ authenticatedRoutes)

  val routes: Route = vinyldnsRoutes
}
// $COVERAGE-ON$
