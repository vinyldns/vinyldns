from hamcrest import *


def test_get_group_success(shared_zone_test_context):
    """
    Tests that we can get a group that has been created
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None
    try:
        new_group = {
            "name": "test-get-group-success",
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
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_get_group_not_found(shared_zone_test_context):
    """
    Tests that getting a group that does not exist returns a 404
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.get_group("doesntexist", status=404)


def test_get_deleted_group(shared_zone_test_context):
    """
    Tests getting a group that was already deleted
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        new_group = {
            "name": "test-get-deleted-group",
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
        client.get_group(saved_group["id"], status=404)
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_get_group_unauthed(shared_zone_test_context):
    """
    Tests that non-group members can still get group
    """
    client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    saved_group = None
    try:
        new_group = {
            "name": "test-get-group-unauthed",
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

        dummy_client.get_group(saved_group["id"], status=200)
        client.get_group(saved_group["id"], status=200)
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))
