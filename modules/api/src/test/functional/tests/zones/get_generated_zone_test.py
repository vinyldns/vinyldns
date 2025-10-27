import pytest

from utils import *


def test_get_generate_zone_by_id(shared_zone_test_context):
    """
    Test get an existing zone by id
    """
    client = shared_zone_test_context.ok_vinyldns_client

    result = client.get_generate_zone(shared_zone_test_context.ok_generate_zone["id"], status=200)

    assert_that(result["id"], is_(shared_zone_test_context.ok_generate_zone["id"]))
    assert_that(result["groupId"], is_(shared_zone_test_context.ok_group["id"]))
    assert_that(result["email"], is_(shared_zone_test_context.ok_generate_zone["email"]))

def test_get_generate_zone_by_id_returns_404_when_not_found(shared_zone_test_context):
    """
    Test get an existing zone returns a 404 when the zone is not found
    """
    client = shared_zone_test_context.ok_vinyldns_client

    client.get_generate_zone(str(uuid.uuid4()), status=404)


def test_get_generate_zone_by_id_no_authorization(shared_zone_test_context):
    """
    Test get an existing zone by id without authorization
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.get_generate_zone("123456", sign_request=False, status=401)

def test_get_generate_zone_by_name(shared_zone_test_context):
    """
    Test get an existing zone by name
    """
    client = shared_zone_test_context.ok_vinyldns_client

    result = client.get_generate_zone_by_name(shared_zone_test_context.system_test_generate_zone["zoneName"], status=200)

    assert_that(result["id"], is_(shared_zone_test_context.system_test_generate_zone["id"]))
    assert_that(result["zoneName"], is_(shared_zone_test_context.system_test_generate_zone["zoneName"]))
    assert_that(result["groupId"], is_(shared_zone_test_context.ok_group["id"]))


def test_get_generate_zone_by_name_without_trailing_dot_succeeds(shared_zone_test_context):
    """
    Test get an existing zone by name without including trailing dot
    """
    client = shared_zone_test_context.ok_vinyldns_client

    result = client.get_generate_zone_by_name(shared_zone_test_context.system_test_generate_zone["zoneName"], status=200)

    assert_that(result["id"], is_(shared_zone_test_context.system_test_generate_zone["id"]))
    assert_that(result["zoneName"], is_(shared_zone_test_context.system_test_generate_zone["zoneName"]))
    assert_that(result["groupId"], is_(shared_zone_test_context.ok_group["id"]))

def test_get_generate_zone_by_name_succeeds_without_access(shared_zone_test_context):
    """
    Test get an existing zone by name without access
    """
    client = shared_zone_test_context.dummy_vinyldns_client

    result = client.get_generate_zone_by_name(shared_zone_test_context.system_test_generate_zone["zoneName"], status=200)
    assert_that(result["id"], is_(shared_zone_test_context.system_test_generate_zone["id"]))
    assert_that(result["zoneName"], is_(shared_zone_test_context.system_test_generate_zone["zoneName"]))
    assert_that(result["groupId"], is_(shared_zone_test_context.ok_group["id"]))

def test_get_generate_zone_by_name_returns_404_when_not_found(shared_zone_test_context):
    """
    Test get an existing zone returns a 404 when the zone is not found
    """
    client = shared_zone_test_context.ok_vinyldns_client

    client.get_generate_zone_by_name("zone_name_does_not_exist", status=404)


def test_get_generate_zone_nameservers(shared_zone_test_context):
    """
    Test that you can get possible backend ids for zones
    """
    client = shared_zone_test_context.ok_vinyldns_client
    response = client.get_nameservers(status=200)
    expected = ['172.17.42.1.', 'ns1.parent.com.', 'ns1.parent.com1.', 'ns1.parent.com2.', 'ns1.parent.com3.', 'ns1.parent.com4.']
    assert_that(response, is_(expected))


def test_get_generate_zone_dns_allowed_providers(shared_zone_test_context):
    """
    Test that you can get possible backend ids for zones
    """
    client = shared_zone_test_context.ok_vinyldns_client
    response = client.get_allowed_dns_provider(status=200)
    expected = ['bind', 'powerdns']
    assert_that(response, is_(expected))
