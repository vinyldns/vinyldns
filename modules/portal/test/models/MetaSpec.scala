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
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.Configuration

class MetaSpec extends Specification with Mockito {
  "Meta" should {
    "load from config" in {
      val config = Map("vinyldns.version" -> "foo-bar")
      Meta(Configuration.from(config)).version must beEqualTo("foo-bar")
    }
    "default to false if shared-display-enabled is not found" in {
      val config = Map("vinyldns.version" -> "foo-bar")
      Meta(Configuration.from(config)).sharedDisplayEnabled must beFalse
    }
    "set to true if shared-display-enabled is true in config" in {
      val config = Map("shared-display-enabled" -> true)
      Meta(Configuration.from(config)).sharedDisplayEnabled must beTrue
    }
    "get the batch-change-limit value in config" in {
      val config = Map("batch-change-limit" -> 21)
      Meta(Configuration.from(config)).batchChangeLimit must beEqualTo(21)
    }
    "default to 1000 if batch-change-limit is not found" in {
      val config = Map("vinyldns.version" -> "foo-bar")
      Meta(Configuration.from(config)).batchChangeLimit must beEqualTo(1000)
    }
    "get the default-ttl value in config" in {
      val config = Map("default-ttl" -> 7210)
      Meta(Configuration.from(config)).defaultTtl must beEqualTo(7210)
    }
    "default to 7200 if default-ttl is not found" in {
      val config = Map("vinyldns.version" -> "foo-bar")
      Meta(Configuration.from(config)).defaultTtl must beEqualTo(7200)
    }
    "default to false if manual-batch-review-enabled is not found" in {
      val config = Map("vinyldns.version" -> "foo-bar")
      Meta(Configuration.from(config)).manualBatchChangeReviewEnabled must beFalse
    }
    "set to true if manual-batch-review-enabled is true in config" in {
      val config = Map("manual-batch-review-enabled" -> true)
      Meta(Configuration.from(config)).manualBatchChangeReviewEnabled must beTrue
    }
    "default to false if scheduled-batch-change-enabled is not found" in {
      val config = Map("vinyldns.version" -> "foo-bar")
      Meta(Configuration.from(config)).scheduledBatchChangesEnabled must beFalse
    }
    "set to true if scheduled-batch-change-enabled is true in config" in {
      val config = Map("scheduled-changes-enabled" -> true)
      Meta(Configuration.from(config)).scheduledBatchChangesEnabled must beTrue
    }
    "default to 3000 if membership-routing-max-groups-list-limit is not found" in {
      val config = Map("vinyldns.version" -> "foo-bar")
      Meta(Configuration.from(config)).maxGroupItemsDisplay must beEqualTo(3000)
    }
    "get the membership-routing-max-groups-list-limit value in config" in {
      val config = Map("api.limits.membership-routing-max-groups-list-limit" -> 3100)
      Meta(Configuration.from(config)).maxGroupItemsDisplay must beEqualTo(3100)
    }
  }
}
