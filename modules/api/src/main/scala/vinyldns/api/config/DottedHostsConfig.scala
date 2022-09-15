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

final case class DottedHostsConfig(zoneList: List[String], allowedUserList: List[String], allowedGroupList: List[String], allowedRecordType: List[String])
object DottedHostsConfig {
  implicit val configReader: ConfigReader[DottedHostsConfig] =
    ConfigReader.forProduct4[DottedHostsConfig, List[String], List[String], List[String], List[String]](
      "zone-list",
      "allowed-user-list",
      "allowed-group-list",
      "allowed-record-type"
    ){
      case (zoneList, allowedUserList, allowedGroupList, allowedRecordType) =>
        DottedHostsConfig(zoneList, allowedUserList, allowedGroupList, allowedRecordType)
    }
}
