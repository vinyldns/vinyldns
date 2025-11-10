import copy
from typing import List, Dict

import pytest

from utils import *

# Defined in docker bind9 conf file
TSIG_KEYS = [
    ("vinyldns-sha1.", "0nIhR1zS/nHUg2n0AIIUyJwXUyQ=", "HMAC-SHA1"),
    ("vinyldns-sha224.", "yud/F666YjcnfqPSulHaYXrNObNnS1Jv+rX61A==", "HMAC-SHA224"),
    ("vinyldns-sha256.", "wzLsDGgPRxFaC6z/9Bc0n1W4KrnmaUdFCgCn2+7zbPU=", "HMAC-SHA256"),
    ("vinyldns-sha384.", "ne9jSUJ7PBGveM37aOX+ZmBXQgz1EqkbYBO1s5l/LNpjEno4OfYvGo1Lv1rnw3pE", "HMAC-SHA384"),
    ("vinyldns-sha512.", "xfKA0DYb88tiUGND+cWddwUg3/SugYSsdvCfBOJ1jr8MEdgbVRyrlVDEXLsfTUGorQ3ShENdymw2yw+rTr+lwA==", "HMAC-SHA512"),
]


@pytest.mark.serial
@pytest.mark.parametrize("key_name,key_secret,key_alg", TSIG_KEYS)
def test_create_zone_with_tsigs(shared_zone_test_context, key_name, key_secret, key_alg):
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id}."

    zone = {
        "name": zone_name,
        "email": "test@test.com",
        "adminGroupId": shared_zone_test_context.ok_group["id"],
        "connection": {
            "name": key_name,
            "keyName": key_name,
            "key": key_secret,
            "primaryServer": VinylDNSTestContext.name_server_ip,
            "algorithm": key_alg
        }
    }

    try:
        zone_change = client.create_zone(zone, status=202)
        zone = zone_change["zone"]
        client.wait_until_zone_active(zone_change["zone"]["id"])

        # Check that it was internally stored correctly using GET
        zone_get = client.get_zone(zone["id"])["zone"]
        assert_that(zone_get["name"], is_(zone_name))
        assert_that("connection" in zone_get)
        assert_that(zone_get["connection"]["keyName"], is_(key_name))
        assert_that(zone_get["connection"]["algorithm"], is_(key_alg))
    finally:
        if "id" in zone:
            client.abandon_zones([zone["id"]], status=202)


