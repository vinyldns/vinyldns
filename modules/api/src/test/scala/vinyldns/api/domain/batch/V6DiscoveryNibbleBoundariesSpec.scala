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

package vinyldns.api.domain.batch

import org.scalatest.{Matchers, WordSpec}

class V6DiscoveryNibbleBoundariesSpec extends WordSpec with Matchers {
  "V6DiscoveryNibbleBoundaries" should {
    "Succeed with valid input" in {
      noException should be thrownBy V6DiscoveryNibbleBoundaries(2, 10)
    }
    "Succeed if min == max" in {
      noException should be thrownBy V6DiscoveryNibbleBoundaries(10, 10)
    }
    "error if min <= 0" in {
      an[AssertionError] should be thrownBy V6DiscoveryNibbleBoundaries(0, 10)
    }
    "error if max > 32" in {
      an[AssertionError] should be thrownBy V6DiscoveryNibbleBoundaries(1, 33)
    }
    "error if min > max" in {
      an[AssertionError] should be thrownBy V6DiscoveryNibbleBoundaries(10, 9)
    }
  }
}
