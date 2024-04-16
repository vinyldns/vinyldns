import pytest
from datetime import datetime
from hamcrest import *


@pytest.fixture(scope="module")
def group_activity_context(request, shared_zone_test_context):
    return {
        "created_group": shared_zone_test_context.group_activity_created,
        "updated_groups": shared_zone_test_context.group_activity_updated
    }


def test_list_group_activity_start_from_success(group_activity_context, shared_zone_test_context):
    """
    Test that we can list the changes starting from a given timestamp
    """

    client = shared_zone_test_context.ok_vinyldns_client
    created_group = group_activity_context["created_group"]
    updated_groups = group_activity_context["updated_groups"]

    # updated groups holds all the groups just updated, not the original group that has no dummy user
    # [0] = dummy000; [1] = dummy001; [2] = dummy002; [3] = dummy003, etc.

    # we grab 3 items, which when sorted by most recent will give the 3 most recent items
    page_one = client.get_group_changes(created_group["id"], max_items=3, status=200)

    # now, we say give me all changes since the start_from, which should yield 8-7-6-5-4
    result = client.get_group_changes(created_group["id"], start_from=page_one["nextId"], max_items=5, status=200)

    assert_that(result["changes"], has_length(5))
    assert_that(result["maxItems"], is_(5))
    assert_that(result["startFrom"], is_(page_one["nextId"]))
    assert_that(result["nextId"], is_not(none()))

    # we should have, in order, changes 8 7 6 5 4
    # changes that came in worked off...
    # <no dummy>, 000, 001, 002, 003, 004, 005, 006 <-- 8th change
    expected_start = 6
    for i in range(0, 5):
        # The new group should be the later, the group it is replacing should be one back
        assert_that(result["changes"][i]["newGroup"], is_(updated_groups[expected_start - i]))
        assert_that(result["changes"][i]["oldGroup"], is_(updated_groups[expected_start - i - 1]))


def test_list_group_activity_start_from_random_number(group_activity_context, shared_zone_test_context):
    """
    Test that we can start from a random number, but it returns no changes
    """
    client = shared_zone_test_context.ok_vinyldns_client
    created_group = group_activity_context["created_group"]
    updated_groups = group_activity_context["updated_groups"]

    result = client.get_group_changes(created_group["id"], start_from=999, max_items=5, status=200)

    # there are 10 updates, proceeded by 1 create
    assert_that(result["changes"], has_length(0))
    assert_that(result["maxItems"], is_(5))


def test_list_group_activity_max_item_success(group_activity_context, shared_zone_test_context):
    """
    Test that we can set the max_items returned
    """
    client = shared_zone_test_context.ok_vinyldns_client
    created_group = group_activity_context["created_group"]
    updated_groups = group_activity_context["updated_groups"]

    result = client.get_group_changes(created_group["id"], max_items=4, status=200)

    # there are 200 updates, and 1 create
    assert_that(result["changes"], has_length(4))
    assert_that(result["maxItems"], is_(4))
    assert_that(result, is_not(has_key("startFrom")))
    assert_that(result["nextId"], is_not(none()))

    for i in range(0, 4):
        assert_that(result["changes"][i]["newGroup"], is_(updated_groups[9 - i]))
        assert_that(result["changes"][i]["oldGroup"], is_(updated_groups[9 - i - 1]))


def test_list_group_activity_max_item_zero(group_activity_context, shared_zone_test_context):
    """
    Test that max_item set to zero fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    created_group = group_activity_context["created_group"]
    client.get_group_changes(created_group["id"], max_items=0, status=400)


def test_list_group_activity_max_item_over_1000(group_activity_context, shared_zone_test_context):
    """
    Test that when max_item is over 1000 fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    created_group = group_activity_context["created_group"]
    client.get_group_changes(created_group["id"], max_items=1001, status=400)


def test_get_group_changes_paging(group_activity_context, shared_zone_test_context):
    """
    Test that we can page through multiple pages of group changes
    """
    client = shared_zone_test_context.ok_vinyldns_client
    created_group = group_activity_context["created_group"]
    updated_groups = group_activity_context["updated_groups"]

    page_one = client.get_group_changes(created_group["id"], max_items=5, status=200)
    page_two = client.get_group_changes(created_group["id"], start_from=page_one["nextId"], max_items=5, status=200)
    page_three = client.get_group_changes(created_group["id"], start_from=page_two["nextId"], max_items=5, status=200)

    assert_that(page_one["changes"], has_length(5))
    assert_that(page_one["maxItems"], is_(5))
    assert_that(page_one, is_not(has_key("startFrom")))
    assert_that(page_one["nextId"], is_not(none()))

    for i in range(0, 5):
        assert_that(page_one["changes"][i]["newGroup"], is_(updated_groups[9 - i]))
        assert_that(page_one["changes"][i]["oldGroup"], is_(updated_groups[9 - i - 1]))

    assert_that(page_two["changes"], has_length(5))
    assert_that(page_two["maxItems"], is_(5))
    assert_that(page_two["startFrom"], is_(page_one["nextId"]))
    assert_that(page_two["nextId"], is_not(none()))

    # Do not compare the last item on the second page, as it is touches the original group
    for i in range(5, 9):
        assert_that(page_two["changes"][i - 5]["newGroup"], is_(updated_groups[9 - i]))
        assert_that(page_two["changes"][i - 5]["oldGroup"], is_(updated_groups[9 - i - 1]))

    # Last page should be only the very last change
    assert_that(page_three["changes"], has_length(1))
    assert_that(page_three["maxItems"], is_(5))
    assert_that(page_three["startFrom"], is_(page_two["nextId"]))
    assert_that(page_three, is_not(has_key("nextId")))

    assert_that(page_three["changes"][0]["newGroup"], is_(created_group))


def test_get_group_changes_unauthorized(shared_zone_test_context):
    """
    Tests that non-group members cannot get group changes
    """
    client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    saved_group = None
    try:
        new_group = {
            "name": "test-list-group-admins-unauthed-2",
            "email": "test@test.com",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}]
        }
        saved_group = client.create_group(new_group, status=200)

        dummy_client.get_group_changes(saved_group["id"], status=403)
        client.get_group_changes(saved_group["id"], status=200)
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))