@pytest.mark.serial
def test_create_zone_success(shared_zone_test_context):
    """
    Test successfully creating a zone
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        # Include a space in the zone name to verify that it is trimmed and properly formatted
        zone_name = f"one-time{shared_zone_test_context.partition_id} "

        zone = {
            "name": zone_name,
            "email": "test@test.com",
            "adminGroupId": shared_zone_test_context.ok_group["id"],
            "backendId": "func-test-backend"
        }
        result = client.create_zone(zone, status=202)
        result_zone = result["zone"]
        client.wait_until_zone_active(result_zone["id"])

        get_result = client.get_zone(result_zone["id"])

        get_zone = get_result["zone"]
        assert_that(get_zone["name"], is_(zone["name"].strip() + "."))
        assert_that(get_zone["email"], is_(zone["email"]))
        assert_that(get_zone["adminGroupId"], is_(zone["adminGroupId"]))
        assert_that(get_zone["latestSync"], is_not(none()))
        assert_that(get_zone["status"], is_("Active"))
        assert_that(get_zone["backendId"], is_("func-test-backend"))

        # confirm that the recordsets in DNS have been saved in vinyldns
        recordsets = client.list_recordsets_by_zone(result_zone["id"])["recordSets"]

        assert_that(len(recordsets), is_(7))
        for rs in recordsets:
            small_rs = dict((k, rs[k]) for k in ["name", "type", "records"])
            small_rs["records"] = small_rs["records"]
            assert_that(retrieve_dns_records(shared_zone_test_context), has_item(small_rs))
    finally:
        if result_zone:
            client.abandon_zones([result_zone["id"]], status=202)


def test_create_zone_success_number_of_dots(shared_zone_test_context):
    """
    Test successfully creating a zone
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        # Include a space in the zone name to verify that it is trimmed and properly formatted
        zone_name = f"one-time{shared_zone_test_context.partition_id} "

        zone = {
            "name": zone_name,
            "email": "test@ok.dummy.com",
            "adminGroupId": shared_zone_test_context.ok_group["id"],
            "backendId": "func-test-backend"
        }
        result = client.create_zone(zone, status=202)
        result_zone = result["zone"]
        client.wait_until_zone_active(result_zone["id"])

        get_result = client.get_zone(result_zone["id"])

        get_zone = get_result["zone"]
        assert_that(get_zone["name"], is_(zone["name"].strip() + "."))
        assert_that(get_zone["email"], is_(zone["email"]))
        assert_that(get_zone["adminGroupId"], is_(zone["adminGroupId"]))
        assert_that(get_zone["latestSync"], is_not(none()))
        assert_that(get_zone["status"], is_("Active"))
        assert_that(get_zone["backendId"], is_("func-test-backend"))

        # confirm that the recordsets in DNS have been saved in vinyldns
        recordsets = client.list_recordsets_by_zone(result_zone["id"])["recordSets"]

        assert_that(len(recordsets), is_(7))
        for rs in recordsets:
            small_rs = dict((k, rs[k]) for k in ["name", "type", "records"])
            small_rs["records"] = small_rs["records"]
            assert_that(retrieve_dns_records(shared_zone_test_context), has_item(small_rs))
    finally:
        if result_zone:
            client.abandon_zones([result_zone["id"]], status=202)


@pytest.mark.skip_production
def test_create_zone_without_transfer_connection_leaves_it_empty(shared_zone_test_context):
    """
    Test that creating a zone with a valid connection but without a transfer connection leaves the transfer connection empty
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        zone_name = f"one-time{shared_zone_test_context.partition_id}"

        zone = {
            "name": zone_name,
            "email": "test@test.com",
            "adminGroupId": shared_zone_test_context.ok_group["id"],
            "connection": {
                "name": "vinyldns.",
                "keyName": VinylDNSTestContext.dns_key_name,
                "key": VinylDNSTestContext.dns_key,
                "primaryServer": VinylDNSTestContext.name_server_ip
            }
        }
        result = client.create_zone(zone, status=202)
        result_zone = result["zone"]
        client.wait_until_zone_active(result["zone"]["id"])

        get_result = client.get_zone(result_zone["id"])

        get_zone = get_result["zone"]
        assert_that(get_zone["name"], is_(zone["name"] + "."))
        assert_that(get_zone["email"], is_(zone["email"]))
        assert_that(get_zone["adminGroupId"], is_(zone["adminGroupId"]))

        assert_that(get_zone, is_not(has_key("transferConnection")))
    finally:
        if result_zone:
            client.abandon_zones([result_zone["id"]], status=202)


def test_create_zone_fails_no_authorization(shared_zone_test_context):
    """
    Test creating a new zone without authorization
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone = {
        "name": str(uuid.uuid4()),
        "email": "test@test.com",
    }
    client.create_zone(zone, sign_request=False, status=401)


