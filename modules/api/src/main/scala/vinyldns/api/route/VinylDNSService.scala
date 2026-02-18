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

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import cats.data.EitherT
import cats.effect.IO
import fs2.concurrent.SignallingRef
import io.prometheus.client.CollectorRegistry
import org.json4s.MappingException
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.api.config.{LimitsConfig, RuntimeVinylDNSConfig}
import vinyldns.api.domain.auth.AuthPrincipalProvider
import vinyldns.api.domain.batch.BatchChangeServiceAlgebra
import vinyldns.api.domain.membership.MembershipServiceAlgebra
import vinyldns.api.domain.record.RecordSetServiceAlgebra
import vinyldns.api.domain.zone.{InvalidRequest, NotAuthorizedError, PendingUpdateError, ZoneServiceAlgebra}
import vinyldns.core.health.HealthService

object VinylDNSService {

  import akka.http.scaladsl.server.Directives._

  implicit def validationRejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case MalformedRequestContentRejection(msg, MappingException(_, _)) =>
          complete(
            HttpResponse(
              status = StatusCodes.BadRequest,
              entity = HttpEntity(ContentTypes.`application/json`, msg)
            )
          )
      }
      .handleNotFound {
        extractUnmatchedPath { p =>
          complete((StatusCodes.NotFound, s"The requested path [$p] does not exist."))
        }
      }
      .result()
}

// $COVERAGE-OFF$
class VinylDNSService(
    val membershipService: MembershipServiceAlgebra,
    val limits: LimitsConfig,
    val processingDisabled: SignallingRef[IO, Boolean],
    val zoneService: ZoneServiceAlgebra,
    val healthService: HealthService,
    val recordSetService: RecordSetServiceAlgebra,
    val batchChangeService: BatchChangeServiceAlgebra,
    val collectorRegistry: CollectorRegistry,
    authPrincipalProvider: AuthPrincipalProvider
) extends PingRoute
    with HealthCheckRoute
    with BlueGreenRoute
    with PrometheusRoute
    with VinylDNSJsonProtocol
    with RequestLogging
    with VinylDNSDirectives[Throwable] {

  import VinylDNSService.validationRejectionHandler

  override def logger: Logger = LoggerFactory.getLogger(classOf[VinylDNSService])

  override def handleErrors(e: Throwable): PartialFunction[Throwable, Route] = {
    case e: NotAuthorizedError => complete(StatusCodes.Forbidden, e.msg)
    case e: InvalidRequest => complete(StatusCodes.UnprocessableEntity, e.msg)
    case e: PendingUpdateError => complete(StatusCodes.Conflict, e.msg)
    case ex: Throwable => complete(StatusCodes.InternalServerError, ex.getMessage)
  }

  val aws4Authenticator = new Aws4Authenticator
  val vinylDNSAuthenticator: VinylDNSAuthenticator =
    new ProductionVinylDNSAuthenticator(
      aws4Authenticator,
      authPrincipalProvider,
      RuntimeVinylDNSConfig.currentIO.map(_.crypto).unsafeRunSync()
    )

  val zoneRoute: Route =
    new ZoneRoute(zoneService, limits, vinylDNSAuthenticator, RuntimeVinylDNSConfig.currentIO.map(_.crypto).unsafeRunSync()).getRoutes
  val recordSetRoute: Route =
    new RecordSetRoute(recordSetService, limits, vinylDNSAuthenticator).getRoutes
  val membershipRoute: Route =
    new MembershipRoute(membershipService, limits, vinylDNSAuthenticator).getRoutes
  val batchChangeRoute: Route =
    new BatchChangeRoute(
      batchChangeService,
      limits,
      vinylDNSAuthenticator,
      RuntimeVinylDNSConfig.currentIO.map(_.manualReviewConfig).unsafeRunSync()
    ).getRoutes
  val statusRoute: Route =
    new StatusRoute(
      RuntimeVinylDNSConfig.currentIO.map(_.serverConfig).unsafeRunSync(),
      vinylDNSAuthenticator,
      processingDisabled
    ).getRoutes

  val unloggedUris = Seq(
    Uri.Path("/health"),
    Uri.Path("/color"),
    Uri.Path("/ping"),
    Uri.Path("/metrics/prometheus")
  )

  private val reloadConfigRoute: Route =
    path("config" / "reload") {
      (post & monitor("Endpoint.reloadConfig")) {
        authenticateAndExecute { authPrincipal =>
          if (authPrincipal.isSuper) {
            logger.warn(s"User ${authPrincipal.signedInUser.userName} attempted to reload config without permission")
            EitherT.leftT[IO, Unit](
              NotAuthorizedError(s"User ${authPrincipal.signedInUser.userName} is not authorized to reload the application config"))
          } else {
            EitherT.liftF(
              for {
                _ <- IO {logger.info(s"User ${authPrincipal.signedInUser.userName} triggered configuration reload")}
                _ <- RuntimeVinylDNSConfig.reload()
                _ <- IO {logger.info("Application configuration reloaded successfully")}
              } yield ()
            )
          }
        } { _ => complete(StatusCodes.OK, "Application configuration reloaded successfully")}
      }
    }

  val unloggedRoutes: Route = healthCheckRoute ~ pingRoute ~ colorRoute(
    RuntimeVinylDNSConfig.currentIO.map(_.serverConfig.color).unsafeRunSync()
  ) ~ prometheusRoute

  val allRoutes: Route = unloggedRoutes ~
    batchChangeRoute ~
    zoneRoute ~
    recordSetRoute ~
    membershipRoute ~
    statusRoute ~
    reloadConfigRoute

  val vinyldnsRoutes: Route = logRequestResult(requestLogger(unloggedUris))(allRoutes)

  val routes: Route =
    injectTrackingId {
      handleExceptions(loggingExceptionHandler) {
        handleRejections(validationRejectionHandler)(vinyldnsRoutes)
      }
    }

  override def getRoutes: Route =
    reloadConfigRoute ~
      unloggedRoutes ~
      zoneRoute ~
      recordSetRoute ~
      membershipRoute ~
      batchChangeRoute ~
      statusRoute
}
// $COVERAGE-ON$
