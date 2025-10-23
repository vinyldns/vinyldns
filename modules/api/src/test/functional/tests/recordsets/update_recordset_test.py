import copy
from urllib.parse import urljoin

import pytest

from tests.test_data import TestData
from utils import *


def test_update_recordset_name_fails(shared_zone_test_context):
    """
    Tests updating a record set by changing the name fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None
    try:
        new_rs = {
            "zoneId": shared_zone_test_context.system_test_zone["id"],
            "name": "test-update-change-name-success-1",
            "type": "A",
            "ttl": 500,
            "records": [
                {
                    "address": "1.1.1.1"
                },
                {
                    "address": "1.1.1.2"
                }
            ]
        }
        result = client.create_recordset(new_rs, status=202)
        result_rs = result["recordSet"]
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        # update the record set, changing the name
        updated_rs = copy.deepcopy(result_rs)
        updated_rs["name"] = "test-update-change-name-success-2"
        updated_rs["ttl"] = 600
        updated_rs["records"] = [
            {
                "address": "2.2.2.2"
            }
        ]

        error = client.update_recordset(updated_rs, status=422)
        assert_that(error, is_("Cannot update RecordSet's name."))
    finally:
        if result_rs:
            result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=(202, 404))
            if result:
                client.wait_until_recordset_change_status(result, "Complete")


def test_update_recordset_type_fails(shared_zone_test_context):
    """
    Tests updating a record set by changing the type fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None
    try:
        new_rs = {
            "zoneId": shared_zone_test_context.system_test_zone["id"],
            "name": "test-update-change-name-success-1",
            "type": "A",
            "ttl": 500,
            "records": [
                {
                    "address": "1.1.1.1"
                },
                {
                    "address": "1.1.1.2"
                }
            ]
        }
        result = client.create_recordset(new_rs, status=202)
        result_rs = result["recordSet"]
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        # update the record set, changing the name
        updated_rs = copy.deepcopy(result_rs)
        updated_rs["type"] = "AAAA"
        updated_rs["records"] = [
            {
                "address": "1::1"
            }
        ]

        error = client.update_recordset(updated_rs, status=422)
        assert_that(error, is_("Cannot update RecordSet's record type."))
    finally:
        if result_rs:
            result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=(202, 404))
            if result:
                client.wait_until_recordset_change_status(result, "Complete")


def test_update_cname_with_multiple_records(shared_zone_test_context):
    """
    Test that creating a CNAME record set and then updating with multiple records returns an error
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None
    try:
        new_rs = {
            "zoneId": shared_zone_test_context.system_test_zone["id"],
            "name": "test_update_cname_with_multiple_records",
            "type": "CNAME",
            "ttl": 500,
            "records": [
                {
                    "cname": "cname1."
                }
            ]
        }
        result = client.create_recordset(new_rs, status=202)
        result_rs = result["recordSet"]
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        # update the record set, adding another cname record so there are multiple
        updated_rs = copy.deepcopy(result_rs)
        updated_rs["records"] = [
            {
                "cname": "cname1."
            },
            {
                "cname": "cname2."
            }
        ]

        errors = client.update_recordset(updated_rs, status=400)["errors"]
        assert_that(errors[0], is_("CNAME record sets cannot contain multiple records"))
    finally:
        if result_rs:
            r = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(r, "Complete")


@pytest.mark.parametrize("record_name,test_rs", TestData.FORWARD_RECORDS)
def test_update_recordset_forward_record_types(shared_zone_test_context, record_name, test_rs):
    """
    Test updating a record set in a forward zone
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None

    try:
        new_rs = dict(test_rs, zoneId=shared_zone_test_context.system_test_zone["id"])
        new_rs["name"] = generate_record_name() + test_rs["type"]

        result = client.create_recordset(new_rs, status=202)
        assert_that(result["status"], is_("Pending"))

        result_rs = result["recordSet"]
        verify_recordset(result_rs, new_rs)

        records = result_rs["records"]

        for record in new_rs["records"]:
            assert_that(records, has_item(has_entries(record)))

        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        # now update
        update_rs = result_rs
        update_rs["ttl"] = 1000

        result = client.update_recordset(update_rs, status=202)
        assert_that(result["status"], is_("Pending"))
        result_rs = result["recordSet"]

        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]
        assert_that(result_rs["ttl"], is_(1000))
    finally:
        if result_rs:
            result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=(202, 404))
            if result:
                client.wait_until_recordset_change_status(result, "Complete")


@pytest.mark.serial
@pytest.mark.parametrize("record_name,test_rs", TestData.REVERSE_RECORDS)
def test_update_reverse_record_types(shared_zone_test_context, record_name, test_rs):
    """
    Test updating a record set in a reverse zone
    """
    # TODO: reverse records are difficult to run in parallel because there aren't many, need to coordinate across tests
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None

    try:
        new_rs = dict(test_rs, zoneId=shared_zone_test_context.ip4_reverse_zone["id"])

        result = client.create_recordset(new_rs, status=202)
        assert_that(result["status"], is_("Pending"))

        result_rs = result["recordSet"]
        verify_recordset(result_rs, new_rs)

        records = result_rs["records"]

        for record in new_rs["records"]:
            assert_that(records, has_item(has_entries(record)))

        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        # now update
        update_rs = result_rs
        update_rs["ttl"] = 1000

        result = client.update_recordset(update_rs, status=202)
        assert_that(result["status"], is_("Pending"))
        result_rs = result["recordSet"]

        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]
        assert_that(result_rs["ttl"], is_(1000))
    finally:
        if result_rs:
            result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=(202, 404))
            if result:
                client.wait_until_recordset_change_status(result, "Complete")


def test_update_record_in_zone_user_owns(shared_zone_test_context):
    """
    Test user can update a record that it owns
    """
    client = shared_zone_test_context.ok_vinyldns_client
    rs = None
    try:
        rs = client.create_recordset(
            {
                "zoneId": shared_zone_test_context.ok_zone["id"],
                "name": "test_user_can_update_record_in_zone_it_owns",
                "type": "A",
                "ttl": 100,
                "records": [
                    {
                        "address": "10.1.1.1"
                    }
                ]
            }, status=202
        )["recordSet"]
        client.wait_until_recordset_exists(rs["zoneId"], rs["id"])

        rs["ttl"] = rs["ttl"] + 1000

        result = client.update_recordset(rs, status=202, retries=3)
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]
        assert_that(result_rs["ttl"], is_(rs["ttl"]))
    finally:
        if rs:
            try:
                client.delete_recordset(rs["zoneId"], rs["id"], status=(202, 404))
                client.wait_until_recordset_deleted(rs["zoneId"], rs["id"])
            finally:
                pass