def test_create_missing_zone_data(shared_zone_test_context):
    """
    Test that creating a zone without providing necessary data (name and email) returns errors
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone = {
        "random_key": "some_value",
        "another_key": "meaningless_data"
    }

    errors = client.create_zone(zone, status=400)["errors"]
    assert_that(errors, contains_inanyorder("Missing Zone.name", "Missing Zone.email", "Missing Zone.adminGroupId"))


def test_create_dotted_zone_without_super_admin_access(shared_zone_test_context):
    """
    Test that creating a dotted hosts zone without providing super admin access returns errors
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone = {
        "name": f"one-time{shared_zone_test_context.partition_id}.",
        "email": "test@test.com",
        "shared": False,
        "allowDottedHosts": True,
        "allowDottedLimits": 3,
        "adminGroupId": shared_zone_test_context.super_group["id"],
        "isTest": True,
        "acl": {
            "rules": [
                {
                    "accessLevel": "Delete",
                    "description": "some_test_rule",
                    "userId": "ok",
                    "allowDottedHosts": True,
                    "recordTypes": ["CNAME"]
                }
            ]
        },
        "connection": {
            "name": "vinyldns.",
            "keyName": VinylDNSTestContext.dns_key_name,
            "key": VinylDNSTestContext.dns_key,
            "primaryServer": VinylDNSTestContext.name_server_ip
        }
    }

    errors = client.create_zone(zone, status=403)
    assert_that(errors, is_("Not Authorised: User is not VinylDNS Admin"))


def test_create_invalid_zone_data(shared_zone_test_context):
    """
    Test that creating a zone with invalid data returns errors
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = "test.zone.invalid."

    zone = {
        "name": zone_name,
        "email": "test@test.com",
        "shared": "invalid_value",
        "adminGroupId": "admin-group-id"
    }

    errors = client.create_zone(zone, status=400)["errors"]
    assert_that(errors, contains_inanyorder("Do not know how to convert JString(invalid_value) into boolean"))

def test_create_invalid_email(shared_zone_test_context):
    """
    Test that creating a zone with invalid email
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id} "

    zone = {
        "name": zone_name,
        "email": "test.abc.com",
        "adminGroupId": shared_zone_test_context.ok_group["id"],
        "backendId": "func-test-backend"
    }

    errors = client.create_zone(zone, status=400)
    assert_that(errors, is_("Please enter a valid Email."))

def test_create_invalid_email_number_of_dots(shared_zone_test_context):
    """
    Test that creating a zone with invalid email
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id} "

    zone = {
        "name": zone_name,
        "email": "test@abc.ok.dummy.com",
        "adminGroupId": shared_zone_test_context.ok_group["id"],
        "backendId": "func-test-backend"
    }

    errors = client.create_zone(zone, status=400)
    assert_that(errors, is_("Please enter a valid Email. Number of dots allowed after @ is 2"))

def test_create_invalid_domain(shared_zone_test_context):
    """
    Test that creating a zone with invalid domain
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id} "

    zone = {
        "name": zone_name,
        "email": "test@abc.com",
        "adminGroupId": shared_zone_test_context.ok_group["id"],
        "backendId": "func-test-backend"
    }

    errors = client.create_zone(zone, status=400)
    assert_that(errors, is_("Please enter a valid Email. Valid domains should end with test.com,dummy.com"))

@pytest.mark.serial
def test_create_zone_with_connection_failure(shared_zone_test_context):
    """
    Test creating a new zone with a an invalid key and connection info fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id}."
    zone = {
        "name": zone_name,
        "email": "test@test.com",
        "connection": {
            "name": zone_name,
            "keyName": zone_name,
            "key": VinylDNSTestContext.dns_key,
            "primaryServer": VinylDNSTestContext.name_server_ip
        }
    }
    client.create_zone(zone, status=400)


def test_create_zone_returns_409_if_already_exists(shared_zone_test_context):
    """
    Test creating a zone returns a 409 Conflict if the zone name already exists
    """
    create_conflict = dict(shared_zone_test_context.ok_zone)
    create_conflict["connection"]["key"] = VinylDNSTestContext.dns_key  # necessary because we encrypt the key
    create_conflict["transferConnection"]["key"] = VinylDNSTestContext.dns_key

    shared_zone_test_context.ok_vinyldns_client.create_zone(create_conflict, status=409)


def test_create_zone_returns_400_for_invalid_data(shared_zone_test_context):
    """
    Test creating a zone returns a 400 if the request body is invalid
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone = {
        "jim": "bob",
        "hey": "you"
    }
    client.create_zone(zone, status=400)


