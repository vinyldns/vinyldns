import copy
from typing import List, Dict

import pytest

from utils import *

@pytest.mark.serial
def test_generate_zone_success(shared_zone_test_context):
    """
    Test successfully creating a zone
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        # Include a space in the zone name to verify that it is trimmed and properly formatted
        zone_name = f"one-time{shared_zone_test_context.partition_id}."

        zone = {
                    "groupId": shared_zone_test_context.ok_group["id"],
                    "email": "test@test.com",
                    "provider": "powerdns",
                    "zoneName": zone_name,
                    "providerParams": {
                        "kind": "Master",
                        "nameservers": [
                            "172.17.42.1.",
                            "ns1.mttest1.example.org."
                        ]
                    }
                 }
        result_zone = client.generate_zone(zone, status=202)

        client.wait_until_generate_zone_active(result_zone["id"])

        get_zone = client.get_generate_zone(result_zone["id"])

        assert_that(get_zone["zoneName"], is_(zone["zoneName"].strip()))
        assert_that(get_zone["email"], is_(zone["email"]))
        assert_that(get_zone["groupId"], is_(zone["groupId"]))
        assert_that(get_zone["status"], is_("Active"))
        assert_that(get_zone["provider"], is_(zone["provider"]))

    finally:
        if result_zone:
            client.abandon_generated_zones([result_zone["id"]], status=202)


def test_generate_zone_fails_no_authorization(shared_zone_test_context):
    """
    Test creating a new zone without authorization
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone = {
        "name": str(uuid.uuid4()),
        "email": "test@test.com",
    }
    client.generate_zone(zone, sign_request=False, status=401)


def test_generate_zone_missing_zone_data(shared_zone_test_context):
    """
    Test that creating a zone without providing necessary data (name and email) returns errors
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone = {
        "random_key": "some_value",
        "another_key": "meaningless_data"
    }

    errors = client.generate_zone(zone, status=400)["errors"]
    assert_that(errors, contains_inanyorder('Missing group id', 'Missing email', 'Missing provider', 'Missing zone name'))

def test_generate_zone_invalid_zone_data(shared_zone_test_context):
    """
    Test that creating a zone with invalid data returns errors
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = "test.zone.invalid."

    zone = {
        "zoneName": zone_name,
        "email": "test@test.com",
        "provider": "invalid_value",
        "groupId": "admin-group-id"
    }

    client.generate_zone(zone, status=400)

def test_generate_zone_invalid_email(shared_zone_test_context):
    """
    Test that creating a zone with invalid email
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id}."

    zone = {
        "zoneName": zone_name,
        "email": "test.abc.com",
        "groupId": shared_zone_test_context.ok_group["id"],
        "provider": "powerdns"
    }

    errors = client.generate_zone(zone, status=400)
    assert_that(errors, is_("Please enter a valid Email."))

def test_generate_zone_invalid_email_number_of_dots(shared_zone_test_context):
    """
    Test that creating a zone with invalid email
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id}."

    zone = {
        "zoneName": zone_name,
        "email": "test@abc.ok.dummy.com",
        "groupId": shared_zone_test_context.ok_group["id"],
        "provider": "powerdns"
    }

    errors = client.generate_zone(zone, status=400)
    assert_that(errors, is_("Please enter a valid Email. Number of dots allowed after @ is 2"))

