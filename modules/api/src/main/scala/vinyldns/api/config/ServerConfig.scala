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

  implicit val configReader: ConfigReader[ServerConfig] = ConfigReader.fromCursor { c =>
    c.asObjectCursor.map { oc =>
      def optInt(key: String, default: Int): Int = {
        val cur = oc.atKeyOrUndefined(key)
        if (cur.isUndefined) default else cur.asInt.fold(_ => default, identity)
      }
      def optBool(key: String, default: Boolean): Boolean = {
        val cur = oc.atKeyOrUndefined(key)
        if (cur.isUndefined) default else cur.asBoolean.fold(_ => default, identity)
      }
      def optString(key: String, default: String): String = {
        val cur = oc.atKeyOrUndefined(key)
        if (cur.isUndefined) default else cur.asString.fold(_ => default, identity)
      }
      def optListOfStrings(key: String): List[String] = {
        val cur = oc.atKeyOrUndefined(key)
        if (cur.isUndefined) Nil else ConfigReader[List[String]].from(cur).fold(_ => Nil, identity)
      }
      val zoneKeyName: String = {
        val cur = oc.atKeyOrUndefined("defaultZoneConnection")
        if (cur.isUndefined) ""
        else cur.asObjectCursor.fold(
          _ => "",
          objCur => {
            val kc = objCur.atKeyOrUndefined("keyName")
            if (kc.isUndefined) "" else kc.asString.fold(_ => "", identity)
          }
        )
      }
      ServerConfig(
        optInt("health-check-timeout",                         10),
        optInt("default-ttl",                                  7200),
        optInt("max-zone-size",                                60000),
        optInt("sync-delay",                                   10000),
        optBool("validate-record-lookup-against-dns-backend",  false),
        toCaseIgnoredRegexList(optListOfStrings("approved-name-servers")),
        optString("color",                                     "blue"),
        optString("version",                                   ""),
        zoneKeyName,
        optBool("processing-disabled",                         false),
        optBool("use-recordset-cache",                         false),
        optBool("load-test-data",                              false),
        optBool("is-zone-sync-schedule-allowed",               true)
      )
    }
  }
}
