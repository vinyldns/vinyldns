import pytest

from utils import *


def check_zone_changes_page_accuracy(results, expected_first_change, expected_num_results):
    assert_that(len(results), is_(expected_num_results))
    change_num = expected_first_change
    for change in results:
        change_email = "i.changed.this.{0}.times@history-test.com".format(change_num)
        assert_that(change["zone"]["email"], is_(change_email))
        # should return changes in reverse order (most recent 1st)
        change_num -= 1


def check_zone_changes_responses(response, zoneId=True, zoneChanges=True, nextId=True, startFrom=True, maxItems=True):
    assert_that(response, has_key("zoneId")) if zoneId else assert_that(response, is_not(has_key("zoneId")))
    assert_that(response, has_key("zoneChanges")) if zoneChanges else assert_that(response,
                                                                                  is_not(has_key("zoneChanges")))
    assert_that(response, has_key("nextId")) if nextId else assert_that(response, is_not(has_key("nextId")))
    assert_that(response, has_key("startFrom")) if startFrom else assert_that(response, is_not(has_key("startFrom")))
    assert_that(response, has_key("maxItems")) if maxItems else assert_that(response, is_not(has_key("maxItems")))


def test_list_zone_changes_no_authorization(shared_zone_test_context):
    """
    Test that list zone changes without authorization fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    client.list_zone_changes("12345", sign_request=False, status=401)


def test_list_zone_changes_member_auth_success(shared_zone_test_context):
    """
    Test list zone changes succeeds with membership auth for member of admin group
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.ok_zone
    client.list_zone_changes(zone["id"], status=200)


@pytest.mark.serial
def test_list_zone_changes_member_auth_no_access(shared_zone_test_context):
    """
    Test list zone changes fails for user not in admin group with no acl rules
    """
    client = shared_zone_test_context.dummy_vinyldns_client
    zone = shared_zone_test_context.ok_zone
    client.list_zone_changes(zone["id"], status=403)


@pytest.mark.serial
def test_list_zone_changes_member_auth_with_acl(shared_zone_test_context):
    """
    Test list zone changes fails for user with acl rules
    """
    zone = shared_zone_test_context.ok_zone
    acl_rule = generate_acl_rule("Write", userId="dummy")
    try:
        client = shared_zone_test_context.dummy_vinyldns_client

        client.list_zone_changes(zone["id"], status=403)
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])
        client.list_zone_changes(zone["id"], status=403)
    finally:
        clear_ok_acl_rules(shared_zone_test_context)


def test_list_zone_changes_no_start(shared_zone_test_context):
    """
    Test getting all zone changes on one page (max items will default to default value)
    """
    client = shared_zone_test_context.history_client
    original_zone = shared_zone_test_context.history_zone
    response = client.list_zone_changes(original_zone["id"], start_from=None)

    check_zone_changes_page_accuracy(response["zoneChanges"], expected_first_change=10, expected_num_results=10)
    check_zone_changes_responses(response, startFrom=False, nextId=False)


def test_list_zone_changes_paging(shared_zone_test_context):
    """
    Test paging for zone changes can use previous nextId as start key of next page
    """
    client = shared_zone_test_context.history_client
    original_zone = shared_zone_test_context.history_zone

    response_1 = client.list_zone_changes(original_zone["id"], start_from=None, max_items=3)
    response_2 = client.list_zone_changes(original_zone["id"], start_from=response_1["nextId"], max_items=3)
    response_3 = client.list_zone_changes(original_zone["id"], start_from=response_2["nextId"], max_items=3)

    check_zone_changes_page_accuracy(response_1["zoneChanges"], expected_first_change=10, expected_num_results=3)
    check_zone_changes_page_accuracy(response_2["zoneChanges"], expected_first_change=7, expected_num_results=3)
    check_zone_changes_page_accuracy(response_3["zoneChanges"], expected_first_change=4, expected_num_results=3)

    check_zone_changes_responses(response_1, startFrom=False)
    check_zone_changes_responses(response_2)
    check_zone_changes_responses(response_3)


def test_list_zone_changes_exhausted(shared_zone_test_context):
    """
    Test next id is none when zone changes are exhausted
    """
    client = shared_zone_test_context.history_client
    original_zone = shared_zone_test_context.history_zone

    response = client.list_zone_changes(original_zone["id"], start_from=None, max_items=11)
    check_zone_changes_page_accuracy(response["zoneChanges"], expected_first_change=10, expected_num_results=10)
    check_zone_changes_responses(response, startFrom=False, nextId=False)


def test_list_zone_changes_default_max_items(shared_zone_test_context):
    """
    Test default max items is 100
    """
    client = shared_zone_test_context.history_client
    original_zone = shared_zone_test_context.history_zone

    response = client.list_zone_changes(original_zone["id"], start_from=None, max_items=None)
    assert_that(response["maxItems"], is_(100))
    check_zone_changes_responses(response, startFrom=None, nextId=None)


def test_list_zone_changes_max_items_boundaries(shared_zone_test_context):
    """
    Test 0 < max_items <= 100
    """
    client = shared_zone_test_context.history_client
    original_zone = shared_zone_test_context.history_zone

    too_large = client.list_zone_changes(original_zone["id"], start_from=None, max_items=101, status=400)
    too_small = client.list_zone_changes(original_zone["id"], start_from=None, max_items=0, status=400)

    assert_that(too_large, is_("maxItems was 101, maxItems must be between 0 exclusive and 100 inclusive"))
    assert_that(too_small, is_("maxItems was 0, maxItems must be between 0 exclusive and 100 inclusive"))
