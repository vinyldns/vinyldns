package vinyldns.dns

import cats.effect.IO
import vinyldns.core.domain.backend.{BackendConfig, BackendConnection, Backend}
import vinyldns.core.domain.zone.Zone

class DnsBackend extends Backend {

  /**
   * Loads a backend based on the provided config so that it is ready to use
   *
   * @param config The BackendConfig, has settings that are specific to this backend
   * @return A ready-to-use Backend instance, or does an IO.raiseError if something bad occurred.
   */
  def load(config: BackendConfig): IO[BackendConnection] = ???

  /**
   * Given a zone, returns a connection to the zone, returns None if cannot connect
   *
   * @param zone The zone to attempt to connect to
   * @return A backend that is usable, or None if it could not connect
   */
  def connect(zone: Zone): Option[BackendConnection] = ???

  /**
   * Given a backend id, looks up the backend for this provider if it exists
   *
   * @return A backend that is usable, or None if could not connect
   */
  def connectById(backendId: String): Option[BackendConnection] = ???

  /**
   * @return The backend ids loaded with this provider
   */
  def ids: List[String] = ???
}
