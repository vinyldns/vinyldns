from hamcrest import *


def test_get_user_success(shared_zone_test_context):
    """
    Tests that we can get a user's details by user ID
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_group = shared_zone_test_context.ok_group

    result = client.get_user("ok", status=200)

    assert_that(result["id"], is_("ok"))
    assert_that(result["userName"], is_("ok"))
    assert_that(result["groupId"], has_item(ok_group["id"]))
    assert_that(result["groupMap"], has_key(ok_group["id"]))
    assert_that(result["groupMap"][ok_group["id"]], is_(ok_group["name"]))


def test_get_user_by_username(shared_zone_test_context):
    """
    Tests that we can get a user's details by username
    """
    client = shared_zone_test_context.ok_vinyldns_client

    result = client.get_user("ok", status=200)

    assert_that(result["id"], is_("ok"))
    assert_that(result["userName"], is_("ok"))


def test_get_user_not_found(shared_zone_test_context):
    """
    Tests that getting a user that does not exist returns a 404
    """
    client = shared_zone_test_context.ok_vinyldns_client

    client.get_user("doesntexistuser", status=404)