@pytest.mark.skip_production
@pytest.mark.serial
def test_create_zone_no_connection_uses_defaults(shared_zone_test_context):
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id}"

    zone = {
        "name": zone_name,
        "email": "test@test.com",
        "adminGroupId": shared_zone_test_context.ok_group["id"]
    }

    try:
        zone_change = client.create_zone(zone, status=202)
        zone = zone_change["zone"]
        client.wait_until_zone_active(zone_change["zone"]["id"])

        # Check response from create
        assert_that(zone["name"], is_(zone_name + "."))
        assert_that("connection" not in zone)
        assert_that("transferConnection" not in zone)

        # Check that it was internally stored correctly using GET
        zone_get = client.get_zone(zone["id"])["zone"]
        assert_that(zone_get["name"], is_(zone_name + "."))
        assert_that("connection" not in zone_get)
        assert_that("transferConnection" not in zone_get)
    finally:
        if "id" in zone:
            client.abandon_zones([zone["id"]], status=202)


@pytest.mark.serial
def test_zone_connection_only(shared_zone_test_context):
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id}"

    zone = {
        "name": zone_name,
        "email": "test@test.com",
        "adminGroupId": shared_zone_test_context.ok_group["id"],
        "connection": {
            "name": "vinyldns.",
            "keyName": VinylDNSTestContext.dns_key_name,
            "key": VinylDNSTestContext.dns_key,
            "primaryServer": VinylDNSTestContext.name_server_ip
        },
        "transferConnection": {
            "name": "vinyldns.",
            "keyName": VinylDNSTestContext.dns_key_name,
            "key": VinylDNSTestContext.dns_key,
            "primaryServer": VinylDNSTestContext.name_server_ip
        }
    }

    expected_connection = {
        "name": "vinyldns.",
        "keyName": VinylDNSTestContext.dns_key_name,
        "key": VinylDNSTestContext.dns_key,
        "primaryServer": VinylDNSTestContext.name_server_ip
    }

    try:
        zone_change = client.create_zone(zone, status=202)
        zone = zone_change["zone"]
        client.wait_until_zone_active(zone_change["zone"]["id"])

        # Check response from create
        assert_that(zone["name"], is_(zone_name + "."))
        assert_that(zone["connection"]["name"], is_(expected_connection["name"]))
        assert_that(zone["connection"]["keyName"], is_(expected_connection["keyName"]))
        assert_that(zone["connection"]["primaryServer"], is_(expected_connection["primaryServer"]))
        assert_that(zone["transferConnection"]["name"], is_(expected_connection["name"]))
        assert_that(zone["transferConnection"]["keyName"], is_(expected_connection["keyName"]))
        assert_that(zone["transferConnection"]["primaryServer"], is_(expected_connection["primaryServer"]))

        # Check that it was internally stored correctly using GET
        zone_get = client.get_zone(zone["id"])["zone"]
        assert_that(zone_get["name"], is_(zone_name + "."))
        assert_that(zone["connection"]["name"], is_(expected_connection["name"]))
        assert_that(zone["connection"]["keyName"], is_(expected_connection["keyName"]))
        assert_that(zone["connection"]["primaryServer"], is_(expected_connection["primaryServer"]))
        assert_that(zone["transferConnection"]["name"], is_(expected_connection["name"]))
        assert_that(zone["transferConnection"]["keyName"], is_(expected_connection["keyName"]))
        assert_that(zone["transferConnection"]["primaryServer"], is_(expected_connection["primaryServer"]))
    finally:
        if "id" in zone:
            client.abandon_zones([zone["id"]], status=202)


