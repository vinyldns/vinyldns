from hamcrest import *


def test_list_group_admins_success(shared_zone_test_context):
    """
    Test that we can list all the admins of a given group
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None
    try:
        new_group = {
            "name": "test-list-group-admins-success",
            "email": "test@test.com",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}, {"id": "dummy"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)

        admin_user_1_id = "ok"
        admin_user_2_id = "dummy"

        result = client.get_group(saved_group["id"], status=200)

        assert_that(result["admins"], has_length(2))
        assert_that([admin_user_1_id, admin_user_2_id], has_item(result["admins"][0]["id"]))
        assert_that([admin_user_1_id, admin_user_2_id], has_item(result["admins"][1]["id"]))

        result = client.list_group_admins(saved_group["id"], status=200)

        result = sorted(result["admins"], key=lambda user: user["userName"])
        assert_that(result, has_length(2))
        assert_that(result[0]["userName"], is_("dummy"))
        assert_that(result[0]["id"], is_("dummy"))
        assert_that(result[0]["created"], not_none())
        assert_that(result[1]["userName"], is_("ok"))
        assert_that(result[1]["id"], is_("ok"))
        assert_that(result[1]["created"], not_none())
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_list_group_admins_group_not_found(shared_zone_test_context):
    """
    Test that listing the admins of a non-existent group fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.list_group_admins("doesntexist", status=404)


def test_list_group_admins_unauthed(shared_zone_test_context):
    """
    Tests that non-group members can still list group admins
    """
    client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    saved_group = None
    try:
        new_group = {
            "name": "test-list-group-admins-unauthed",
            "email": "test@test.com",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)

        dummy_client.list_group_admins(saved_group["id"], status=200)
        client.list_group_admins(saved_group["id"], status=200)
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))