def test_generate_zone_invalid_domain(shared_zone_test_context):
    """
    Test that creating a zone with invalid domain
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id}."

    zone = {
        "zoneName": zone_name,
        "email": "test@abc.com",
        "groupId": shared_zone_test_context.ok_group["id"],
        "provider": "powerdns"
    }

    errors = client.generate_zone(zone, status=400)
    assert_that(errors, is_("Please enter a valid Email. Valid domains should end with test.com,dummy.com"))


def test_generate_zone_returns_409_if_already_exists(shared_zone_test_context):
    """
    Test creating a zone returns a 409 Conflict if the zone name already exists
    """
    generate_conflict = dict(shared_zone_test_context.ok_generate_zone)

    shared_zone_test_context.ok_vinyldns_client.generate_zone(generate_conflict, status=409)


def test_generate_zone_zone_returns_400_for_invalid_data(shared_zone_test_context):
    """
    Test creating a zone returns a 400 if the request body is invalid
    """
    client = shared_zone_test_context.ok_vinyldns_client

    generate_zone = {
        "jim": "bob",
        "hey": "you"
    }
    client.generate_zone(generate_zone, status=400)

@pytest.mark.serial
def test_generate_zone_unsupported_providers(shared_zone_test_context):

    zone_name = f"one-time{shared_zone_test_context.partition_id}."

    zone = {
        "groupId": shared_zone_test_context.ok_group["id"],
        "email": "test@test.com",
        "provider": "cloudFare",
        "zoneName": zone_name,
        "providerParams": {
            "nameservers": [
                "172.17.42.1.",
                "ns1.mttest1.example.org."
            ]
        }
    }
    result = shared_zone_test_context.ok_vinyldns_client.generate_zone(zone, status=400)

    assert_that(result, contains_string("Unsupported DNS provider: cloudFare"))

@pytest.mark.serial
def test_generate_zone_bad_connection(shared_zone_test_context):
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id}"

    zone = {
        "groupId": shared_zone_test_context.ok_group["id"],
        "email": "test@test.com",
        "provider": "bind",
        "zoneName": zone_name,
        "providerParams": {
            "nameservers": [
                "172.17.42.1.",
                "ns1.mttest1.example.org."
            ]
        }
    }

    client.generate_zone(zone, status=400)

@pytest.mark.serial
def test_user_cannot_generate_zone_with_nonmember_admin_group(shared_zone_test_context):
    """
    Test user cannot create a zone with an admin group they are not a member of
    """
    zone_name = f"one-time{shared_zone_test_context.partition_id}"

    zone = {
        "groupId": f"one-time{shared_zone_test_context.partition_id}.",
        "email": "test@test.com",
        "provider": "powerdns",
        "zoneName": zone_name,
        "providerParams": {
            "kind": "Master",
            "nameservers": [
                "172.17.42.1.",
                "ns1.mttest1.example.org."
            ]
        }
    }

    shared_zone_test_context.ok_vinyldns_client.generate_zone(zone, status=400)


def test_user_cannot_generate_zone_with_failed_validations(shared_zone_test_context):
    """
    Test that a user cannot create a zone that has invalid zone data
    """
    zone_name = f"one-time{shared_zone_test_context.partition_id}"

    zone = {
        "groupId": f"one-time{shared_zone_test_context.partition_id}.",
        "email": "test@test.com",
        "provider": "powerdns",
        "zoneName": zone_name,
        "providerParams": {
            "kind": "Master",
            "nameservers": [
                "172.17.42.98.",
                "ns1.mttest1.example.org."
            ]
        }
    }

    result = shared_zone_test_context.ok_vinyldns_client.generate_zone(zone, status=400)

    assert_that(result, contains_string(f"Invalid zone name: {zone_name}"))

@pytest.mark.serial
def test_generate_zone_invalid_fields(shared_zone_test_context):
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = f"one-time{shared_zone_test_context.partition_id}."

    zone = {
        "groupId": shared_zone_test_context.ok_group["id"],
        "email": "test@test.com",
        "provider": "powerdns",
        "zoneName": zone_name,
        "providerParams": {
            "nameservers": [
                "172.17.42.1.",
                "ns1.mttest1.example.org."
            ],
            "TTL" : "1200"
        }
    }

    result = shared_zone_test_context.ok_vinyldns_client.generate_zone(zone, status=400)

    assert_that(result, contains_string("JSON schema validation error: $: required property 'kind' not found; $: "
                                        "property 'TTL' is not defined in the schema and the schema does "
                                        "not allow additional properties")
                )
