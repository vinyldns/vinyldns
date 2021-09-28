from hamcrest import *
from utils import *


def test_list_zones_success(shared_zone_test_context):
    """
    Test that we can retrieve a list of the user's zones
    """
    result = shared_zone_test_context.list_zones_client.list_zones(status=200)
    retrieved = result["zones"]

    assert_that(retrieved, has_length(5))
    assert_that(retrieved, has_item(has_entry("name", "list-zones-test-searched-1.")))
    assert_that(retrieved, has_item(has_entry("adminGroupName", "list-zones-group")))
    assert_that(retrieved, has_item(has_entry("backendId", "func-test-backend")))


def test_list_zones_max_items_100(shared_zone_test_context):
    """
    Test that the default max items for a list zones request is 100
    """
    result = shared_zone_test_context.list_zones_client.list_zones(status=200)
    assert_that(result["maxItems"], is_(100))

def test_list_zones_ignore_access_default_false(shared_zone_test_context):
    """
    Test that the default ignore access value for a list zones request is false
    """
    result = shared_zone_test_context.list_zones_client.list_zones(status=200)
    assert_that(result["ignoreAccess"], is_(False))

def test_list_zones_invalid_max_items_fails(shared_zone_test_context):
    """
    Test that passing in an invalid value for max items fails
    """
    errors = shared_zone_test_context.list_zones_client.list_zones(max_items=700, status=400)
    assert_that(errors, contains_string("maxItems was 700, maxItems must be between 0 and 100"))


def test_list_zones_no_authorization(shared_zone_test_context):
    """
    Test that we cannot retrieve a list of zones without authorization
    """
    shared_zone_test_context.list_zones_client.list_zones(sign_request=False, status=401)


def test_list_zones_no_search_first_page(shared_zone_test_context):
    """
    Test that the first page of listing zones returns correctly when no name filter is provided
    """
    result = shared_zone_test_context.list_zones_client.list_zones(max_items=3)
    zones = result["zones"]

    assert_that(zones, has_length(3))
    assert_that(zones[0]["name"], is_("list-zones-test-searched-1."))
    assert_that(zones[1]["name"], is_("list-zones-test-searched-2."))
    assert_that(zones[2]["name"], is_("list-zones-test-searched-3."))

    assert_that(result["nextId"], is_("list-zones-test-searched-3."))
    assert_that(result["maxItems"], is_(3))
    assert_that(result, is_not(has_key("startFrom")))
    assert_that(result, is_not(has_key("nameFilter")))


def test_list_zones_no_search_second_page(shared_zone_test_context):
    """
    Test that the second page of listing zones returns correctly when no name filter is provided
    """
    result = shared_zone_test_context.list_zones_client.list_zones(start_from="list-zones-test-searched-2.", max_items=2, status=200)
    zones = result["zones"]

    assert_that(zones, has_length(2))
    assert_that(zones[0]["name"], is_("list-zones-test-searched-3."))
    assert_that(zones[1]["name"], is_("list-zones-test-unfiltered-1."))

    assert_that(result["nextId"], is_("list-zones-test-unfiltered-1."))
    assert_that(result["maxItems"], is_(2))
    assert_that(result["startFrom"], is_("list-zones-test-searched-2."))
    assert_that(result, is_not(has_key("nameFilter")))


def test_list_zones_no_search_last_page(shared_zone_test_context):
    """
    Test that the last page of listing zones returns correctly when no name filter is provided
    """
    result = shared_zone_test_context.list_zones_client.list_zones(start_from="list-zones-test-searched-3.", max_items=4, status=200)
    zones = result["zones"]

    assert_that(zones, has_length(2))
    assert_that(zones[0]["name"], is_("list-zones-test-unfiltered-1."))
    assert_that(zones[1]["name"], is_("list-zones-test-unfiltered-2."))

    assert_that(result, is_not(has_key("nextId")))
    assert_that(result["maxItems"], is_(4))
    assert_that(result["startFrom"], is_("list-zones-test-searched-3."))
    assert_that(result, is_not(has_key("nameFilter")))


def test_list_zones_with_search_first_page(shared_zone_test_context):
    """
    Test that the first page of listing zones returns correctly when a name filter is provided
    """
    result = shared_zone_test_context.list_zones_client.list_zones(name_filter="*searched*", max_items=2, status=200)
    zones = result["zones"]

    assert_that(zones, has_length(2))
    assert_that(zones[0]["name"], is_("list-zones-test-searched-1."))
    assert_that(zones[1]["name"], is_("list-zones-test-searched-2."))

    assert_that(result["nextId"], is_("list-zones-test-searched-2."))
    assert_that(result["maxItems"], is_(2))
    assert_that(result["nameFilter"], is_("*searched*"))
    assert_that(result, is_not(has_key("startFrom")))


def test_list_zones_with_no_results(shared_zone_test_context):
    """
    Test that the response is formed correctly when no results are found
    """
    result = shared_zone_test_context.list_zones_client.list_zones(name_filter="this-wont-be-found", max_items=2, status=200)
    zones = result["zones"]

    assert_that(zones, has_length(0))

    assert_that(result["maxItems"], is_(2))
    assert_that(result["nameFilter"], is_("this-wont-be-found"))
    assert_that(result, is_not(has_key("startFrom")))
    assert_that(result, is_not(has_key("nextId")))


def test_list_zones_with_search_last_page(shared_zone_test_context):
    """
    Test that the second page of listing zones returns correctly when a name filter is provided
    """
    result = shared_zone_test_context.list_zones_client.list_zones(name_filter="*test-searched-3", start_from="list-zones-test-searched-2.", max_items=2, status=200)
    zones = result["zones"]

    assert_that(zones, has_length(1))
    assert_that(zones[0]["name"], is_("list-zones-test-searched-3."))

    assert_that(result, is_not(has_key("nextId")))
    assert_that(result["maxItems"], is_(2))
    assert_that(result["nameFilter"], is_("*test-searched-3"))
    assert_that(result["startFrom"], is_("list-zones-test-searched-2."))

def test_list_zones_ignore_access_success(shared_zone_test_context):
    """
    Test that we can retrieve a list of zones regardless of zone access
    """
    result = shared_zone_test_context.list_zones_client.list_zones(ignore_access=True, status=200)
    retrieved = result["zones"]

    assert_that(result["ignoreAccess"], is_(True))
    assert_that(len(retrieved), greater_than(5))


def test_list_zones_ignore_access_success_with_name_filter(shared_zone_test_context):
    """
    Test that we can retrieve a list of all zones with a name filter
    """
    result = shared_zone_test_context.list_zones_client.list_zones(name_filter="shared", ignore_access=True, status=200)
    retrieved = result["zones"]

    assert_that(result["ignoreAccess"], is_(True))
    assert_that(retrieved, has_item(has_entry("name", "shared.")))
    assert_that(retrieved, has_item(has_entry("accessLevel", "NoAccess")))
