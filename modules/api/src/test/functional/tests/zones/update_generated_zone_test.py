import copy

import pytest

from utils import *
from vinyldns_context import VinylDNSTestContext
from datetime import datetime, timezone, timedelta


@pytest.mark.serial
def test_update_zone_success(shared_zone_test_context):
    """
    Test updating a zone
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        zone_name = f"one-time{shared_zone_test_context.partition_id}"

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
        result_zone["acl"]["rules"] = [acl_rule]
        update_result = client.update_generate_zone(result_zone, status=202)

        assert_that(update_result["changeType"], is_("Update"))
        assert_that(update_result["userId"], is_("ok"))
        assert_that(update_result, has_key("created"))

        uz = client.get_generate_zone(result_zone["id"])
        assert_that(uz["email"], is_("test@dummy.com"))
        assert_that(uz["updated"], is_not(none()))

    finally:
        if result_zone:
            client.abandon_zones([result_zone["id"]], status=202)

def test_update_zone_success_wildcard(shared_zone_test_context):
    """
    Test updating a zone for email validation wildcard
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        zone_name = f"one-time{shared_zone_test_context.partition_id}"

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
        update_result = client.update_zone(result_zone, status=202)

        assert_that(update_result["changeType"], is_("Update"))
        assert_that(update_result["userId"], is_("ok"))
        assert_that(update_result, has_key("created"))

        uz = client.get_generate_zone(result_zone["id"])
        assert_that(uz["email"], is_("test@ok.dummy.com"))
        assert_that(uz["updated"], is_not(none()))

    finally:
        if result_zone:
            client.abandon_zones([result_zone["id"]], status=202)

def test_update_zone_success_number_of_dots(shared_zone_test_context):
    """
    Test updating a zone for email validation wildcard
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        zone_name = f"one-time{shared_zone_test_context.partition_id}"

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
        result = client.generate_zone(zone, status=202)
        result_zone = result["zone"]
        client.wait_until_generate_zone_active(result_zone["id"])

        result_zone["email"] = "test@ok.dummy.com"
        result_zone["acl"]["rules"] = [acl_rule]
        update_result = client.update_generate_zone(result_zone, status=202)

        assert_that(update_result["changeType"], is_("Update"))
        assert_that(update_result["userId"], is_("ok"))
        assert_that(update_result, has_key("created"))

        uz = client.get_generate_zone(result_zone["id"])

        assert_that(uz["email"], is_("test@ok.dummy.com"))
        assert_that(uz["updated"], is_not(none()))

    finally:
        if result_zone:
            client.abandon_zones([result_zone["id"]], status=202)

def test_update_invalid_email(shared_zone_test_context):
    """
    Test that updating a zone with invalid email
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        zone_name = f"one-time{shared_zone_test_context.partition_id}"

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
        result = client.generate_zone(zone, status=202)
        result_zone = result["zone"]
        client.wait_until_generate_zone_active(result_zone["id"])

        result_zone["email"] = "test.trial.com"
        errors = client.update_generate_zone(result_zone, status=400)
        assert_that(errors, is_("Please enter a valid Email."))
    finally:
        if result_zone:
            client.abandon_zones([result_zone["id"]], status=202)

def test_update_invalid_domain(shared_zone_test_context):
    """
    Test that updating a zone with invalid domain
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        zone_name = f"one-time{shared_zone_test_context.partition_id}"

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
        result = client.generate_zone(zone, status=202)
        result_zone = result["zone"]
        client.wait_until_generate_zone_active(result_zone["id"])

        result_zone["email"] = "test@trial.com"
        errors = client.update_generate_zone(result_zone, status=400)
        assert_that(errors, is_("Please enter a valid Email. Valid domains should end with test.com,dummy.com"))

    finally:
        if result_zone:
            client.abandon_zones([result_zone["id"]], status=202)

def test_update_invalid_email_number_of_dots(shared_zone_test_context):
    """
    Test that updating a zone with invalid domain
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        zone_name = f"one-time{shared_zone_test_context.partition_id}"

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
        result = client.generate_zone(zone, status=202)
        result_zone = result["zone"]
        client.wait_until_generate_zone_active(result_zone["id"])

        result_zone["email"] = "test@ok.ok.dummy.com"
        errors = client.update_generate_zone(result_zone, status=400)
        assert_that(errors, is_("Please enter a valid Email. Number of dots allowed after @ is 2"))

    finally:
        if result_zone:
            client.abandon_zones([result_zone["id"]], status=202)


