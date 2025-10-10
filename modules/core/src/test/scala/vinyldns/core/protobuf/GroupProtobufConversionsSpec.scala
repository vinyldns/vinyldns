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

import vinyldns.core.domain.membership.{Group, GroupChange, GroupChangeType, MembershipAccess, MembershipAccessStatus}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

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
  "Converting MembershipAccessStatus to protobufs" should {
    "work with pending, approved, and rejected members" in {
      val pendingMember: MembershipAccess = MembershipAccess(
        userId = "pending-user",
        created = Instant.ofEpochSecond(1600000000),
        submittedBy = "test-user",
        description = Some("Please add me"),
        status = "Request"
      )

      val approvedMember: MembershipAccess = MembershipAccess(
        userId = "approved-user",
        created = Instant.ofEpochSecond(1600001000),
        submittedBy = "admin-user",
        description = Some("Approved request"),
        status = "Approved"
      )

      val rejectedMember: MembershipAccess = MembershipAccess(
        userId = "rejected-user",
        created = Instant.ofEpochSecond(1600002000),
        submittedBy = "admin-user",
        description = Some("Rejected request"),
        status = "Rejected"
      )

      val membershipAccessStatus: MembershipAccessStatus = MembershipAccessStatus(
        pendingReviewMember = Set(pendingMember),
        approvedMember = Set(approvedMember),
        rejectedMember = Set(rejectedMember)
      )

      val roundTrip = fromPB(toPB(membershipAccessStatus))

      roundTrip shouldBe membershipAccessStatus
    }

    "work with empty sets" in {
      val emptyStatus: MembershipAccessStatus = MembershipAccessStatus(Set(), Set(), Set())
      val roundTrip = fromPB(toPB(emptyStatus))
      roundTrip shouldBe emptyStatus
    }

    "preserve description field correctly" in {
      val memberWithDescription: MembershipAccess = MembershipAccess(
        userId = "test-user",
        created = Instant.ofEpochSecond(1600000000),
        submittedBy = "test-user",
        description = Some("Test description"),
        status = "Request"
      )

      val memberWithoutDescription: MembershipAccess = MembershipAccess(
        userId = "another-user",
        created = Instant.ofEpochSecond(1600000000),
        submittedBy = "test-user",
        description = None,
        status = "Request"
      )

      val status: MembershipAccessStatus = MembershipAccessStatus(
        pendingReviewMember = Set(memberWithDescription, memberWithoutDescription),
        approvedMember = Set(),
        rejectedMember = Set()
      )

      val roundTrip = fromPB(toPB(status))

      roundTrip shouldBe status
    }
  }
  "Converting MembershipAccess to protobufs" should {
    "work with all fields present" in {
      val membershipAccess = MembershipAccess(
        userId = "test-user",
        created = Instant.ofEpochSecond(1600000000),
        submittedBy = "admin-user",
        description = Some("Test description"),
        status = "Request"
      )

      val roundTrip = fromPB(toPB(membershipAccess))

      roundTrip shouldBe membershipAccess
    }

    "work with description = None" in {
      val membershipAccess = MembershipAccess(
        userId = "test-user",
        created = Instant.ofEpochSecond(1600000000),
        submittedBy = "admin-user",
        description = None,
        status = "Request"
      )

      val roundTrip = fromPB(toPB(membershipAccess))

      roundTrip shouldBe membershipAccess
    }

    "preserve all fields correctly during conversion" in {
      val membershipAccess = MembershipAccess(
        userId = "test-user-123",
        created = Instant.ofEpochSecond(1600000000),
        submittedBy = "admin-user-456",
        description = Some("This is a detailed description"),
        status = "Approved"
      )

      val pb = toPB(membershipAccess)

      pb.getUserId shouldBe "test-user-123"
      pb.getCreated shouldBe Instant.ofEpochSecond(1600000000).toEpochMilli
      pb.getSubmittedBy shouldBe "admin-user-456"
      pb.getDescription shouldBe "This is a detailed description"
      pb.getStatus shouldBe "Approved"

      val result = fromPB(pb)
      result shouldBe membershipAccess
    }
  }
}
