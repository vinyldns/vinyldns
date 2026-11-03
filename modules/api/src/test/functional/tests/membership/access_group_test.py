import time

from hamcrest import *


def test_create_group_with_membership_access_status(shared_zone_test_context):
    """
    Tests that we can create a group with membership access status
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        member1 = {"userId": "user1", "submittedBy": "admin1", "status": "PendingReview"}
        member2 = {"userId": "user2", "submittedBy": "admin2", "status": "Rejected"}
        member3 = {"userId": "user3", "submittedBy": "admin1", "status": "Approved"}

        new_group = {
            "name": "test-membership-access-status-group",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [member1],
                "rejectedMember": [member2],
                "approvedMember": [member3]
            }
        }
        saved_group = client.create_group(new_group, status=200)

        update_request = {
            "id": saved_group["id"],
            "name": "test-membership-access-status-group",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [member1],
                "rejectedMember": [member2],
                "approvedMember": [member3]
            }
        }
        client.update_group(update_request["id"], update_request, status=200)
        group = client.get_group(saved_group["id"], status=200)

        assert_that(len(group["membershipAccessStatus"]["pendingReviewMember"]), is_(1))
        assert_that(group["membershipAccessStatus"]["pendingReviewMember"][0]["userId"], is_("user1"))
        assert_that(len(group["membershipAccessStatus"]["rejectedMember"]), is_(1))
        assert_that(group["membershipAccessStatus"]["rejectedMember"][0]["userId"], is_("user2"))
        assert_that(len(group["membershipAccessStatus"]["approvedMember"]), is_(1))
        assert_that(group["membershipAccessStatus"]["approvedMember"][0]["userId"], is_("user3"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))

def test_update_group_membership_access_status(shared_zone_test_context):
    """
    Tests that we can update a group's membership access status
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        new_group = {
            "name": "test-update-membership-access",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)

        member1 = {"userId": "user1", "submittedBy": "admin1", "status": "PendingReview"}
        member2 = {"userId": "user2", "submittedBy": "admin1", "status": "Rejected"}
        member3 = {"userId": "user3", "submittedBy": "admin1", "status": "Approved"}

        update_group = {
            "id": saved_group["id"],
            "name": "test-update-membership-access",
            "email": "test@test.com",
            "description": "updated description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [member1],
                "rejectedMember": [member2],
                "approvedMember": [member3]
            }
        }

        updated = client.update_group(update_group["id"], update_group, status=200)

        assert_that(len(updated["membershipAccessStatus"]["pendingReviewMember"]), is_(1))
        assert_that(updated["membershipAccessStatus"]["pendingReviewMember"][0]["userId"], is_("user1"))
        assert_that(len(updated["membershipAccessStatus"]["rejectedMember"]), is_(1))
        assert_that(updated["membershipAccessStatus"]["rejectedMember"][0]["userId"], is_("user2"))
        assert_that(len(updated["membershipAccessStatus"]["approvedMember"]), is_(1))
        assert_that(updated["membershipAccessStatus"]["approvedMember"][0]["userId"], is_("user3"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_approve_pending_membership_request(shared_zone_test_context):
    """
    Tests that we can move a user from pending to approved status
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        pending_member = {"userId": "pending-user", "submittedBy": "admin1", "status": "PendingReview"}

        new_group = {
            "name": "test-approve-pending-member",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [pending_member],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)

        approved_member = {"userId": "pending-user", "submittedBy": "admin1", "status": "Approved"}

        update_group = {
            "id": saved_group["id"],
            "name": "test-approve-pending-member",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": [approved_member]
            }
        }

        updated = client.update_group(update_group["id"], update_group, status=200)

        assert_that(len(updated["membershipAccessStatus"]["pendingReviewMember"]), is_(0))
        assert_that(len(updated["membershipAccessStatus"]["approvedMember"]), is_(1))
        assert_that(updated["membershipAccessStatus"]["approvedMember"][0]["userId"], is_("pending-user"))
        assert_that(updated["membershipAccessStatus"]["approvedMember"][0]["status"], is_("Approved"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_reject_pending_membership_request(shared_zone_test_context):
    """
    Tests that we can move a user from pending to rejected status
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        pending_member = {"userId": "pending-user", "submittedBy": "admin1", "status": "PendingReview"}

        new_group = {
            "name": "test-reject-pending-member",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [pending_member],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)

        rejected_member = {"userId": "pending-user", "submittedBy": "admin1", "status": "Rejected"}

        update_group = {
            "id": saved_group["id"],
            "name": "test-reject-pending-member",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [rejected_member],
                "approvedMember": []
            }
        }

        updated = client.update_group(update_group["id"], update_group, status=200)

        assert_that(len(updated["membershipAccessStatus"]["pendingReviewMember"]), is_(0))
        assert_that(len(updated["membershipAccessStatus"]["rejectedMember"]), is_(1))
        assert_that(updated["membershipAccessStatus"]["rejectedMember"][0]["userId"], is_("pending-user"))
        assert_that(updated["membershipAccessStatus"]["rejectedMember"][0]["status"], is_("Rejected"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_multiple_membership_status_changes(shared_zone_test_context):
    """
    Tests that we can handle multiple membership status changes at once
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        member1 = {"userId": "user1", "submittedBy": "admin1", "status": "PendingReview"}
        member2 = {"userId": "user2", "submittedBy": "admin1", "status": "PendingReview"}
        member3 = {"userId": "user3", "submittedBy": "admin1", "status": "PendingReview"}

        new_group = {
            "name": "test-multiple-status-changes",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [member1, member2, member3],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)

        approved = {"userId": "user1", "submittedBy": "admin1", "status": "Approved"}
        rejected = {"userId": "user2", "submittedBy": "admin1", "status": "Rejected"}

        update_group = {
            "id": saved_group["id"],
            "name": "test-multiple-status-changes",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [member3],
                "rejectedMember": [rejected],
                "approvedMember": [approved]
            }
        }

        updated = client.update_group(update_group["id"], update_group, status=200)

        assert_that(len(updated["membershipAccessStatus"]["pendingReviewMember"]), is_(1))
        assert_that(updated["membershipAccessStatus"]["pendingReviewMember"][0]["userId"], is_("user3"))

        assert_that(len(updated["membershipAccessStatus"]["rejectedMember"]), is_(1))
        assert_that(updated["membershipAccessStatus"]["rejectedMember"][0]["userId"], is_("user2"))

        assert_that(len(updated["membershipAccessStatus"]["approvedMember"]), is_(1))
        assert_that(updated["membershipAccessStatus"]["approvedMember"][0]["userId"], is_("user1"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))

def test_reject_with_reason(shared_zone_test_context):
    """
    Tests that an admin can reject a membership request with a reason
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        pending_member = {"userId": "to-be-rejected", "submittedBy": "admin1", "status": "PendingReview"}

        new_group = {
            "name": "test-reject-with-reason",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [pending_member],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)
        rejected_member = {
            "userId": "to-be-rejected",
            "submittedBy": "ok",
            "status": "Rejected",
            "description": "Group is currently at capacity"
        }

        reject_request = {
            "id": saved_group["id"],
            "name": "test-reject-with-reason",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],  # Member not added to group
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [rejected_member],
                "approvedMember": []
            }
        }

        updated = client.update_group(reject_request["id"], reject_request, status=200)

        assert_that(len(updated["membershipAccessStatus"]["pendingReviewMember"]), is_(0))
        assert_that(len(updated["membershipAccessStatus"]["rejectedMember"]), is_(1))
        assert_that(updated["membershipAccessStatus"]["rejectedMember"][0]["userId"], is_("to-be-rejected"))
        assert_that(updated["membershipAccessStatus"]["rejectedMember"][0]["description"], is_("Group is currently at capacity"))

        member_ids = [member["id"] for member in updated["members"]]
        assert_that(member_ids, not_(has_item("to-be-rejected")))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))

