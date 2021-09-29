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
  implicit val configReader: ConfigReader[LimitsConfig] =
    ConfigReader.forProduct7[LimitsConfig, Int, Int, Int, Int, Int, Int, Int](
      "batchchange-routing-max-items-limit",
      "membership-routing-default-max-items",
      "membership-routing-max-items-limit",
      "membership-routing-max-groups-list-limit",
      "recordset-routing-default-max-items",
      "zone-routing-default-max-items",
      "zone-routing-max-items-limit"
    ) {
      case (
          batchchange_routing_max_items_limit,
          membership_routing_default_max_items,
          membership_routing_max_items_limit,
          membership_routing_max_groups_list_limit,
          recordset_routing_default_max_items,
          zone_routing_default_max_items,
          zone_routing_max_items_limit
          ) =>
        LimitsConfig(
          batchchange_routing_max_items_limit,
          membership_routing_default_max_items,
          membership_routing_max_items_limit,
          membership_routing_max_groups_list_limit,
          recordset_routing_default_max_items,
          zone_routing_default_max_items,
          zone_routing_max_items_limit
        )
    }

}
