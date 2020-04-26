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

package vinyldns.api.route

import cats.scalatest.{ValidatedMatchers, ValidatedValues}
import org.json4s.JsonDSL._
import org.json4s.{Extraction, Serializer => json4sSerializer}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.zone.AccessLevel

class ACLRuleInfoSerializerSpec
    extends AnyWordSpec
    with ACLJsonProtocol
    with Matchers
    with ValidatedMatchers
    with ValidatedValues {

  // need to add the record type serializer since we pull record type
  val serializers: Seq[json4sSerializer[_]] = aclSerializers ++ Seq(JsonEnumV(RecordType))

  "Serializing from json" should {
    "reject a full json with both user and group ids provided" in {
      val fullJson =
        ("accessLevel" -> "Write") ~~
          ("description" -> Extraction.decompose(Some("description"))) ~~
          ("userId" -> Extraction.decompose(Some("johnny"))) ~~
          ("groupId" -> Extraction.decompose(Some("level2"))) ~~
          ("recordMask" -> "www-*") ~~
          ("recordTypes" -> Extraction.decompose(Set(RecordType.CNAME, RecordType.A)))
      val result = ACLRuleInfoSerializer.fromJson(fullJson)
      result should haveInvalid("Cannot specify both a userId and a groupId")
    }

    "serialize a full json when just the group id is provided" in {
      val fullJson =
        ("accessLevel" -> "Write") ~~
          ("description" -> Extraction.decompose(Some("description"))) ~~
          ("groupId" -> Extraction.decompose(Some("level2"))) ~~
          ("recordMask" -> "www-*") ~~
          ("recordTypes" -> Extraction.decompose(Set(RecordType.CNAME, RecordType.A)))
      val result = ACLRuleInfoSerializer.fromJson(fullJson).value

      result.accessLevel shouldBe AccessLevel.Write
      result.description shouldBe Some("description")
      result.userId shouldBe None
      result.groupId shouldBe Some("level2")
      result.recordMask shouldBe Some("www-*")
      result.recordTypes shouldBe Set(RecordType.CNAME, RecordType.A)
    }

    "serialize a full json when just the user id is provided" in {
      val fullJson =
        ("accessLevel" -> "Write") ~~
          ("description" -> Extraction.decompose(Some("description"))) ~~
          ("userId" -> Extraction.decompose(Some("johnny"))) ~~
          ("recordMask" -> "www-*") ~~
          ("recordTypes" -> Extraction.decompose(Set(RecordType.CNAME, RecordType.A)))
      val result = ACLRuleInfoSerializer.fromJson(fullJson).value

      result.accessLevel shouldBe AccessLevel.Write
      result.description shouldBe Some("description")
      result.userId shouldBe Some("johnny")
      result.groupId shouldBe None
      result.recordMask shouldBe Some("www-*")
      result.recordTypes shouldBe Set(RecordType.CNAME, RecordType.A)
    }

    "serialize minimum json when just the group id is specified" in {
      val json = ("groupId" -> Extraction.decompose(Some("level2"))) ~~ ("accessLevel" -> "Read")
      val result = ACLRuleInfoSerializer.fromJson(json).value
      result.accessLevel shouldBe AccessLevel.Read
      result.description shouldBe None
      result.groupId shouldBe Some("level2")
      result.userId shouldBe None
      result.recordMask shouldBe None
      result.recordTypes shouldBe Set.empty
    }

    "serialize minimum json when just the user id is specified" in {
      val json = ("userId" -> Extraction.decompose(Some("johnny"))) ~~ ("accessLevel" -> "Read")
      val result = ACLRuleInfoSerializer.fromJson(json).value
      result.accessLevel shouldBe AccessLevel.Read
      result.description shouldBe None
      result.userId shouldBe Some("johnny")
      result.groupId shouldBe None
      result.recordMask shouldBe None
      result.recordTypes shouldBe Set.empty
    }

    "serialize json when neither the user id nor group id is specified" in {
      val json = "accessLevel" -> "Read"
      val result = ACLRuleInfoSerializer.fromJson(json).value
      result.accessLevel shouldBe AccessLevel.Read
      result.description shouldBe None
      result.userId shouldBe None
      result.groupId shouldBe None
      result.recordMask shouldBe None
      result.recordTypes shouldBe Set.empty
    }

    "require the access level" in {
      val json =
        ("description" -> Extraction.decompose(Some("description"))) ~~
          ("userId" -> Extraction.decompose(Some("johnny")))

      val result = ACLRuleInfoSerializer.fromJson(json)
      result should haveInvalid("Missing ACLRule.accessLevel")
    }
  }
}
