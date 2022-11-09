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

import com.comcast.ip4s.IpAddress
import pureconfig.ConfigReader
import vinyldns.api.domain.zone.ZoneRecordValidations

import scala.util.matching.Regex

final case class HighValueDomainConfig(fqdnRegexes: List[Regex], ipList: List[IpAddress])
object HighValueDomainConfig {
  import ZoneRecordValidations.toCaseIgnoredRegexList
  implicit val configReader: ConfigReader[HighValueDomainConfig] =
    ConfigReader.forProduct2[HighValueDomainConfig, List[String], List[String]](
      "regex-list",
      "ip-list"
    ) {
      case (regexList, ipList) =>
        HighValueDomainConfig(toCaseIgnoredRegexList(regexList), ipList.flatMap(IpAddress.fromString(_)))
    }
}
