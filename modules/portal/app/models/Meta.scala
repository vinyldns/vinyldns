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

package models
import play.api.Configuration

case class Meta(
    version: String,
    sharedDisplayEnabled: Boolean,
    batchChangeLimit: Int,
    defaultTtl: Long,
    manualBatchChangeReviewEnabled: Boolean,
    scheduledBatchChangesEnabled: Boolean,
    portalUrl: String,
    maxGroupItemsDisplay: Int
)
object Meta {
  def apply(config: Configuration): Meta =
    Meta(
      config.getOptional[String]("vinyldns.version").getOrElse("unknown"),
      config.getOptional[Boolean]("shared-display-enabled").getOrElse(false),
      config.getOptional[Int]("batch-change-limit").getOrElse(1000),
      config.getOptional[Long]("default-ttl").getOrElse(7200L),
      config.getOptional[Boolean]("manual-batch-review-enabled").getOrElse(false),
      config.getOptional[Boolean]("scheduled-changes-enabled").getOrElse(false),
      config.getOptional[String]("portal.vinyldns.url").getOrElse("http://localhost:9001"),
      config.getOptional[Int]("api.limits.membership-routing-max-groups-list-limit").getOrElse(3000)
    )
}
