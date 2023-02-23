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
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json4s.JsonDSL._
import org.json4s._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.domain.membership.GroupStatus

class GroupSerializerSpec
    extends AnyWordSpec
    with MembershipJsonProtocol
    with Matchers
    with ValidatedMatchers
    with ValidatedValues {

  val serializers: Seq[Serializer[_]] = membershipSerializers

  private val fullJson =
    ("name" -> "name") ~~
      ("email" -> "test@test.com") ~~
      ("description" -> Extraction.decompose(Some("description"))) ~~
      ("id" -> "uuid") ~~
      ("created" -> Extraction.decompose(Instant.now.truncatedTo(ChronoUnit.MILLIS))) ~~
      ("status" -> GroupStatus.Deleted.toString) ~~
      ("memberIds" -> Set("Johnny", "Bravo")) ~~
      ("adminUserIds" -> Set("Johnny"))

  private val minJson = ("name" -> "name") ~~ ("email" -> "test@test.com")

  "Serializing a Group from json" should {
    "serialize a full json" in {
      val result = GroupSerializer.fromJson(fullJson).value

      result.name shouldBe "name"
      result.email shouldBe "test@test.com"
      result.description shouldBe Some("description")
      result.id shouldBe "uuid"
      result.status shouldBe GroupStatus.Deleted
      result.memberIds shouldBe Set("Johnny", "Bravo")
      result.adminUserIds shouldBe Set("Johnny")
      Option(result.created) shouldBe defined
    }

    "require the name" in {
      val json =
        ("email" -> "test@test.com") ~~
          ("description" -> Extraction.decompose(Some("description")))

      val result = GroupSerializer.fromJson(json)
      result should haveInvalid("Missing Group.name")
    }

    "require the email" in {
      val json =
        ("name" -> "name") ~~
          ("description" -> Extraction.decompose(Some("description")))

      val result = GroupSerializer.fromJson(json)
      result should haveInvalid("Missing Group.email")
    }

    "default the description to None" in {
      val result = GroupSerializer.fromJson(minJson).value

      result.description shouldBe None
    }

    "default the id" in {
      val result = GroupSerializer.fromJson(minJson).value

      Option(result.id) shouldBe defined
    }

    "default the created" in {
      val result = GroupSerializer.fromJson(minJson).value

      Option(result.created) shouldBe defined
    }

    "default the status to Active" in {
      val result = GroupSerializer.fromJson(minJson).value

      result.status shouldBe GroupStatus.Active
    }

    "default the memberIds to an empty Set" in {
      val result = GroupSerializer.fromJson(minJson).value

      result.memberIds shouldBe Set.empty[String]
    }

    "default the adminUserIds to an empty Set" in {
      val result = GroupSerializer.fromJson(minJson).value

      result.adminUserIds shouldBe Set.empty[String]
    }
  }
}
