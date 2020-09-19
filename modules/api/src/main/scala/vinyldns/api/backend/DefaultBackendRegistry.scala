package vinyldns.api.backend

import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import vinyldns.core.domain.backend.{BackendConnection, BackendLoader, Backend, BackendRegistry}
import vinyldns.core.domain.zone.Zone
import vinyldns.core.health.HealthCheck
import vinyldns.core.health.HealthCheck.HealthCheck

class DefaultBackendRegistry(
    defaultConn: BackendConnection,
    backendProviders: NonEmptyList[Backend]
) extends BackendRegistry {

  val ids: NonEmptyList[String] = NonEmptyList(defaultConn.id, backendProviders.toList.flatMap(_.ids)).distinct

  /**
    * Attempts to get the backend for a given zone, returns `None` if not found
    *
    * @param zone A `Zone` to get a backend for
    * @return A working `Backend`, or `None` if the backend could not be found for this zone
    */
  def backendForZone(zone: Zone): BackendConnection =
    backendProviders.collectFirstSome(_.connect(zone)).getOrElse(defaultConn)

  /**
   * Performs whatever health check considered necessary to ensure that the backends are in good health
   *
   * TODO: Implement sane health check here, possibly check the default connection
   *
   * @param timeout Timeout in seconds to wait before raising an error
   * @return A HealthCheck that can be run to determine the health of the registered backends
   */
  def healthCheck(timeout: Int): HealthCheck = IO.pure(().asRight[HealthCheck.HealthCheckError])

  /**
   * Determines if a given backend id is registered
   *
   * @param backendId The id to lookup
   * @return true if it is registered; false otherwise
   */
  def isRegistered(backendId: String): Boolean = backendProviders.collectFirstSome(_.connectById(backendId)).isDefined
}

object DefaultBackendRegistry {
  def apply(apiBackendConfig: ApiBackendConfig): IO[BackendRegistry] =
    for {
      backends <- BackendLoader.load(apiBackendConfig.backends)
      defaultConn <- IO.fromOption(
        backends.collectFirstSome(_.connectById(apiBackendConfig.defaultBackendId))
      )(
        new RuntimeException(
          s"Unable to find default backend for configured id '${apiBackendConfig.defaultBackendId}''"
        )
      )
    } yield new DefaultBackendRegistry(defaultConn, backends)
}
