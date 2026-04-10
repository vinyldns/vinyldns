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

final case class LimitsConfig(
    BATCHCHANGE_ROUTING_MAX_ITEMS_LIMIT: Int,
    MEMBERSHIP_ROUTING_DEFAULT_MAX_ITEMS: Int,
    MEMBERSHIP_ROUTING_MAX_ITEMS_LIMIT: Int,
    MEMBERSHIP_ROUTING_MAX_GROUPS_LIST_LIMIT: Int,
    RECORDSET_ROUTING_DEFAULT_MAX_ITEMS: Int,
    ZONE_ROUTING_DEFAULT_MAX_ITEMS: Int,
    ZONE_ROUTING_MAX_ITEMS_LIMIT: Int
)
object LimitsConfig {
  // All fields are DB-backed. Defaults match the DB insert script.
  implicit val configReader: ConfigReader[LimitsConfig] = ConfigReader.fromCursor { c =>
    c.asObjectCursor.map { oc =>
      def optInt(key: String, default: Int): Int = {
        val cur = oc.atKeyOrUndefined(key)
        if (cur.isUndefined) default else cur.asInt.fold(_ => default, identity)
      }
      LimitsConfig(
        optInt("batchchange-routing-max-items-limit",      100),
        optInt("membership-routing-default-max-items",     100),
        optInt("membership-routing-max-items-limit",       1000),
        optInt("membership-routing-max-groups-list-limit", 3000),
        optInt("recordset-routing-default-max-items",      100),
        optInt("zone-routing-default-max-items",           100),
        optInt("zone-routing-max-items-limit",             100)
      )
    }
  }

}
