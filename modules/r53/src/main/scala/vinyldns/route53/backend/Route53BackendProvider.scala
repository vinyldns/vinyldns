package vinyldns.route53.backend

import cats.effect.{ContextShift, IO}
import vinyldns.core.domain.backend.{Backend, BackendConfig, BackendProvider}

class Route53BackendProvider extends BackendProvider {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  /**
   * Loads a backend based on the provided config so that it is ready to use
   * This is internally used typically during startup
   *
   * @param config The BackendConfig, has settings that are specific to this backend
   * @return A ready-to-use Backend instance, or does an IO.raiseError if something bad occurred.
   */
  def load(config: BackendConfig): IO[Backend] = ???
}
