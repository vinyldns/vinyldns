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
import vinyldns.core.domain.zone.AccessLevel

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

  "RecordSetResponse.canUpdate" should {
    val record = generateRecordSetResponses(1, "zone-id").head

    "return true if AccessLevel is Delete or Write" in {
      record.copy(accessLevel = Some(AccessLevel.Delete)).canUpdate("foo.ok.") shouldBe true
      record.copy(accessLevel = Some(AccessLevel.Write)).canUpdate("foo.ok.") shouldBe true
    }

    "return false if AccessLevel is Read, NoAccess, or None" in {
      record.copy(accessLevel = Some(AccessLevel.Read)).canUpdate("foo.ok.") shouldBe false
      record.copy(accessLevel = Some(AccessLevel.NoAccess)).canUpdate("foo.ok.") shouldBe false
      record.copy(accessLevel = None).canUpdate("foo.ok.") shouldBe false
    }

    "return false if record is SOA" in {
      record
        .copy(accessLevel = Some(AccessLevel.Delete), `type` = RecordType.SOA)
        .canUpdate("foo.ok.") shouldBe false
    }

    "return false if record is NS and name matches zone name" in {
      record
        .copy(accessLevel = Some(AccessLevel.Delete), `type` = RecordType.NS, name = "foo.ok.")
        .canUpdate("foo.ok.") shouldBe false
    }

    "return true if record is NS and name does not match zone name" in {
      record
        .copy(accessLevel = Some(AccessLevel.Delete), `type` = RecordType.NS, name = "not-foo")
        .canUpdate("foo.ok.") shouldBe true
    }
  }

  "RecordSetResponse.canDelete" should {
    val record = generateRecordSetResponses(1, "zone-id").head

    "return true if AccessLevel is Delete" in {
      record.copy(accessLevel = Some(AccessLevel.Delete)).canDelete("foo.ok.") shouldBe true
    }

    "return false if AccessLevel is Read, Write, NoAccess, or None" in {
      record.copy(accessLevel = Some(AccessLevel.Read)).canDelete("foo.ok.") shouldBe false
      record.copy(accessLevel = Some(AccessLevel.Write)).canDelete("foo.ok.") shouldBe false
      record.copy(accessLevel = Some(AccessLevel.NoAccess)).canDelete("foo.ok.") shouldBe false
      record.copy(accessLevel = None).canDelete("foo.ok.") shouldBe false
    }

    "return false if record is SOA" in {
      record
        .copy(accessLevel = Some(AccessLevel.Delete), `type` = RecordType.SOA)
        .canDelete("foo.ok.") shouldBe false
    }

    "return false if record is NS and name matches zone name" in {
      record
        .copy(accessLevel = Some(AccessLevel.Delete), `type` = RecordType.NS, name = "foo.ok.")
        .canDelete("foo.ok.") shouldBe false
    }

    "return true if record is NS and name does not match zone name" in {
      record
        .copy(accessLevel = Some(AccessLevel.Delete), `type` = RecordType.NS, name = "not-foo")
        .canDelete("foo.ok.") shouldBe true
    }
  }
}
