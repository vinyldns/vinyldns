import copy

import pytest

from utils import *
from vinyldns_context import VinylDNSTestContext
from datetime import datetime, timezone, timedelta


@pytest.mark.serial
def test_update_generate_zone_success(shared_zone_test_context):
    """
    Test updating a zone
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
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

        result_zone["email"] = "test@dummy.com"
        result_zone["providerParams"].pop("nameservers", None)

        update_result = client.update_generate_zone(result_zone, status=202)

        assert_that(update_result["response"]["changeType"], is_("Update"))
        assert_that(update_result, has_key("created"))

        uz = client.get_generate_zone(result_zone["id"])
        assert_that(uz["email"], is_("test@dummy.com"))
        assert_that(uz["updated"], is_not(none()))

    finally:
        if result_zone:
            client.abandon_generated_zones([result_zone["id"]], status=202)

def test_update_generate_zone_failure_with_nameserver(shared_zone_test_context):
    """
    Test updating a zone
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
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

        result_zone["email"] = "test@dummy.com"

        update_result = client.update_generate_zone(result_zone, status=400)

        assert_that(update_result, is_("JSON schema validation error: $: property 'nameservers' is not defined in the schema and the schema does not allow additional properties"))

    finally:
        if result_zone:
            client.abandon_generated_zones([result_zone["id"]], status=202)


def test_update_generate_zone_failure_with_invalid_fields(shared_zone_test_context):
    """
    Test updating a zone
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
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

        result_zone["providerParams"]["ttl"] = "100"
        result_zone["providerParams"].pop("nameservers", None)

        update_result = client.update_generate_zone(result_zone, status=400)

        assert_that(update_result, is_("JSON schema validation error: $: property 'ttl' is not defined in the schema and the schema does not allow additional properties"))

    finally:
        if result_zone:
            client.abandon_generated_zones([result_zone["id"]], status=202)


def test_update_generate_zone_success_wildcard(shared_zone_test_context):
    """
    Test updating a zone for email validation wildcard
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
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

        result_zone["email"] = "test@ok.dummy.com"
        result_zone["providerParams"].pop("nameservers", None)
        update_result = client.update_generate_zone(result_zone, status=202)

        assert_that(update_result["response"]["changeType"], is_("Update"))
        assert_that(update_result, has_key("created"))

        uz = client.get_generate_zone(result_zone["id"])
        assert_that(uz["email"], is_("test@ok.dummy.com"))
        assert_that(uz["updated"], is_not(none()))

    finally:
        if result_zone:
            client.abandon_generated_zones([result_zone["id"]], status=202)

def test_update_generate_zone_success_number_of_dots(shared_zone_test_context):
    """
    Test updating a zone for email validation wildcard
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
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

        result_zone["email"] = "test@ok.dummy.com"
        result_zone["providerParams"].pop("nameservers", None)
        update_result = client.update_generate_zone(result_zone, status=202)

        assert_that(update_result["response"]["changeType"], is_("Update"))
        assert_that(update_result, has_key("created"))

        uz = client.get_generate_zone(result_zone["id"])

        assert_that(uz["email"], is_("test@ok.dummy.com"))
        assert_that(uz["updated"], is_not(none()))

    finally:
        if result_zone:
            client.abandon_generated_zones([result_zone["id"]], status=202)

def test_update_generate_invalid_email(shared_zone_test_context):
    """
    Test that updating a zone with invalid email
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
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

        result_zone["email"] = "test.trial.com"
        errors = client.update_generate_zone(result_zone, status=400)
        assert_that(errors, is_("Please enter a valid Email."))
    finally:
        if result_zone:
            client.abandon_generated_zones([result_zone["id"]], status=202)

def test_update_generate_zone_invalid_domain(shared_zone_test_context):
    """
    Test that updating a zone with invalid domain
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
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

        result_zone["email"] = "test@trial.com"
        errors = client.update_generate_zone(result_zone, status=400)
        assert_that(errors, is_("Please enter a valid Email. Valid domains should end with test.com,dummy.com"))

    finally:
        if result_zone:
            client.abandon_generated_zones([result_zone["id"]], status=202)

def test_update_generate_zone_invalid_email_number_of_dots(shared_zone_test_context):
    """
    Test that updating a zone with invalid domain
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
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

        result_zone["email"] = "test@ok.ok.dummy.com"
        errors = client.update_generate_zone(result_zone, status=400)
        assert_that(errors, is_("Please enter a valid Email. Number of dots allowed after @ is 2"))

    finally:
        if result_zone:
            client.abandon_generated_zones([result_zone["id"]], status=202)