@pytest.mark.serial
def test_zone_bad_connection(shared_zone_test_context):
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id}"

    zone = {
        "name": zone_name,
        "email": "test@test.com",
        "connection": {
            "name": zone_name,
            "keyName": VinylDNSTestContext.dns_key_name,
            "key": "somebadkey",
            "primaryServer": VinylDNSTestContext.name_server_ip
        }
    }

    client.create_zone(zone, status=400)


@pytest.mark.serial
def test_zone_bad_transfer_connection(shared_zone_test_context):
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id}"

    zone = {
        "name": zone_name,
        "email": "test@test.com",
        "connection": {
            "name": zone_name,
            "keyName": VinylDNSTestContext.dns_key_name,
            "key": VinylDNSTestContext.dns_key,
            "primaryServer": VinylDNSTestContext.name_server_ip
        },
        "transferConnection": {
            "name": zone_name,
            "keyName": VinylDNSTestContext.dns_key_name,
            "key": "bad",
            "primaryServer": VinylDNSTestContext.name_server_ip
        }
    }

    client.create_zone(zone, status=400)


@pytest.mark.serial
def test_zone_transfer_connection(shared_zone_test_context):
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id}"

    zone = {
        "name": zone_name,
        "email": "test@test.com",
        "adminGroupId": shared_zone_test_context.ok_group["id"],
        "connection": {
            "name": zone_name,
            "keyName": VinylDNSTestContext.dns_key_name,
            "key": VinylDNSTestContext.dns_key,
            "primaryServer": VinylDNSTestContext.name_server_ip
        },
        "transferConnection": {
            "name": zone_name,
            "keyName": VinylDNSTestContext.dns_key_name,
            "key": VinylDNSTestContext.dns_key,
            "primaryServer": VinylDNSTestContext.name_server_ip
        }
    }

    expected_connection = {
        "name": zone_name,
        "keyName": VinylDNSTestContext.dns_key_name,
        "key": VinylDNSTestContext.dns_key,
        "primaryServer": VinylDNSTestContext.name_server_ip
    }

    try:
        zone_change = client.create_zone(zone, status=202)
        zone = zone_change["zone"]
        client.wait_until_zone_active(zone_change["zone"]["id"])

        # Check response from create
        assert_that(zone["name"], is_(zone_name + "."))
        assert_that(zone["connection"]["name"], is_(expected_connection["name"]))
        assert_that(zone["connection"]["keyName"], is_(expected_connection["keyName"]))
        assert_that(zone["connection"]["primaryServer"], is_(expected_connection["primaryServer"]))
        assert_that(zone["transferConnection"]["name"], is_(expected_connection["name"]))
        assert_that(zone["transferConnection"]["keyName"], is_(expected_connection["keyName"]))
        assert_that(zone["transferConnection"]["primaryServer"], is_(expected_connection["primaryServer"]))

        # Check that it was internally stored correctly using GET
        zone_get = client.get_zone(zone["id"])["zone"]
        assert_that(zone_get["name"], is_(zone_name + "."))
        assert_that(zone["connection"]["name"], is_(expected_connection["name"]))
        assert_that(zone["connection"]["keyName"], is_(expected_connection["keyName"]))
        assert_that(zone["connection"]["primaryServer"], is_(expected_connection["primaryServer"]))
        assert_that(zone["transferConnection"]["name"], is_(expected_connection["name"]))
        assert_that(zone["transferConnection"]["keyName"], is_(expected_connection["keyName"]))
        assert_that(zone["transferConnection"]["primaryServer"], is_(expected_connection["primaryServer"]))
    finally:
        if "id" in zone:
            client.abandon_zones([zone["id"]], status=202)


