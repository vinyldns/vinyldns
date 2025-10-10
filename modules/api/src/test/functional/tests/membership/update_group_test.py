import time

from hamcrest import *


def test_update_group_success(shared_zone_test_context):
    """
    Tests that we can update a group that has been created
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        new_group = {
            "name": "test-update-group-success",
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

        group = client.get_group(saved_group["id"], status=200)

        assert_that(group["name"], is_(saved_group["name"]))
        assert_that(group["email"], is_(saved_group["email"]))
        assert_that(group["description"], is_(saved_group["description"]))
        assert_that(group["status"], is_(saved_group["status"]))
        assert_that(group["created"], is_(saved_group["created"]))
        assert_that(group["id"], is_(saved_group["id"]))

        time.sleep(1)  # sleep to ensure that update doesnt change created time

        update_group = {
            "id": group["id"],
            "name": "updated-name",
            "email": "update@test.com",
            "description": "this is a new description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        group = client.update_group(update_group["id"], update_group, status=200)

        assert_that(group["name"], is_(update_group["name"]))
        assert_that(group["email"], is_(update_group["email"]))
        assert_that(group["description"], is_(update_group["description"]))
        assert_that(group["status"], is_(saved_group["status"]))
        assert_that(group["created"], is_(saved_group["created"]))
        assert_that(group["id"], is_(saved_group["id"]))
        assert_that(group["members"][0]["id"], is_("ok"))
        assert_that(group["admins"][0]["id"], is_("ok"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_update_group_without_name(shared_zone_test_context):
    """
    Tests that updating a group without a name fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = None
    try:
        new_group = {
            "name": "test-update-without-name",
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
        result = client.create_group(new_group, status=200)
        assert_that(result["name"], is_(new_group["name"]))
        assert_that(result["email"], is_(new_group["email"]))

        update_group = {
            "id": result["id"],
            "email": "update@test.com",
            "description": "this is a new description"
        }

        errors = client.update_group(update_group["id"], update_group, status=400)["errors"]
        assert_that(errors[0], is_("Missing Group.name"))
    finally:
        if result:
            client.delete_group(result["id"], status=(200, 404))


def test_update_group_without_email(shared_zone_test_context):
    """
    Tests that updating a group without an email fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = None
    try:
        new_group = {
            "name": "test-update-without-email",
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
        result = client.create_group(new_group, status=200)
        assert_that(result["name"], is_(new_group["name"]))
        assert_that(result["email"], is_(new_group["email"]))

        update_group = {
            "id": result["id"],
            "name": "without-email",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        errors = client.update_group(update_group["id"], update_group, status=400)["errors"]
        assert_that(errors[0], is_("Missing Group.email"))
    finally:
        if result:
            client.delete_group(result["id"], status=(200, 404))


def test_updating_group_without_name_or_email(shared_zone_test_context):
    """
    Tests that updating a group without name or an email fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = None
    try:
        new_group = {
            "name": "test-update-without-name-and-email",
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
        result = client.create_group(new_group, status=200)
        assert_that(result["name"], is_(new_group["name"]))
        assert_that(result["email"], is_(new_group["email"]))

        update_group = {
            "id": result["id"],
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        errors = client.update_group(update_group["id"], update_group, status=400)["errors"]
        assert_that(errors, has_length(2))
        assert_that(errors, contains_inanyorder(
            "Missing Group.name",
            "Missing Group.email"
        ))
    finally:
        if result:
            client.delete_group(result["id"], status=(200, 404))


def test_updating_group_without_members_or_admins(shared_zone_test_context):
    """
    Tests that updating a group without members or admins fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = None

    try:
        new_group = {
            "name": "test-update-without-members",
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
        result = client.create_group(new_group, status=200)
        assert_that(result["name"], is_(new_group["name"]))
        assert_that(result["email"], is_(new_group["email"]))

        update_group = {
            "id": result["id"],
            "name": "test-update-without-members",
            "email": "test@test.com",
            "description": "this is a description",
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        errors = client.update_group(update_group["id"], update_group, status=400)["errors"]
        assert_that(errors, has_length(2))
        assert_that(errors, contains_inanyorder(
            "Missing Group.members",
            "Missing Group.admins"
        ))
    finally:
        if result:
            client.delete_group(result["id"], status=(200, 404))


def test_update_group_adds_admins_as_members(shared_zone_test_context):
    """
    Tests that when we add an admin to a group the admin is also a member
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        new_group = {
            "name": "test-update-group-admins-as-members",
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

        group = client.get_group(saved_group["id"], status=200)

        assert_that(group["name"], is_(saved_group["name"]))
        assert_that(group["email"], is_(saved_group["email"]))
        assert_that(group["description"], is_(saved_group["description"]))
        assert_that(group["status"], is_(saved_group["status"]))
        assert_that(group["created"], is_(saved_group["created"]))
        assert_that(group["id"], is_(saved_group["id"]))

        update_group = {
            "id": group["id"],
            "name": "test-update-group-admins-as-members",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}, {"id": "dummy"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        group = client.update_group(update_group["id"], update_group, status=200)

        assert_that(group["members"], has_length(2))
        assert_that(["ok", "dummy"], has_item(group["members"][0]["id"]))
        assert_that(["ok", "dummy"], has_item(group["members"][1]["id"]))
        assert_that(group["admins"], has_length(2))
        assert_that(["ok", "dummy"], has_item(group["admins"][0]["id"]))
        assert_that(["ok", "dummy"], has_item(group["admins"][1]["id"]))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_update_group_conflict(shared_zone_test_context):
    """
    Tests that we can not update a groups name to a name already in use
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = None
    conflict_group = None
    try:
        new_group = {
            "name": "test_update_group_conflict",
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
        conflict_group = client.create_group(new_group, status=200)
        assert_that(conflict_group["name"], is_(new_group["name"]))

        other_group = {
            "name": "change_me",
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
        result = client.create_group(other_group, status=200)
        assert_that(result["name"], is_(other_group["name"]))

        # change the name of the other_group to the first group (conflict)
        update_group = {
            "id": result["id"],
            "name": "test_update_group_conflict",
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
        client.update_group(update_group["id"], update_group, status=409)
    finally:
        if result:
            client.delete_group(result["id"], status=(200, 404))
        if conflict_group:
            client.delete_group(conflict_group["id"], status=(200, 404))


def test_update_group_not_found(shared_zone_test_context):
    """
    Tests that we can not update a group that has not been created
    """
    client = shared_zone_test_context.ok_vinyldns_client

    update_group = {
        "id": "test-update-group-not-found",
        "name": "test-update-group-not-found",
        "email": "update@test.com",
        "description": "this is a new description",
        "members": [{"id": "ok"}],
        "admins": [{"id": "ok"}],
        "membershipAccessStatus": {
            "pendingReviewMember": [],
            "rejectedMember": [],
            "approvedMember": []
        }
    }
    client.update_group(update_group["id"], update_group, status=404)


def test_update_group_deleted(shared_zone_test_context):
    """
    Tests that we can not update a group that has been deleted
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        new_group = {
            "name": "test-update-group-deleted",
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
        client.delete_group(saved_group["id"], status=200)

        update_group = {
            "id": saved_group["id"],
            "name": "test-update-group-deleted-updated",
            "email": "update@test.com",
            "description": "this is a new description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}]
        }
        client.update_group(update_group["id"], update_group, status=404)
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_add_member_via_update_group_success(shared_zone_test_context):
    """
    Tests that we can add a member to a group via update successfully
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None
    try:
        new_group = {
            "name": "test-add-member-to-via-update-group-success",
            "email": "test@test.com",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}]
        }
        saved_group = client.create_group(new_group, status=200)

        updated_group = {
            "id": saved_group["id"],
            "name": "test-add-member-to-via-update-group-success",
            "email": "test@test.com",
            "members": [{"id": "ok"}, {"id": "dummy"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }

        saved_group = client.update_group(updated_group["id"], updated_group, status=200)
        expected_members = ["ok", "dummy"]
        assert_that(saved_group["members"], has_length(2))
        assert_that(expected_members, has_item(saved_group["members"][0]["id"]))
        assert_that(expected_members, has_item(saved_group["members"][1]["id"]))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_add_member_to_group_twice_via_update_group(shared_zone_test_context):
    """
    Tests that we can add a member to a group twice successfully via update group
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None
    try:
        new_group = {
            "name": "test-add-member-to-group-twice-success-via-update-group",
            "email": "test@test.com",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}]
        }
        saved_group = client.create_group(new_group, status=200)

        updated_group = {
            "id": saved_group["id"],
            "name": "test-add-member-to-group-twice-success-via-update-group",
            "email": "test@test.com",
            "members": [{"id": "ok"}, {"id": "dummy"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }

        saved_group = client.update_group(updated_group["id"], updated_group, status=200)
        saved_group = client.update_group(updated_group["id"], updated_group, status=200)
        expected_members = ["ok", "dummy"]
        assert_that(saved_group["members"], has_length(2))
        assert_that(expected_members, has_item(saved_group["members"][0]["id"]))
        assert_that(expected_members, has_item(saved_group["members"][1]["id"]))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_add_not_found_member_to_group_via_update_group(shared_zone_test_context):
    """
    Tests that we can not add a non-existent member to a group via update group
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        new_group = {
            "name": "test-add-not-found-member-to-group-via-update-group",
            "email": "test@test.com",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}]
        }
        saved_group = client.create_group(new_group, status=200)
        result = client.get_group(saved_group["id"], status=200)
        assert_that(result["members"], has_length(1))

        updated_group = {
            "id": saved_group["id"],
            "name": "test-add-not-found-member-to-group-via-update-group",
            "email": "test@test.com",
            "members": [{"id": "ok"}, {"id": "not_found"}],
            "admins": [{"id": "ok"}]
        }

        client.update_group(updated_group["id"], updated_group, status=404)
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_remove_member_via_update_group_success(shared_zone_test_context):
    """
    Tests that we can remove a member via update group successfully
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        new_group = {
            "name": "test-remove-member-via-update-group-success",
            "email": "test@test.com",
            "members": [{"id": "ok"}, {"id": "dummy"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)
        assert_that(saved_group["members"], has_length(2))

        updated_group = {
            "id": saved_group["id"],
            "name": "test-remove-member-via-update-group-success",
            "email": "test@test.com",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}]
        }
        saved_group = client.update_group(updated_group["id"], updated_group, status=200)

        assert_that(saved_group["members"], has_length(1))
        assert_that(saved_group["members"][0]["id"], is_("ok"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_remove_member_and_admin(shared_zone_test_context):
    """
    Tests that if we remove a member who is an admin, the admin is also removed
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        new_group = {
            "name": "test-remove-member-and-admin",
            "email": "test@test.com",
            "members": [{"id": "ok"}, {"id": "dummy"}],
            "admins": [{"id": "ok"}, {"id": "dummy"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)
        assert_that(saved_group["members"], has_length(2))

        updated_group = {
            "id": saved_group["id"],
            "name": "test-remove-member-and-admin",
            "email": "test@test.com",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}]
        }
        saved_group = client.update_group(updated_group["id"], updated_group, status=200)

        assert_that(saved_group["members"], has_length(1))
        assert_that(saved_group["members"][0]["id"], is_("ok"))
        assert_that(saved_group["admins"], has_length(1))
        assert_that(saved_group["admins"][0]["id"], is_("ok"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_remove_member_but_not_admin_keeps_member(shared_zone_test_context):
    """
    Tests that if we remove a member but do not remove the admin, the admin remains a member
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        new_group = {
            "name": "test-remove-member-not-admin-keeps-member",
            "email": "test@test.com",
            "members": [{"id": "ok"}, {"id": "dummy"}],
            "admins": [{"id": "ok"}, {"id": "dummy"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)
        assert_that(saved_group["members"], has_length(2))

        updated_group = {
            "id": saved_group["id"],
            "name": "test-remove-member-not-admin-keeps-member",
            "email": "test@test.com",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}, {"id": "dummy"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.update_group(updated_group["id"], updated_group, status=200)

        expected_members = ["ok", "dummy"]
        assert_that(saved_group["members"], has_length(2))
        assert_that(expected_members, has_item(saved_group["members"][0]["id"]))
        assert_that(expected_members, has_item(saved_group["members"][1]["id"]))
        assert_that(expected_members, has_item(saved_group["admins"][0]["id"]))
        assert_that(expected_members, has_item(saved_group["admins"][1]["id"]))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_remove_admin_keeps_member(shared_zone_test_context):
    """
    Tests that if we remove a member from admins, the member still remains part of the group
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        new_group = {
            "name": "test-remove-admin-keeps-member",
            "email": "test@test.com",
            "members": [{"id": "ok"}, {"id": "dummy"}],
            "admins": [{"id": "ok"}, {"id": "dummy"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)
        assert_that(saved_group["members"], has_length(2))

        updated_group = {
            "id": saved_group["id"],
            "name": "test-remove-admin-keeps-member",
            "email": "test@test.com",
            "members": [{"id": "ok"}, {"id": "dummy"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.update_group(updated_group["id"], updated_group, status=200)

        expected_members = ["ok", "dummy"]
        assert_that(saved_group["members"], has_length(2))
        assert_that(expected_members, has_item(saved_group["members"][0]["id"]))
        assert_that(expected_members, has_item(saved_group["members"][1]["id"]))

        assert_that(saved_group["admins"], has_length(1))
        assert_that(saved_group["admins"][0]["id"], is_("ok"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_update_group_not_authorized(shared_zone_test_context):
    """
    Tests that only the admins can update a zone
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    not_admin_client = shared_zone_test_context.dummy_vinyldns_client
    try:
        new_group = {
            "name": "test-update-group-not-authorized",
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
        saved_group = ok_client.create_group(new_group, status=200)

        update_group = {
            "id": saved_group["id"],
            "name": "updated-name",
            "email": "update@test.com",
            "description": "this is a new description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}]
        }
        not_admin_client.update_group(update_group["id"], update_group, status=403)
    finally:
        if saved_group:
            ok_client.delete_group(saved_group["id"], status=(200, 404))


def test_update_group_adds_admins_to_member_list(shared_zone_test_context):
    """
    Tests that updating a group adds admins to member list
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    result = None

    try:
        new_group = {
            "name": "test-update-group-add-admins-to-members",
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

        saved_group = ok_client.create_group(new_group, status=200)

        saved_group["admins"] = [{"id": "dummy"}]
        result = ok_client.update_group(saved_group["id"], saved_group, status=200)

        assert_that([x["id"] for x in result["members"]], contains_exactly("ok", "dummy"))
        assert_that(result["admins"][0]["id"], is_("dummy"))
    finally:
        if result:
            dummy_client.delete_group(result["id"], status=(200, 404))
