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

package vinyldns.api.backend.dns

import cats.effect.IO
import org.slf4j.LoggerFactory
import vinyldns.api.VinylDNSConfig
import vinyldns.api.crypto.Crypto
import vinyldns.core.domain.backend.{Backend, BackendConfig, BackendProvider}

class DnsBackendProvider extends BackendProvider {

  private val logger = LoggerFactory.getLogger(classOf[DnsBackendProvider])

  /**
    * Loads a backend based on the provided config so that it is ready to use
    * This is internally used typically during startup
    *
    * @param config The BackendConfig, has settings that are specific to this backend
    * @return A ready-to-use Backend instance, or does an IO.raiseError if something bad occurred.
    */
  def load(config: BackendConfig): IO[Backend] = {
    // TODO: Migrate the YAML, for the time being using old yaml format for backward compat
    logger.warn(s"Skipping backend configuration ${config.className}, using legacy config instead")

    // Load the deprecated vinyldns config section elements into the DnsBackend
    IO(new DnsBackend(VinylDNSConfig.configuredDnsConnections, Crypto.instance))
  }
}