def test_update_recordset_no_authorization(shared_zone_test_context):
    """
    Test updating a record set without authorization
    """
    client = shared_zone_test_context.ok_vinyldns_client
    rs = {
        "id": "12345",
        "zoneId": shared_zone_test_context.ok_zone["id"],
        "name": "test_update_recordset_no_authorization",
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
    client.update_recordset(rs, sign_request=False, status=401)


def test_update_recordset_replace_2_records_with_1_different_record(shared_zone_test_context):
    """
    Test creating a new record set in an existing zone and then updating that record set to replace the existing
    records with one new one
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    result_rs = None
    try:
        new_rs = {
            "zoneId": ok_zone["id"],
            "name": "test_update_recordset_replace_2_records_with_1_different_record",
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

        assert_that(result["changeType"], is_("Create"))
        assert_that(result["status"], is_("Pending"))
        assert_that(result["created"], is_not(none()))
        assert_that(result["userId"], is_not(none()))

        result_rs = result["recordSet"]
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]
        verify_recordset(result_rs, new_rs)

        records = [x["address"] for x in result_rs["records"]]
        assert_that(records, has_length(2))
        assert_that("10.1.1.1", is_in(records))
        assert_that("10.2.2.2", is_in(records))

        result_rs["ttl"] = 200

        modified_records = [
            {
                "address": "1.1.1.1"
            }
        ]
        result_rs["records"] = modified_records

        result = client.update_recordset(result_rs, status=202)
        assert_that(result["status"], is_("Pending"))
        result = client.wait_until_recordset_change_status(result, "Complete")

        assert_that(result["changeType"], is_("Update"))
        assert_that(result["status"], is_("Complete"))
        assert_that(result["created"], is_not(none()))
        assert_that(result["userId"], is_not(none()))

        # make sure the update was applied
        result_rs = result["recordSet"]
        records = [x["address"] for x in result_rs["records"]]
        assert_that(records, has_length(1))
        assert_that(records[0], is_("1.1.1.1"))

        # verify that the record exists in the backend dns server
        answers = dns_resolve(ok_zone, result_rs["name"], result_rs["type"])
        rdata_strings = rdata(answers)
        assert_that(rdata_strings, has_length(1))
        assert_that("1.1.1.1", is_in(rdata_strings))
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_existing_record_set_add_record(shared_zone_test_context):
    """
    Test creating a new record set in an existing zone and then updating that record set to add a record
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    result_rs = None
    try:
        new_rs = {
            "zoneId": ok_zone["id"],
            "name": "test_update_existing_record_set_add_record",
            "type": "A",
            "ttl": 100,
            "records": [
                {
                    "address": "10.2.2.2"
                }
            ]
        }
        result = client.create_recordset(new_rs, status=202)

        assert_that(result["changeType"], is_("Create"))
        assert_that(result["status"], is_("Pending"))
        assert_that(result["created"], is_not(none()))
        assert_that(result["userId"], is_not(none()))

        result_rs = result["recordSet"]
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]
        verify_recordset(result_rs, new_rs)

        records = [x["address"] for x in result_rs["records"]]
        assert_that(records, has_length(1))
        assert_that(records[0], is_("10.2.2.2"))

        answers = dns_resolve(ok_zone, result_rs["name"], result_rs["type"])
        rdata_strings = rdata(answers)

        # Update the record set, adding a new record to the existing one
        modified_records = [
            {
                "address": "4.4.4.8"
            },
            {
                "address": "10.2.2.2"
            }
        ]
        result_rs["records"] = modified_records

        result = client.update_recordset(result_rs, status=202)
        assert_that(result["status"], is_("Pending"))
        result = client.wait_until_recordset_change_status(result, "Complete")

        assert_that(result["changeType"], is_("Update"))
        assert_that(result["status"], is_("Complete"))
        assert_that(result["created"], is_not(none()))
        assert_that(result["userId"], is_not(none()))

        # make sure the update was applied
        result_rs = result["recordSet"]
        records = [x["address"] for x in result_rs["records"]]
        assert_that(records, has_length(2))
        assert_that("10.2.2.2", is_in(records))
        assert_that("4.4.4.8", is_in(records))

        answers = dns_resolve(ok_zone, result_rs["name"], result_rs["type"])
        rdata_strings = rdata(answers)

        assert_that(rdata_strings, has_length(2))
        assert_that("10.2.2.2", is_in(rdata_strings))
        assert_that("4.4.4.8", is_in(rdata_strings))
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_existing_record_set_delete_record(shared_zone_test_context):
    """
    Test creating a new record set in an existing zone and then updating that record set to delete a record
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    result_rs = None
    try:
        new_rs = {
            "zoneId": ok_zone["id"],
            "name": "test_update_existing_record_set_delete_record",
            "type": "A",
            "ttl": 100,
            "records": [
                {
                    "address": "10.1.1.1"
                },
                {
                    "address": "10.2.2.2"
                },
                {
                    "address": "10.3.3.3"
                },
                {
                    "address": "10.4.4.4"
                }
            ]
        }
        result = client.create_recordset(new_rs, status=202)

        assert_that(result["changeType"], is_("Create"))
        assert_that(result["status"], is_("Pending"))
        assert_that(result["created"], is_not(none()))
        assert_that(result["userId"], is_not(none()))

        result_rs = result["recordSet"]
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]
        verify_recordset(result_rs, new_rs)

        records = [x["address"] for x in result_rs["records"]]
        assert_that(records, has_length(4))
        assert_that(records[0], is_("10.1.1.1"))
        assert_that(records[1], is_("10.2.2.2"))
        assert_that(records[2], is_("10.3.3.3"))
        assert_that(records[3], is_("10.4.4.4"))

        answers = dns_resolve(ok_zone, result_rs["name"], result_rs["type"])
        rdata_strings = rdata(answers)
        assert_that(rdata_strings, has_length(4))

        # Update the record set, delete three records and leave one
        modified_records = [
            {
                "address": "10.2.2.2"
            }
        ]
        result_rs["records"] = modified_records

        result = client.update_recordset(result_rs, status=202)
        result = client.wait_until_recordset_change_status(result, "Complete")

        # make sure the update was applied
        result_rs = result["recordSet"]
        records = [x["address"] for x in result_rs["records"]]
        assert_that(records, has_length(1))
        assert_that("10.2.2.2", is_in(records))

        # do a DNS query
        answers = dns_resolve(ok_zone, result_rs["name"], result_rs["type"])
        rdata_strings = rdata(answers)

        assert_that(rdata_strings, has_length(1))
        assert_that("10.2.2.2", is_in(rdata_strings))
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_ipv4_ptr_recordset_with_verify(shared_zone_test_context):
    """
    Test updating an IPv4 PTR record set returns the updated values after complete
    """
    client = shared_zone_test_context.ok_vinyldns_client
    reverse4_zone = shared_zone_test_context.ip4_reverse_zone
    result_rs = None
    try:
        orig_rs = {
            "zoneId": reverse4_zone["id"],
            "name": "30.0",
            "type": "PTR",
            "ttl": 100,
            "records": [
                {
                    "ptrdname": "ftp.vinyldns."
                }
            ]
        }
        result = client.create_recordset(orig_rs, status=202)

        result_rs = result["recordSet"]
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        new_ptr_target = "www.vinyldns."
        new_rs = result_rs
        new_rs["records"][0]["ptrdname"] = new_ptr_target
        result = client.update_recordset(new_rs, status=202)

        result_rs = result["recordSet"]
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        verify_recordset(result_rs, new_rs)

        records = result_rs["records"]
        assert_that(records[0]["ptrdname"], is_(new_ptr_target))

        # verify that the record exists in the backend dns server
        answers = dns_resolve(reverse4_zone, result_rs["name"], result_rs["type"])
        rdata_strings = rdata(answers)

        assert_that(rdata_strings, has_length(1))
        assert_that(rdata_strings[0], is_(new_ptr_target))
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_ipv6_ptr_recordset(shared_zone_test_context):
    """
    Test updating an IPv6 PTR record set returns the updated values after complete
    """
    client = shared_zone_test_context.ok_vinyldns_client
    reverse6_zone = shared_zone_test_context.ip6_reverse_zone
    result_rs = None
    try:
        orig_rs = {
            "zoneId": reverse6_zone["id"],
            "name": "0.6.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0",
            "type": "PTR",
            "ttl": 100,
            "records": [
                {
                    "ptrdname": "ftp.vinyldns."
                }
            ]
        }
        result = client.create_recordset(orig_rs, status=202)

        result_rs = result["recordSet"]
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        new_ptr_target = "www.vinyldns."
        new_rs = result_rs
        new_rs["records"][0]["ptrdname"] = new_ptr_target
        result = client.update_recordset(new_rs, status=202)

        result_rs = result["recordSet"]
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        verify_recordset(result_rs, new_rs)

        records = result_rs["records"]
        assert_that(records[0]["ptrdname"], is_(new_ptr_target))

        answers = dns_resolve(reverse6_zone, result_rs["name"], result_rs["type"])
        rdata_strings = rdata(answers)
        assert_that(rdata_strings, has_length(1))
        assert_that(rdata_strings[0], is_(new_ptr_target))
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_recordset_zone_not_found(shared_zone_test_context):
    """
    Test updating a record set in a zone that doesn't exist should return a 404
    """
    client = shared_zone_test_context.ok_vinyldns_client
    new_rs = None

    try:
        new_rs = {
            "zoneId": shared_zone_test_context.ok_zone["id"],
            "name": "test_update_recordset_zone_not_found",
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
        new_rs = result["recordSet"]
        client.wait_until_recordset_exists(new_rs["zoneId"], new_rs["id"])
        new_rs["zoneId"] = "1234"
        client.update_recordset(new_rs, status=404)
    finally:
        if new_rs:
            try:
                client.delete_recordset(shared_zone_test_context.ok_zone["id"], new_rs["id"], status=(202, 404))
                client.wait_until_recordset_deleted(shared_zone_test_context.ok_zone["id"], new_rs["id"])
            finally:
                pass


def test_update_recordset_not_found(shared_zone_test_context):
    """
    Test updating a record set that doesn't exist should return a 404
    """
    client = shared_zone_test_context.ok_vinyldns_client
    new_rs = {
        "id": "nothere",
        "zoneId": shared_zone_test_context.ok_zone["id"],
        "name": "test_update_recordset_not_found",
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
    client.update_recordset(new_rs, status=404)


def test_at_update_recordset(shared_zone_test_context):
    """
    Test creating a new record set with name @ in an existing zone and then updating that recordset
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

        assert_that(result["changeType"], is_("Create"))
        assert_that(result["status"], is_("Pending"))
        assert_that(result["created"], is_not(none()))
        assert_that(result["userId"], is_not(none()))

        result_rs = result["recordSet"]
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        expected_rs = new_rs
        expected_rs["name"] = ok_zone["name"]
        verify_recordset(result_rs, expected_rs)

        records = result_rs["records"]
        assert_that(records, has_length(1))
        assert_that(records[0]["text"], is_("someText"))

        result_rs["ttl"] = 200
        result_rs["records"][0]["text"] = "differentText"

        result = client.update_recordset(result_rs, status=202)
        assert_that(result["status"], is_("Pending"))
        result = client.wait_until_recordset_change_status(result, "Complete")

        assert_that(result["changeType"], is_("Update"))
        assert_that(result["status"], is_("Complete"))
        assert_that(result["created"], is_not(none()))
        assert_that(result["userId"], is_not(none()))

        # make sure the update was applied
        result_rs = result["recordSet"]
        records = result_rs["records"]
        assert_that(records, has_length(1))
        assert_that(records[0]["text"], is_("differentText"))
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_user_can_update_record_via_user_acl_rule(shared_zone_test_context):
    """
    Test user WRITE ACL rule - update
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule = generate_acl_rule("Write", userId="dummy")

        result_rs = seed_text_recordset(client, "test_user_can_update_record_via_user_acl_rule", ok_zone)

        expected_ttl = result_rs["ttl"] + 1000
        result_rs["ttl"] = result_rs["ttl"] + 1000

        # Dummy user cannot update record in zone
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403, retries=3)

        # add rule
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])

        # Dummy user can update record
        result = shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
        result_rs = shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_change_status(result, "Complete")["recordSet"]
        assert_that(result_rs["ttl"], is_(expected_ttl))
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_user_can_update_record_via_group_acl_rule(shared_zone_test_context):
    """
    Test group WRITE ACL rule - update
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    acl_rule = generate_acl_rule("Write", groupId=shared_zone_test_context.dummy_group["id"])
    try:
        result_rs = seed_text_recordset(client, "test_user_can_update_record_via_group_acl_rule", ok_zone)

        expected_ttl = result_rs["ttl"] + 1000
        result_rs["ttl"] = result_rs["ttl"] + 1000

        # Dummy user cannot update record in zone
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403)

        # add rule
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])

        # Dummy user can update record
        result = shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
        result_rs = shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_change_status(result, "Complete")["recordSet"]
        assert_that(result_rs["ttl"], is_(expected_ttl))
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_user_rule_priority_over_group_acl_rule(shared_zone_test_context):
    """
    Test user rule takes priority over group rule
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        group_acl_rule = generate_acl_rule("Read", groupId=shared_zone_test_context.dummy_group["id"])
        user_acl_rule = generate_acl_rule("Write", userId="dummy")

        result_rs = seed_text_recordset(client, "test_user_rule_priority_over_group_acl_rule", ok_zone)

        expected_ttl = result_rs["ttl"] + 1000
        result_rs["ttl"] = result_rs["ttl"] + 1000

        # add rules
        add_ok_acl_rules(shared_zone_test_context, [group_acl_rule, user_acl_rule])

        # Dummy user can update record
        result = shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
        result_rs = shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_change_status(result, "Complete")["recordSet"]
        assert_that(result_rs["ttl"], is_(expected_ttl))
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=(202, 404))
            client.wait_until_recordset_deleted(result_rs["zoneId"], result_rs["id"])


@pytest.mark.serial
def test_more_permissive_acl_rule_priority(shared_zone_test_context):
    """
    Test more permissive rule takes priority
    """
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None
    try:
        read_rule = generate_acl_rule("Read", userId="dummy")
        write_rule = generate_acl_rule("Write", userId="dummy")

        result_rs = seed_text_recordset(client, "test_more_permissive_acl_rule_priority", ok_zone)
        result_rs["ttl"] = result_rs["ttl"] + 1000

        # add rules 
        add_ok_acl_rules(shared_zone_test_context, [read_rule, write_rule])

        # Dummy user can update record
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_acl_rule_with_record_type_success(shared_zone_test_context):
    """
    Test a rule on a specific record type applies to that type
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule = generate_acl_rule("Write", userId="dummy", recordTypes=["TXT"])

        result_rs = seed_text_recordset(client, "test_acl_rule_with_record_type_success", ok_zone)

        expected_ttl = result_rs["ttl"] + 1000
        result_rs["ttl"] = result_rs["ttl"] + 1000

        z = client.get_zone(ok_zone["id"])

        # Dummy user cannot update record in zone
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403, retries=3)

        # add rule
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])

        # Dummy user can update record
        result = shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
        result_rs = shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_change_status(result, "Complete")[
            "recordSet"]
        assert_that(result_rs["ttl"], is_(expected_ttl))
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_acl_rule_with_cidr_ip4_success(shared_zone_test_context):
    """
    Test a rule on a specific record type applies to that type
    """
    result_rs = None
    ip4_zone = shared_zone_test_context.ip4_reverse_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule = generate_acl_rule("Write", userId="dummy", recordTypes=["PTR"], recordMask=f"{shared_zone_test_context.ip4_10_prefix}.0.0/32")

        result_rs = seed_ptr_recordset(client, "0.0", ip4_zone)

        expected_ttl = result_rs["ttl"] + 1000
        result_rs["ttl"] = result_rs["ttl"] + 1000

        # Dummy user cannot update record in zone
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403, retries=3)

        # add rule
        add_ip4_acl_rules(shared_zone_test_context, [acl_rule])

        # Dummy user can update record
        result = shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
        result_rs = shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_change_status(result, "Complete")["recordSet"]
        assert_that(result_rs["ttl"], is_(expected_ttl))
    finally:
        clear_ip4_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_acl_rule_with_cidr_ip4_failure(shared_zone_test_context):
    """
    Test a rule on a specific record type applies to that type
    """
    result_rs = None
    ip4_zone = shared_zone_test_context.ip4_reverse_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule = generate_acl_rule("Write", userId="dummy", recordTypes=["PTR"], recordMask="172.30.0.0/32")

        result_rs = seed_ptr_recordset(client, "0.1", ip4_zone)

        # Dummy user cannot update record in zone
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403, retries=3)

        # add rule
        add_ip4_acl_rules(shared_zone_test_context, [acl_rule])

        # Dummy user still cant update record
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403)
    finally:
        clear_ip4_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_acl_rule_with_cidr_ip6_success(shared_zone_test_context):
    """
    Test a rule on a specific record type applies to that type
    """
    result_rs = None
    ip6_zone = shared_zone_test_context.ip6_reverse_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule = generate_acl_rule("Write", userId="dummy", recordTypes=["PTR"],
                                     recordMask=f"{shared_zone_test_context.ip6_prefix}:0000:0000:0000:0000:0000/127")

        result_rs = seed_ptr_recordset(client, "0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0", ip6_zone)

        expected_ttl = result_rs["ttl"] + 1000
        result_rs["ttl"] = result_rs["ttl"] + 1000

        # Dummy user cannot update record in zone
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403, retries=3)

        # add rule
        add_ip6_acl_rules(shared_zone_test_context, [acl_rule])

        # Dummy user can update record
        result = shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
        result_rs = shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_change_status(result, "Complete")[
            "recordSet"]
        assert_that(result_rs["ttl"], is_(expected_ttl))
    finally:
        clear_ip6_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_acl_rule_with_cidr_ip6_failure(shared_zone_test_context):
    """
    Test a rule on a specific record type applies to that type
    """
    result_rs = None
    ip6_zone = shared_zone_test_context.ip6_reverse_zone
    client = shared_zone_test_context.ok_vinyldns_client
    ip6_prefix = shared_zone_test_context.ip6_prefix
    try:
        acl_rule = generate_acl_rule("Write", userId="dummy", recordTypes=["PTR"],
                                     recordMask=f"{ip6_prefix}:0000:0000:0000:0000:0000/127")

        result_rs = seed_ptr_recordset(client, "0.0.0.0.0.0.0.0.0.0.0.0.0.0.5.0.0.0.0.0", ip6_zone)

        # Dummy user cannot update record in zone
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403, retries=3)

        # add rule
        add_ip6_acl_rules(shared_zone_test_context, [acl_rule])

        # Dummy user still cant update record
        result = shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403)
    finally:
        clear_ip6_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_more_restrictive_cidr_ip4_rule_priority(shared_zone_test_context):
    """
    Test more restrictive cidr rule takes priority
    """
    ip4_zone = shared_zone_test_context.ip4_reverse_zone
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None
    try:
        slash16_rule = generate_acl_rule("Read", userId="dummy", recordTypes=["PTR"], recordMask=f"{shared_zone_test_context.ip4_10_prefix}.0.0/16")
        slash32_rule = generate_acl_rule("Write", userId="dummy", recordTypes=["PTR"], recordMask=f"{shared_zone_test_context.ip4_10_prefix}.0.0/32")

        result_rs = seed_ptr_recordset(client, "0.0", ip4_zone)
        result_rs["ttl"] = result_rs["ttl"] + 1000

        # add rules
        add_ip4_acl_rules(shared_zone_test_context, [slash16_rule, slash32_rule])

        # Dummy user can update record
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
    finally:
        clear_ip4_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_more_restrictive_cidr_ip6_rule_priority(shared_zone_test_context):
    """
    Test more restrictive cidr rule takes priority
    """
    ip6_zone = shared_zone_test_context.ip6_reverse_zone
    client = shared_zone_test_context.ok_vinyldns_client
    ip6_prefix = shared_zone_test_context.ip6_prefix
    result_rs = None
    try:
        slash50_rule = generate_acl_rule("Read", userId="dummy", recordTypes=["PTR"],
                                         recordMask=f"{ip6_prefix}:0000:0000:0000:0000:0000/50")
        slash100_rule = generate_acl_rule("Write", userId="dummy", recordTypes=["PTR"],
                                          recordMask=f"{ip6_prefix}:0000:0000:0000:0000:0000/100")

        result_rs = seed_ptr_recordset(client, "0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0", ip6_zone)
        result_rs["ttl"] = result_rs["ttl"] + 1000

        # add rules
        add_ip6_acl_rules(shared_zone_test_context, [slash50_rule, slash100_rule])

        # Dummy user can update record
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
    finally:
        clear_ip6_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_mix_of_cidr_ip6_and_acl_rules_priority(shared_zone_test_context):
    """
    A and AAAA should have read from mixed rule, PTR should have Write from rule with mask
    """
    ip6_zone = shared_zone_test_context.ip6_reverse_zone
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    ip6_prefix = shared_zone_test_context.ip6_prefix
    result_rs_ptr = None
    result_rs_a = None
    result_rs_aaaa = None

    try:
        mixed_type_rule_no_mask = generate_acl_rule("Read", userId="dummy", recordTypes=["PTR", "AAAA", "A"])
        ptr_rule_with_mask = generate_acl_rule("Write", userId="dummy", recordTypes=["PTR"],
                                               recordMask=f"{ip6_prefix}:0000:0000:0000:0000:0000/50")

        result_rs_ptr = seed_ptr_recordset(client, "0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0", ip6_zone)
        result_rs_ptr["ttl"] = result_rs_ptr["ttl"] + 1000

        result_rs_a = seed_text_recordset(client, "test_more_restrictive_acl_rule_priority_1", ok_zone)
        result_rs_a["ttl"] = result_rs_a["ttl"] + 1000

        result_rs_aaaa = seed_text_recordset(client, "test_more_restrictive_acl_rule_priority_2", ok_zone)
        result_rs_aaaa["ttl"] = result_rs_aaaa["ttl"] + 1000

        # add rules
        add_ip6_acl_rules(shared_zone_test_context, [mixed_type_rule_no_mask, ptr_rule_with_mask])
        add_ok_acl_rules(shared_zone_test_context, [mixed_type_rule_no_mask, ptr_rule_with_mask])

        # Dummy user cannot update record for A,AAAA, but can for PTR
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs_ptr, status=202)
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs_a, status=403)
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs_aaaa, status=403)
    finally:
        clear_ip6_acl_rules(shared_zone_test_context)
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs_a:
            delete_result = client.delete_recordset(result_rs_a["zoneId"], result_rs_a["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")
        if result_rs_aaaa:
            delete_result = client.delete_recordset(result_rs_aaaa["zoneId"], result_rs_aaaa["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")
        if result_rs_ptr:
            delete_result = client.delete_recordset(result_rs_ptr["zoneId"], result_rs_ptr["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_acl_rule_with_wrong_record_type(shared_zone_test_context):
    """
    Test a rule on a specific record type does not apply to other types
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule = generate_acl_rule("Write", userId="dummy", recordTypes=["CNAME"])

        result_rs = seed_text_recordset(client, "test_acl_rule_with_wrong_record_type", ok_zone)
        result_rs["ttl"] = result_rs["ttl"] + 1000

        # Dummy user cannot update record in zone
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403, retries=3)

        # add rule
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])

        # Dummy user cannot update record
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403, retries=3)
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_empty_acl_record_type_applies_to_all(shared_zone_test_context):
    """
    Test an empty record set rule applies to all types
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule = generate_acl_rule("Write", userId="dummy", recordTypes=[])

        result_rs = seed_text_recordset(client, "test_empty_acl_record_type_applies_to_all", ok_zone)
        expected_ttl = result_rs["ttl"] + 1000
        result_rs["ttl"] = expected_ttl

        # Dummy user cannot update record in zone
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403, retries=3)

        # add rule
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])

        # Dummy user can update record
        result = shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
        result_rs = shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_change_status(result, "Complete")[
            "recordSet"]
        assert_that(result_rs["ttl"], is_(expected_ttl))
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_acl_rule_with_fewer_record_types_prioritized(shared_zone_test_context):
    """
    Test a rule on a specific record type takes priority over a group of types
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule_base = generate_acl_rule("Write", userId="dummy")
        acl_rule1 = generate_acl_rule("Write", userId="dummy", recordTypes=["TXT", "CNAME"])
        acl_rule2 = generate_acl_rule("Read", userId="dummy", recordTypes=["TXT"])

        result_rs = seed_text_recordset(client, "test_acl_rule_with_fewer_record_types_prioritized", ok_zone)
        result_rs["ttl"] = result_rs["ttl"] + 1000

        add_ok_acl_rules(shared_zone_test_context, [acl_rule_base])

        # Dummy user can update record in zone with base rule
        result = shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
        result_rs = shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        # add rule
        add_ok_acl_rules(shared_zone_test_context, [acl_rule1, acl_rule2])

        # Dummy user cannot update record
        result_rs["ttl"] = result_rs["ttl"] + 1000
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403)
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_acl_rule_user_over_record_type_priority(shared_zone_test_context):
    """
    Test the user priority takes precedence over record type priority
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule_base = generate_acl_rule("Write", userId="dummy")
        acl_rule1 = generate_acl_rule("Write", groupId=shared_zone_test_context.dummy_group["id"], recordTypes=["TXT"])
        acl_rule2 = generate_acl_rule("Read", userId="dummy", recordTypes=["TXT", "CNAME"])

        result_rs = seed_text_recordset(client, "test_acl_rule_user_over_record_type_priority", ok_zone)
        result_rs["ttl"] = result_rs["ttl"] + 1000

        add_ok_acl_rules(shared_zone_test_context, [acl_rule_base])

        # Dummy user can update record in zone with base rule
        result = shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
        result_rs = shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        # add rule
        add_ok_acl_rules(shared_zone_test_context, [acl_rule1, acl_rule2])

        # Dummy user cannot update record
        result_rs["ttl"] = result_rs["ttl"] + 1000
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403)
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_acl_rule_with_record_mask_success(shared_zone_test_context):
    """
    Test rule with record mask allows user to update record
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule = generate_acl_rule("Write", groupId=shared_zone_test_context.dummy_group["id"], recordMask="test.*")

        result_rs = seed_text_recordset(client, "test_acl_rule_with_record_mask_success", ok_zone)
        expected_ttl = result_rs["ttl"] + 1000
        result_rs["ttl"] = expected_ttl

        # Dummy user cannot update record in zone
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403)

        # add rule
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])

        # Dummy user can update record
        result = shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
        result_rs = shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_change_status(result, "Complete")[
            "recordSet"]
        assert_that(result_rs["ttl"], is_(expected_ttl))
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_acl_rule_with_record_mask_failure(shared_zone_test_context):
    """
    Test rule with unmatching record mask is not applied
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule = generate_acl_rule("Write", groupId=shared_zone_test_context.dummy_group["id"], recordMask="bad.*")

        result_rs = seed_text_recordset(client, "test_acl_rule_with_record_mask_failure", ok_zone)
        result_rs["ttl"] = result_rs["ttl"] + 1000

        # add rule
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])

        # Dummy user cannot update record
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403)
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_acl_rule_with_defined_mask_prioritized(shared_zone_test_context):
    """
    Test a rule on a specific record mask takes priority over All
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule_base = generate_acl_rule("Write", userId="dummy")
        acl_rule1 = generate_acl_rule("Write", userId="dummy", recordMask=".*")
        acl_rule2 = generate_acl_rule("Read", userId="dummy", recordMask="test.*")

        result_rs = seed_text_recordset(client, "test_acl_rule_with_defined_mask_prioritized", ok_zone)
        result_rs["ttl"] = result_rs["ttl"] + 1000

        add_ok_acl_rules(shared_zone_test_context, [acl_rule_base])

        # Dummy user can update record in zone with base rule
        result = shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
        result_rs = shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_change_status(result, "Complete")[
            "recordSet"]

        # add rule
        add_ok_acl_rules(shared_zone_test_context, [acl_rule1, acl_rule2])

        # Dummy user cannot update record
        result_rs["ttl"] = result_rs["ttl"] + 1000
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403)
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_user_rule_over_mask_prioritized(shared_zone_test_context):
    """
    Test user/group logic priority over record mask
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule_base = generate_acl_rule("Write", userId="dummy")
        acl_rule1 = generate_acl_rule("Write", groupId=shared_zone_test_context.dummy_group["id"], recordMask="test.*")
        acl_rule2 = generate_acl_rule("Read", userId="dummy", recordMask=".*")

        result_rs = seed_text_recordset(client, "test_user_rule_over_mask_prioritized", ok_zone)
        result_rs["ttl"] = result_rs["ttl"] + 1000

        add_ok_acl_rules(shared_zone_test_context, [acl_rule_base])

        # Dummy user can update record in zone with base rule
        result = shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=202)
        result_rs = shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_change_status(result, "Complete")[
            "recordSet"]

        # add rule
        add_ok_acl_rules(shared_zone_test_context, [acl_rule1, acl_rule2])

        # Dummy user cannot update record
        result_rs["ttl"] = result_rs["ttl"] + 1000
        shared_zone_test_context.dummy_vinyldns_client.update_recordset(result_rs, status=403)
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


