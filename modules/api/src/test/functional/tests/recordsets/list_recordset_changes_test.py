import pytest

from utils import *


def check_changes_response(response, recordChanges=False, nextId=False, startFrom=False, maxItems=100):
    """
    :param response: return value of list_recordset_changes()
    :param recordChanges: true if not empty or False if empty, cannot check exact values because don't have access to all attributes
    :param nextId: true if exists, false if doesn"t, wouldn"t be able to check exact value
    :param startFrom: the string for startFrom or false if doesnt exist
    :param maxItems: maxItems is defined as an Int by default so will always return an Int
    """
    assert_that(response, has_key("zoneId"))  # always defined as random string
    if recordChanges:
        assert_that(response["recordSetChanges"], is_not(has_length(0)))
    else:
        assert_that(response["recordSetChanges"], has_length(0))
    if nextId:
        assert_that(response, has_key("nextId"))
    else:
        assert_that(response, is_not(has_key("nextId")))
    if startFrom:
        assert_that(response["startFrom"], is_(startFrom))
    else:
        assert_that(response, is_not(has_key("startFrom")))
    assert_that(response["maxItems"], is_(maxItems))

    for change in response["recordSetChanges"]:
        assert_that(change["userName"], is_("history-user"))


def check_change_history_response(response, fqdn, type, recordChanges=False, nextId=False, startFrom=False,
                                  maxItems=100):
    """
    :param type: type of the record
    :param fqdn: fqdn of the record
    :param response: return value of list_recordset_changes()
    :param recordChanges: true if not empty or False if empty, cannot check exact values because don't have access to all attributes
    :param nextId: true if exists, false if doesn't, wouldn't be able to check exact value
    :param startFrom: the string for startFrom or false if doesnt exist
    :param maxItems: maxItems is defined as an Int by default so will always return an Int
    """
    assert_that(response, has_key("zoneId"))  # always defined as random string
    if recordChanges:
        assert_that(response["recordSetChanges"], is_not(has_length(0)))
    else:
        assert_that(response["recordSetChanges"], has_length(0))
    if nextId:
        assert_that(response, has_key("nextId"))
    else:
        assert_that(response, is_not(has_key("nextId")))
    if startFrom:
        assert_that(response["startFrom"], is_(startFrom))
    else:
        assert_that(response, is_not(has_key("startFrom")))
    assert_that(response["maxItems"], is_(maxItems))

    for change in response["recordSetChanges"]:
        assert_that(change["userName"], is_("history-user"))
        for recordset in change["recordSet"]:
            assert_that(change["recordSet"]["type"], is_(type))
            assert_that(change["recordSet"]["name"] + "." + response["recordSetChanges"][0]["zone"]["name"], is_(fqdn))


def test_list_recordset_changes_no_authorization(shared_zone_test_context):
    """
    Test that recordset changes without authorization fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    client.list_recordset_changes("12345", sign_request=False, status=401)


def test_list_recordset_changes_member_auth_success(shared_zone_test_context):
    """
    Test recordset changes succeeds with membership auth for member of admin group
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.ok_zone
    client.list_recordset_changes(zone["id"], status=200)


def test_list_recordset_changes_member_auth_no_access(shared_zone_test_context):
    """
    Test recordset changes fails for user not in admin group with no acl rules
    """
    client = shared_zone_test_context.dummy_vinyldns_client
    zone = shared_zone_test_context.ok_zone
    client.list_recordset_changes(zone["id"], status=403)


@pytest.mark.serial
def test_list_recordset_changes_member_auth_with_acl(shared_zone_test_context):
    """
    Test recordset changes succeeds for user with acl rules
    """
    zone = shared_zone_test_context.ok_zone
    acl_rule = generate_acl_rule("Write", userId="dummy")
    try:
        client = shared_zone_test_context.dummy_vinyldns_client

        client.list_recordset_changes(zone["id"], status=403)
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])
        client.list_recordset_changes(zone["id"], status=200)
    finally:
        clear_ok_acl_rules(shared_zone_test_context)


