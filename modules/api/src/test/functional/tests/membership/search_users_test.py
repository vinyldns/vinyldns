from hamcrest import *


def test_search_users_success(shared_zone_test_context):
    """
    Tests that searching for a user by an exact username returns that user's details
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_group = shared_zone_test_context.ok_group

    results = client.search_users("ok", status=200)
    users_by_id = {user["id"]: user for user in results}

    assert_that(users_by_id, has_key("ok"))
    ok_user = users_by_id["ok"]
    assert_that(ok_user["userName"], is_("ok"))
    assert_that(ok_user["groupId"], has_item(ok_group["id"]))
    assert_that(ok_user["groupMap"], has_key(ok_group["id"]))
    assert_that(ok_user["groupMap"][ok_group["id"]], is_(ok_group["name"]))


def test_search_users_returns_every_matching_user(shared_zone_test_context):
    """
    Regression test: searching must return every user whose username matches the pattern,
    not just a single arbitrary match (previously .first() silently dropped the rest).
    """
    client = shared_zone_test_context.ok_vinyldns_client

    ok_results = client.search_users("ok", status=200)
    dummy_results = client.search_users("dummy", status=200)

    assert_that([user["id"] for user in ok_results], has_item("ok"))
    assert_that([user["id"] for user in dummy_results], has_item("dummy"))


def test_search_users_not_found(shared_zone_test_context):
    """
    Tests that searching for a user whose username does not match any user returns an empty list
    """
    client = shared_zone_test_context.ok_vinyldns_client

    results = client.search_users("doesntexistuser", status=200)

    assert_that(results, is_([]))
