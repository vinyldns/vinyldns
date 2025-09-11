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

package vinyldns.core.protobuf

import vinyldns.core.domain.membership.{Group, GroupChange, GroupChangeType, MembershipAccessStatus}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GroupProtobufConversionsSpec extends AnyWordSpec with Matchers with GroupProtobufConversions {

  "Converting groups to protobufs" should {
    "works with all fields present" in {
      val group =
        Group(
          "ok",
          "test@test.com",
          Some("a test group"),
          memberIds = Set("foo", "bar"),
          adminUserIds = Set("foo", "bar"),
          membershipAccessStatus = Some(MembershipAccessStatus(Set(),Set(),Set()))
        )

      val roundTrip = fromPB(toPB(group))

      roundTrip shouldBe group
    }

    "works with an empty description" in {
      val group =
        Group(
          "ok",
          "test@test.com",
          description = None,
          memberIds = Set("foo", "bar"),
          adminUserIds = Set("foo", "bar"),
          membershipAccessStatus = Some(MembershipAccessStatus(Set(),Set(),Set()))
        )

      val roundTrip = fromPB(toPB(group))

      roundTrip shouldBe group
    }
  }

  "Converting group changes with protobufs" should {
    "works with all fields present" in {
      val newGroup = Group(
        "ok",
        "test@test.com",
        Some("a test group"),
        memberIds = Set("foo", "bar"),
        adminUserIds = Set("foo", "bar"),
        membershipAccessStatus = Some(MembershipAccessStatus(Set(),Set(),Set()))
      )
      val oldGroup = Group(
        "ok",
        "changed@test.com",
        Some("a changed group"),
        memberIds = Set("foo"),
        adminUserIds = Set("foo"),
        membershipAccessStatus = Some(MembershipAccessStatus(Set(),Set(),Set()))
      )

      val groupChange =
        GroupChange(
          id = "test",
          userId = "ok",
          changeType = GroupChangeType.Update,
          newGroup = newGroup,
          oldGroup = Some(oldGroup)
        )
      val roundTrip = fromPB(toPB(groupChange))
      println(roundTrip)
      println(groupChange)

      roundTrip shouldBe groupChange
    }

    "works with oldGroup = None" in {
      val newGroup = Group(
        "ok",
        "test@test.com",
        Some("a test group"),
        memberIds = Set("foo", "bar"),
        adminUserIds = Set("foo", "bar"),
        membershipAccessStatus = Some(MembershipAccessStatus(Set(),Set(),Set()))
      )

      val groupChange =
        GroupChange(
          id = "test",
          userId = "ok",
          changeType = GroupChangeType.Update,
          newGroup = newGroup,
          oldGroup = None
        )
      val roundTrip = fromPB(toPB(groupChange))

      roundTrip shouldBe groupChange
    }
  }
}