def test_list_recordset_changes_no_start(shared_zone_test_context):
    """
    Test getting all recordset changes on one page (max items will default to default value)
    """
    client = shared_zone_test_context.history_client
    original_zone = shared_zone_test_context.history_zone
    response = client.list_recordset_changes(original_zone["id"], start_from=None, max_items=None)
    check_changes_response(response, recordChanges=True, startFrom=False, nextId=False)

    deleteChanges = response["recordSetChanges"][0:3]
    updateChanges = response["recordSetChanges"][3:6]
    createChanges = response["recordSetChanges"][6:9]

    for change in deleteChanges:
        assert_that(change["changeType"], is_("Delete"))
    for change in updateChanges:
        assert_that(change["changeType"], is_("Update"))
    for change in createChanges:
        assert_that(change["changeType"], is_("Create"))


def test_list_recordset_changes_paging(shared_zone_test_context):
    """
    Test paging for recordset changes can use previous nextId as start key of next page
    """
    client = shared_zone_test_context.history_client
    original_zone = shared_zone_test_context.history_zone

    response_1 = client.list_recordset_changes(original_zone["id"], start_from=None, max_items=3)
    response_2 = client.list_recordset_changes(original_zone["id"], start_from=response_1["nextId"], max_items=3)
    # nextId differs local/in dev where we get exactly the last item
    # Requesting one over the total in the local in memory dynamo will force consistent behavior.
    response_3 = client.list_recordset_changes(original_zone["id"], start_from=response_2["nextId"], max_items=11)

    check_changes_response(response_1, recordChanges=True, nextId=True, startFrom=False, maxItems=3)
    check_changes_response(response_2, recordChanges=True, nextId=True, startFrom=response_1["nextId"], maxItems=3)
    check_changes_response(response_3, recordChanges=True, nextId=False, startFrom=response_2["nextId"], maxItems=11)

    for change in response_1["recordSetChanges"]:
        assert_that(change["changeType"], is_("Delete"))
    for change in response_2["recordSetChanges"]:
        assert_that(change["changeType"], is_("Update"))
    for change in response_3["recordSetChanges"]:
        assert_that(change["changeType"], is_("Create"))


def test_list_recordset_changes_exhausted(shared_zone_test_context):
    """
    Test next id is none when zone changes are exhausted
    """
    client = shared_zone_test_context.history_client
    original_zone = shared_zone_test_context.history_zone
    response = client.list_recordset_changes(original_zone["id"], start_from=None, max_items=17)
    check_changes_response(response, recordChanges=True, startFrom=False, nextId=False, maxItems=17)

    deleteChanges = response["recordSetChanges"][0:3]
    updateChanges = response["recordSetChanges"][3:6]
    createChanges = response["recordSetChanges"][6:9]

    for change in deleteChanges:
        assert_that(change["changeType"], is_("Delete"))
    for change in updateChanges:
        assert_that(change["changeType"], is_("Update"))
    for change in createChanges:
        assert_that(change["changeType"], is_("Create"))


def test_list_recordset_returning_no_changes(shared_zone_test_context):
    """
    Pass in startFrom of "2000" should return empty list because start key exceeded number of recordset changes
    """
    client = shared_zone_test_context.history_client
    original_zone = shared_zone_test_context.history_zone
    response = client.list_recordset_changes(original_zone["id"], start_from=2000, max_items=None)
    check_changes_response(response, recordChanges=False, startFrom=2000, nextId=False)


def test_list_recordset_changes_default_max_items(shared_zone_test_context):
    """
    Test default max items is 100
    """
    client = shared_zone_test_context.history_client
    original_zone = shared_zone_test_context.history_zone

    response = client.list_recordset_changes(original_zone["id"], start_from=None, max_items=None)
    check_changes_response(response, recordChanges=True, startFrom=False, nextId=False, maxItems=100)


def test_list_recordset_changes_max_items_boundaries(shared_zone_test_context):
    """
    Test 0 < max_items <= 100
    """
    client = shared_zone_test_context.history_client
    original_zone = shared_zone_test_context.history_zone

    too_large = client.list_recordset_changes(original_zone["id"], start_from=None, max_items=101, status=400)
    too_small = client.list_recordset_changes(original_zone["id"], start_from=None, max_items=0, status=400)

    assert_that(too_large, is_("maxItems was 101, maxItems must be between 0 exclusive and 100 inclusive"))
    assert_that(too_small, is_("maxItems was 0, maxItems must be between 0 exclusive and 100 inclusive"))


def test_list_recordset_history_no_authorization(shared_zone_test_context):
    """
    Test that recordset history without authorization fails
    """
    client = shared_zone_test_context.history_client
    zone_id = shared_zone_test_context.history_zone["id"]
    fqdn = "test-create-cname-ok.system-test-history1."
    type = "CNAME"
    client.list_recordset_change_history(zone_id, fqdn, type, sign_request=False, status=401)