def test_ns_update_passes(shared_zone_test_context):
    """
    Tests that someone in the admin group can update ns record
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.parent_zone
    ns_rs = None

    try:
        new_rs = {
            "zoneId": zone["id"],
            "name": "someNS",
            "type": "NS",
            "ttl": 38400,
            "records": [
                {
                    "nsdname": "ns1.parent.com."
                }
            ]
        }
        result = client.create_recordset(new_rs, status=202)
        ns_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        changed_rs = ns_rs
        changed_rs["ttl"] = changed_rs["ttl"] + 100

        change_result = client.update_recordset(changed_rs, status=202)
        client.wait_until_recordset_change_status(change_result, "Complete")
    finally:
        if ns_rs:
            client.delete_recordset(ns_rs["zoneId"], ns_rs["id"], status=(202, 404))
            client.wait_until_recordset_deleted(ns_rs["zoneId"], ns_rs["id"])


def test_ns_update_for_unapproved_server_fails(shared_zone_test_context):
    """
    Tests that an ns update fails if one of the servers isnt approved
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.parent_zone
    ns_rs = None

    try:
        new_rs = {
            "zoneId": zone["id"],
            "name": "badNSupdate",
            "type": "NS",
            "ttl": 38400,
            "records": [
                {
                    "nsdname": "ns1.parent.com."
                }
            ]
        }
        result = client.create_recordset(new_rs, status=202)
        ns_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]

        changed_rs = ns_rs

        bad_records = [
            {
                "nsdname": "ns1.parent.com."
            },
            {
                "nsdname": "this.is.bad."
            }
        ]
        changed_rs["records"] = bad_records

        client.update_recordset(changed_rs, status=422)
    finally:
        if ns_rs:
            client.delete_recordset(ns_rs["zoneId"], ns_rs["id"], status=(202, 404))
            client.wait_until_recordset_deleted(ns_rs["zoneId"], ns_rs["id"])


