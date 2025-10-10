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

import cats.scalatest.ValidatedMatchers
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.domain.membership.UserId

class UpdateGroupInputSerializerSpec
    extends AnyWordSpec
    with MembershipJsonProtocol
    with Matchers
    with ValidatedMatchers {

  val serializers: Seq[Serializer[_]] = membershipSerializers

  def testJson(
      id: String,
      name: String,
      email: String,
      description: Option[String],
      members: Set[UserId] = Set.empty,
      admins: Set[UserId] = Set.empty
  ): JValue =
    ("id" -> id) ~~
      ("name" -> name) ~~
      ("email" -> email) ~~
      ("description" -> Extraction.decompose(description)) ~~
      ("members" -> Extraction.decompose(members)) ~~
      ("admins" -> Extraction.decompose(admins))

  "Serializing from json" should {
    "complete successfully" in {
      val expectedId = "id"
      val expectedName = "foo"
      val expectedEmail = "test@test.com"
      val expectedDesc = Some("this is a test")
      val members = Set(UserId("foo"))
      val admins = Set(UserId("bar"))

      val json = testJson(expectedId, expectedName, expectedEmail, expectedDesc, members, admins)

      val result = UpdateGroupInputSerializer.fromJson(json).toOption.get
      result.id shouldBe expectedId
      result.name shouldBe expectedName
      result.email shouldBe expectedEmail
      result.description shouldBe expectedDesc
      result.members shouldBe members
      result.admins shouldBe admins
    }

    "set the description to None if not specified" in {
      val expectedId = "id"
      val expectedName = "foo"
      val expectedEmail = "test@test.com"
      val expectedDesc = None

      val json = testJson(expectedId, expectedName, expectedEmail, expectedDesc)
      val result = UpdateGroupInputSerializer.fromJson(json).toOption.get
      result.name shouldBe expectedName
      result.email shouldBe expectedEmail
      result.description shouldBe None
    }

    "return an error if the id is not specified" in {
      val json = render("email" -> "test@test.com")
      val result = UpdateGroupInputSerializer.fromJson(json)
      result should haveInvalid("Missing Group.name")
    }

    "return an error if the name is not specified" in {
      val json = render("email" -> "test@test.com")
      val result = UpdateGroupInputSerializer.fromJson(json)
      result should haveInvalid("Missing Group.name")
    }

    "return an error if the email is not specified" in {
      val json = render("name" -> "myname")
      val result = UpdateGroupInputSerializer.fromJson(json)
      result should haveInvalid("Missing Group.email")
    }

    "return an error if both the email and name are not specified" in {
      val json = render("description" -> "some description")
      val result = UpdateGroupInputSerializer.fromJson(json)
      result should haveInvalid("Missing Group.email")
      result should haveInvalid("Missing Group.name")
    }

    "return an error if the members or admins are not provided" in {
      val json = render(("name" -> "name") ~~ ("email" -> "email"))
      val result = UpdateGroupInputSerializer.fromJson(json)
      result should haveInvalid("Missing Group.members")
      result should haveInvalid("Missing Group.admins")
    }
  }
  "deserialize MemberStatusGroupInput from JSON with all fields present" in {
    val members = Set("user1", "user2")
    val admins = Set("admin1", "admin2")
    val accessMembers = Set("pending1", "pending2")
    val json =
      ("members" -> Extraction.decompose(members)) ~~
        ("admins" -> Extraction.decompose(admins)) ~~
        ("membershipAccessStatus" -> Extraction.decompose(accessMembers))
    val result = MemberStatusGroupInputSerializer.fromJson(json).toOption.get

    result.pendingReviewMember shouldBe members
    result.rejectedMember shouldBe admins
    result.approvedMember shouldBe accessMembers
  }

  "deserialize MemberStatusGroupInput with missing fields using defaults" in {
    val json = ("members" -> Extraction.decompose(Set("user1")))
    val result = MemberStatusGroupInputSerializer.fromJson(json).toOption.get
    result.pendingReviewMember shouldBe Set("user1")
    result.rejectedMember shouldBe Set.empty
    result.approvedMember shouldBe Set.empty
  }

  "deserialize MemberStatusGroupInput from empty JSON using all defaults" in {
    val json = JObject()
    val result = MemberStatusGroupInputSerializer.fromJson(json).toOption.get
    result.pendingReviewMember shouldBe Set.empty
    result.rejectedMember shouldBe Set.empty
    result.approvedMember shouldBe Set.empty
  }
}