def test_list_recordset_history_member_auth_success(shared_zone_test_context):
    """
    Test recordset history succeeds with membership auth for member of admin group
    """
    client = shared_zone_test_context.history_client
    zone_id = shared_zone_test_context.history_zone["id"]
    fqdn = "test-create-cname-ok.system-test-history1."
    type = "CNAME"
    response = client.list_recordset_change_history(zone_id, fqdn, type, status=200)
    check_change_history_response(response, fqdn, type, recordChanges=True, startFrom=False, nextId=False)


def test_list_recordset_history_member_auth_no_access(shared_zone_test_context):
    """
    Test recordset history fails for user not in admin group with no acl rules
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone_id = shared_zone_test_context.history_zone["id"]
    fqdn = "test-create-cname-ok.system-test-history1."
    type = "CNAME"
    client.list_recordset_change_history(zone_id, fqdn, type, status=403)


def test_list_recordset_history_success(shared_zone_test_context):
    """
    Test recordset history succeeds with membership auth for member of admin group
    """
    client = shared_zone_test_context.history_client
    zone_id = shared_zone_test_context.history_zone["id"]
    fqdn = "test-create-cname-ok.system-test-history1."
    type = "CNAME"
    response = client.list_recordset_change_history(zone_id, fqdn, type, status=200)
    check_change_history_response(response, fqdn, type, recordChanges=True, startFrom=False, nextId=False)


def test_list_recordset_history_paging(shared_zone_test_context):
    """
    Test paging for recordset history can use previous nextId as start key of next page
    """
    client = shared_zone_test_context.history_client

    zone_id = shared_zone_test_context.history_zone["id"]
    fqdn = "test-create-cname-ok.system-test-history1."
    type = "CNAME"

    response_1 = client.list_recordset_change_history(zone_id, fqdn, type, start_from=None, max_items=1)
    response_2 = client.list_recordset_change_history(zone_id, fqdn, type, start_from=response_1["nextId"], max_items=1)

    check_change_history_response(response_1, fqdn, type, recordChanges=True, nextId=True, startFrom=False, maxItems=1)
    check_change_history_response(response_2, fqdn, type, recordChanges=True, nextId=True, startFrom=response_1["nextId"], maxItems=1)


def test_list_recordset_history_returning_no_changes(shared_zone_test_context):
    """
    Pass in startFrom of "2000" should return empty list because start key exceeded number of recordset change history
    """
    client = shared_zone_test_context.history_client
    zone_id = shared_zone_test_context.history_zone["id"]
    fqdn = "test-create-cname-ok.system-test-history1."
    type = "CNAME"
    response = client.list_recordset_change_history(zone_id, fqdn, type, start_from=2000, max_items=None)
    assert_that(response["recordSetChanges"], has_length(0))
    assert_that(response["startFrom"], is_(2000))
    assert_that(response["maxItems"], is_(100))


def test_list_recordset_history_default_max_items(shared_zone_test_context):
    """
    Test default max items is 100
    """
    client = shared_zone_test_context.history_client
    zone_id = shared_zone_test_context.history_zone["id"]
    fqdn = "test-create-cname-ok.system-test-history1."
    type = "CNAME"

    response = client.list_recordset_change_history(zone_id, fqdn, type, start_from=None, max_items=None)
    check_change_history_response(response, fqdn, type, recordChanges=True, startFrom=False, nextId=False, maxItems=100)


def test_list_recordset_history_max_items_boundaries(shared_zone_test_context):
    """
    Test 0 < max_items <= 100
    """
    client = shared_zone_test_context.history_client
    zone_id = shared_zone_test_context.history_zone["id"]
    fqdn = "test-create-cname-ok.system-test-history1."
    type = "CNAME"

    too_large = client.list_recordset_change_history(zone_id, fqdn, type, start_from=None, max_items=101, status=400)
    too_small = client.list_recordset_change_history(zone_id, fqdn, type, start_from=None, max_items=0, status=400)

    assert_that(too_large, is_("maxItems was 101, maxItems must be between 0 exclusive and 100 inclusive"))
    assert_that(too_small, is_("maxItems was 0, maxItems must be between 0 exclusive and 100 inclusive"))
