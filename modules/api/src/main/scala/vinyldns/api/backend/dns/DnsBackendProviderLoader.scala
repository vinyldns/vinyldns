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

import cats.effect.{ContextShift, IO}
import vinyldns.api.config.VinylDNSConfig
import vinyldns.core.domain.backend.{BackendProvider, BackendProviderConfig, BackendProviderLoader}

class DnsBackendProviderLoader extends BackendProviderLoader {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  /**
    * Loads a backend based on the provided config so that it is ready to use
    * This is internally used typically during startup
    *
    * @param config The BackendConfig, has settings that are specific to this backend
    * @return A ready-to-use Backend instance, or does an IO.raiseError if something bad occurred.
    */
  def load(config: BackendProviderConfig): IO[BackendProvider] =
    // if legacy = true, load from the old configured dns connections
    // otherwise, load new stuff
    VinylDNSConfig.load().flatMap { vinyldnsConfig =>
      DnsBackendProviderConfig.load(config.settings).map { bec =>
        if (bec.legacy) {
          // legacy adds a backend id named "default" with the default configuration
          // and loads the backend connections from the legacy YAML config
          val conns = vinyldnsConfig.configuredDnsConnections.dnsBackends.map { be =>
            DnsBackend
              .apply(be.id, be.zoneConnection, Some(be.transferConnection), vinyldnsConfig.crypto)
          }
          val defaultConn =
            DnsBackend.apply(
              "default",
              vinyldnsConfig.configuredDnsConnections.defaultZoneConnection,
              Some(vinyldnsConfig.configuredDnsConnections.defaultTransferConnection),
              vinyldnsConfig.crypto
            )
          new DnsBackendProvider(defaultConn :: conns, vinyldnsConfig.crypto)
        } else {
          // Assumes the "new" YAML config
          new DnsBackendProvider(
            bec.backends.map(_.toDnsConnection(vinyldnsConfig.crypto)),
            vinyldnsConfig.crypto
          )
        }
      }
    }
}