@pytest.mark.serial
def test_update_missing_zone_data(shared_zone_test_context):
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
        result = client.generate_zone(zone, status=202)
        result_zone = result["zone"]
        client.wait_until_generate_zone_active(result["zone"]["id"])

        update_zone = {
            "id": result_zone["id"],
            "name": result_zone["name"],
            "random_key": "some_value",
            "another_key": "meaningless_data",
            "adminGroupId": zone["adminGroupId"]
        }

        errors = client.update_generate_zone(update_zone, status=400)["errors"]
        assert_that(errors, contains_inanyorder("Missing Zone.email"))

        # Check that the failed update didn't go through
        zone_get = client.get_generate_zone(result_zone["id"])["zone"]
        assert_that(zone_get["name"], is_(zone_name))
    finally:
        if result_zone:
            client.abandon_zones([result_zone["id"]], status=202)


@pytest.mark.serial
def test_update_invalid_zone_data(shared_zone_test_context):
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
        result = client.generate_zone(zone, status=202)
        result_zone = result["zone"]
        client.wait_until_generate_zone_active(result["zone"]["id"])

        update_zone = {
            "id": result_zone["id"],
            "name": result_zone["name"],
            "email": "test@test.com",
            "adminGroupId": True
        }

        errors = client.update_generate_zone(update_zone, status=400)["errors"]
        assert_that(errors, contains_inanyorder("Do not know how to convert JBool(true) into class java.lang.String"))

        # Check that the failed update didn't go through
        zone_get = client.get_generate_zone(result_zone["id"])["zone"]
        assert_that(zone_get["name"], is_(zone_name))
    finally:
        if result_zone:
            client.abandon_zones([result_zone["id"]], status=202)


@pytest.mark.serial
def test_update_zone_returns_404_if_zone_not_found(shared_zone_test_context):
    """
    Test updating a zone returns a 404 if the zone was not found
    """
    client = shared_zone_test_context.ok_vinyldns_client
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
    client.update_generate_zone(zone, status=404)


def test_user_cannot_update_zone_to_nonexisting_admin_group(shared_zone_test_context):
    """
    Test user cannot update a zone adminGroupId to a group that does not exist
    """
    zone_update = copy.deepcopy(shared_zone_test_context.ok_zone)
    zone_update["groupId"] = "some-bad-id"

    shared_zone_test_context.ok_vinyldns_client.update_generate_zone(zone_update, status=400)


@pytest.mark.serial
def test_user_can_update_zone_to_another_admin_group(shared_zone_test_context):
    """
    Test user can update a zone with an admin group they are a member of
    """
    client = shared_zone_test_context.dummy_vinyldns_client
    group = None
    zone = None
    try:
        result = client.create_zone(
            {
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
            }, status=202
        )
        zone = result["zone"]
        client.wait_until_generate_zone_active(result["zone"]["id"])

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

        result = client.update_generate_zone(zone_update, status=202)
    finally:
        if zone:
            client.delete_generated_zone(zone["id"], status=202)
            client.wait_until_generated_zone_deleted(zone["id"])
        if group:
            shared_zone_test_context.ok_vinyldns_client.delete_group(group["id"], status=(200, 404))


@pytest.mark.serial
def test_user_cannot_update_zone_to_nonmember_admin_group(shared_zone_test_context):
    """
    Test user cannot update a zone adminGroupId to a group they are not a member of
    """
    # TODO: I don't know why this consistently fails but marking serial
    # TODO: STRANGE!  When doing ALL serially it returns 400, when separating PAR from SER it returns a 403
    # TODO: somehow changing the order of when this run changes the status code!  Who is messing with the ok_zone?
    zone_update = copy.deepcopy(shared_zone_test_context.ok_zone)
    zone_update["groupId"] = shared_zone_test_context.history_group["id"]

    shared_zone_test_context.ok_vinyldns_client.update_generate_zone(zone_update, status=403)

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


def test_normal_user_cannot_update_shared_zone_flag(shared_zone_test_context):
    """
    Test updating a zone shared status as a normal user fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    result = client.get_zone(shared_zone_test_context.ok_zone["id"], status=200)
    zone_update = result["zone"]
    zone_update["shared"] = True

    error = shared_zone_test_context.ok_vinyldns_client.update_generate_zone(zone_update, status=403)
    assert_that(error, contains_string("Not authorized to update zone shared status from false to true."))


@pytest.mark.serial
def test_update_connection_info_invalid_backendid(shared_zone_test_context):
    """
    Test user can update zone to bad backendId fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.ok_zone

    to_update = client.get_generate_zone(zone["id"])["zone"]
    to_update.pop("connection")
    to_update.pop("transferConnection")
    to_update["backendId"] = "bad-backend-id"

    result = client.update_generate_zone(to_update, status=400)
    assert_that(result, contains_string("Invalid backendId"))