def test_update_to_txt_dotted_host_succeeds(shared_zone_test_context):
    """
    Tests that a TXT dotted host record set update succeeds
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        result_rs = seed_text_recordset(client, "update_with.dots", ok_zone)
        result_rs["ttl"] = 333

        update_rs = client.update_recordset(result_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(update_rs, "Complete")["recordSet"]
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


def test_ns_update_existing_ns_origin_fails(shared_zone_test_context):
    """
    Tests that an ns update for existing ns origin fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.parent_zone

    list_results_page = client.list_recordsets_by_zone(zone["id"], status=200)["recordSets"]
    apex_ns = [item for item in list_results_page if item["type"] == "NS" and item["name"] in zone["name"]][0]
    apex_ns["ttl"] = apex_ns["ttl"] + 100

    client.update_recordset(apex_ns, status=422)


def test_update_existing_dotted_a_record_succeeds(shared_zone_test_context):
    """
    Test that updating an existing A record with dotted host name succeeds
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.ok_zone

    recordsets = client.list_recordsets_by_zone(zone["id"], record_name_filter="dotted.a", status=200)["recordSets"]
    update_rs = recordsets[0]
    update_rs["records"] = [{"address": "1.1.1.1"}]

    try:
        update_response = client.update_recordset(update_rs, status=202)
        updated_rs = client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(updated_rs["records"], is_([{"address": "1.1.1.1"}]))
    finally:
        update_rs["records"] = [{"address": "7.7.7.7"}]
        revert_rs_update = client.update_recordset(update_rs, status=202)
        client.wait_until_recordset_change_status(revert_rs_update, "Complete")


def test_update_existing_dotted_cname_record_succeeds(shared_zone_test_context):
    """
    Test that updating an existing CNAME record with dotted host name succeeds
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.ok_zone

    recordsets = client.list_recordsets_by_zone(zone["id"], record_name_filter="dottedc.name", status=200)["recordSets"]
    update_rs = recordsets[0]
    update_rs["records"] = [{"cname": "got.reference"}]
    try:
        update_response = client.update_recordset(update_rs, status=202)
        updated_rs = client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(updated_rs["records"], is_([{"cname": "got.reference."}]))
    finally:
        update_rs["records"] = [{"cname": "test.example.com"}]
        revert_rs_update = client.update_recordset(update_rs, status=202)
        client.wait_until_recordset_change_status(revert_rs_update, "Complete")