def test_previously_rejected_member_can_reapply(shared_zone_test_context):
    """
    Tests that a previously rejected member can reapply for membership
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        rejected_member = {"userId": "reapplying-user", "status": "Rejected"}

        new_group = {
            "name": "test-reapplication",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [rejected_member],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)

        reapplication = {"userId": "reapplying-user", "status": "PendingReview"}

        reapply_request = {
            "id": saved_group["id"],
            "name": "test-reapplication",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [reapplication],
                "rejectedMember": [],
                "approvedMember": []
            }
        }

        updated = client.update_group(reapply_request["id"], reapply_request, status=200)

        assert_that(len(updated["membershipAccessStatus"]["pendingReviewMember"]), is_(1))
        assert_that(updated["membershipAccessStatus"]["pendingReviewMember"][0]["userId"], is_("reapplying-user"))
        assert_that(len(updated["membershipAccessStatus"]["rejectedMember"]), is_(0))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_approve_pending_membership_request_with_access_group(shared_zone_test_context):
    """
    Tests that a member can approve a pending membership request using access_group
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        pending_member = {"userId": "dummy", "status": "PendingReview"}

        new_group = {
            "name": "test-approve-access-group",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [pending_member],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)

        access_update = {
            "userId": "dummy",
            "status": "Approved"
        }

        response = client.access_group(saved_group["id"], access_update, status=200)

        assert_that(len(response["membershipAccessStatus"]["pendingReviewMember"]), is_(0))
        assert_that(len(response["membershipAccessStatus"]["approvedMember"]), is_(1))
        assert_that(response["membershipAccessStatus"]["approvedMember"][0]["userId"], is_("dummy"))
        assert_that(response["membershipAccessStatus"]["approvedMember"][0]["status"], is_("Approved"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_reject_pending_membership_request_with_access_group(shared_zone_test_context):
    """
    Tests that a member can reject a pending membership request using access_group
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        pending_member = {"userId": "dummy", "status": "PendingReview"}

        new_group = {
            "name": "test-reject-access-group",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [pending_member],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)

        access_update = {
            "userId": "dummy",
            "status": "Rejected",
            "description": "Not eligible at this time"
        }

        response = client.access_group(saved_group["id"], access_update, status=200)

        assert_that(len(response["membershipAccessStatus"]["pendingReviewMember"]), is_(0))
        assert_that(len(response["membershipAccessStatus"]["rejectedMember"]), is_(1))
        assert_that(response["membershipAccessStatus"]["rejectedMember"][0]["userId"], is_("dummy"))
        assert_that(response["membershipAccessStatus"]["rejectedMember"][0]["status"], is_("Rejected"))
        assert_that(response["membershipAccessStatus"]["rejectedMember"][0]["description"], is_("Not eligible at this time"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_non_member_cannot_update_access_group(shared_zone_test_context):
    """
    Tests that a non-member cannot update group access settings
    """
    client = shared_zone_test_context.ok_vinyldns_client
    non_member_client = shared_zone_test_context.dummy_vinyldns_client
    saved_group = None

    try:
        pending_member = {"userId": "dummy", "status": "PendingReview"}

        new_group = {
            "name": "test-non-member-access-group",
            "email": "test@test.com",
            "description": "testing non-member access restriction",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [pending_member],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)

        access_update = {
            "userId": "dummy",
            "status": "Approved"
        }
        non_member_client.access_group(saved_group["id"], access_update, status=403)
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_group_not_found_access_group(shared_zone_test_context):
    """
    Tests that accessing a non-existent group returns a 404
    """
    client = shared_zone_test_context.ok_vinyldns_client
    fake_id = "nonexistent-group-id"
    access_update = {
        "userId": "user1",
        "status": "PendingReview"
    }

    client.access_group(fake_id, access_update, status=404)


def test_already_member_cannot_approve_access_group(shared_zone_test_context):
    """
    Tests that a regular member (not admin) can update group access
    """
    admin_client = shared_zone_test_context.ok_vinyldns_client
    member_client = shared_zone_test_context.dummy_vinyldns_client
    saved_group = None

    try:
        pending_member = {"userId": "dummy", "status": "PendingReview"}

        new_group = {
            "name": "test-member-approval-group",
            "email": "test@test.com",
            "description": "testing member approval",
            "members": [{"id": "ok"}, {"id": "dummy"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [pending_member],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = admin_client.create_group(new_group, status=200)

        access_update = {
            "userId": "dummy",
            "status": "Approved"
        }
        response = member_client.access_group(saved_group["id"], access_update, status=409)
        assert_that(response, is_("User dummy is already a member of the group"))

    finally:
        if saved_group:
            admin_client.delete_group(saved_group["id"], status=(200, 404))


def test_already_member_cannot_request_access_group(shared_zone_test_context):
    """
    Tests that a regular member (not admin) can update group access
    """
    admin_client = shared_zone_test_context.ok_vinyldns_client
    member_client = shared_zone_test_context.dummy_vinyldns_client
    saved_group = None

    try:
        new_group = {
            "name": "test-member-request-group",
            "email": "test@test.com",
            "description": "testing member request",
            "members": [{"id": "ok"}, {"id": "dummy"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = admin_client.create_group(new_group, status=200)

        access_update = {
            "userId": "dummy",
            "status": "Requested"
        }

        response = member_client.access_group(saved_group["id"], access_update, status=409)
        assert_that(response, is_("User dummy is already a member of the group"))
    finally:
        if saved_group:
            admin_client.delete_group(saved_group["id"], status=(200, 404))


def test_already_pending_review_member_cannot_request_access_group(shared_zone_test_context):
    """
    Tests that a regular member (not admin) can update group access
    """
    admin_client = shared_zone_test_context.ok_vinyldns_client
    member_client = shared_zone_test_context.dummy_vinyldns_client
    saved_group = None

    try:
        new_group = {
            "name": "test-member-pending-review-group-name",
            "email": "test@test.com",
            "description": "testing member pending review",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = admin_client.create_group(new_group, status=200)
        access_update = {
            "userId": "dummy",
            "status": "Request"
        }
        member_client.access_group(saved_group["id"], access_update, status=200)
        response = member_client.access_group(saved_group["id"], access_update, status=409)
        assert_that(response, is_("User dummy already has a pending membership request"))
    finally:
        if saved_group:
            admin_client.delete_group(saved_group["id"], status=(200, 404))

def test_not_authorised_member_cannot_approve_access_group(shared_zone_test_context):
    """
    Tests that a regular member (not admin) can update group access
    """
    admin_client = shared_zone_test_context.ok_vinyldns_client
    member_client = shared_zone_test_context.dummy_vinyldns_client
    saved_group = None

    try:
        pending_member = {"userId": "dummy", "status": "PendingReview"}

        new_group = {
            "name": "test-member-pending-review-group",
            "email": "test@test.com",
            "description": "testing member pending review",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [pending_member],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = admin_client.create_group(new_group, status=200)

        access_update = {
            "userId": "dummy",
            "status": "Approved"
        }

        response = member_client.access_group(saved_group["id"], access_update, status=403)
        assert_that(response, is_("Not authorized"))

    finally:
        if saved_group:
            admin_client.delete_group(saved_group["id"], status=(200, 404))



def test_multiple_access_status_changes(shared_zone_test_context):
    """
    Tests handling multiple membership status changes at once with access_group
    """
    client = shared_zone_test_context.ok_vinyldns_client
    member_client1 = shared_zone_test_context.dummy_vinyldns_client
    member_client2 = shared_zone_test_context.support_user_client
    member_client3 = shared_zone_test_context.shared_zone_vinyldns_client

    saved_group = None

    try:
        member1 = {"userId": "dummy", "status": "Request"}
        member2 = {"userId": "support-user-id", "status": "Request"}
        member3 = {"userId": "sharedZoneUser", "status": "Request"}

        new_group = {
            "name": "test-multiple-access-changes",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)

        member_client1.access_group(saved_group["id"], member1, status=200)
        member_client2.access_group(saved_group["id"], member2, status=200)
        member_client3.access_group(saved_group["id"], member3, status=200)
        client.access_group(saved_group["id"], {"userId": "dummy", "status": "Approved"}, status=200)
        client.access_group(saved_group["id"], {"userId": "sharedZoneUser", "status": "Rejected"}, status=200)

        response = client.get_group(saved_group["id"], status=200)

        assert_that(len(response["membershipAccessStatus"]["pendingReviewMember"]), is_(1))
        assert_that(response["membershipAccessStatus"]["pendingReviewMember"][0]["userId"], is_("support-user-id"))

        assert_that(len(response["membershipAccessStatus"]["rejectedMember"]), is_(1))
        assert_that(response["membershipAccessStatus"]["rejectedMember"][0]["userId"], is_("sharedZoneUser"))

        assert_that(len(response["membershipAccessStatus"]["approvedMember"]), is_(1))
        assert_that(response["membershipAccessStatus"]["approvedMember"][0]["userId"], is_("dummy"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_access_group_with_empty_lists(shared_zone_test_context):
    """
    Tests that we can update a group with empty access lists
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        member1 = {"id": "dummy", "userStatus": "PendingReview"}
        member2 = {"id": "support-user-id", "userStatus": "PendingReview"}

        new_group = {
            "name": "test-clear-access-lists",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [member1, member2],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)

        client.access_group(saved_group["id"], {"userId": "dummy", "status": "Rejected"}, status=200)
        client.access_group(saved_group["id"], {"userId": "support-user-id", "status": "Rejected"}, status=200)

        response = client.get_group(saved_group["id"], status=200)

        assert_that(len(response["membershipAccessStatus"]["pendingReviewMember"]), is_(0))
        assert_that(len(response["membershipAccessStatus"]["rejectedMember"]), is_(2))
        assert_that(len(response["membershipAccessStatus"]["approvedMember"]), is_(0))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))