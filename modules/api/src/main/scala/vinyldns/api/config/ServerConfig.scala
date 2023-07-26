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

package vinyldns.api.config

import com.typesafe.config.Config
import pureconfig.ConfigReader
import vinyldns.api.domain.zone.ZoneRecordValidations

import scala.util.matching.Regex

final case class ServerConfig(
                               healthCheckTimeout: Int,
                               defaultTtl: Int,
                               maxZoneSize: Int,
                               syncDelay: Int,
                               validateRecordLookupAgainstDnsBackend: Boolean,
                               approvedNameServers: List[Regex],
                               color: String,
                               version: String,
                               keyName: String,
                               processingDisabled: Boolean,
                               useRecordSetCache: Boolean,
                               loadTestData: Boolean,
                               isZoneSyncScheduleAllowed: Boolean,
                             )
object ServerConfig {

  import ZoneRecordValidations.toCaseIgnoredRegexList

  implicit val configReader: ConfigReader[ServerConfig] = ConfigReader.forProduct13[
    ServerConfig,
    Int,
    Int,
    Int,
    Int,
    Boolean,
    List[String],
    String,
    String,
    Config,
    Boolean,
    Boolean,
    Boolean,
    Boolean
  ](
    "health-check-timeout",
    "default-ttl",
    "max-zone-size",
    "sync-delay",
    "validate-record-lookup-against-dns-backend",
    "approved-name-servers",
    "color",
    "version",
    "defaultZoneConnection",
    "processing-disabled",
    "use-recordset-cache",
    "load-test-data",
    "is-zone-sync-schedule-allowed"
  ) {
    case (
      timeout,
      ttl,
      maxZone,
      syncDelay,
      validateDnsBackend,
      approvedNameServers,
      color,
      version,
      zoneConnConfig,
      processingDisabled,
      useRecordSetCache,
      loadTestData,
      isZoneSyncScheduleAllowed) =>
      ServerConfig(
        timeout,
        ttl,
        maxZone,
        syncDelay,
        validateDnsBackend,
        toCaseIgnoredRegexList(approvedNameServers),
        color,
        version,
        zoneConnConfig.getString("keyName"),
        processingDisabled,
        useRecordSetCache,
        loadTestData,
        isZoneSyncScheduleAllowed
      )
  }
}