def test_update_succeeds_for_applied_unsynced_record_change(shared_zone_test_context):
    """
    Update should succeed if record change is not synced with DNS backend, but has already been applied
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.parent_zone

    a_rs = create_recordset(zone, "already-applied-unsynced-update", "A",
                            [{"address": "1.1.1.1"}, {"address": "2.2.2.2"}])

    create_rs = {}

    try:
        create_response = client.create_recordset(a_rs, status=202)
        create_rs = client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]

        dns_update(zone, "already-applied-unsynced-update", 550, "A", "8.8.8.8")

        updates = create_rs
        updates["ttl"] = 550
        updates["records"] = [
            {
                "address": "8.8.8.8"
            }
        ]

        update_response = client.update_recordset(updates, status=202)
        update_rs = client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]

        retrieved_rs = client.get_recordset(zone["id"], update_rs["id"])["recordSet"]
        verify_recordset(retrieved_rs, updates)
    finally:
        try:
            delete_result = client.delete_recordset(zone["id"], create_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")
        except Exception:
            traceback.print_exc()
            pass


def test_update_fails_for_unapplied_unsynced_record_change(shared_zone_test_context):
    """
    Update should fail if record change is not synced with DNS backend
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.parent_zone

    a_rs = create_recordset(zone, "unapplied-unsynced-update", "A", [{"address": "1.1.1.1"}, {"address": "2.2.2.2"}])

    create_rs = {}

    try:
        create_response = client.create_recordset(a_rs, status=202)
        create_rs = client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]

        dns_update(zone, "unapplied-unsynced-update", 550, "A", "8.8.8.8")

        update_rs = create_rs
        update_rs["records"] = [
            {
                "address": "5.5.5.5"
            }
        ]
        update_response = client.update_recordset(update_rs, status=202)
        response = client.wait_until_recordset_change_status(update_response, "Failed")
        assert_that(response["systemMessage"], is_(f"This record set is out of sync with the DNS backend. Sync this zone before attempting to update this record set."))
    finally:
        try:
            delete_result = client.delete_recordset(zone["id"], create_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")
        except Exception:
            traceback.print_exc()
            pass


def test_update_high_value_domain_fails(shared_zone_test_context):
    """
    Test that updating a high value domain fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone_system = shared_zone_test_context.system_test_zone
    list_results_page_system = client.list_recordsets_by_zone(zone_system["id"], status=200)["recordSets"]
    record_system = [item for item in list_results_page_system if item["name"] == "high-value-domain"][0]
    record_system["ttl"] = record_system["ttl"] + 100

    errors_system = client.update_recordset(record_system, status=422)
    assert_that(errors_system, is_(f'Record name "high-value-domain.{zone_system["name"]}" is configured as a High Value Domain, so it cannot be modified.'))


def test_update_high_value_domain_fails_case_insensitive(shared_zone_test_context):
    """
    Test that updating a high value domain fails regardless of case
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone_system = shared_zone_test_context.system_test_zone
    list_results_page_system = client.list_recordsets_by_zone(zone_system["id"], status=200)["recordSets"]
    record_system = [item for item in list_results_page_system if item["name"] == "high-VALUE-domain-UPPER-CASE"][0]
    record_system["ttl"] = record_system["ttl"] + 100

    errors_system = client.update_recordset(record_system, status=422)
    assert_that(errors_system, is_(f'Record name "high-VALUE-domain-UPPER-CASE.{zone_system["name"]}" is configured as a High Value Domain, so it cannot be modified.'))


def test_update_high_value_domain_fails_ip4_ptr(shared_zone_test_context):
    """
    Test that updating a high value domain fails for ip4 ptr
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone_ip4 = shared_zone_test_context.classless_base_zone
    list_results_page_ip4 = client.list_recordsets_by_zone(zone_ip4["id"], status=200)["recordSets"]
    record_ip4 = [item for item in list_results_page_ip4 if item["name"] == "253"][0]
    record_ip4["ttl"] = record_ip4["ttl"] + 100

    errors_ip4 = client.update_recordset(record_ip4, status=422)
    assert_that(errors_ip4, is_(f'Record name "{shared_zone_test_context.ip4_classless_prefix}.253" is configured as a High Value Domain, so it cannot be modified.'))


def test_update_high_value_domain_fails_ip6_ptr(shared_zone_test_context):
    """
    Test that updating a high value domain fails for ip6 ptr
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone_ip6 = shared_zone_test_context.ip6_reverse_zone
    list_results_page_ip6 = client.list_recordsets_by_zone(zone_ip6["id"], status=200)["recordSets"]
    record_ip6 = [item for item in list_results_page_ip6 if item["name"] == "0.0.0.0.f.f.f.f.0.0.0.0.0.0.0.0.0.0.0.0"][0]
    record_ip6["ttl"] = record_ip6["ttl"] + 100

    errors_ip6 = client.update_recordset(record_ip6, status=422)
    assert_that(errors_ip6, is_(f'Record name "{shared_zone_test_context.ip6_prefix}:0000:0000:0000:ffff:0000" is configured as a High Value Domain, so it cannot be modified.'))


def test_update_from_user_in_record_owner_group_for_private_zone_fails(shared_zone_test_context):
    """
    Test that updating with a user in the record owner group fails when the zone is not set to shared
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    shared_record_group = shared_zone_test_context.shared_record_group
    shared_zone_client = shared_zone_test_context.shared_zone_vinyldns_client
    zone = shared_zone_test_context.ok_zone
    create_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_failure", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_record_group["id"]
        create_response = ok_client.create_recordset(record_json, status=202)
        create_rs = ok_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(create_rs["ownerGroupId"], is_(shared_record_group["id"]))

        update = create_rs
        update["ttl"] = update["ttl"] + 100
        error = shared_zone_client.update_recordset(update, status=403)
        assert_that(error, is_(f'User sharedZoneUser does not have access to update test-shared-failure.{shared_zone_test_context.ok_zone["name"]}'))
    finally:
        if create_rs:
            delete_result = ok_client.delete_recordset(zone["id"], create_rs["id"], status=202)
            ok_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_owner_group_from_user_in_record_owner_group_for_shared_zone_passes(shared_zone_test_context):
    """
    Test that updating with a user in the record owner group passes when the zone is set to shared
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    shared_record_group = shared_zone_test_context.shared_record_group
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone
    update_rs = None

    try:
        record_json = create_recordset(shared_zone, "test_shared_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_record_group["id"]
        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(shared_record_group["id"]))

        update["ttl"] = update["ttl"] + 100
        update_response = ok_client.update_recordset(update, status=202)
        update_rs = shared_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["ownerGroupId"], is_(shared_record_group["id"]))
    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(shared_zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_owner_group_from_admin_in_shared_zone_passes(shared_zone_test_context):
    """
    Test that updating with a zone admin user when the zone is set to shared passes
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    group = shared_zone_test_context.shared_record_group
    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update, is_not(has_key("ownerGroupId")))

        update["ownerGroupId"] = group["id"]
        update["ttl"] = update["ttl"] + 100
        update_response = shared_client.update_recordset(update, status=202)
        update_rs = shared_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["ownerGroupId"], is_(group["id"]))
    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_from_unassociated_user_in_shared_zone_passes_when_record_type_is_approved(shared_zone_test_context):
    """
    Test that updating with a user that does not have write access succeeds in a shared zone if the record type is approved
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_approved_record_type", "A", [{"address": "1.1.1.1"}])
        create_response = shared_client.create_recordset(record_json, status=202)
        create_rs = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(create_rs, is_not(has_key("ownerGroupId")))

        update = create_rs
        update["ttl"] = update["ttl"] + 100
        update_response = ok_client.update_recordset(update, status=202)
        update_rs = shared_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_from_unassociated_user_in_shared_zone_fails(shared_zone_test_context):
    """
    Test that updating with a user that does not have write access fails in a shared zone
    """
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    create_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_unapproved_record_type", "MX", [{"preference": 3, "exchange": "mx"}])
        create_response = shared_client.create_recordset(record_json, status=202)
        create_rs = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(create_rs, is_not(has_key("ownerGroupId")))

        update = create_rs
        update["ttl"] = update["ttl"] + 100
        error = dummy_client.update_recordset(update, status=403)
        assert_that(error, is_(f'User dummy does not have access to update test-shared-unapproved-record-type.{zone["name"]}'))
    finally:
        if create_rs:
            delete_result = shared_client.delete_recordset(zone["id"], create_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")

def test_update_from_super_user_in_shared_zone_passes_when_owner_group_is_only_update(shared_zone_test_context):
    """
    Test that updating with a superuser passes when the zone is set to shared and the owner group is the only change
    """
    super_user_client = shared_zone_test_context.super_user_client
    shared_record_group = shared_zone_test_context.shared_record_group
    dummy_group = shared_zone_test_context.dummy_group
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone
    update_rs = None

    try:
        record_json = create_recordset(shared_zone, "test_shared_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_record_group["id"]
        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(shared_record_group["id"]))

        update["ownerGroupId"] = dummy_group["id"]
        update_response = super_user_client.update_recordset(update, status=202)
        update_rs = shared_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["ownerGroupId"], is_(dummy_group["id"]))
    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(shared_zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")

def test_update_from_unassociated_user_in_shared_zone_fails_when_owner_group_is_only_update(shared_zone_test_context):
    """
    Test that updating with a user not in the record owner group fails when the zone is set to shared and
    the owner group is the only change
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    shared_record_group = shared_zone_test_context.shared_record_group
    dummy_group = shared_zone_test_context.dummy_group
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone
    create_rs = None

    try:
        record_json = create_recordset(shared_zone, "test_shared_fail", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_record_group["id"]
        create_response = shared_client.create_recordset(record_json, status=202)
        create_rs = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(create_rs["ownerGroupId"], is_(shared_record_group["id"]))

        update = create_rs
        update["ownerGroupId"] = dummy_group["id"]
        error = ok_client.update_recordset(update, status=422)
        assert_that(error, is_(f"User not in record owner group with id \"{dummy_group['id']}\""))
    finally:
        if create_rs:
            delete_result = shared_client.delete_recordset(shared_zone["id"], create_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")

def test_update_from_super_user_in_private_zone_succeeds_when_owner_group_is_only_update(shared_zone_test_context):
    """
    Test that updating with a superuser succeeds when the zone is set to private and the owner group is the only change
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    super_user_client = shared_zone_test_context.super_user_client
    ok_record_group = shared_zone_test_context.ok_group
    dummy_group = shared_zone_test_context.dummy_group
    ok_zone = shared_zone_test_context.ok_zone
    create_rs = None
    try:
        record_json = create_recordset(ok_zone, "test_private_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = ok_record_group["id"]
        create_response = ok_client.create_recordset(record_json, status=202)
        create_rs = ok_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(create_rs["ownerGroupId"], is_(ok_record_group["id"]))

        
        update = create_rs
        update["ownerGroupId"] = dummy_group["id"]
        update_response = super_user_client.update_recordset(update, status=202)
        updated_rs = super_user_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(updated_rs["ownerGroupId"], is_(dummy_group["id"]))
    finally:
        if create_rs:
            delete_result = ok_client.delete_recordset(ok_zone["id"], create_rs["id"], status=202)
            ok_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_from_super_user_in_shared_zone_succeeds_when_owner_group_is_not_the_only_update(shared_zone_test_context):
    """
    Test that updating with a superuser succeeds when the zone is shared and the change includes more than just the owner group
    """
    super_user_client = shared_zone_test_context.super_user_client
    shared_record_group = shared_zone_test_context.shared_record_group
    dummy_group = shared_zone_test_context.dummy_group
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone
    create_rs = None

    try:
        record_json = create_recordset(shared_zone, "test_shared_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_record_group["id"]
        create_response = shared_client.create_recordset(record_json, status=202)
        create_rs = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(create_rs["ownerGroupId"], is_(shared_record_group["id"]))

        update = create_rs.copy()
        update["ownerGroupId"] = dummy_group["id"]
        update["ttl"] = update["ttl"] + 100  
        update_response = super_user_client.update_recordset(update, status=202)
        updated_rs = shared_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(updated_rs["ownerGroupId"], is_(dummy_group["id"]))
        assert_that(updated_rs["ttl"], is_(update["ttl"]))
    finally:
        if create_rs:
            delete_result = shared_client.delete_recordset(shared_zone["id"], create_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_update_from_acl_for_shared_zone_passes(shared_zone_test_context):
    """
    Test that updating with a user that has an acl passes when the zone is set to shared
    """
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    acl_rule = generate_acl_rule("Write", userId="dummy")
    zone = shared_zone_test_context.shared_zone
    update_rs = None

    try:
        add_shared_zone_acl_rules(shared_zone_test_context, [acl_rule])

        record_json = create_recordset(zone, "test_shared_acl", "A", [{"address": "1.1.1.1"}])
        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update, is_not(has_key("ownerGroupId")))

        update["ttl"] = update["ttl"] + 100
        update_response = dummy_client.update_recordset(update, status=202)
        update_rs = dummy_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update, is_not(has_key("ownerGroupId")))
    finally:
        clear_shared_zone_acl_rules(shared_zone_test_context)
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")

def test_update_owner_group_transfer_auto_approved(shared_zone_test_context):
    """
    Test auto approve ownerShip transfer, for shared zones
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    ok_group = shared_zone_test_context.ok_group

    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success_fail", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_group["id"]
        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "AutoApproved",
                                       "requestedOwnerGroupId": ok_group["id"]}

        update["recordSetGroupChange"] = recordset_group_change_json
        error = shared_client.update_recordset(update, status=422)
        assert_that(error, is_(f"Unable to AutoApproved the Ownership transfer status for the record: None"))
    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_owner_group_transfer_request(shared_zone_test_context):
    """
    Test requesting ownerShip transfer, for shared zones
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    dummy_group = shared_zone_test_context.dummy_group

    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = dummy_group["id"]

        create_response = dummy_client.create_recordset(record_json, status=202)
        update = dummy_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(dummy_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": shared_group["id"]}
        recordset_group_change_pending_review_json = {"ownerShipTransferStatus": "PendingReview",
                                                      "requestedOwnerGroupId": shared_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json

        update_response = shared_client.update_recordset(update, status=202)
        update_rs = shared_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["recordSetGroupChange"], is_(recordset_group_change_pending_review_json))
        assert_that(update_rs["ownerGroupId"], is_(dummy_group["id"]))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_request_owner_group_transfer_manually_approved(shared_zone_test_context):
    """
    Test approving ownerShip transfer request, for shared zones
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    ok_group = shared_zone_test_context.ok_group

    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_group["id"]

        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": ok_group["id"]}
        recordset_group_change_pending_review_json = {"ownerShipTransferStatus": "PendingReview",
                                                      "requestedOwnerGroupId": ok_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json

        update_response = ok_client.update_recordset(update, status=202)
        update_rs = ok_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["recordSetGroupChange"], is_(recordset_group_change_pending_review_json))
        assert_that(update_rs["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "ManuallyApproved"}
        recordset_group_change_manually_approved_json = {"ownerShipTransferStatus": "ManuallyApproved",
                                                         "requestedOwnerGroupId": ok_group["id"]}
        update_rs["recordSetGroupChange"] = recordset_group_change_json
        update_rs_response = shared_client.update_recordset(update_rs, status=202)
        update_rs_ownership = shared_client.wait_until_recordset_change_status(update_rs_response, "Complete")[
            "recordSet"]
        assert_that(update_rs_ownership["recordSetGroupChange"], is_(recordset_group_change_manually_approved_json))
        assert_that(update_rs_ownership["ownerGroupId"], is_(ok_group["id"]))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_request_owner_group_transfer_manually_rejected(shared_zone_test_context):
    """
    Test rejecting ownerShip transfer request, for shared zones
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    ok_group = shared_zone_test_context.ok_group

    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_group["id"]

        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": ok_group["id"]}
        recordset_group_change_pending_review_json = {"ownerShipTransferStatus": "PendingReview",
                                                      "requestedOwnerGroupId": ok_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json

        update_response = ok_client.update_recordset(update, status=202)
        update_rs = ok_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["recordSetGroupChange"], is_(recordset_group_change_pending_review_json))
        assert_that(update_rs["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "ManuallyRejected"}
        recordset_group_change_manually_rejected_json = {"ownerShipTransferStatus": "ManuallyRejected",
                                                         "requestedOwnerGroupId": ok_group["id"]}
        update_rs["recordSetGroupChange"] = recordset_group_change_json
        update_rs_response = shared_client.update_recordset(update_rs, status=202)
        update_rs_ownership = shared_client.wait_until_recordset_change_status(update_rs_response, "Complete")[
            "recordSet"]
        assert_that(update_rs_ownership["recordSetGroupChange"], is_(recordset_group_change_manually_rejected_json))
        assert_that(update_rs_ownership["ownerGroupId"], is_(shared_group["id"]))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_request_owner_group_transfer_cancelled(shared_zone_test_context):
    """
    Test cancelling ownerShip transfer request
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    ok_group = shared_zone_test_context.ok_group

    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_group["id"]

        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": ok_group["id"]}
        recordset_group_change_pending_review_json = {"ownerShipTransferStatus": "PendingReview",
                                                      "requestedOwnerGroupId": ok_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json

        update_response = ok_client.update_recordset(update, status=202)
        update_rs = ok_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["recordSetGroupChange"], is_(recordset_group_change_pending_review_json))
        assert_that(update_rs["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Cancelled",
                                       "requestedOwnerGroupId": ok_group["id"]}
        recordset_group_change_cancelled_json = {"ownerShipTransferStatus": "Cancelled",
                                                 "requestedOwnerGroupId": ok_group["id"]}
        update_rs["recordSetGroupChange"] = recordset_group_change_json
        update_rs_response = ok_client.update_recordset(update_rs, status=202)
        update_rs_ownership = ok_client.wait_until_recordset_change_status(update_rs_response, "Complete")["recordSet"]
        assert_that(update_rs_ownership["recordSetGroupChange"], is_(recordset_group_change_cancelled_json))
        assert_that(update_rs_ownership["ownerGroupId"], is_(shared_group["id"]))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")

def test_update_request_owner_group_transfer_cancelled_not_a_member_fails(shared_zone_test_context):
    """
    Test cancelling ownerShip transfer request when user not a member of owner requested group fails
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    ok_group = shared_zone_test_context.ok_group
    dummy_group = shared_zone_test_context.dummy_group

    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_group["id"]

        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": ok_group["id"]}
        recordset_group_change_pending_review_json = {"ownerShipTransferStatus": "PendingReview",
                                                      "requestedOwnerGroupId": ok_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json

        update_response = ok_client.update_recordset(update, status=202)
        update_rs = ok_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["recordSetGroupChange"], is_(recordset_group_change_pending_review_json))
        assert_that(update_rs["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Cancelled",
                                       "requestedOwnerGroupId": dummy_group["id"]}

        update_rs["recordSetGroupChange"] = recordset_group_change_json
        error = shared_client.update_recordset(update_rs, status=422)
        assert_that(error, is_(f"Unauthorised to Cancel the ownership transfer"))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")

def test_update_request_owner_group_transfer_cancelled_superuser(shared_zone_test_context):
    """
    Test cancelling ownerShip transfer request when user is a superuser
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    super_user_client = shared_zone_test_context.super_user_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    ok_group = shared_zone_test_context.ok_group
    dummy_group = shared_zone_test_context.dummy_group

    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_group["id"]

        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": ok_group["id"]}
        recordset_group_change_pending_review_json = {"ownerShipTransferStatus": "PendingReview",
                                                      "requestedOwnerGroupId": ok_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json

        update_response = ok_client.update_recordset(update, status=202)
        update_rs = ok_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["recordSetGroupChange"], is_(recordset_group_change_pending_review_json))
        assert_that(update_rs["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Cancelled",
                                       "requestedOwnerGroupId": dummy_group["id"]}

        update_rs["recordSetGroupChange"] = recordset_group_change_json
        recordset_group_change_cancelled_json = {"ownerShipTransferStatus": "Cancelled",
                                                 "requestedOwnerGroupId": ok_group["id"]}

        update_rs_response = super_user_client.update_recordset(update_rs, status=202)
        update_rs_ownership = super_user_client.wait_until_recordset_change_status(update_rs_response, "Complete")["recordSet"]
        assert_that(update_rs_ownership["recordSetGroupChange"], is_(recordset_group_change_cancelled_json))
        assert_that(update_rs_ownership ["ownerGroupId"], is_(shared_group["id"]))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")

def test_update_owner_group_transfer_approval_to_group_a_superuser(shared_zone_test_context):
    """
    Test approving ownerShip transfer request when user is a superuser
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    super_user_client = shared_zone_test_context.super_user_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    dummy_group = shared_zone_test_context.dummy_group

    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = dummy_group["id"]

        create_response = dummy_client.create_recordset(record_json, status=202)
        update = dummy_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(dummy_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": shared_group["id"]}
        recordset_group_change_pending_review_json = {"ownerShipTransferStatus": "PendingReview",
                                                      "requestedOwnerGroupId": shared_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json

        update_response = dummy_client.update_recordset(update, status=202)
        update_rs = dummy_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["recordSetGroupChange"], is_(recordset_group_change_pending_review_json))
        assert_that(update_rs["ownerGroupId"], is_(dummy_group["id"]))

        recordset_group_change_approved_json = {"ownerShipTransferStatus": "ManuallyApproved",
                                                "requestedOwnerGroupId": shared_group["id"]}

        update_rs["recordSetGroupChange"] = recordset_group_change_approved_json
        update_rs_response = super_user_client.update_recordset(update_rs, status=202)
        update_rs_ownership = super_user_client.wait_until_recordset_change_status(update_rs_response, "Complete")["recordSet"]
        assert_that(update_rs_ownership["recordSetGroupChange"], is_(recordset_group_change_approved_json))
        assert_that(update_rs_ownership["ownerGroupId"], is_(shared_group["id"]))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")

def test_update_owner_group_transfer_approval_to_group_a_user_is_not_in_fails(shared_zone_test_context):
    """
    Test approving ownerShip transfer request, for user not a member of owner group
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    dummy_group = shared_zone_test_context.dummy_group

    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = dummy_group["id"]

        create_response = dummy_client.create_recordset(record_json, status=202)
        update = dummy_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(dummy_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": shared_group["id"]}
        recordset_group_change_pending_review_json = {"ownerShipTransferStatus": "PendingReview",
                                                      "requestedOwnerGroupId": shared_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json

        update_response = shared_client.update_recordset(update, status=202)
        update_rs = shared_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["recordSetGroupChange"], is_(recordset_group_change_pending_review_json))
        assert_that(update_rs["ownerGroupId"], is_(dummy_group["id"]))

        recordset_group_change_approved_json = {"ownerShipTransferStatus": "ManuallyApproved"}

        update_rs["recordSetGroupChange"] = recordset_group_change_approved_json
        error = shared_client.update_recordset(update_rs, status=422)
        assert_that(error, is_(f"User not in record owner group with id \"{dummy_group['id']}\""))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_owner_group_transfer_reject_to_group_a_user_is_not_in_fails(shared_zone_test_context):
    """
    Test rejecting ownerShip transfer request, for user not a member of owner group
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    dummy_group = shared_zone_test_context.dummy_group

    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = dummy_group["id"]

        create_response = dummy_client.create_recordset(record_json, status=202)
        update = dummy_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(dummy_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": shared_group["id"]}
        recordset_group_change_pending_review_json = {"ownerShipTransferStatus": "PendingReview",
                                                      "requestedOwnerGroupId": shared_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json

        update_response = shared_client.update_recordset(update, status=202)
        update_rs = shared_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["recordSetGroupChange"], is_(recordset_group_change_pending_review_json))
        assert_that(update_rs["ownerGroupId"], is_(dummy_group["id"]))

        recordset_group_change_approved_json = {"ownerShipTransferStatus": "ManuallyRejected"}
        update_rs["recordSetGroupChange"] = recordset_group_change_approved_json
        error = shared_client.update_recordset(update_rs, status=422)
        assert_that(error, is_(f"User not in record owner group with id \"{dummy_group['id']}\""))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_owner_group_transfer_auto_approved_to_group_a_user_is_not_in_fails(shared_zone_test_context):
    """
    Test approving ownerShip transfer request, for user not a member of owner group
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    dummy_group = shared_zone_test_context.dummy_group

    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = dummy_group["id"]

        create_response = dummy_client.create_recordset(record_json, status=202)
        update = dummy_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(dummy_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": shared_group["id"]}
        recordset_group_change_pending_review_json = {"ownerShipTransferStatus": "PendingReview",
                                                      "requestedOwnerGroupId": shared_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json

        update_response = shared_client.update_recordset(update, status=202)
        update_rs = shared_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["recordSetGroupChange"], is_(recordset_group_change_pending_review_json))
        assert_that(update_rs["ownerGroupId"], is_(dummy_group["id"]))

        recordset_group_change_approved_json = {"ownerShipTransferStatus": "AutoApproved"}

        update_rs["recordSetGroupChange"] = recordset_group_change_approved_json
        error = shared_client.update_recordset(update_rs, status=422)
        assert_that(error, is_(f"Record owner group with id \"{dummy_group['id']}\" not found"))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_owner_group_transfer_approved_when_request_cancelled_in_fails(shared_zone_test_context):
    """
    Test approving ownerShip transfer, for cancelled request
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    ok_group = shared_zone_test_context.ok_group

    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_group["id"]

        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": ok_group["id"]}
        recordset_group_change_pending_review_json = {"ownerShipTransferStatus": "PendingReview",
                                                      "requestedOwnerGroupId": ok_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json

        update_response = ok_client.update_recordset(update, status=202)
        update_rs = ok_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["recordSetGroupChange"], is_(recordset_group_change_pending_review_json))
        assert_that(update_rs["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Cancelled",
                                       "requestedOwnerGroupId": ok_group["id"]}
        recordset_group_change_cancelled_json = {"ownerShipTransferStatus": "Cancelled",
                                                 "requestedOwnerGroupId": ok_group["id"]}
        update_rs["recordSetGroupChange"] = recordset_group_change_json
        update_rs_response = ok_client.update_recordset(update_rs, status=202)
        update_rs_ownership = ok_client.wait_until_recordset_change_status(update_rs_response, "Complete")["recordSet"]
        assert_that(update_rs_ownership["recordSetGroupChange"], is_(recordset_group_change_cancelled_json))
        assert_that(update_rs_ownership["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "ManuallyApproved"}

        update_rs["recordSetGroupChange"] = recordset_group_change_json
        error = ok_client.update_recordset(update_rs, status=422)
        assert_that(error, is_("Cannot update RecordSet OwnerShip Status when request is cancelled."))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_owner_group_transfer_rejected_when_request_cancelled_in_fails(shared_zone_test_context):
    """
    Test rejecting ownerShip transfer, for cancelled request
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    ok_group = shared_zone_test_context.ok_group

    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_group["id"]

        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": ok_group["id"]}
        recordset_group_change_pending_review_json = {"ownerShipTransferStatus": "PendingReview",
                                                      "requestedOwnerGroupId": ok_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json

        update_response = ok_client.update_recordset(update, status=202)
        update_rs = ok_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["recordSetGroupChange"], is_(recordset_group_change_pending_review_json))
        assert_that(update_rs["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Cancelled",
                                       "requestedOwnerGroupId": ok_group["id"]}
        recordset_group_change_cancelled_json = {"ownerShipTransferStatus": "Cancelled",
                                                 "requestedOwnerGroupId": ok_group["id"]}
        update_rs["recordSetGroupChange"] = recordset_group_change_json
        update_rs_response = ok_client.update_recordset(update_rs, status=202)
        update_rs_ownership = ok_client.wait_until_recordset_change_status(update_rs_response, "Complete")["recordSet"]
        assert_that(update_rs_ownership["recordSetGroupChange"], is_(recordset_group_change_cancelled_json))
        assert_that(update_rs_ownership["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "ManuallyRejected"}

        update_rs["recordSetGroupChange"] = recordset_group_change_json
        error = ok_client.update_recordset(update_rs, status=422)
        assert_that(error, is_("Cannot update RecordSet OwnerShip Status when request is cancelled."))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_owner_group_transfer_auto_approved_when_request_cancelled_in_fails(shared_zone_test_context):
    """
    Test auto_approving ownerShip transfer, for cancelled request
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    ok_group = shared_zone_test_context.ok_group

    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_group["id"]

        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": ok_group["id"]}
        recordset_group_change_pending_review_json = {"ownerShipTransferStatus": "PendingReview",
                                                      "requestedOwnerGroupId": ok_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json

        update_response = ok_client.update_recordset(update, status=202)
        update_rs = ok_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs["recordSetGroupChange"], is_(recordset_group_change_pending_review_json))
        assert_that(update_rs["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Cancelled",
                                       "requestedOwnerGroupId": ok_group["id"]}
        recordset_group_change_cancelled_json = {"ownerShipTransferStatus": "Cancelled",
                                                 "requestedOwnerGroupId": ok_group["id"]}
        update_rs["recordSetGroupChange"] = recordset_group_change_json
        update_rs_response = ok_client.update_recordset(update_rs, status=202)
        update_rs_ownership = ok_client.wait_until_recordset_change_status(update_rs_response, "Complete")["recordSet"]
        assert_that(update_rs_ownership["recordSetGroupChange"], is_(recordset_group_change_cancelled_json))
        assert_that(update_rs_ownership["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "AutoApproved"}

        update_rs["recordSetGroupChange"] = recordset_group_change_json
        error = ok_client.update_recordset(update_rs, status=422)
        assert_that(error, is_("Cannot update RecordSet OwnerShip Status when request is cancelled."))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_owner_group_transfer_on_non_shared_zones_in_fails(shared_zone_test_context):
    """
    Test that requesting ownerShip transfer for non shared zones
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    shared_group = shared_zone_test_context.shared_record_group
    ok_zone = shared_zone_test_context.ok_zone

    update_rs = None

    try:
        # record_json = create_recordset(ok_zone, "test_update_success", "A", [{"address": "1.1.1.1"}], recordSetGroupChange={"ownerShipTransferStatus": None, "requestedOwnerGroupId": None})
        record_json = {
            "zoneId": ok_zone["id"],
            "name": "test_update_success",
            "type": "A",
            "ttl": 38400,
            "records": [
                {"address": "1.1.1.1"}
            ],
            "recordSetGroupChange": {"ownerShipTransferStatus": "None",
                                     "requestedOwnerGroupId": None}
        }

        create_response = ok_client.create_recordset(record_json, status=202)
        update = ok_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": shared_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json

        error = shared_client.update_recordset(update, status=422)
        assert_that(error, is_("Cannot update RecordSet OwnerShip Status when zone is not shared."))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_owner_group_transfer_and_ttl_on_user_not_in_owner_group_in_fails(shared_zone_test_context):
    """
    Test that updating record "i.e.ttl" with requesting ownerShip transfer, where user not in the member of the owner group
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    dummy_group = shared_zone_test_context.dummy_group
    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_admin_update_success", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_group["id"]

        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(shared_group["id"]))

        recordset_group_change_json = {"ownerShipTransferStatus": "Requested",
                                       "requestedOwnerGroupId": dummy_group["id"]}
        update["recordSetGroupChange"] = recordset_group_change_json
        update["ttl"] = update["ttl"] + 100

        error = dummy_client.update_recordset(update, status=422)
        assert_that(error, is_(f"Cannot update RecordSet's if user not a member of ownership group. User can only request for ownership transfer"))

    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_to_no_group_owner_passes(shared_zone_test_context):
    """
    Test that updating to have no record owner group passes
    """
    shared_record_group = shared_zone_test_context.shared_record_group
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    update_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_success_no_owner", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_record_group["id"]
        create_response = shared_client.create_recordset(record_json, status=202)
        update = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]
        assert_that(update["ownerGroupId"], is_(shared_record_group["id"]))

        update["ownerGroupId"] = None
        update_response = shared_client.update_recordset(update, status=202)
        update_rs = shared_client.wait_until_recordset_change_status(update_response, "Complete")["recordSet"]
        assert_that(update_rs, is_not(has_key("ownerGroupId")))
    finally:
        if update_rs:
            delete_result = shared_client.delete_recordset(zone["id"], update_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_to_invalid_record_owner_group_fails(shared_zone_test_context):
    """
    Test that updating to a record owner group that does not exist fails
    """
    shared_record_group = shared_zone_test_context.shared_record_group
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    create_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_fail_no_owner2", "A", [{"address": "1.1.1.1"}])
        record_json["ownerGroupId"] = shared_record_group["id"]
        create_response = shared_client.create_recordset(record_json, status=202)
        create_rs = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]

        update = create_rs
        update["ownerGroupId"] = "no-existo"
        error = shared_client.update_recordset(update, status=422)
        assert_that(error, is_('Record owner group with id "no-existo" not found'))
    finally:
        if create_rs:
            delete_result = shared_client.delete_recordset(zone["id"], create_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_to_group_a_user_is_not_in_fails(shared_zone_test_context):
    """
    Test that updating to a record owner group that the user is not in fails
    """
    dummy_group = shared_zone_test_context.dummy_group
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    create_rs = None

    try:
        record_json = create_recordset(zone, "test_shared_fail_no_owner1", "A", [{"address": "1.1.1.1"}])
        create_response = shared_client.create_recordset(record_json, status=202)
        create_rs = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]

        update = create_rs
        update["ownerGroupId"] = dummy_group["id"]
        error = shared_client.update_recordset(update, status=422)
        assert_that(error, is_(f"User not in record owner group with id \"{dummy_group['id']}\""))
    finally:
        if create_rs:
            delete_result = shared_client.delete_recordset(zone["id"], create_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_update_with_global_acl_rule_only_fails(shared_zone_test_context):
    """
    Test that updating an owned recordset fails if the user has a global acl rule but is not in the record owner group
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    zone = shared_zone_test_context.shared_zone
    create_rs = None

    try:
        record_json = create_recordset(zone, "test-global-acl", "A", [{"address": "1.1.1.1"}], 200, "shared-zone-group")
        create_response = shared_client.create_recordset(record_json, status=202)
        create_rs = shared_client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]

        update = create_rs
        update["ttl"] = 400
        error = dummy_client.update_recordset(update, status=403)
        assert_that(error, is_(f'User dummy does not have access to update test-global-acl.{zone["name"]}'))
    finally:
        if create_rs:
            delete_result = shared_client.delete_recordset(zone["id"], create_rs["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.serial
def test_update_ds_success(shared_zone_test_context):
    """
    Test that creating a valid DS record succeeds
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.ds_zone
    record_data_create = [
        {"keytag": 60485, "algorithm": 5, "digesttype": 1, "digest": "2BB183AF5F22588179A53B0A98631FAD1A292118"}
    ]
    record_data_update = [
        {"keytag": 60485, "algorithm": 5, "digesttype": 1, "digest": "2BB183AF5F22588179A53B0A98631FAD1A292118"},
        {"keytag": 60485, "algorithm": 5, "digesttype": 2, "digest": "D4B7D520E7BB5F0F67674A0CCEB1E3E0614B93C4F9E99B8383F6A1E4469DA50A"}
    ]
    record_json = create_recordset(zone, "dskey", "DS", record_data_create, ttl=3600)
    result_rs = None
    try:
        create_call = client.create_recordset(record_json, status=202)
        result_rs = client.wait_until_recordset_change_status(create_call, "Complete")["recordSet"]

        update_json = result_rs
        update_json["records"] = record_data_update
        update_call = client.update_recordset(update_json, status=202)
        result_rs = client.wait_until_recordset_change_status(update_call, "Complete")["recordSet"]

        # get result
        get_result = client.get_recordset(result_rs["zoneId"], result_rs["id"])["recordSet"]
        verify_recordset(get_result, update_json)
    finally:
        if result_rs:
            client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=(202, 404))
            client.wait_until_recordset_deleted(result_rs["zoneId"], result_rs["id"])


@pytest.mark.serial
def test_update_ds_data_failures(shared_zone_test_context):
    """
    Test that updating a DS record fails with bad hex, digest, algorithm
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.ds_zone
    record_data_create = [
        {"keytag": 60485, "algorithm": 5, "digesttype": 1, "digest": "2BB183AF5F22588179A53B0A98631FAD1A292118"}
    ]
    record_json = create_recordset(zone, "dskey", "DS", record_data_create, ttl=3600)
    result_rs = None
    try:
        create_call = client.create_recordset(record_json, status=202)
        result_rs = client.wait_until_recordset_change_status(create_call, "Complete")["recordSet"]

        update_json_bad_hex = result_rs
        record_data_update = [
            {"keytag": 60485, "algorithm": 5, "digesttype": 1, "digest": "BADWWW"}
        ]
        update_json_bad_hex["records"] = record_data_update
        client.update_recordset(update_json_bad_hex, status=400)

        update_json_bad_alg = result_rs
        record_data_update = [
            {"keytag": 60485, "algorithm": 0, "digesttype": 1, "digest": "2BB183AF5F22588179A53B0A98631FAD1A292118"}
        ]
        update_json_bad_alg["records"] = record_data_update
        client.update_recordset(update_json_bad_alg, status=400)

        update_json_bad_dig = result_rs
        record_data_update = [
            {"keytag": 60485, "algorithm": 5, "digesttype": 0, "digest": "2BB183AF5F22588179A53B0A98631FAD1A292118"}
        ]
        update_json_bad_dig["records"] = record_data_update
        client.update_recordset(update_json_bad_dig, status=400)
    finally:
        if result_rs:
            client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=(202, 404))
            client.wait_until_recordset_deleted(result_rs["zoneId"], result_rs["id"])


def test_update_fails_when_payload_and_route_zone_id_does_not_match(shared_zone_test_context):
    """
    Test that a 422 is returned if the zoneId in the body and route do not match
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.ok_zone

    created = None

    try:
        record_json = create_recordset(zone, "test_update_zone_id1", "A", [{"address": "1.1.1.1"}])
        create_response = client.create_recordset(record_json, status=202)
        created = client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]

        update = created
        update["ttl"] = update["ttl"] + 100
        update["zoneId"] = shared_zone_test_context.dummy_zone["id"]

        url = urljoin(client.index_url, "/zones/{0}/recordsets/{1}".format(zone["id"], update["id"]))
        response, error = client.make_request(url, "PUT", client.headers, json.dumps(update), not_found_ok=True, status=422)

        assert_that(error, is_("Cannot update RecordSet's zoneId attribute"))
    finally:
        if created:
            delete_result = client.delete_recordset(zone["id"], created["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


def test_update_fails_when_payload_and_actual_zone_id_do_not_match(shared_zone_test_context):
    """
    Test that a 422 is returned if the zoneId in the body and the recordSets actual zoneId do not match
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.ok_zone

    created = None
    try:
        record_json = create_recordset(zone, "test_update_zone_id", "A", [{"address": "1.1.1.1"}])
        create_response = client.create_recordset(record_json, status=202)
        created = client.wait_until_recordset_change_status(create_response, "Complete")["recordSet"]

        update = created
        update["zoneId"] = shared_zone_test_context.dummy_zone["id"]

        error = client.update_recordset(update, status=422)

        assert_that(error, is_("Cannot update RecordSet's zone ID."))
    finally:
        if created:
            delete_result = client.delete_recordset(zone["id"], created["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")