@pytest.mark.serial
def test_user_cannot_create_zone_with_nonmember_admin_group(shared_zone_test_context):
    """
    Test user cannot create a zone with an admin group they are not a member of
    """
    zone = {
        "name": f"one-time{shared_zone_test_context.partition_id}.",
        "email": "test@test.com",
        "adminGroupId": shared_zone_test_context.dummy_group["id"],
        "connection": {
            "name": "vinyldns.",
            "keyName": VinylDNSTestContext.dns_key_name,
            "key": VinylDNSTestContext.dns_key,
            "primaryServer": VinylDNSTestContext.name_server_ip
        },
        "transferConnection": {
            "name": "vinyldns.",
            "keyName": VinylDNSTestContext.dns_key_name,
            "key": VinylDNSTestContext.dns_key,
            "primaryServer": VinylDNSTestContext.name_server_ip
        }
    }

    shared_zone_test_context.ok_vinyldns_client.create_zone(zone, status=403)


def test_user_cannot_create_zone_with_failed_validations(shared_zone_test_context):
    """
    Test that a user cannot create a zone that has invalid zone data
    """
    zone = {
        "name": f"invalid-zone{shared_zone_test_context.partition_id}.",
        "email": "test@test.com",
        "adminGroupId": shared_zone_test_context.ok_group["id"],
        "connection": {
            "name": "vinyldns.",
            "keyName": VinylDNSTestContext.dns_key_name,
            "key": VinylDNSTestContext.dns_key,
            "primaryServer": VinylDNSTestContext.name_server_ip
        },
        "transferConnection": {
            "name": "vinyldns.",
            "keyName": VinylDNSTestContext.dns_key_name,
            "key": VinylDNSTestContext.dns_key,
            "primaryServer": VinylDNSTestContext.name_server_ip
        }
    }

    result = shared_zone_test_context.ok_vinyldns_client.create_zone(zone, status=400)
    assert_that(result["errors"], contains_inanyorder(
        contains_string("not-approved.thing.com. is not an approved name server")
    ))


def test_normal_user_cannot_create_shared_zone(shared_zone_test_context):
    """
    Test that a normal user cannot create a shared zone
    """
    super_zone = copy.deepcopy(shared_zone_test_context.ok_zone)
    super_zone["shared"] = True

    shared_zone_test_context.ok_vinyldns_client.create_zone(super_zone, status=403)


def test_create_zone_bad_backend_id(shared_zone_test_context):
    """
    Test that a user cannot create a zone with a backendId that is not in config
    """
    zone = {
        "name": "test-create-zone-bad-backend-id",
        "email": "test@test.com",
        "adminGroupId": shared_zone_test_context.ok_group["id"],
        "backendId": "does-not-exist-id"
    }
    result = shared_zone_test_context.ok_vinyldns_client.create_zone(zone, status=400)
    assert_that(result, contains_string("Invalid backendId"))


def retrieve_dns_records(shared_zone_test_context) -> List[Dict]:
    """
    Returns a representation of what is current configured in the one-time. zone
    :param shared_zone_test_context: The test context
    :return: An array of recordsets
    """
    partition_id = shared_zone_test_context.partition_id
    return [
        {"name": f"one-time{partition_id}.",
         "type": "SOA",
         "records": [{"mname": "172.17.42.1.",
                      "rname": "admin.test.com.",
                      "retry": 3600,
                      "refresh": 10800,
                      "minimum": 38400,
                      "expire": 604800,
                      "serial": 1439234395}]},
        {"name": f"one-time{partition_id}.",
         "type": "NS",
         "records": [{"nsdname": "172.17.42.1."}]},
        {"name": "jenkins",
         "type": "A",
         "records": [{"address": "10.1.1.1"}]},
        {"name": "foo",
         "type": "A",
         "records": [{"address": "2.2.2.2"}]},
        {"name": "test",
         "type": "A",
         "records": [{"address": "3.3.3.3"}, {"address": "4.4.4.4"}]},
        {"name": f"one-time{partition_id}.",
         "type": "A",
         "records": [{"address": "5.5.5.5"}]},
        {"name": "already-exists",
         "type": "A",
         "records": [{"address": "6.6.6.6"}]}]
