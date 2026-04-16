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

import com.typesafe.config.ConfigObject
import pureconfig.ConfigReader
import vinyldns.api.domain.zone.ZoneRecordValidations

import scala.util.Try
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
      def readInt(key: String): Int = {
        val cur = oc.atKeyOrUndefined(key)
        if (cur.isUndefined) 0 else cur.asInt.fold(_ => 0, identity)
      }
      def readBool(key: String): Boolean = {
        val cur = oc.atKeyOrUndefined(key)
        if (cur.isUndefined) false else cur.asBoolean.fold(_ => false, identity)
      }
      def readString(key: String): String = {
        val cur = oc.atKeyOrUndefined(key)
        if (cur.isUndefined) "" else cur.asString.fold(_ => "", identity)
      }
      def readListOfStrings(key: String): List[String] = {
        val cur = oc.atKeyOrUndefined(key)
        if (cur.isUndefined) Nil else ConfigReader[List[String]].from(cur).fold(_ => Nil, identity)
      }
      val zoneKeyName: String = {
        val cur = oc.atKeyOrUndefined("defaultZoneConnection")
        if (!cur.isUndefined) {
          cur.asObjectCursor.fold(
            _ => "",
            objCur => {
              val kc = objCur.atKeyOrUndefined("keyName")
              if (kc.isUndefined) "" else kc.asString.fold(_ => "", identity)
            }
          )
        } else {
          // Fall back to new backend format: backend.backend-providers[0].settings.backends[0].zone-connection.key-name
          Try {
            val topConfig = oc.objValue.toConfig
            val firstProvider = topConfig.getList("backend.backend-providers")
              .get(0).asInstanceOf[ConfigObject].toConfig
            val firstBackend = firstProvider.getList("settings.backends")
              .get(0).asInstanceOf[ConfigObject].toConfig
            firstBackend.getString("zone-connection.key-name")
          }.getOrElse("")
        }
      }
      ServerConfig(
        readInt("health-check-timeout"),
        readInt("default-ttl"),
        readInt("max-zone-size"),
        readInt("sync-delay"),
        readBool("validate-record-lookup-against-dns-backend"),
        toCaseIgnoredRegexList(readListOfStrings("approved-name-servers")),
        readString("color"),
        readString("version"),
        zoneKeyName,
        readBool("processing-disabled"),
        readBool("use-recordset-cache"),
        readBool("load-test-data"),
        readBool("is-zone-sync-schedule-allowed")
      )
    }
  }
}
