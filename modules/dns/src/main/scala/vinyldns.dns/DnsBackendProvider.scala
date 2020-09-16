package vinyldns.dns

import cats.effect.IO
import vinyldns.core.domain.backend.{Backend, BackendConfig, BackendProvider}

class DnsBackendProvider extends BackendProvider {
  /**
   * Loads a backend based on the provided config so that it is ready to use
   *
   * @param config The BackendConfig, has settings that are specific to this backend
   * @return A ready-to-use Backend instance, or does an IO.raiseError if something bad occurred.
   */
  def load(config: BackendConfig): IO[Backend] = ???
}
