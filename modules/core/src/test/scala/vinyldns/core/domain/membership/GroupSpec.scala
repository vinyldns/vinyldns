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

package vinyldns.core.domain.membership
import vinyldns.core.TestMembershipData._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GroupSpec extends AnyWordSpec with Matchers {
  "Group" should {
    "correctly adds a user as a member" in {
      val newGroup = emptyGroup.addMember(okUser)
      (newGroup.memberIds should contain).only(okUser.id)
    }
    "handles adding a member twice" in {
      val newGroup = emptyGroup
        .addMember(okUser)
        .addMember(okUser)
      (newGroup.memberIds should contain).only(okUser.id)
    }
    "correctly removes a member" in {
      val newGroup = emptyGroup.addMember(okUser).addMember(dummyUser)
      val updatedGroup = newGroup.removeMember(okUser)
      (updatedGroup.memberIds should contain).only(dummyUser.id)
    }
    "does not fail when a non member is removed" in {
      val newGroup = emptyGroup.addMember(dummyUser)
      val updatedGroup = newGroup.removeMember(okUser)
      (updatedGroup.memberIds should contain).only(dummyUser.id)
    }
    "can not remove the last member from the group" in {
      val group = emptyGroup.copy(memberIds = Set.empty)
      val newGroup = group.addMember(dummyUser)
      val updatedGroup = newGroup.removeMember(dummyUser)
      updatedGroup.memberIds should contain(dummyUser.id)
    }
    "correctly adds a user as an admin user" in {
      val group = emptyGroup.copy(memberIds = Set.empty)
      val newGroup = group.addAdminUser(okUser)
      (newGroup.adminUserIds should contain).only(okUser.id)
      (newGroup.memberIds should contain).only(okUser.id)
    }
    "correctly adds two different users as admin users" in {
      val group = emptyGroup.copy(memberIds = Set.empty)
      val newGroup = group
        .addAdminUser(okUser)
        .addAdminUser(dummyUser)
      (newGroup.adminUserIds should contain).only(okUser.id, dummyUser.id)
    }
    "handles adding the same admin user twice" in {
      val newGroup = emptyGroup
        .addAdminUser(okUser)
        .addAdminUser(okUser)
      (newGroup.adminUserIds should contain).only(okUser.id)
    }
    "correctly removes an admin user" in {
      val newGroup = emptyGroup.addAdminUser(okUser).addAdminUser(dummyUser)
      val updatedGroup = newGroup.removeAdminUser(okUser)
      (updatedGroup.adminUserIds should contain).only(dummyUser.id)
    }
    "does not fail when a non admin user is removed" in {
      val newGroup = emptyGroup.addAdminUser(dummyUser)
      val updatedGroup = newGroup.removeAdminUser(okUser)
      (updatedGroup.adminUserIds should contain).only(dummyUser.id)
    }
    "can not remove the last admin user from the group" in {
      val group = emptyGroup.copy(adminUserIds = Set.empty)
      val newGroup = group.addAdminUser(dummyUser)
      val updatedGroup = newGroup.removeAdminUser(dummyUser)
      (updatedGroup.adminUserIds should contain).only(dummyUser.id)
    }
    "correctly adds a user to existing pending review members" in {
      val existingMemberAccess = MembershipAccess(
        userId = dummyUser.id,
        submittedBy = "admin-user",
        description = Some("Previous request"),
        status = MemberStatus.PendingReview.toString
      )
      val initialStatus = MembershipAccessStatus(pendingReviewMember = Set(existingMemberAccess))
      val groupWithExistingStatus = emptyGroup.copy(membershipAccessStatus = Some(initialStatus))
      val updatedGroup = groupWithExistingStatus.pendingReviewMember(okUser, Some("Please add me"), okAuth)

      updatedGroup.membershipAccessStatus.isDefined shouldBe true
      val pendingMembers = updatedGroup.membershipAccessStatus.get.pendingReviewMember.map(_.userId)
      pendingMembers should contain(okUser.id)
      pendingMembers should contain(dummyUser.id)
      pendingMembers.size shouldBe 2

      updatedGroup.memberIds should not contain okUser.id
      updatedGroup.memberIds should not contain dummyUser.id
    }
    "correctly approves a user membership" in {
      val pendingGroup = emptyGroup.pendingReviewMember(okUser, Some("Please add me"), okAuth)
      val approvedGroup = pendingGroup.approvedMember(okUser, Some("Approved"), okAuth)
      approvedGroup.membershipAccessStatus.isDefined shouldBe true
      approvedGroup.membershipAccessStatus.get.approvedMember.map(_.userId) should contain(okUser.id)
      approvedGroup.membershipAccessStatus.get.pendingReviewMember.map(_.userId) should not contain okUser.id
      approvedGroup.memberIds should contain(okUser.id)
    }
    "correctly rejects a user membership" in {
      val pendingGroup = emptyGroup.pendingReviewMember(okUser, Some("Please add me"), okAuth)
      val rejectedGroup = pendingGroup.rejectedMember(okUser, Some("Rejected"), okAuth)
      rejectedGroup.membershipAccessStatus.isDefined shouldBe true
      rejectedGroup.membershipAccessStatus.get.rejectedMember.map(_.userId) should contain(okUser.id)
      rejectedGroup.membershipAccessStatus.get.pendingReviewMember.map(_.userId) should not contain okUser.id
      rejectedGroup.memberIds should not contain okUser.id
    }
    "maintains correct membership status when approving a user directly" in {
      val approvedGroup = emptyGroup.approvedMember(okUser, Some("Direct approval"), okAuth)
      approvedGroup.membershipAccessStatus.isDefined shouldBe true
      approvedGroup.membershipAccessStatus.get.approvedMember.map(_.userId) should contain(okUser.id)
      approvedGroup.memberIds should contain(okUser.id)
    }
    "maintains correct membership status when rejecting a user directly" in {
      val rejectedGroup = emptyGroup.rejectedMember(okUser, Some("Direct rejection"), okAuth)
      rejectedGroup.membershipAccessStatus.isDefined shouldBe true
      rejectedGroup.membershipAccessStatus.get.rejectedMember.map(_.userId) should contain(okUser.id)
      rejectedGroup.memberIds should not contain okUser.id
    }
  }
}