import pytest

from utils import *


@pytest.mark.serial
def test_delete_zone_success(shared_zone_test_context):
    """
    Test deleting a zone
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

        print("result_zone---------                         ----------------------------",result_zone["id"])

        client.delete_generated_zone(result_zone["id"], status=202)
        #client.wait_until_generated_zone_deleted(result_zone["id"])

        client.get_generate_zone(result_zone["id"], status=404)
        result_zone = None
    finally:
        if result_zone:
            client.abandon_generated_zones([result_zone["id"]], status=202)


@pytest.mark.serial
def test_delete_zone_twice(shared_zone_test_context):
    """
    Test deleting a zone with deleted status returns 404
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
                "kind": "Native",
                "nameservers": [
                    "172.17.42.1.",
                    "ns1.mttest1.example.org."
                ]
            }
        }
        result_zone = client.generate_zone(zone, status=202)
        client.wait_until_generate_zone_active(result_zone["id"])

        client.delete_generated_zone(result_zone["id"], status=202)
        client.wait_until_generated_zone_deleted(result_zone["id"])

        client.get_generate_zone(result_zone["id"], status=404)
        result_zone = None
    finally:
        if result_zone:
            client.abandon_generated_zones([result_zone["id"]], status=202)


def test_delete_zone_returns_404_if_zone_not_found(shared_zone_test_context):
    """
    Test deleting a zone returns a 404 if the zone was not found
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.delete_generated_zone("nothere", status=404)


def test_delete_zone_no_authorization(shared_zone_test_context):
    """
    Test deleting a zone without authorization
    """
    client = shared_zone_test_context.ok_vinyldns_client

    client.delete_generated_zone("1234", sign_request=False, status=401)
