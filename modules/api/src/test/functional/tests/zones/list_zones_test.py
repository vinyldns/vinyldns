import pytest

from utils import *


@pytest.fixture(scope="module")
def list_zone_context(shared_zone_test_context):
    return shared_zone_test_context.list_zones


def test_list_zones_success(list_zone_context, shared_zone_test_context):
    """
    Test that we can retrieve a list of the user's zones
    """
    result = shared_zone_test_context.list_zones_client.list_zones(name_filter=f"*{shared_zone_test_context.partition_id}", status=200)
    retrieved = result["zones"]

    assert_that(retrieved, has_length(5))
    assert_that(retrieved, has_item(has_entry("name", list_zone_context.search_zone1["name"])))
    assert_that(retrieved, has_item(has_entry("adminGroupName", list_zone_context.list_zones_group["name"])))
    assert_that(retrieved, has_item(has_entry("backendId", "func-test-backend")))

    assert_that(result["nameFilter"], is_(f"*{shared_zone_test_context.partition_id}"))


def test_list_zones_by_admin_group_name(list_zone_context, shared_zone_test_context):
    """
    Test that we can retrieve list of zones by searching with admin group name
    """
    result = shared_zone_test_context.list_zones_client.list_zones(name_filter=f"list-zones-group{shared_zone_test_context.partition_id}", search_by_admin_group=True, status=200)
    retrieved = result["zones"]

    assert_that(retrieved, has_length(5))
    assert_that(retrieved, has_item(has_entry("name", list_zone_context.search_zone1["name"])))
    assert_that(retrieved, has_item(has_entry("name", list_zone_context.search_zone2["name"])))
    assert_that(retrieved, has_item(has_entry("name", list_zone_context.search_zone3["name"])))
    assert_that(retrieved, has_item(has_entry("name", list_zone_context.non_search_zone1["name"])))
    assert_that(retrieved, has_item(has_entry("name", list_zone_context.non_search_zone2["name"])))
    assert_that(retrieved, has_item(has_entry("adminGroupName", list_zone_context.list_zones_group["name"])))
    assert_that(retrieved, has_item(has_entry("backendId", "func-test-backend")))

    assert_that(result["nameFilter"], is_(f"list-zones-group{shared_zone_test_context.partition_id}"))


def test_list_zones_by_admin_group_name_with_wildcard(list_zone_context, shared_zone_test_context):
    """
    Test that we can retrieve list of zones by searching with admin group name with wildcard character
    """
    result = shared_zone_test_context.list_zones_client.list_zones(name_filter=f"*group{shared_zone_test_context.partition_id}", search_by_admin_group=True, status=200)
    retrieved = result["zones"]

    assert_that(retrieved, has_length(5))
    assert_that(retrieved, has_item(has_entry("name", list_zone_context.search_zone1["name"])))
    assert_that(retrieved, has_item(has_entry("name", list_zone_context.search_zone2["name"])))
    assert_that(retrieved, has_item(has_entry("name", list_zone_context.search_zone3["name"])))
    assert_that(retrieved, has_item(has_entry("name", list_zone_context.non_search_zone1["name"])))
    assert_that(retrieved, has_item(has_entry("name", list_zone_context.non_search_zone2["name"])))
    assert_that(retrieved, has_item(has_entry("adminGroupName", list_zone_context.list_zones_group["name"])))
    assert_that(retrieved, has_item(has_entry("backendId", "func-test-backend")))

    assert_that(result["nameFilter"], is_(f"*group{shared_zone_test_context.partition_id}"))


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


def test_list_zones_no_search_first_page(list_zone_context, shared_zone_test_context):
    """
    Test that the first page of listing zones returns correctly when no name filter is provided
    """
    result = shared_zone_test_context.list_zones_client.list_zones(name_filter=f"*{shared_zone_test_context.partition_id}", max_items=3)
    zones = result["zones"]

    assert_that(zones, has_length(3))
    assert_that(zones[0]["name"], is_(list_zone_context.search_zone1["name"]))
    assert_that(zones[1]["name"], is_(list_zone_context.search_zone2["name"]))
    assert_that(zones[2]["name"], is_(list_zone_context.search_zone3["name"]))

    assert_that(result["nextId"], is_(list_zone_context.search_zone3["name"]))
    assert_that(result["maxItems"], is_(3))
    assert_that(result, is_not(has_key("startFrom")))

    assert_that(result["nameFilter"], is_(f"*{shared_zone_test_context.partition_id}"))


