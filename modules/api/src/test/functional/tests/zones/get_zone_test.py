import pytest

from utils import *


def test_get_zone_by_id(shared_zone_test_context):
    """
    Test get an existing zone by id
    """
    client = shared_zone_test_context.ok_vinyldns_client

    result = client.get_zone(shared_zone_test_context.system_test_zone["id"], status=200)
    retrieved = result["zone"]

    assert_that(retrieved["id"], is_(shared_zone_test_context.system_test_zone["id"]))
    assert_that(retrieved["adminGroupName"], is_(shared_zone_test_context.ok_group["name"]))
    assert_that(retrieved["accessLevel"], is_("Delete"))


def test_get_zone_shared_by_id_as_owner(shared_zone_test_context):
    """
    Test get an existing shared zone by id as a zone owner
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    group_name = shared_zone_test_context.shared_record_group["name"]
    result = client.get_zone(shared_zone_test_context.shared_zone["id"], status=200)
    retrieved = result["zone"]

    assert_that(retrieved["id"], is_(shared_zone_test_context.shared_zone["id"]))
    assert_that(retrieved["adminGroupName"], is_(group_name))
    assert_that(retrieved["shared"], is_(True))
    assert_that(retrieved["accessLevel"], is_("Delete"))


def test_get_zone_shared_by_id_non_owner(shared_zone_test_context):
    """
    Test get an existing shared zone by id as a non-zone-owner. Non-owner should have read-only access
    """
    client = shared_zone_test_context.dummy_vinyldns_client
    group_name = shared_zone_test_context.shared_record_group["name"]
    result = client.get_zone(shared_zone_test_context.shared_zone["id"], status=200)
    retrieved = result["zone"]

    assert_that(retrieved["id"], is_(shared_zone_test_context.shared_zone["id"]))
    assert_that(retrieved["adminGroupName"], is_(group_name))
    assert_that(retrieved["shared"], is_(True))
    assert_that(retrieved["accessLevel"], is_("Read"))

def test_get_zone_private_by_id_fails_without_access(shared_zone_test_context):
    """
    Test get an existing zone by id without access
    """
    client = shared_zone_test_context.dummy_vinyldns_client

    client.get_zone(shared_zone_test_context.ok_zone["id"], status=403)


def test_get_zone_by_id_returns_404_when_not_found(shared_zone_test_context):
    """
    Test get an existing zone returns a 404 when the zone is not found
    """
    client = shared_zone_test_context.ok_vinyldns_client

    client.get_zone(str(uuid.uuid4()), status=404)


def test_get_zone_by_id_no_authorization(shared_zone_test_context):
    """
    Test get an existing zone by id without authorization
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.get_zone("123456", sign_request=False, status=401)


@pytest.mark.serial
def test_get_zone_by_id_includes_acl_display_name(shared_zone_test_context):
    """
    Test get an existing zone with acl rules
    """
    client = shared_zone_test_context.ok_vinyldns_client

    user_acl_rule = generate_acl_rule("Write", userId="ok", recordTypes=[])
    group_acl_rule = generate_acl_rule("Write", groupId=shared_zone_test_context.ok_group["id"], recordTypes=[])
    bad_acl_rule = generate_acl_rule("Write", userId="badId", recordTypes=[])

    client.add_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone["id"], user_acl_rule, status=202)
    client.add_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone["id"], group_acl_rule, status=202)
    client.add_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone["id"], bad_acl_rule, status=202)

    result = client.get_zone(shared_zone_test_context.system_test_zone["id"], status=200)
    retrieved = result["zone"]

    assert_that(retrieved["id"], is_(shared_zone_test_context.system_test_zone["id"]))
    assert_that(retrieved["adminGroupName"], is_(shared_zone_test_context.ok_group["name"]))

    acl = retrieved["acl"]["rules"]

    user_acl_rule["displayName"] = "ok"
    group_acl_rule["displayName"] = shared_zone_test_context.ok_group["name"]

    assert_that(acl, has_item(user_acl_rule))
    assert_that(acl, has_item(group_acl_rule))
    assert_that(len(acl), is_(2))


def test_get_zone_by_name(shared_zone_test_context):
    """
    Test get an existing zone by name
    """
    client = shared_zone_test_context.ok_vinyldns_client

    result = client.get_zone_by_name(shared_zone_test_context.system_test_zone["name"], status=200)["zone"]

    assert_that(result["id"], is_(shared_zone_test_context.system_test_zone["id"]))
    assert_that(result["name"], is_(shared_zone_test_context.system_test_zone["name"]))
    assert_that(result["adminGroupName"], is_(shared_zone_test_context.ok_group["name"]))
    assert_that(result["accessLevel"], is_("Delete"))


def test_get_zone_by_name_without_trailing_dot_succeeds(shared_zone_test_context):
    """
    Test get an existing zone by name without including trailing dot
    """
    client = shared_zone_test_context.ok_vinyldns_client

    result = client.get_zone_by_name(shared_zone_test_context.system_test_zone["name"], status=200)["zone"]

    assert_that(result["id"], is_(shared_zone_test_context.system_test_zone["id"]))
    assert_that(result["name"], is_(shared_zone_test_context.system_test_zone["name"]))
    assert_that(result["adminGroupName"], is_(shared_zone_test_context.ok_group["name"]))
    assert_that(result["accessLevel"], is_("Delete"))


def test_get_zone_by_name_shared_zone_succeeds(shared_zone_test_context):
    """
    Test get an existing zone by name
    """
    client = shared_zone_test_context.ok_vinyldns_client

    result = client.get_zone_by_name(shared_zone_test_context.shared_zone["name"], status=200)["zone"]
    assert_that(result["id"], is_(shared_zone_test_context.shared_zone["id"]))
    assert_that(result["name"], is_(shared_zone_test_context.shared_zone["name"]))
    assert_that(result["adminGroupName"], is_(shared_zone_test_context.shared_record_group["name"]))
    assert_that(result["accessLevel"], is_("Delete"))


def test_get_zone_by_name_succeeds_without_access(shared_zone_test_context):
    """
    Test get an existing zone by name without access
    """
    client = shared_zone_test_context.dummy_vinyldns_client

    result = client.get_zone_by_name(shared_zone_test_context.system_test_zone["name"], status=200)["zone"]
    assert_that(result["id"], is_(shared_zone_test_context.system_test_zone["id"]))
    assert_that(result["name"], is_(shared_zone_test_context.system_test_zone["name"]))
    assert_that(result["adminGroupName"], is_(shared_zone_test_context.ok_group["name"]))
    assert_that(result["accessLevel"], is_("NoAccess"))


def test_get_zone_by_name_returns_404_when_not_found(shared_zone_test_context):
    """
    Test get an existing zone returns a 404 when the zone is not found
    """
    client = shared_zone_test_context.ok_vinyldns_client

    client.get_zone_by_name("zone_name_does_not_exist", status=404)


def test_get_zone_backend_ids(shared_zone_test_context):
    """
    Test that you can get possible backend ids for zones
    """
    client = shared_zone_test_context.ok_vinyldns_client
    response = client.get_backend_ids(status=200)
    assert_that(response, has_item("func-test-backend"))
