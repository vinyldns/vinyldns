import pytest

from utils import *


@pytest.fixture(scope="module")
def list_my_groups_context(shared_zone_test_context):
    return shared_zone_test_context.list_groups_context


def test_list_my_groups_no_parameters(list_my_groups_context):
    """
    Test that we can get all the groups where a user is a member
    """
    results = list_my_groups_context.client.list_my_groups(status=200)
    assert_that(results, has_length(4))  # 4 fields
    assert_that(results, is_not(has_key("groupNameFilter")))
    assert_that(results, is_not(has_key("startFrom")))
    assert_that(results, is_(has_key("nextId")))
    assert_that(results["maxItems"], is_(100))


def test_get_my_groups_using_old_account_auth(list_my_groups_context):
    """
    Test passing in an account will return an empty set
    """
    results = list_my_groups_context.client.list_my_groups(status=200)
    assert_that(results, has_length(4))
    assert_that(results, is_not(has_key("groupNameFilter")))
    assert_that(results, is_not(has_key("startFrom")))
    assert_that(results, is_(has_key("nextId")))
    assert_that(results["maxItems"], is_(100))


def test_list_my_groups_max_items(list_my_groups_context):
    """
    Tests that when maxItem is set, only return #maxItems items
    """
    results = list_my_groups_context.client.list_my_groups(max_items=5, status=200)

    assert_that(results, has_length(4))  # 4 fields

    assert_that(results, has_key("groups"))
    assert_that(results, is_not(has_key("groupNameFilter")))
    assert_that(results, is_not(has_key("startFrom")))
    assert_that(results, has_key("nextId"))
    assert_that(results["maxItems"], is_(5))


def test_list_my_groups_paging(list_my_groups_context):
    """
    Tests that we can return all items by paging
    """
    results = list_my_groups_context.client.list_my_groups(max_items=20, status=200)

    assert_that(results, has_length(4))  # 4 fields
    assert_that(results, has_key("groups"))
    assert_that(results, is_not(has_key("groupNameFilter")))
    assert_that(results, is_not(has_key("startFrom")))
    assert_that(results, has_key("nextId"))
    assert_that(results["maxItems"], is_(20))

    while "nextId" in results:
        prev = results
        results = list_my_groups_context.client.list_my_groups(max_items=20, start_from=results["nextId"], status=200)

        if "nextId" in results:
            assert_that(results, has_length(5))  # 5 fields
            assert_that(results, has_key("groups"))
            assert_that(results, is_not(has_key("groupNameFilter")))
            assert_that(results["startFrom"], is_(prev["nextId"]))
            assert_that(results, has_key("nextId"))
            assert_that(results["maxItems"], is_(20))

        else:
            assert_that(results, has_length(4))  # 4 fields
            assert_that(results, has_key("groups"))
            assert_that(results, is_not(has_key("groupNameFilter")))
            assert_that(results["startFrom"], is_(prev["nextId"]))
            assert_that(results, is_not(has_key("nextId")))
            assert_that(results["maxItems"], is_(20))


def test_list_my_groups_filter_matches(list_my_groups_context):
    """
    Tests that only matched groups are returned
    """
    results = list_my_groups_context.client.list_my_groups(group_name_filter=f"{list_my_groups_context.group_prefix}-01", status=200)

    assert_that(results, has_length(4))  # 4 fields

    assert_that(results["groups"], has_length(10))
    assert_that(results["groupNameFilter"], is_(f"{list_my_groups_context.group_prefix}-01"))
    assert_that(results, is_not(has_key("startFrom")))
    assert_that(results, is_not(has_key("nextId")))
    assert_that(results["maxItems"], is_(100))

    results["groups"] = sorted(results["groups"], key=lambda x: x["name"])

    for i in range(0, 10):
        assert_that(results["groups"][i]["name"], is_("{0}-{1:0>3}".format(list_my_groups_context.group_prefix, i + 10)))


def test_list_my_groups_no_deleted(list_my_groups_context):
    """
    Tests that no deleted groups are returned
    """
    client = list_my_groups_context.client
    results = client.list_my_groups(max_items=100, status=200)

    assert_that(results, has_key("groups"))
    for g in results["groups"]:
        assert_that(g["status"], is_not("Deleted"))

    while "nextId" in results:
        results = client.list_my_groups(max_items=20, group_name_filter=f"{list_my_groups_context.group_prefix}-", start_from=results["nextId"], status=200)
        assert_that(results, has_key("groups"))
        for g in results["groups"]:
            assert_that(g["status"], is_not("Deleted"))


def test_list_my_groups_with_ignore_access_true(list_my_groups_context):
    """
    Test that we can get all the groups whether a user is a member or not
    """
    results = list_my_groups_context.client.list_my_groups(ignore_access=True, status=200)
    assert_that(results, has_length(4))  # 4 fields
    assert_that(len(results["groups"]), greater_than(50))
    assert_that(results["maxItems"], is_(100))
    assert_that(results["ignoreAccess"], is_(True))


def test_list_my_groups_as_support_user(list_my_groups_context):
    """
    Test that we can get all the groups as a support user, even without ignore_access
    """
    results = list_my_groups_context.support_user_client.list_my_groups(status=200)
    assert_that(results, has_length(4))  # 4 fields
    assert_that(len(results["groups"]), greater_than(50))
    assert_that(results["maxItems"], is_(100))
    assert_that(results["ignoreAccess"], is_(False))


def test_list_my_groups_as_support_user_with_ignore_access_true(list_my_groups_context):
    """
    Test that we can get all the groups as a support user
    """
    results = list_my_groups_context.support_user_client.list_my_groups(ignore_access=True, status=200)
    assert_that(results, has_length(4))  # 4 fields
    assert_that(len(results["groups"]), greater_than(50))
    assert_that(results["maxItems"], is_(100))
    assert_that(results["ignoreAccess"], is_(True))
