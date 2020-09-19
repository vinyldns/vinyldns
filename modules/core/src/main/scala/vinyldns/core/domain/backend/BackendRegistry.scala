package vinyldns.core.domain.backend

import cats.implicits._
import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import vinyldns.core.domain.zone.Zone
import vinyldns.core.health.HealthCheck
import vinyldns.core.health.HealthCheck.HealthCheck

/**
 * Provides the means to discover backends for zones
 */
trait BackendRegistry {

  /**
   * Attempts to get the backend for a given zone, returns `None` if not found
   * @param zone A `Zone` to get a backend for
   * @return A working `Backend`, or `None` if the backend could not be found for this zone
   */
  def backendForZone(zone: Zone): BackendConnection

  /**
   * Performs whatever health check considered necessary to ensure that the backends are in good health
   *
   * @param timeout Timeout in seconds to wait before raising an error
   *
   * @return A HealthCheck that can be run to determine the health of the registered backends
   */
  def healthCheck(timeout: Int): HealthCheck

  /**
   * Determines if a given backend id is registered
   *
   * @param backendId The id to lookup
   *
   * @return true if it is registered; false otherwise
   */
  def isRegistered(backendId: String): Boolean

  /**
   * @return All of the backend ids registered
   */
  def ids: NonEmptyList[String]
}
object BackendRegistry {
  def apply(configs: BackendConfigs)(implicit cs: ContextShift[IO]): IO[BackendRegistry] = {
    for {
      backends <- BackendLoader.load(configs.backends)
      defaultConn <- IO.fromOption(
        backends.collectFirstSome(_.connectById(configs.defaultBackendId))
      )(
        new RuntimeException(
          s"Unable to find default backend for configured id '${configs.defaultBackendId}''"
        )
      )
    } yield new BackendRegistry {

      /**
       * Attempts to get the backend for a given zone, returns `None` if not found
       *
       * @param zone A `Zone` to get a backend for
       * @return A working `Backend`, or `None` if the backend could not be found for this zone
       */
      def backendForZone(zone: Zone): BackendConnection =
        backends.collectFirstSome(_.connect(zone)).getOrElse(defaultConn)

      /**
       * Performs whatever health check considered necessary to ensure that the backends are in good health
       *
       * @param timeout Timeout in seconds to wait before raising an error
       * @return A HealthCheck that can be run to determine the health of the registered backends
       */
      def healthCheck(timeout: Int): HealthCheck =
        IO.pure(().asRight[HealthCheck.HealthCheckError])

      /**
       * Determines if a given backend id is registered
       *
       * @param backendId The id to lookup
       * @return true if it is registered; false otherwise
       */
      def isRegistered(backendId: String): Boolean =
        backends.collectFirstSome(_.connectById(backendId)).isDefined

      /**
       * @return All of the backend ids registered
       */
      val ids: NonEmptyList[String] = NonEmptyList(defaultConn.id, backends.toList.flatMap(_.ids)).distinct
    }
  }
}