def test_list_zones_no_search_second_page(list_zone_context, shared_zone_test_context):
    """
    Test that the second page of listing zones returns correctly when no name filter is provided
    """
    result = shared_zone_test_context.list_zones_client.list_zones(name_filter=f"*{shared_zone_test_context.partition_id}",
                                                                   start_from=list_zone_context.search_zone2["name"],
                                                                   max_items=2,
                                                                   status=200)
    zones = result["zones"]

    assert_that(zones, has_length(2))
    assert_that(zones[0]["name"], is_(list_zone_context.search_zone3["name"]))
    assert_that(zones[1]["name"], is_(list_zone_context.non_search_zone1["name"]))

    assert_that(result["nextId"], is_(list_zone_context.non_search_zone1["name"]))
    assert_that(result["maxItems"], is_(2))
    assert_that(result["startFrom"], is_(list_zone_context.search_zone2["name"]))

    assert_that(result["nameFilter"], is_(f"*{shared_zone_test_context.partition_id}"))


def test_list_zones_no_search_last_page(list_zone_context, shared_zone_test_context):
    """
    Test that the last page of listing zones returns correctly when no name filter is provided
    """
    result = shared_zone_test_context.list_zones_client.list_zones(name_filter=f"*{shared_zone_test_context.partition_id}",
                                                                   start_from=list_zone_context.search_zone3["name"],
                                                                   max_items=4,
                                                                   status=200)
    zones = result["zones"]

    assert_that(zones, has_length(2))
    assert_that(zones[0]["name"], is_(list_zone_context.non_search_zone1["name"]))
    assert_that(zones[1]["name"], is_(list_zone_context.non_search_zone2["name"]))

    assert_that(result, is_not(has_key("nextId")))
    assert_that(result["maxItems"], is_(4))
    assert_that(result["startFrom"], is_(list_zone_context.search_zone3["name"]))
    assert_that(result["nameFilter"], is_(f"*{shared_zone_test_context.partition_id}"))


def test_list_zones_with_search_first_page(list_zone_context, shared_zone_test_context):
    """
    Test that the first page of listing zones returns correctly when a name filter is provided
    """
    result = shared_zone_test_context.list_zones_client.list_zones(name_filter=f"*searched*{shared_zone_test_context.partition_id}", max_items=2, status=200)
    zones = result["zones"]

    assert_that(zones, has_length(2))
    assert_that(zones[0]["name"], is_(list_zone_context.search_zone1["name"]))
    assert_that(zones[1]["name"], is_(list_zone_context.search_zone2["name"]))

    assert_that(result["nextId"], is_(list_zone_context.search_zone2["name"]))
    assert_that(result["maxItems"], is_(2))
    assert_that(result["nameFilter"], is_(f"*searched*{shared_zone_test_context.partition_id}"))
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


def test_list_zones_with_search_last_page(list_zone_context, shared_zone_test_context):
    """
    Test that the second page of listing zones returns correctly when a name filter is provided
    """
    result = shared_zone_test_context.list_zones_client.list_zones(name_filter=f"*test-searched-3{shared_zone_test_context.partition_id}",
                                                                   start_from=list_zone_context.search_zone2["name"],
                                                                   max_items=2,
                                                                   status=200)
    zones = result["zones"]

    assert_that(zones, has_length(1))
    assert_that(zones[0]["name"], is_(list_zone_context.search_zone3["name"]))

    assert_that(result, is_not(has_key("nextId")))
    assert_that(result["maxItems"], is_(2))
    assert_that(result["nameFilter"], is_(f"*test-searched-3{shared_zone_test_context.partition_id}"))
    assert_that(result["startFrom"], is_(list_zone_context.search_zone2["name"]))


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
    Test that we can retrieve a list of all zones with a name filter. Should have Read access to shared zone
    """
    result = shared_zone_test_context.list_zones_client.list_zones(name_filter=shared_zone_test_context.shared_zone["name"].rstrip("."), ignore_access=True, status=200)
    retrieved = result["zones"]

    assert_that(result["ignoreAccess"], is_(True))
    assert_that(retrieved, has_item(has_entry("name", shared_zone_test_context.shared_zone["name"])))
    assert_that(retrieved, has_item(has_entry("accessLevel", "Read")))