@pytest.mark.serial
def test_update_missing_generate_zone_data(shared_zone_test_context):
    """
    Test that updating a zone without providing necessary data returns errors and fails the update
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
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

        update_zone = {
            "id": result_zone["id"],
            "zoneName": result_zone["zoneName"],
            "random_key": "some_value",
            "another_key": "meaningless_data",
            "groupId": zone["groupId"],
            "provider": "powerdns"
        }

        errors = client.update_generate_zone(update_zone, status=400)["errors"]
        assert_that(errors, contains_inanyorder("Missing email"))

        # Check that the failed update didn't go through
        zone_get = client.get_generate_zone(result_zone["id"])
        assert_that(zone_get["zoneName"], is_(zone_name))
    finally:
        if result_zone:
            client.abandon_generated_zones([result_zone["id"]], status=202)


@pytest.mark.serial
def test_update_invalid_generate_zone_data(shared_zone_test_context):
    """
    Test that creating a zone with invalid data returns errors and fails the update
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
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

        update_zone = {
            "id": result_zone["id"],
            "zoneName": result_zone["zoneName"],
            "email": "test@test.com",
            "groupId": True,
            "provider": "powerdns",
        }

        errors = client.update_generate_zone(update_zone, status=400)["errors"]
        assert_that(errors, contains_inanyorder("Do not know how to convert JBool(true) into class java.lang.String"))

        # Check that the failed update didn't go through
        zone_get = client.get_generate_zone(result_zone["id"])
        assert_that(zone_get["zoneName"], is_(zone_name))
    finally:
        if result_zone:
            client.abandon_generated_zones([result_zone["id"]], status=202)


@pytest.mark.serial
def test_update_generate_zone_returns_404_if_zone_not_found(shared_zone_test_context):
    """
    Test updating a zone returns a 404 if the zone was not found
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = {
        "id": "nothere",
        "groupId": shared_zone_test_context.ok_group["id"],
        "email": "test@test.com",
        "provider": "powerdns",
        "zoneName": f"one-time{shared_zone_test_context.partition_id}.",
        "providerParams": {
            "kind": "Master",
            "nameservers": [
                "172.17.42.1.",
                "ns1.mttest1.example.org."
            ]
        }
    }
    client.update_generate_zone(zone, status=404)


def test_user_cannot_update_generate_zone_to_nonexisting_admin_group(shared_zone_test_context):
    """
    Test user cannot update a zone adminGroupId to a group that does not exist
    """
    zone_update = copy.deepcopy(shared_zone_test_context.ok_generate_zone)
    zone_update["groupId"] = "some-bad-id"

    shared_zone_test_context.ok_vinyldns_client.update_generate_zone(zone_update, status=400)


@pytest.mark.serial
def test_user_can_update_generate_zone_to_another_admin_group(shared_zone_test_context):
    """
    Test user can update a zone with an admin group they are a member of
    """
    client = shared_zone_test_context.dummy_vinyldns_client
    group = None
    zone = None
    try:
        zone = client.generate_zone(
            {
                "groupId": shared_zone_test_context.dummy_group["id"],
                "email": "test@test.com",
                "provider": "powerdns",
                "zoneName": f"one-time{shared_zone_test_context.partition_id}.",
                "providerParams": {
                    "kind": "Master",
                    "nameservers": [
                        "172.17.42.1.",
                        "ns1.mttest1.example.org."
                    ]
                }
            }, status=202
        )
        client.wait_until_generate_zone_active(zone["id"])

        new_joint_group = {
            "name": "new-ok-group",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}, {"id": "dummy"}],
            "admins": [{"id": "ok"}]
        }

        group = client.create_group(new_joint_group, status=200)

        # changing the zone
        zone_update = dict(zone)
        zone_update["groupId"] = group["id"]
        zone_update["providerParams"].pop("nameservers", None)

        client.update_generate_zone(zone_update, status=202)
    finally:
        if zone:
            client.delete_generated_zone(zone["id"], status=202)
            client.wait_until_generated_zone_deleted(zone["id"])
        if group:
            shared_zone_test_context.ok_vinyldns_client.delete_group(group["id"], status=(200, 404))

def test_update_zone_no_authorization(shared_zone_test_context):
    """
    Test updating a zone without authorization
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone = {
        "id": "12345",
        "name": str(uuid.uuid4()),
        "email": "test@test.com",
    }

    client.update_generate_zone(zone, sign_request=False, status=401)

@pytest.mark.serial
def test_update_connection_info_missing_provider(shared_zone_test_context):
    """
    Test user can update zone to bad backendId fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.ok_generate_zone

    to_update = client.get_generate_zone(zone["id"])
    to_update.pop("provider")

    result = client.update_generate_zone(to_update, status=400)
    assert_that(result, has_entry("errors", has_item("Missing provider")))

