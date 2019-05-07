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

package vinyldns.client.models.record

import org.scalatest._
import vinyldns.client.SharedTestData
import vinyldns.core.domain.record.RecordType

class RecordSetResponseSpec extends WordSpec with Matchers with SharedTestData {
  "RecordSetResponse.labelHasInvalidDot" should {
    val zoneName = "foo.bar."
    val dotted = "sometimes.bad"
    val notDotted = "always-good"

    val whitelistedType = RecordType.NAPTR
    val normalType = RecordType.A

    "return false if type is part of approved list" in {
      RecordSetResponse.labelHasInvalidDot(zoneName, whitelistedType, zoneName) shouldBe false
      RecordSetResponse.labelHasInvalidDot(dotted, whitelistedType, zoneName) shouldBe false
      RecordSetResponse.labelHasInvalidDot(notDotted, whitelistedType, zoneName) shouldBe false
    }

    "return false if record is not dotted" in {
      RecordSetResponse.labelHasInvalidDot(notDotted, whitelistedType, zoneName) shouldBe false
      RecordSetResponse.labelHasInvalidDot(notDotted, normalType, zoneName) shouldBe false
    }

    "return false if record is same as zone name" in {
      RecordSetResponse.labelHasInvalidDot(zoneName, whitelistedType, zoneName) shouldBe false
      RecordSetResponse.labelHasInvalidDot(zoneName, normalType, zoneName) shouldBe false
    }

    "return true if record is dotted, not whitelisted, and not the zone name" in {
      RecordSetResponse.labelHasInvalidDot(dotted, normalType, zoneName) shouldBe true
    }
  }
}
