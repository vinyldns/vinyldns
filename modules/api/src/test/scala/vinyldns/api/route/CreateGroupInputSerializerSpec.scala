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
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.domain.membership.UserId

class CreateGroupInputSerializerSpec
    extends WordSpec
    with MembershipJsonProtocol
    with Matchers
    with ValidatedMatchers {

  val serializers: Seq[Serializer[_]] = membershipSerializers

  def testJson(
      name: String,
      email: String,
      description: Option[String],
      members: Set[UserId] = Set.empty,
      admins: Set[UserId] = Set.empty
  ): JValue =
    ("name" -> name) ~~
      ("email" -> email) ~~
      ("description" -> Extraction.decompose(description)) ~~
      ("members" -> Extraction.decompose(members)) ~~
      ("admins" -> Extraction.decompose(admins))

  "Serializing from json" should {
    "complete successfully" in {
      val expectedName = "foo"
      val expectedEmail = "test@test.com"
      val expectedDesc = Some("this is a test")
      val members = Set(UserId("foo"))
      val admins = Set(UserId("bar"))

      val json = testJson(expectedName, expectedEmail, expectedDesc, members, admins)

      val result = CreateGroupInputSerializer.fromJson(json).toOption.get
      result.name shouldBe expectedName
      result.email shouldBe expectedEmail
      result.description shouldBe expectedDesc
      result.members shouldBe members
      result.admins shouldBe admins
    }

    "set the description to None if not specified" in {
      val expectedName = "foo"
      val expectedEmail = "test@test.com"
      val expectedDesc = None

      val json = testJson(expectedName, expectedEmail, expectedDesc)
      val result = CreateGroupInputSerializer.fromJson(json).toOption.get
      result.name shouldBe expectedName
      result.email shouldBe expectedEmail
      result.description shouldBe None
    }

    "return an error if the name is not specified" in {
      val json = render("email" -> "test@test.com")
      val result = CreateGroupInputSerializer.fromJson(json)
      result should haveInvalid("Missing Group.name")
    }

    "return an error if the email is not specified" in {
      val json = render("name" -> "myname")
      val result = CreateGroupInputSerializer.fromJson(json)
      result should haveInvalid("Missing Group.email")
    }

    "return an error if both the email and name are not specified" in {
      val json = render("description" -> "some description")
      val result = CreateGroupInputSerializer.fromJson(json)
      result should haveInvalid("Missing Group.email")
      result should haveInvalid("Missing Group.name")
    }

    "return an error if the members or admins are not provided" in {
      val json = render(("name" -> "name") ~~ ("email" -> "email"))
      val result = CreateGroupInputSerializer.fromJson(json)
      result should haveInvalid("Missing Group.members")
      result should haveInvalid("Missing Group.admins")
    }
  }
}
