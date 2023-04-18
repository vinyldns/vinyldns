import pytest

from utils import *


def test_get_recordset_no_authorization(shared_zone_test_context):
    """
    Test getting a recordset without authorization
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.get_recordset(shared_zone_test_context.ok_zone["id"], "12345", sign_request=False, status=401)


def test_get_recordset(shared_zone_test_context):
    """
    Test getting a recordset
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None
    try:
        new_rs = {
            "zoneId": shared_zone_test_context.ok_zone["id"],
            "name": "test_get_recordset",
            "type": "A",
            "ttl": 100,
            "records": [
                {
                    "address": "10.1.1.1"
                },
                {
                    "address": "10.2.2.2"
                }
            ]
        }
        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        # Get the recordset we just made and verify
        result = client.get_recordset(result_rs["zoneId"], result_rs["id"])
        result_rs = result["recordSet"]
        verify_recordset(result_rs, new_rs)

        records = [x["address"] for x in result_rs["records"]]
        assert_that(records, has_length(2))
        assert_that("10.1.1.1", is_in(records))
        assert_that("10.2.2.2", is_in(records))
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


def test_get_recordset_zone_doesnt_exist(shared_zone_test_context):
    """
    Test getting a recordset in a zone that doesn't exist should return a 404
    """
    client = shared_zone_test_context.ok_vinyldns_client
    new_rs = {
        "zoneId": shared_zone_test_context.ok_zone["id"],
        "name": "test_get_recordset_zone_doesnt_exist",
        "type": "A",
        "ttl": 100,
        "records": [
            {
                "address": "10.1.1.1"
            },
            {
                "address": "10.2.2.2"
            }
        ]
    }
    result_rs = None
    try:
        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]
        client.get_recordset("5678", result_rs["id"], status=404)
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


def test_get_recordset_doesnt_exist(shared_zone_test_context):
    """
    Test getting a new recordset that doesn't exist should return a 404
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.get_recordset(shared_zone_test_context.ok_zone["id"], "123", status=404)

def test_get_recordsetcount(shared_zone_test_context):
    """
    Test getting a new recordset that doesn't exist should return a 404
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.get_recordset_count(shared_zone_test_context.ok_zone["id"],status=200)

def test_get_recordsetcount_error(shared_zone_test_context):
    """
    Test getting a new recordset that doesn't exist should return a 404
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.get_recordset_count(shared_zone_test_context.ok_zone["id"],status=404)

@pytest.mark.serial
def test_at_get_recordset(shared_zone_test_context):
    """
    Test getting a recordset with name @
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    result_rs = None
    try:
        new_rs = {
            "zoneId": ok_zone["id"],
            "name": "@",
            "type": "TXT",
            "ttl": 100,
            "records": [
                {
                    "text": "someText"
                }
            ]
        }
        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        # Get the recordset we just made and verify
        result = client.get_recordset(result_rs["zoneId"], result_rs["id"])
        result_rs = result["recordSet"]

        expected_rs = new_rs
        expected_rs["name"] = ok_zone["name"]
        verify_recordset(result_rs, expected_rs)

        records = result_rs["records"]
        assert_that(records, has_length(1))
        assert_that(records[0]["text"], is_("someText"))
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


def test_get_recordset_from_shared_zone(shared_zone_test_context):
    """
    Test getting a recordset as the record group owner
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    retrieved_rs = None
    try:
        shared_group = shared_zone_test_context.shared_record_group
        new_rs = create_recordset(shared_zone_test_context.shared_zone,
                                  "test_get_recordset", "TXT", [{"text": "should-work"}],
                                  100,
                                  shared_group["id"])

        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        # Get the recordset we just made and verify
        ok_client = shared_zone_test_context.ok_vinyldns_client
        retrieved = ok_client.get_recordset(result_rs["zoneId"], result_rs["id"])
        retrieved_rs = retrieved["recordSet"]
        verify_recordset(retrieved_rs, new_rs)

        assert_that(retrieved_rs["ownerGroupId"], is_(shared_group["id"]))
        assert_that(retrieved_rs["ownerGroupName"], is_(shared_group["name"]))
    finally:
        if retrieved_rs:
            delete_result = client.delete_recordset(retrieved_rs["zoneId"], retrieved_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


def test_get_unowned_recordset_from_shared_zone_succeeds_if_record_type_approved(shared_zone_test_context):
    """
    Test getting an unowned recordset with no admin rights succeeds if the record type is approved
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None
    try:
        new_rs = create_recordset(shared_zone_test_context.shared_zone, "test_get_unowned_recordset_approved_type", "A", [{"address": "1.2.3.4"}])

        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        # Get the recordset we just made and verify
        retrieved = ok_client.get_recordset(result_rs["zoneId"], result_rs["id"], status=200)
        retrieved_rs = retrieved["recordSet"]
        verify_recordset(retrieved_rs, new_rs)
    finally:
        if result_rs:
            delete_result = ok_client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            ok_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_get_unowned_recordset_from_shared_zone_fails_if_record_type_not_approved(shared_zone_test_context):
    """
    Test getting an unowned recordset with no admin rights fails if the record type is not approved
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    result_rs = None
    try:
        new_rs = create_recordset(shared_zone_test_context.shared_zone, "test_get_unowned_recordset", "MX", [{"preference": 3, "exchange": "mx"}])

        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        # Get the recordset we just made and verify
        dummy_client = shared_zone_test_context.dummy_vinyldns_client
        error = dummy_client.get_recordset(result_rs["zoneId"], result_rs["id"], status=403)
        assert_that(error, is_(f'User dummy does not have access to view test-get-unowned-recordset.{shared_zone_test_context.shared_zone["name"]}'))
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


def test_get_owned_recordset_from_not_shared_zone(shared_zone_test_context):
    """
    Test getting a recordset as the record group owner not in a shared zone fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None
    try:
        new_rs = create_recordset(shared_zone_test_context.ok_zone, "test_cant_get_owned_recordset", "TXT", [{"text": "should-work"}],
                                  ttl=100,
                                  ownergroup_id=shared_zone_test_context.shared_record_group["id"])
        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        # Get the recordset we just made and verify
        shared_client = shared_zone_test_context.shared_zone_vinyldns_client
        shared_client.get_recordset(result_rs["zoneId"], result_rs["id"], status=403)
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")
