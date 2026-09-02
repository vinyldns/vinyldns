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

package vinyldns.core.domain.zone.generate

import com.typesafe.config.{Config, ConfigFactory}
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.{Encrypted, Encryption}

import scala.collection.JavaConverters._

case class DnsProviderConfig(
    endpoints: Map[String, String],
    requestTemplates: Map[String, String],
    schemas: Map[String, String],
    apiKey: Encrypted
  )

case class DnsProviderApiConnection(
    providers: Map[String, DnsProviderConfig],
    nameServers: List[String],
    allowedProviders: List[String]
  )

object DnsProviderApiConnection {
  // Parse the dns-provider-api backend-provider block into a DnsProviderApiConnection. Moved
  // verbatim out of ConfiguredDnsConnections.load; no pureconfig conversion so error/missing-key
  // semantics are unchanged.
  def load(config: Config, crypto: CryptoAlgebra): DnsProviderApiConnection =
    if (config.hasPath("vinyldns.backend.backend-providers")) {
      val providersConfig = config
        .getConfigList("vinyldns.backend.backend-providers")
        .asScala
        .find(_.hasPath("settings.dns-provider-api.providers"))
        .map(_.getConfig("settings.dns-provider-api.providers"))
        .getOrElse(ConfigFactory.empty())

      val allowedProviders: List[String] = providersConfig.root().keySet().asScala.toList

      val nameServers = config
        .getConfigList("vinyldns.backend.backend-providers")
        .asScala
        .find(_.hasPath("settings.dns-provider-api.name-servers"))
        .map(_.getStringList("settings.dns-provider-api.name-servers").asScala.toList)
        .getOrElse(List.empty[String])

      val providerConfigs: Map[String, DnsProviderConfig] = providersConfig.root().keySet().asScala.map { provider =>
        val providerConfig = providersConfig.getConfig(provider)

        // Helper to turn a Config section into a Map[String, String]
        def configToMap(config: com.typesafe.config.Config): Map[String, String] =
          config.entrySet().asScala.map { entry =>
            val key = entry.getKey
            val value = config.getString(key)
            key -> value
          }.toMap

        val endpoints = configToMap(providerConfig.getConfig("endpoints"))
        val requestTemplates = configToMap(providerConfig.getConfig("request-templates"))
        val schemas = configToMap(providerConfig.getConfig("schemas"))
        // Encrypt the provider API key at load time so it is never held in memory as plaintext.
        val apiKey = Encryption(crypto, providerConfig.getString("api-key"))

        provider -> DnsProviderConfig(
          endpoints = endpoints,
          requestTemplates = requestTemplates,
          schemas = schemas,
          apiKey = apiKey
        )
      }.toMap

      DnsProviderApiConnection(providerConfigs, nameServers, allowedProviders)
    } else {
      DnsProviderApiConnection(Map.empty, List.empty[String], List.empty[String])
    }
}
