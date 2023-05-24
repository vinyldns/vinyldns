import datetime
from typing import Optional, Union

import pytest

from utils import *


def does_not_contain(x):
    is_not(contains_exactly(x))


def validate_change_error_response_basics(input_json, change_type, input_name, record_type, ttl, record_data):
    assert_that(input_json["changeType"], is_(change_type))
    assert_that(input_json["inputName"], is_(input_name))
    assert_that(input_json["type"], is_(record_type))
    assert_that(record_type, is_in(["A", "AAAA", "CNAME", "PTR", "TXT", "MX"]))
    if change_type == "Add":
        assert_that(input_json["ttl"], is_(ttl))
        if record_type in ["A", "AAAA"]:
            assert_that(input_json["record"]["address"], is_(record_data))
        elif record_type == "CNAME":
            assert_that(input_json["record"]["cname"], is_(record_data))
        elif record_type == "PTR":
            assert_that(input_json["record"]["ptrdname"], is_(record_data))
        elif record_type == "TXT":
            assert_that(input_json["record"]["text"], is_(record_data))
        elif record_type == "MX":
            assert_that(input_json["record"]["preference"], is_(record_data["preference"]))
            assert_that(input_json["record"]["exchange"], is_(record_data["exchange"]))
    return


def assert_failed_change_in_error_response(input_json, change_type="Add", input_name="fqdn.", record_type="A", ttl=200,
                                           record_data: Optional[Union[str, dict]] = "1.1.1.1", error_messages=[]):
    validate_change_error_response_basics(input_json, change_type, input_name, record_type, ttl, record_data)
    assert_error(input_json, error_messages)
    return


def assert_successful_change_in_error_response(input_json, change_type="Add", input_name="fqdn.", record_type="A", ttl=200,
                                               record_data: Optional[Union[str, dict]] = "1.1.1.1"):
    validate_change_error_response_basics(input_json, change_type, input_name, record_type, ttl, record_data)
    assert_that("errors" in input_json, is_(False))
    return


def assert_change_success(changes_json, zone, index, record_name, input_name, record_data, ttl=200,
                          record_type="A", change_type="Add"):
    assert_that(changes_json[index]["zoneId"], is_(zone["id"]))
    assert_that(changes_json[index]["zoneName"], is_(zone["name"]))
    assert_that(changes_json[index]["recordName"], is_(record_name))
    assert_that(changes_json[index]["inputName"], is_(input_name))
    if change_type == "Add":
        assert_that(changes_json[index]["ttl"], is_(ttl))
    assert_that(changes_json[index]["type"], is_(record_type))
    assert_that(changes_json[index]["id"], is_not(none()))
    assert_that(changes_json[index]["changeType"], is_(change_type))
    assert_that(record_type, is_in(["A", "AAAA", "CNAME", "PTR", "TXT", "MX"]))
    if record_type in ["A", "AAAA"] and change_type == "Add":
        assert_that(changes_json[index]["record"]["address"], is_(record_data))
    elif record_type == "CNAME" and change_type == "Add":
        assert_that(changes_json[index]["record"]["cname"], is_(record_data))
    elif record_type == "PTR" and change_type == "Add":
        assert_that(changes_json[index]["record"]["ptrdname"], is_(record_data))
    elif record_type == "TXT" and change_type == "Add":
        assert_that(changes_json[index]["record"]["text"], is_(record_data))
    elif record_type == "MX" and change_type == "Add":
        assert_that(changes_json[index]["record"]["preference"], is_(record_data["preference"]))
        assert_that(changes_json[index]["record"]["exchange"], is_(record_data["exchange"]))
    return


def assert_error(input_json, error_messages):
    for error in error_messages:
        assert_that(input_json["errors"], has_item(error))
        assert_that(len(input_json["errors"]), is_(len(error_messages)))


@pytest.mark.serial
def test_create_batch_change_with_adds_success(shared_zone_test_context):
    """
    Test successfully creating a batch change with adds
    """
    client = shared_zone_test_context.ok_vinyldns_client
    parent_zone = shared_zone_test_context.parent_zone
    ok_zone = shared_zone_test_context.ok_zone
    classless_delegation_zone = shared_zone_test_context.classless_zone_delegation_zone
    classless_base_zone = shared_zone_test_context.classless_base_zone
    ip6_reverse_zone = shared_zone_test_context.ip6_16_nibble_zone

    partition_id = shared_zone_test_context.partition_id
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    parent_zone_name = shared_zone_test_context.parent_zone["name"]
    ip4_zone_name = shared_zone_test_context.classless_base_zone["name"]
    ip4_prefix = shared_zone_test_context.ip4_classless_prefix
    ip6_prefix = shared_zone_test_context.ip6_prefix

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json(f"{parent_zone_name}", address="4.5.6.7"),
            get_change_A_AAAA_json(f"{ok_zone_name}", record_type="AAAA", address=f"{ip6_prefix}::60"),
            get_change_A_AAAA_json(f"relative.{parent_zone_name}"),
            get_change_CNAME_json(f"CNAME.PARENT.COM{partition_id}", cname="nice.parent.com"),
            get_change_CNAME_json(f"_2cname.{parent_zone_name}", cname="nice.parent.com"),
            get_change_CNAME_json(f"4.{ip4_zone_name}", cname=f"4.4/30.{ip4_zone_name}"),
            get_change_PTR_json(f"{ip4_prefix}.193", ptrdname="www.vinyldns"),
            get_change_PTR_json(f"{ip4_prefix}.44"),
            get_change_PTR_json(f"{ip6_prefix}:1000::60", ptrdname="www.vinyldns"),
            get_change_TXT_json(f"txt.{ok_zone_name}"),
            get_change_TXT_json(f"{ok_zone_name}"),
            get_change_TXT_json(f"txt-unique-characters.{ok_zone_name}", text='a\\\\`=` =\\"Cat\\"\nattr=val'),
            get_change_TXT_json(f"txt.{ip4_zone_name}"),
            get_change_MX_json(f"mx.{ok_zone_name}", preference=0),
            get_change_MX_json(f"{ok_zone_name}", preference=1000, exchange="bar.foo.")
        ]
    }

    to_delete = []
    try:
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        record_set_list = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"]]
        to_delete = set(record_set_list)  # set here because multiple items in the batch combine to one RS

        # validate initial response
        assert_that(result["comments"], is_("this is optional"))
        assert_that(result["userName"], is_("ok"))
        assert_that(result["userId"], is_("ok"))
        assert_that(result["id"], is_not(none()))
        assert_that(completed_batch["status"], is_("Complete"))

        assert_change_success(result["changes"], zone=parent_zone, index=0,
                              record_name=f"{parent_zone_name}", input_name=f"{parent_zone_name}", record_data="4.5.6.7")
        assert_change_success(result["changes"], zone=ok_zone, index=1,
                              record_name=f"{ok_zone_name}", input_name=f"{ok_zone_name}", record_data=f"{ip6_prefix}::60", record_type="AAAA")
        assert_change_success(result["changes"], zone=parent_zone, index=2,
                              record_name="relative", input_name=f"relative.{parent_zone_name}", record_data="1.1.1.1")
        assert_change_success(result["changes"], zone=parent_zone, index=3,
                              record_name="CNAME", input_name=f"CNAME.PARENT.COM{partition_id}.", record_data="nice.parent.com.", record_type="CNAME")
        assert_change_success(result["changes"], zone=parent_zone, index=4,
                              record_name="_2cname", input_name=f"_2cname.{parent_zone_name}", record_data="nice.parent.com.", record_type="CNAME")
        assert_change_success(result["changes"], zone=classless_base_zone, index=5,
                              record_name="4", input_name=f"4.{ip4_zone_name}", record_data=f"4.4/30.{ip4_zone_name}", record_type="CNAME")
        assert_change_success(result["changes"], zone=classless_delegation_zone, index=6,
                              record_name="193", input_name=f"{ip4_prefix}.193", record_data="www.vinyldns.", record_type="PTR")
        assert_change_success(result["changes"], zone=classless_base_zone, index=7,
                              record_name="44", input_name=f"{ip4_prefix}.44", record_data="test.com.", record_type="PTR")
        assert_change_success(result["changes"], zone=ip6_reverse_zone, index=8,
                              record_name="0.6.0.0.0.0.0.0.0.0.0.0.0.0.0.0", input_name=f"{ip6_prefix}:1000::60", record_data="www.vinyldns.", record_type="PTR")
        assert_change_success(result["changes"], zone=ok_zone, index=9,
                              record_name="txt", input_name=f"txt.{ok_zone_name}", record_data="test", record_type="TXT")
        assert_change_success(result["changes"], zone=ok_zone, index=10,
                              record_name=f"{ok_zone_name}", input_name=f"{ok_zone_name}", record_data="test", record_type="TXT")
        assert_change_success(result["changes"], zone=ok_zone, index=11,
                              record_name="txt-unique-characters", input_name=f"txt-unique-characters.{ok_zone_name}", record_data='a\\\\`=` =\\"Cat\\"\nattr=val', record_type="TXT")
        assert_change_success(result["changes"], zone=classless_base_zone, index=12,
                              record_name="txt", input_name=f"txt.{ip4_zone_name}", record_data="test", record_type="TXT")
        assert_change_success(result["changes"], zone=ok_zone, index=13,
                              record_name="mx", input_name=f"mx.{ok_zone_name}", record_data={"preference": 0, "exchange": "foo.bar."}, record_type="MX")
        assert_change_success(result["changes"], zone=ok_zone, index=14,
                              record_name=f"{ok_zone_name}", input_name=f"{ok_zone_name}", record_data={"preference": 1000, "exchange": "bar.foo."}, record_type="MX")

        completed_status = [change["status"] == "Complete" for change in completed_batch["changes"]]
        assert_that(all(completed_status), is_(True))

        # get all the recordsets created by this batch, validate
        rs1 = client.get_recordset(record_set_list[0][0], record_set_list[0][1])["recordSet"]
        expected1 = {"name": parent_zone_name,
                     "zoneId": parent_zone["id"],
                     "type": "A",
                     "ttl": 200,
                     "records": [{"address": "4.5.6.7"}]}
        verify_recordset(rs1, expected1)

        rs3 = client.get_recordset(record_set_list[1][0], record_set_list[1][1])["recordSet"]
        expected3 = {"name": ok_zone_name,
                     "zoneId": ok_zone["id"],
                     "type": "AAAA",
                     "ttl": 200,
                     "records": [{"address": f"{ip6_prefix}::60"}]}
        verify_recordset(rs3, expected3)

        rs4 = client.get_recordset(record_set_list[2][0], record_set_list[2][1])["recordSet"]
        expected4 = {"name": "relative",
                     "zoneId": parent_zone["id"],
                     "type": "A",
                     "ttl": 200,
                     "records": [{"address": "1.1.1.1"}]}
        verify_recordset(rs4, expected4)

        rs5 = client.get_recordset(record_set_list[3][0], record_set_list[3][1])["recordSet"]
        expected5 = {"name": "CNAME",
                     "zoneId": parent_zone["id"],
                     "type": "CNAME",
                     "ttl": 200,
                     "records": [{"cname": "nice.parent.com."}]}
        verify_recordset(rs5, expected5)

        rs6 = client.get_recordset(record_set_list[4][0], record_set_list[4][1])["recordSet"]
        expected6 = {"name": "_2cname",
                     "zoneId": parent_zone["id"],
                     "type": "CNAME",
                     "ttl": 200,
                     "records": [{"cname": "nice.parent.com."}]}
        verify_recordset(rs6, expected6)

        rs7 = client.get_recordset(record_set_list[5][0], record_set_list[5][1])["recordSet"]
        expected7 = {"name": "4",
                     "zoneId": classless_base_zone["id"],
                     "type": "CNAME",
                     "ttl": 200,
                     "records": [{"cname": f"4.4/30.{ip4_zone_name}"}]}
        verify_recordset(rs7, expected7)

        rs8 = client.get_recordset(record_set_list[6][0], record_set_list[6][1])["recordSet"]
        expected8 = {"name": "193",
                     "zoneId": classless_delegation_zone["id"],
                     "type": "PTR",
                     "ttl": 200,
                     "records": [{"ptrdname": "www.vinyldns."}]}
        verify_recordset(rs8, expected8)

        rs9 = client.get_recordset(record_set_list[7][0], record_set_list[7][1])["recordSet"]
        expected9 = {"name": "44",
                     "zoneId": classless_base_zone["id"],
                     "type": "PTR",
                     "ttl": 200,
                     "records": [{"ptrdname": "test.com."}]}
        verify_recordset(rs9, expected9)

        rs10 = client.get_recordset(record_set_list[8][0], record_set_list[8][1])["recordSet"]
        expected10 = {"name": "0.6.0.0.0.0.0.0.0.0.0.0.0.0.0.0",
                      "zoneId": ip6_reverse_zone["id"],
                      "type": "PTR",
                      "ttl": 200,
                      "records": [{"ptrdname": "www.vinyldns."}]}
        verify_recordset(rs10, expected10)

        rs11 = client.get_recordset(record_set_list[9][0], record_set_list[9][1])["recordSet"]
        expected11 = {"name": "txt",
                      "zoneId": ok_zone["id"],
                      "type": "TXT",
                      "ttl": 200,
                      "records": [{"text": "test"}]}
        verify_recordset(rs11, expected11)

        rs12 = client.get_recordset(record_set_list[10][0], record_set_list[10][1])["recordSet"]
        expected12 = {"name": f"{ok_zone_name}",
                      "zoneId": ok_zone["id"],
                      "type": "TXT",
                      "ttl": 200,
                      "records": [{"text": "test"}]}
        verify_recordset(rs12, expected12)

        rs13 = client.get_recordset(record_set_list[11][0], record_set_list[11][1])["recordSet"]
        expected13 = {"name": "txt-unique-characters",
                      "zoneId": ok_zone["id"],
                      "type": "TXT",
                      "ttl": 200,
                      "records": [{"text": 'a\\\\`=` =\\"Cat\\"\nattr=val'}]}
        verify_recordset(rs13, expected13)

        rs14 = client.get_recordset(record_set_list[12][0], record_set_list[12][1])["recordSet"]
        expected14 = {"name": "txt",
                      "zoneId": classless_base_zone["id"],
                      "type": "TXT",
                      "ttl": 200,
                      "records": [{"text": "test"}]}
        verify_recordset(rs14, expected14)

        rs15 = client.get_recordset(record_set_list[13][0], record_set_list[13][1])["recordSet"]
        expected15 = {"name": "mx",
                      "zoneId": ok_zone["id"],
                      "type": "MX",
                      "ttl": 200,
                      "records": [{"preference": 0, "exchange": "foo.bar."}]}
        verify_recordset(rs15, expected15)

        rs16 = client.get_recordset(record_set_list[14][0], record_set_list[14][1])["recordSet"]
        expected16 = {"name": f"{ok_zone_name}",
                      "zoneId": ok_zone["id"],
                      "type": "MX",
                      "ttl": 200,
                      "records": [{"preference": 1000, "exchange": "bar.foo."}]}
        verify_recordset(rs16, expected16)
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)


@pytest.mark.manual_batch_review
def test_create_batch_change_with_scheduled_time_and_owner_group_succeeds(shared_zone_test_context):
    """
    Test successfully creating a batch change with scheduled time and owner group set
    """
    client = shared_zone_test_context.ok_vinyldns_client
    dt = (datetime.datetime.now() + datetime.timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json(generate_record_name(ok_zone_name), address="4.5.6.7"),
        ],
        "scheduledTime": dt,
        "ownerGroupId": shared_zone_test_context.ok_group["id"]
    }
    result = None
    try:
        result = client.create_batch_change(batch_change_input, status=202)
        assert_that(result["status"], "Scheduled")
        assert_that(result["scheduledTime"], dt)
    finally:
        if result:
            rejecter = shared_zone_test_context.support_user_client
            rejecter.reject_batch_change(result["id"], status=200)


@pytest.mark.manual_batch_review
def test_create_scheduled_batch_change_with_zone_discovery_error_without_owner_group_fails(shared_zone_test_context):
    """
    Test creating a scheduled batch without owner group ID fails if there is a zone discovery error
    """
    client = shared_zone_test_context.ok_vinyldns_client
    dt = (datetime.datetime.now() + datetime.timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ")

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("zone-discovery.failure.", address="4.5.6.7"),
        ],
        "scheduledTime": dt
    }

    errors = client.create_batch_change(batch_change_input, status=400)

    assert_that(errors, is_("Batch change requires owner group for manual review."))


@pytest.mark.manual_batch_review
def test_create_scheduled_batch_change_with_scheduled_time_in_the_past_fails(shared_zone_test_context):
    """
    Test creating a scheduled batch with a scheduled time in the past
    """
    client = shared_zone_test_context.ok_vinyldns_client
    yesterday = (datetime.datetime.now() - datetime.timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json(generate_record_name(ok_zone_name), address="4.5.6.7"),
        ],
        "ownerGroupId": shared_zone_test_context.ok_group["id"],
        "scheduledTime": yesterday
    }

    errors = client.create_batch_change(batch_change_input, status=400)

    assert_that(errors, is_("Scheduled time must be in the future."))


@pytest.mark.manual_batch_review
def test_create_batch_change_with_soft_failures_scheduled_time_and_allow_manual_review_disabled_fails(
        shared_zone_test_context):
    """
    Test creating a batch change with soft errors, scheduled time, and allowManualReview disabled results in hard failure
    """
    client = shared_zone_test_context.ok_vinyldns_client
    dt = (datetime.datetime.now() + datetime.timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ")

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("non.existent", address="4.5.6.7"),
        ],
        "scheduledTime": dt,
        "ownerGroupId": shared_zone_test_context.ok_group["id"]
    }

    response = client.create_batch_change(batch_change_input, False, status=400)
    assert_failed_change_in_error_response(response[0], input_name="non.existent.", record_type="A",
                                           record_data="4.5.6.7",
                                           error_messages=["Zone Discovery Failed: zone for \"non.existent.\" does not exist in VinylDNS. "
                                                           "If zone exists, then it must be connected to in VinylDNS."])


def test_create_batch_change_without_scheduled_time_succeeds(shared_zone_test_context):
    """
    Test successfully creating a batch change without scheduled time set
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json(generate_record_name(ok_zone_name), address="4.5.6.7"),
        ]
    }

    to_delete = []
    try:
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        record_set_list = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"]]
        to_delete = set(record_set_list)
        assert_that(completed_batch, is_not(has_key("scheduledTime")))
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)


@pytest.mark.manual_batch_review
def test_create_batch_change_with_zone_discovery_error_without_owner_group_fails(shared_zone_test_context):
    """
    Test creating a batch change with zone discovery error fails if no owner group ID is provided
    """
    client = shared_zone_test_context.ok_vinyldns_client

    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json("some.non-existent.zone.")
        ]
    }

    errors = client.create_batch_change(batch_change_input, status=400)

    assert_that(errors, is_("Batch change requires owner group for manual review."))


@pytest.mark.serial
def test_create_batch_change_with_updates_deletes_success(shared_zone_test_context):
    """
    Test successfully creating a batch change with updates and deletes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    dummy_zone = shared_zone_test_context.dummy_zone
    ok_zone = shared_zone_test_context.ok_zone
    classless_zone_delegation_zone = shared_zone_test_context.classless_zone_delegation_zone

    ok_zone_acl = generate_acl_rule("Delete", groupId=shared_zone_test_context.dummy_group["id"], recordMask=".*", recordTypes=["CNAME"])
    classless_zone_delegation_zone_acl = generate_acl_rule("Write", groupId=shared_zone_test_context.dummy_group["id"], recordTypes=["PTR"])

    rs_delete_dummy = create_recordset(dummy_zone, "delete", "AAAA", [{"address": "1:2:3:4:5:6:7:8"}])
    rs_update_dummy = create_recordset(dummy_zone, "update", "A", [{"address": "1.2.3.4"}])
    rs_delete_ok = create_recordset(ok_zone, "delete", "CNAME", [{"cname": "delete.cname."}])
    rs_update_classless = create_recordset(classless_zone_delegation_zone, "193", "PTR", [{"ptrdname": "will.change."}])
    txt_delete_dummy = create_recordset(dummy_zone, "delete-txt", "TXT", [{"text": "test"}])
    mx_delete_dummy = create_recordset(dummy_zone, "delete-mx", "MX", [{"preference": 1, "exchange": "foo.bar."}])
    mx_update_dummy = create_recordset(dummy_zone, "update-mx", "MX", [{"preference": 1, "exchange": "foo.bar."}])

    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    dummy_zone_name = shared_zone_test_context.dummy_zone["name"]
    ip4_prefix = shared_zone_test_context.ip4_classless_prefix
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json(f"delete.{dummy_zone_name}", record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(f"update.{dummy_zone_name}", ttl=300, address="1.2.3.4"),
            get_change_A_AAAA_json(f"Update.{dummy_zone_name}", change_type="DeleteRecordSet"),
            get_change_CNAME_json(f"delete.{ok_zone_name}", change_type="DeleteRecordSet"),
            get_change_PTR_json(f"{ip4_prefix}.193", ttl=300, ptrdname="has.changed."),
            get_change_PTR_json(f"{ip4_prefix}.193", change_type="DeleteRecordSet"),
            get_change_TXT_json(f"delete-txt.{dummy_zone_name}", change_type="DeleteRecordSet"),
            get_change_MX_json(f"delete-mx.{dummy_zone_name}", change_type="DeleteRecordSet"),
            get_change_MX_json(f"update-mx.{dummy_zone_name}", change_type="DeleteRecordSet"),
            get_change_MX_json(f"update-mx.{dummy_zone_name}", preference=1000)
        ]
    }

    to_create = [rs_delete_dummy, rs_update_dummy, rs_delete_ok, rs_update_classless, txt_delete_dummy, mx_delete_dummy, mx_update_dummy]
    to_delete = []

    try:
        for rs in to_create:
            if rs["zoneId"] == dummy_zone["id"]:
                create_client = dummy_client
            else:
                create_client = ok_client

            create_rs = create_client.create_recordset(rs, status=202)
            create_client.wait_until_recordset_change_status(create_rs, "Complete")

        # Configure ACL rules
        add_ok_acl_rules(shared_zone_test_context, [ok_zone_acl])
        add_classless_acl_rules(shared_zone_test_context, [classless_zone_delegation_zone_acl])

        result = dummy_client.create_batch_change(batch_change_input, status=202)
        completed_batch = dummy_client.wait_until_batch_change_completed(result)

        record_set_list = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"]]

        to_delete = set(record_set_list)  # set here because multiple items in the batch combine to one RS

        ## validate initial response
        assert_that(result["comments"], is_("this is optional"))
        assert_that(result["userName"], is_("dummy"))
        assert_that(result["userId"], is_("dummy"))
        assert_that(result["id"], is_not(none()))
        assert_that(completed_batch["status"], is_("Complete"))

        assert_change_success(result["changes"], zone=dummy_zone, index=0, record_name="delete",
                              input_name=f"delete.{dummy_zone_name}", record_data=None, record_type="AAAA", change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=dummy_zone, index=1, record_name="update", ttl=300,
                              input_name=f"update.{dummy_zone_name}", record_data="1.2.3.4")
        assert_change_success(result["changes"], zone=dummy_zone, index=2, record_name="Update",
                              input_name=f"Update.{dummy_zone_name}", record_data=None, change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=3, record_name="delete",
                              input_name=f"delete.{ok_zone_name}", record_data=None, record_type="CNAME", change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=classless_zone_delegation_zone, index=4, record_name="193", ttl=300,
                              input_name=f"{ip4_prefix}.193", record_data="has.changed.", record_type="PTR")
        assert_change_success(result["changes"], zone=classless_zone_delegation_zone, index=5, record_name="193",
                              input_name=f"{ip4_prefix}.193", record_data=None, record_type="PTR", change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=dummy_zone, index=6, record_name="delete-txt",
                              input_name=f"delete-txt.{dummy_zone_name}", record_data=None, record_type="TXT", change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=dummy_zone, index=7, record_name="delete-mx",
                              input_name=f"delete-mx.{dummy_zone_name}", record_data=None, record_type="MX", change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=dummy_zone, index=8, record_name="update-mx",
                              input_name=f"update-mx.{dummy_zone_name}", record_data=None, record_type="MX", change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=dummy_zone, index=9, record_name="update-mx",
                              input_name=f"update-mx.{dummy_zone_name}", record_data={"preference": 1000, "exchange": "foo.bar."}, record_type="MX")

        rs1 = dummy_client.get_recordset(record_set_list[0][0], record_set_list[0][1], status=404)
        assert_that(rs1, is_("RecordSet with id " + record_set_list[0][1] + " does not exist."))

        rs2 = dummy_client.get_recordset(record_set_list[1][0], record_set_list[1][1])["recordSet"]
        expected2 = {"name": "update",
                     "zoneId": dummy_zone["id"],
                     "type": "A",
                     "ttl": 300,
                     "records": [{"address": "1.2.3.4"}]}
        verify_recordset(rs2, expected2)

        # since this is an update, record_set_list[1] and record_set_list[2] are the same record
        rs3 = dummy_client.get_recordset(record_set_list[2][0], record_set_list[2][1])["recordSet"]
        verify_recordset(rs3, expected2)

        rs4 = dummy_client.get_recordset(record_set_list[3][0], record_set_list[3][1], status=404)
        assert_that(rs4, is_("RecordSet with id " + record_set_list[3][1] + " does not exist."))

        rs5 = dummy_client.get_recordset(record_set_list[4][0], record_set_list[4][1])["recordSet"]
        expected5 = {"name": "193",
                     "zoneId": classless_zone_delegation_zone["id"],
                     "type": "PTR",
                     "ttl": 300,
                     "records": [{"ptrdname": "has.changed."}]}
        verify_recordset(rs5, expected5)

        # since this is an update, record_set_list[5] and record_set_list[4] are the same record
        rs6 = dummy_client.get_recordset(record_set_list[5][0], record_set_list[5][1])["recordSet"]
        verify_recordset(rs6, expected5)

        rs7 = dummy_client.get_recordset(record_set_list[6][0], record_set_list[6][1], status=404)
        assert_that(rs7, is_("RecordSet with id " + record_set_list[6][1] + " does not exist."))

        rs8 = dummy_client.get_recordset(record_set_list[7][0], record_set_list[7][1], status=404)
        assert_that(rs8, is_("RecordSet with id " + record_set_list[7][1] + " does not exist."))

        rs9 = dummy_client.get_recordset(record_set_list[8][0], record_set_list[8][1])["recordSet"]
        expected9 = {"name": "update-mx",
                     "zoneId": dummy_zone["id"],
                     "type": "MX",
                     "ttl": 200,
                     "records": [{"preference": 1000, "exchange": "foo.bar."}]}
        verify_recordset(rs9, expected9)
    finally:
        # Clean up updates
        dummy_deletes = [rs for rs in to_delete if rs[0] == dummy_zone["id"]]
        ok_deletes = [rs for rs in to_delete if rs[0] != dummy_zone["id"]]
        clear_zoneid_rsid_tuple_list(dummy_deletes, dummy_client)
        clear_zoneid_rsid_tuple_list(ok_deletes, ok_client)

        # Clean up ACL rules
        clear_ok_acl_rules(shared_zone_test_context)
        clear_classless_acl_rules(shared_zone_test_context)


def test_create_batch_change_without_comments_succeeds(shared_zone_test_context):
    """
    Test successfully creating a batch change without comments
    Test successfully creating a batch using inputName without a trailing dot, and that the
    returned inputName is dotted
    """
    client = shared_zone_test_context.ok_vinyldns_client
    parent_zone = shared_zone_test_context.parent_zone
    test_record_name = generate_record_name()
    test_record_fqdn = "{0}.{1}".format(test_record_name, parent_zone["name"])
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(test_record_fqdn, address="4.5.6.7"),
        ]
    }
    to_delete = []

    try:
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        to_delete = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"]]

        assert_change_success(result["changes"], zone=parent_zone, index=0, record_name=test_record_name, input_name=test_record_fqdn, record_data="4.5.6.7")
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)


def test_create_batch_change_with_owner_group_id_succeeds(shared_zone_test_context):
    """
    Test successfully creating a batch change with owner group ID specified
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    test_record_name = generate_record_name()
    test_record_fqdn = "{0}.{1}".format(test_record_name, ok_zone["name"])
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(test_record_fqdn, address="4.3.2.1")
        ],
        "ownerGroupId": shared_zone_test_context.ok_group["id"]
    }
    to_delete = []

    try:
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        to_delete = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"]]

        assert_change_success(result["changes"], zone=ok_zone, index=0, record_name=test_record_name, input_name=test_record_fqdn, record_data="4.3.2.1")
        assert_that(completed_batch["ownerGroupId"], is_(shared_zone_test_context.ok_group["id"]))
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)


def test_create_batch_change_without_owner_group_id_succeeds(shared_zone_test_context):
    """
    Test successfully creating a batch change without owner group ID specified
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    test_record_name = generate_record_name()
    test_record_fqdn = "{0}.{1}".format(test_record_name, ok_zone["name"])
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(test_record_fqdn, address="4.3.2.1")
        ]
    }
    to_delete = []

    try:
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        to_delete = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"]]

        assert_change_success(result["changes"], zone=ok_zone, index=0, record_name=test_record_name, input_name=test_record_fqdn, record_data="4.3.2.1")
        assert_that(completed_batch, is_not(has_key("ownerGroupId")))
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)


@pytest.mark.skip_production
def test_create_batch_change_with_missing_ttl_returns_default_or_existing(shared_zone_test_context):
    """
    Test creating a batch change without a ttl returns the default or existing value
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    update_name = generate_record_name()
    update_fqdn = "{0}.{1}".format(update_name, ok_zone["name"])
    rs_update = create_recordset(ok_zone, update_name, "CNAME", [{"cname": "old-ttl.cname."}], ttl=300)
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "DeleteRecordSet",
                "inputName": update_fqdn,
                "type": "CNAME",
            },
            {
                "changeType": "Add",
                "inputName": update_fqdn,
                "type": "CNAME",
                "record": {
                    "cname": "updated-ttl.cname."
                }
            },
            {
                "changeType": "Add",
                "inputName": generate_record_name(ok_zone["name"]),
                "type": "CNAME",
                "record": {
                    "cname": "new-ttl-record.cname."
                }
            }
        ]
    }
    to_delete = []

    try:
        create_rs = client.create_recordset(rs_update, status=202)
        client.wait_until_recordset_change_status(create_rs, "Complete")
        to_delete = [(create_rs["zone"]["id"], create_rs["recordSet"]["id"])]

        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        record_set_list = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"]]
        to_delete = set(record_set_list)

        updated_record = client.get_recordset(record_set_list[0][0], record_set_list[0][1])["recordSet"]
        assert_that(updated_record["ttl"], is_(300))

        new_record = client.get_recordset(record_set_list[2][0], record_set_list[2][1])["recordSet"]
        assert_that(new_record["ttl"], is_(7200))
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)


def test_create_batch_change_partial_failure(shared_zone_test_context):
    """
    Test batch change status with partial failures
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json(f"will-succeed.{ok_zone['name']}", address="4.5.6.7"),
            get_change_A_AAAA_json(f"direct-to-backend.{ok_zone['name']}", address="4.5.6.7")  # this record will fail in processing
        ]
    }

    to_delete = []

    try:
        dns_add(shared_zone_test_context.ok_zone, "direct-to-backend", 200, "A", "1.2.3.4")
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        record_set_list = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"] if
                           change["status"] == "Complete"]
        to_delete = set(record_set_list)  # set here because multiple items in the batch combine to one RS

        assert_that(completed_batch["status"], is_("PartialFailure"))
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)
        dns_delete(shared_zone_test_context.ok_zone, "direct-to-backend", "A")


def test_create_batch_change_failed(shared_zone_test_context):
    """
    Test batch change status with all failures
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json(f"backend-foo.{ok_zone_name}", address="4.5.6.7"),
            get_change_A_AAAA_json(f"backend-already-exists.{ok_zone_name}", address="4.5.6.7")
        ]
    }

    try:
        # these records already exist in the backend, but are not synced in zone
        dns_add(shared_zone_test_context.ok_zone, "backend-foo", 200, "A", "1.2.3.4")
        dns_add(shared_zone_test_context.ok_zone, "backend-already-exists", 200, "A", "1.2.3.4")
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)

        assert_that(completed_batch["status"], is_("Failed"))
    finally:
        dns_delete(shared_zone_test_context.ok_zone, "backend-foo", "A")
        dns_delete(shared_zone_test_context.ok_zone, "backend-already-exists", "A")


def test_empty_batch_fails(shared_zone_test_context):
    """
    Test creating batch without any changes fails with
    """
    batch_change_input = {
        "comments": "this should fail processing",
        "changes": []
    }

    errors = shared_zone_test_context.ok_vinyldns_client.create_batch_change(batch_change_input, status=400)["errors"]
    assert_that(errors[0], contains_string(
        "Batch change contained no changes. Batch change must have at least one change, up to a maximum of"))


def test_create_batch_change_without_changes_fails(shared_zone_test_context):
    """
    Test creating a batch change with missing changes fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional"
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing BatchChangeInput.changes"])


def test_create_batch_change_with_missing_change_type_fails(shared_zone_test_context):
    """
    Test creating a batch change with missing change type fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "inputName": "thing.thing.com.",
                "type": "A",
                "ttl": 200,
                "record": {
                    "address": "4.5.6.7"
                }
            }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing BatchChangeInput.changes.changeType"])


def test_create_batch_change_with_invalid_change_type_fails(shared_zone_test_context):
    """
    Test creating a batch change with invalid change type fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "InvalidChangeType",
                "data": {
                    "inputName": "thing.thing.com.",
                    "type": "A",
                    "ttl": 200,
                    "record": {
                        "address": "4.5.6.7"
                    }
                }
            }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Invalid ChangeInputType"])


def test_create_batch_change_with_missing_input_name_fails(shared_zone_test_context):
    """
    Test creating a batch change without an inputName fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "Add",
                "type": "A",
                "ttl": 200,
                "record": {
                    "address": "4.5.6.7"
                }
            }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing BatchChangeInput.changes.inputName"])


def test_create_batch_change_with_unsupported_record_type_fails(shared_zone_test_context):
    """
    Test creating a batch change with unsupported record type fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "Add",
                "inputName": "thing.thing.com.",
                "type": "UNKNOWN",
                "ttl": 200,
                "record": {
                    "address": "4.5.6.7"
                }
            }
        ]
    }

    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors,
                 error_messages=["Unsupported type UNKNOWN, valid types include: A, AAAA, CNAME, PTR, TXT, and MX"])


def test_create_batch_change_with_high_value_domain_fails(shared_zone_test_context):
    """
    Test creating a batch change with a high value domain as an inputName fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    ip4_prefix = shared_zone_test_context.ip4_classless_prefix
    ip6_prefix = shared_zone_test_context.ip6_prefix

    batch_change_input = {

        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json(f"high-value-domain-add.{ok_zone_name}"),
            get_change_A_AAAA_json(f"high-value-domain-update.{ok_zone_name}", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(f"high-value-domain-update.{ok_zone_name}"),
            get_change_A_AAAA_json(f"high-value-domain-delete.{ok_zone_name}", change_type="DeleteRecordSet"),
            get_change_PTR_json(f"{ip4_prefix}.252"),
            get_change_PTR_json(f"{ip4_prefix}.253", change_type="DeleteRecordSet"),  # 253 exists already
            get_change_PTR_json(f"{ip4_prefix}.253"),
            get_change_PTR_json(f"{ip4_prefix}.253", change_type="DeleteRecordSet"),
            get_change_PTR_json(f"{ip6_prefix}:0:0:0:0:ffff"),
            get_change_PTR_json(f"{ip6_prefix}:0:0:0:ffff:0", change_type="DeleteRecordSet"),  # ffff:0 exists already
            get_change_PTR_json(f"{ip6_prefix}:0:0:0:ffff:0"),
            get_change_PTR_json(f"{ip6_prefix}:0:0:0:ffff:0", change_type="DeleteRecordSet"),

            get_change_A_AAAA_json(f"i-can-be-touched.{ok_zone_name}")
        ]
    }

    response = client.create_batch_change(batch_change_input, status=400)

    assert_error(response[0], error_messages=[f'Record name "high-value-domain-add.{ok_zone_name}" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[1], error_messages=[f'Record name "high-value-domain-update.{ok_zone_name}" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[2], error_messages=[f'Record name "high-value-domain-update.{ok_zone_name}" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[3], error_messages=[f'Record name "high-value-domain-delete.{ok_zone_name}" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[4], error_messages=[f'Record name "{ip4_prefix}.252" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[5], error_messages=[f'Record name "{ip4_prefix}.253" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[6], error_messages=[f'Record name "{ip4_prefix}.253" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[7], error_messages=[f'Record name "{ip4_prefix}.253" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[8], error_messages=[f'Record name "{ip6_prefix}:0:0:0:0:ffff" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[9], error_messages=[f'Record name "{ip6_prefix}:0:0:0:ffff:0" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[10], error_messages=[f'Record name "{ip6_prefix}:0:0:0:ffff:0" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[11], error_messages=[f'Record name "{ip6_prefix}:0:0:0:ffff:0" is configured as a High Value Domain, so it cannot be modified.'])

    assert_that(response[12], is_not(has_key("errors")))


@pytest.mark.manual_batch_review
def test_create_batch_change_with_domains_requiring_review_succeeds(shared_zone_test_context):
    """
    Test creating a batch change with an input name requiring review is accepted
    """
    rejecter = shared_zone_test_context.support_user_client
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    ip4_prefix = shared_zone_test_context.ip4_classless_prefix
    ip6_prefix = shared_zone_test_context.ip6_prefix

    batch_change_input = {
        "ownerGroupId": shared_zone_test_context.ok_group["id"],
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json(f"needs-review-add.{ok_zone_name}"),
            get_change_A_AAAA_json(f"needs-review-update.{ok_zone_name}", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(f"needs-review-update.{ok_zone_name}"),
            get_change_A_AAAA_json(f"needs-review-delete.{ok_zone_name}", change_type="DeleteRecordSet"),
            get_change_PTR_json(f"{ip4_prefix}.254"),
            get_change_PTR_json(f"{ip4_prefix}.255", change_type="DeleteRecordSet"),  # 255 exists already
            get_change_PTR_json(f"{ip4_prefix}.255"),
            get_change_PTR_json(f"{ip4_prefix}.255", change_type="DeleteRecordSet"),
            get_change_PTR_json(f"{ip6_prefix}:0:0:0:ffff:1"),
            get_change_PTR_json(f"{ip6_prefix}:0:0:0:ffff:2", change_type="DeleteRecordSet"),  # ffff:2 exists already
            get_change_PTR_json(f"{ip6_prefix}:0:0:0:ffff:2"),
            get_change_PTR_json(f"{ip6_prefix}:0:0:0:ffff:2", change_type="DeleteRecordSet"),

            get_change_A_AAAA_json(f"i-can-be-touched.{ok_zone_name}")
        ]
    }
    response = None

    try:
        response = client.create_batch_change(batch_change_input, status=202)
        get_batch = client.get_batch_change(response["id"])
        assert_that(get_batch["status"], is_("PendingReview"))
        assert_that(get_batch["approvalStatus"], is_("PendingReview"))
        for i in range(1, 11):
            assert_that(get_batch["changes"][i]["status"], is_("NeedsReview"))
            assert_that(get_batch["changes"][i]["validationErrors"][0]["errorType"], is_("RecordRequiresManualReview"))
        assert_that(get_batch["changes"][12]["validationErrors"], empty())
    finally:
        # Clean up so data doesn't change
        if response:
            rejecter.reject_batch_change(response["id"], status=200)


@pytest.mark.manual_batch_review
def test_create_batch_change_with_soft_failures_and_allow_manual_review_disabled_fails(shared_zone_test_context):
    """
    Test creating a batch change with soft errors and allowManualReview disabled results in hard failure
    """
    client = shared_zone_test_context.ok_vinyldns_client
    dt = (datetime.datetime.now() + datetime.timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ")

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("non.existent", address="4.5.6.7"),
        ],
        "ownerGroupId": shared_zone_test_context.ok_group["id"]
    }

    response = client.create_batch_change(batch_change_input, False, status=400)
    assert_failed_change_in_error_response(response[0], input_name="non.existent.", record_type="A",
                                           record_data="4.5.6.7",
                                           error_messages=["Zone Discovery Failed: zone for \"non.existent.\" does not exist in VinylDNS. "
                                                           "If zone exists, then it must be connected to in VinylDNS."])


def test_create_batch_change_with_invalid_record_type_fails(shared_zone_test_context):
    """
    Test creating a batch change with invalid record type fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("thing.thing.com.", "B", address="4.5.6.7")
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Invalid RecordType"])


def test_create_batch_change_with_missing_record_fails(shared_zone_test_context):
    """
    Test creating a batch change without a record fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "Add",
                "inputName": "thing.thing.com.",
                "type": "A",
                "ttl": 200
            }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing BatchChangeInput.changes.record.address"])


def test_create_batch_change_with_empty_record_fails(shared_zone_test_context):
    """
    Test creating a batch change with empty record fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "Add",
                "inputName": "thing.thing.com.",
                "type": "A",
                "ttl": 200,
                "record": {}
            }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing A.address"])


def test_create_batch_change_with_bad_A_record_data_fails(shared_zone_test_context):
    """
    Test creating a batch change with malformed A record address fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    bad_A_data_request_add = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("thing.thing.com.", address="bad address")
        ]
    }

    bad_A_data_request_delete_record_set = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("thing.thing.com.", address="bad address", change_type="DeleteRecordSet")
        ]
    }
    error1 = client.create_batch_change(bad_A_data_request_add, status=400)
    error2 = client.create_batch_change(bad_A_data_request_delete_record_set, status=400)

    assert_error(error1, error_messages=["A must be a valid IPv4 Address"])
    assert_error(error2, error_messages=["A must be a valid IPv4 Address"])


def test_create_batch_change_with_bad_AAAA_record_data_fails(shared_zone_test_context):
    """
    Test creating a batch change with malformed AAAA record address fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    bad_AAAA_data_request_add = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("thing.thing.com.", record_type="AAAA", address="bad address")
        ]
    }

    bad_AAAA_data_request_delete_record_set = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("thing.thing.com.", record_type="AAAA", address="bad address", change_type="DeleteRecordSet")
        ]
    }
    error1 = client.create_batch_change(bad_AAAA_data_request_add, status=400)
    error2 = client.create_batch_change(bad_AAAA_data_request_delete_record_set, status=400)

    assert_error(error1, error_messages=["AAAA must be a valid IPv6 Address"])
    assert_error(error2, error_messages=["AAAA must be a valid IPv6 Address"])


def test_create_batch_change_with_incorrect_CNAME_record_attribute_fails(shared_zone_test_context):
    """
    Test creating a batch change with incorrect CNAME record attribute fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    bad_CNAME_data_request = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "Add",
                "inputName": "bizz.bazz.",
                "type": "CNAME",
                "ttl": 200,
                "record": {
                    "address": "buzz."
                }
            }
        ]
    }
    errors = client.create_batch_change(bad_CNAME_data_request, status=400)["errors"]

    assert_that(errors, contains_exactly("Missing CNAME.cname"))


def test_create_batch_change_with_incorrect_PTR_record_attribute_fails(shared_zone_test_context):
    """
    Test creating a batch change with incorrect PTR record attribute fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    bad_PTR_data_request = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "Add",
                "inputName": "4.5.6.7",
                "type": "PTR",
                "ttl": 200,
                "record": {
                    "address": "buzz."
                }
            }
        ]
    }
    errors = client.create_batch_change(bad_PTR_data_request, status=400)["errors"]

    assert_that(errors, contains_exactly("Missing PTR.ptrdname"))


def test_create_batch_change_with_bad_CNAME_record_attribute_fails(shared_zone_test_context):
    """
    Test creating a batch change with malformed CNAME record fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    bad_CNAME_data_request_add = {
        "comments": "this is optional",
        "changes": [
            get_change_CNAME_json(input_name="bizz.baz.", cname="s." + "s" * 256)
        ]
    }

    bad_CNAME_data_request_delete_record_set = {
        "comments": "this is optional",
        "changes": [
            get_change_CNAME_json(input_name="bizz.baz.", cname="s." + "s" * 256, change_type="DeleteRecordSet")
        ]
    }
    error1 = client.create_batch_change(bad_CNAME_data_request_add, status=400)
    error2 = client.create_batch_change(bad_CNAME_data_request_delete_record_set, status=400)

    assert_error(error1, error_messages=["CNAME domain name must not exceed 255 characters"])
    assert_error(error2, error_messages=["CNAME domain name must not exceed 255 characters"])


def test_create_batch_change_with_bad_PTR_record_attribute_fails(shared_zone_test_context):
    """
    Test creating a batch change with malformed PTR record fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    bad_PTR_data_request_add = {
        "comments": "this is optional",
        "changes": [
            get_change_PTR_json("4.5.6.7", ptrdname="s" * 256),
        ]
    }

    bad_PTR_data_request_delete_record_set = {
        "comments": "this is optional",
        "changes": [
            get_change_PTR_json("4.5.6.7", ptrdname="s" * 256),
        ]
    }
    error1 = client.create_batch_change(bad_PTR_data_request_add, status=400)
    error2 = client.create_batch_change(bad_PTR_data_request_delete_record_set, status=400)

    assert_error(error1, error_messages=["PTR must be less than 255 characters"])
    assert_error(error2, error_messages=["PTR must be less than 255 characters"])


def test_create_batch_change_with_missing_input_name_for_delete_fails(shared_zone_test_context):
    """
    Test creating a batch change without an inputName for DeleteRecordSet fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "DeleteRecordSet",
                "type": "A"
            }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing BatchChangeInput.changes.inputName"])


def test_create_batch_change_with_missing_record_type_for_delete_fails(shared_zone_test_context):
    """
    Test creating a batch change without record type for DeleteRecordSet fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "DeleteRecordSet",
                "inputName": "thing.thing.com."
            }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing BatchChangeInput.changes.type"])


def test_mx_recordtype_cannot_have_invalid_preference(shared_zone_test_context):
    """
    Test batch fails with bad mx preference
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    ok_zone_name = shared_zone_test_context.ok_zone["name"]

    batch_change_input_low_add = {
        "comments": "this is optional",
        "changes": [
            get_change_MX_json(f"too-small.{ok_zone_name}", preference=-1)
        ]
    }

    batch_change_input_high_add = {
        "comments": "this is optional",
        "changes": [
            get_change_MX_json(f"too-big.{ok_zone_name}", preference=65536)
        ]
    }

    batch_change_input_low_delete_record_set = {
        "comments": "this is optional",
        "changes": [
            get_change_MX_json(f"too-small.{ok_zone_name}", preference=-1, change_type="DeleteRecordSet")
        ]
    }

    batch_change_input_high_delete_record_set = {
        "comments": "this is optional",
        "changes": [
            get_change_MX_json(f"too-big.{ok_zone_name}", preference=65536, change_type="DeleteRecordSet")
        ]
    }

    error_low_add = ok_client.create_batch_change(batch_change_input_low_add, status=400)
    error_high_add = ok_client.create_batch_change(batch_change_input_high_add, status=400)
    error_low_delete_record_set = ok_client.create_batch_change(batch_change_input_low_delete_record_set, status=400)
    error_high_delete_record_set = ok_client.create_batch_change(batch_change_input_high_delete_record_set, status=400)

    assert_error(error_low_add, error_messages=["MX.preference must be a 16 bit integer"])
    assert_error(error_high_add, error_messages=["MX.preference must be a 16 bit integer"])
    assert_error(error_low_delete_record_set, error_messages=["MX.preference must be a 16 bit integer"])
    assert_error(error_high_delete_record_set, error_messages=["MX.preference must be a 16 bit integer"])


def test_create_batch_change_with_invalid_duplicate_record_names_fails(shared_zone_test_context):
    """
    Test creating a batch change that contains_exactly a CNAME record and another record with the same name fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone_name: str = shared_zone_test_context.ok_zone["name"]

    rs_A_delete = create_recordset(shared_zone_test_context.ok_zone, "delete1", "A", [{"address": "10.1.1.1"}])
    rs_CNAME_delete = create_recordset(shared_zone_test_context.ok_zone, "delete-this1", "CNAME",
                                       [{"cname": "cname."}])

    to_create = [rs_A_delete, rs_CNAME_delete]
    to_delete = []
    bare_ok_zone_name = ok_zone_name.rstrip('.')
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json(f"thing1.{ok_zone_name}", address="4.5.6.7"),
            get_change_CNAME_json(f"thing1.{bare_ok_zone_name}"),
            get_change_A_AAAA_json(f"delete1.{bare_ok_zone_name}", change_type="DeleteRecordSet"),
            get_change_CNAME_json(f"delete1.{bare_ok_zone_name}"),
            get_change_A_AAAA_json(f"delete-this1.{bare_ok_zone_name}", address="4.5.6.7"),
            get_change_CNAME_json(f"delete-this1.{bare_ok_zone_name}", change_type="DeleteRecordSet")
        ]
    }

    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, "Complete"))

        response = client.create_batch_change(batch_change_input, status=400)
        assert_successful_change_in_error_response(response[0], input_name=f"thing1.{ok_zone_name}", record_data="4.5.6.7")
        assert_failed_change_in_error_response(response[1], input_name=f"thing1.{ok_zone_name}", record_type="CNAME", record_data="test.com.",
                                               error_messages=[f'Record Name "thing1.{ok_zone_name}" Not Unique In Batch Change: '
                                                               f'cannot have multiple "CNAME" records with the same name.'])
        assert_successful_change_in_error_response(response[2], input_name=f"delete1.{ok_zone_name}", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[3], input_name=f"delete1.{ok_zone_name}", record_type="CNAME", record_data="test.com.")
        assert_successful_change_in_error_response(response[4], input_name=f"delete-this1.{ok_zone_name}", record_data="4.5.6.7")
        assert_successful_change_in_error_response(response[5], input_name=f"delete-this1.{ok_zone_name}", change_type="DeleteRecordSet", record_type="CNAME")
    finally:
        clear_recordset_list(to_delete, client)


def test_create_batch_change_with_readonly_user_fails(shared_zone_test_context):
    """
    Test creating a batch change with an read-only user fails (acl rules on zone)
    """
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    ok_group_name = shared_zone_test_context.ok_group["name"]

    acl_rule = generate_acl_rule("Read", groupId=shared_zone_test_context.dummy_group["id"], recordMask=".*",
                                 recordTypes=["A", "AAAA"])

    delete_rs = create_recordset(shared_zone_test_context.ok_zone, "delete", "A", [{"address": "127.0.0.1"}], 300)
    update_rs = create_recordset(shared_zone_test_context.ok_zone, "update", "A", [{"address": "127.0.0.1"}], 300)

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json(f"relative.{ok_zone_name}", address="4.5.6.7"),
            get_change_A_AAAA_json(f"delete.{ok_zone_name}", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(f"update.{ok_zone_name}", address="1.2.3.4"),
            get_change_A_AAAA_json(f"update.{ok_zone_name}", change_type="DeleteRecordSet")
        ]
    }

    to_delete = []
    try:
        add_ok_acl_rules(shared_zone_test_context, acl_rule)

        for rs in [delete_rs, update_rs]:
            create_result = ok_client.create_recordset(rs, status=202)
            to_delete.append(ok_client.wait_until_recordset_change_status(create_result, "Complete"))

        errors = dummy_client.create_batch_change(batch_change_input, status=400)

        assert_failed_change_in_error_response(errors[0], input_name=f"relative.{ok_zone_name}", record_data="4.5.6.7",
                                               error_messages=[f'User \"dummy\" is not authorized. Contact zone owner group: {ok_group_name} at test@test.com to make DNS changes.'])
        assert_failed_change_in_error_response(errors[1], input_name=f"delete.{ok_zone_name}", change_type="DeleteRecordSet",
                                               record_data="4.5.6.7",
                                               error_messages=[f'User "dummy" is not authorized. Contact zone owner group: {ok_group_name} at test@test.com to make DNS changes.'])
        assert_failed_change_in_error_response(errors[2], input_name=f"update.{ok_zone_name}", record_data="1.2.3.4",
                                               error_messages=[f'User \"dummy\" is not authorized. Contact zone owner group: {ok_group_name} at test@test.com to make DNS changes.'])
        assert_failed_change_in_error_response(errors[3], input_name=f"update.{ok_zone_name}", change_type="DeleteRecordSet",
                                               record_data=None,
                                               error_messages=[f'User \"dummy\" is not authorized. Contact zone owner group: {ok_group_name} at test@test.com to make DNS changes.'])
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        clear_recordset_list(to_delete, ok_client)


def test_a_recordtype_add_checks(shared_zone_test_context):
    """
    Test all add validations performed on A records submitted in batch changes
    """
    client = shared_zone_test_context.ok_vinyldns_client
    dummy_zone_name = shared_zone_test_context.dummy_zone["name"]
    dummy_group_name = shared_zone_test_context.dummy_group["name"]
    parent_zone_name = shared_zone_test_context.parent_zone["name"]

    existing_a_name = generate_record_name()
    existing_a_fqdn = "{0}.{1}".format(existing_a_name, shared_zone_test_context.parent_zone["name"])
    existing_a = create_recordset(shared_zone_test_context.parent_zone, existing_a_name, "A", [{"address": "10.1.1.1"}],
                                  100)

    existing_cname_name = generate_record_name()
    existing_cname_fqdn = "{0}.{1}".format(existing_cname_name, shared_zone_test_context.parent_zone["name"])
    existing_cname = create_recordset(shared_zone_test_context.parent_zone, existing_cname_name, "CNAME",
                                      [{"cname": "cname.data."}], 100)

    good_record_name = generate_record_name()
    good_record_fqdn = "{0}.{1}".format(good_record_name, shared_zone_test_context.parent_zone["name"])
    batch_change_input = {
        "changes": [
            # valid changes
            get_change_A_AAAA_json(good_record_fqdn, address="1.2.3.4"),

            # input validation failures
            get_change_A_AAAA_json(f"bad-ttl-and-invalid-name$.{parent_zone_name}", ttl=29, address="1.2.3.4"),
            get_change_A_AAAA_json("reverse-zone.10.10.in-addr.arpa.", address="1.2.3.4"),

            # zone discovery failures
            get_change_A_AAAA_json(f"no.subzone.{parent_zone_name}", address="1.2.3.4"),
            get_change_A_AAAA_json("no.zone.at.all.", address="1.2.3.4"),

            # context validation failures
            get_change_CNAME_json(f"cname-duplicate.{parent_zone_name}"),
            get_change_A_AAAA_json(f"cname-duplicate.{parent_zone_name}", address="1.2.3.4"),
            get_change_A_AAAA_json(existing_a_fqdn, address="1.2.3.4"),
            get_change_A_AAAA_json(existing_cname_fqdn, address="1.2.3.4"),
            get_change_A_AAAA_json(f"user-add-unauthorized.{dummy_zone_name}", address="1.2.3.4")
        ]
    }

    to_create = [existing_a, existing_cname]
    to_delete = []
    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, "Complete"))

        response = client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name=good_record_fqdn, record_data="1.2.3.4")

        # ttl, domain name, reverse zone input validations
        assert_failed_change_in_error_response(response[1], input_name=f"bad-ttl-and-invalid-name$.{parent_zone_name}", ttl=29,
                                               record_data="1.2.3.4",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               f'Invalid domain name: "bad-ttl-and-invalid-name$.{parent_zone_name}", '
                                                               "valid domain names must be letters, numbers, underscores, and hyphens, joined by dots, and terminated with a dot."])
        assert_failed_change_in_error_response(response[2], input_name="reverse-zone.10.10.in-addr.arpa.",
                                               record_data="1.2.3.4",
                                               error_messages=["Invalid Record Type In Reverse Zone: record with name \"reverse-zone.10.10.in-addr.arpa.\" and "
                                                               "type \"A\" is not allowed in a reverse zone."])

        # zone discovery failure
        assert_failed_change_in_error_response(response[3], input_name=f"no.subzone.{parent_zone_name}", record_data="1.2.3.4",
                                               error_messages=[f'Zone Discovery Failed: zone for "no.subzone.{parent_zone_name}" does not exist in VinylDNS. '
                                                               f'If zone exists, then it must be connected to in VinylDNS.'])
        assert_failed_change_in_error_response(response[4], input_name="no.zone.at.all.", record_data="1.2.3.4",
                                               error_messages=['Zone Discovery Failed: zone for "no.zone.at.all." does not exist in VinylDNS. '
                                                               'If zone exists, then it must be connected to in VinylDNS.'])

        # context validations: duplicate name failure is always on the cname
        assert_failed_change_in_error_response(response[5], input_name=f"cname-duplicate.{parent_zone_name}",
                                               record_type="CNAME", record_data="test.com.",
                                               error_messages=[f"Record Name \"cname-duplicate.{parent_zone_name}\" Not Unique In Batch Change: "
                                                               f"cannot have multiple \"CNAME\" records with the same name."])
        assert_successful_change_in_error_response(response[6], input_name=f"cname-duplicate.{parent_zone_name}",
                                                   record_data="1.2.3.4")

        # context validations: conflicting recordsets, unauthorized error
        assert_failed_change_in_error_response(response[7], input_name=existing_a_fqdn, record_data="1.2.3.4",
                                               error_messages=[f"RecordName \"{existing_a_fqdn}\" already exists. "
                                                               f"If you intended to update this record, submit a DeleteRecordSet entry followed by an Add."])
        assert_failed_change_in_error_response(response[8], input_name=existing_cname_fqdn,
                                               record_data="1.2.3.4",
                                               error_messages=[f'CNAME Conflict: CNAME record names must be unique. '
                                                               f'Existing record with name "{existing_cname_fqdn}" and type \"CNAME\" conflicts with this record.'])
        assert_failed_change_in_error_response(response[9], input_name=f"user-add-unauthorized.{dummy_zone_name}",
                                               record_data="1.2.3.4",
                                               error_messages=[f"User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes."])
    finally:
        clear_recordset_list(to_delete, client)


def test_a_recordtype_update_delete_checks(shared_zone_test_context):
    """
    Test all update and delete validations performed on A records submitted in batch changes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    dummy_zone = shared_zone_test_context.dummy_zone
    ok_zone_name = ok_zone["name"]
    dummy_zone_name = dummy_zone["name"]
    dummy_group_name = shared_zone_test_context.dummy_group["name"]

    group_to_delete = {}
    temp_group = {
        "name": "test-group-for-record-in-private-zone",
        "email": "test@test.com",
        "description": "for testing that a get batch change still works when record owner group is deleted",
        "members": [{"id": "ok"}, {"id": "dummy"}],
        "admins": [{"id": "ok"}, {"id": "dummy"}]
    }

    rs_delete_name = generate_record_name()
    rs_delete_fqdn = rs_delete_name + f".{ok_zone_name}"
    rs_delete_ok = create_recordset(ok_zone, rs_delete_name, "A", [{"address": "1.1.1.1"}])

    rs_update_name = generate_record_name()
    rs_update_fqdn = rs_update_name + f".{ok_zone_name}"
    rs_update_ok = create_recordset(ok_zone, rs_update_name, "A", [{"address": "1.1.1.1"}])

    rs_delete_dummy_name = generate_record_name()
    rs_delete_dummy_fqdn = rs_delete_dummy_name + f".{dummy_zone_name}"
    rs_delete_dummy = create_recordset(dummy_zone, rs_delete_dummy_name, "A", [{"address": "1.1.1.1"}])

    rs_update_dummy_name = generate_record_name()
    rs_update_dummy_fqdn = rs_update_dummy_name + f".{dummy_zone_name}"
    rs_update_dummy = create_recordset(dummy_zone, rs_update_dummy_name, "A", [{"address": "1.1.1.1"}])

    rs_dummy_with_owner_name = generate_record_name()
    rs_delete_dummy_with_owner_fqdn = rs_dummy_with_owner_name + f".{dummy_zone_name}"
    rs_update_dummy_with_owner_fqdn = rs_dummy_with_owner_name + f".{dummy_zone_name}"

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            # valid changes
            get_change_A_AAAA_json(rs_delete_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(rs_update_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(rs_update_fqdn, ttl=300),
            get_change_A_AAAA_json(f"non-existent.{ok_zone_name}", change_type="DeleteRecordSet"),

            # input validations failures
            get_change_A_AAAA_json("$invalid.host.name.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("reverse.zone.in-addr.arpa.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("$another.invalid.host.name.", ttl=300),
            get_change_A_AAAA_json("$another.invalid.host.name.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("another.reverse.zone.in-addr.arpa.", ttl=10),
            get_change_A_AAAA_json("another.reverse.zone.in-addr.arpa.", change_type="DeleteRecordSet"),

            # zone discovery failures
            get_change_A_AAAA_json("zone.discovery.error.", change_type="DeleteRecordSet"),

            # context validation failures: record does not exist, not authorized
            get_change_A_AAAA_json(rs_delete_dummy_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(rs_update_dummy_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(rs_update_dummy_fqdn, ttl=300),
            get_change_A_AAAA_json(rs_delete_dummy_with_owner_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(rs_update_dummy_with_owner_fqdn, ttl=300)
        ]
    }

    to_create = [rs_delete_ok, rs_update_ok, rs_delete_dummy, rs_update_dummy]
    to_delete = []

    try:
        group_to_delete = dummy_client.create_group(temp_group, status=200)

        rs_update_dummy_with_owner = create_recordset(dummy_zone, rs_dummy_with_owner_name, "A", [{"address": "1.1.1.1"}], 100, group_to_delete["id"])
        create_rs_update_dummy_with_owner = dummy_client.create_recordset(rs_update_dummy_with_owner, status=202)
        to_delete.append(dummy_client.wait_until_recordset_change_status(create_rs_update_dummy_with_owner, "Complete"))

        for rs in to_create:
            if rs["zoneId"] == dummy_zone["id"]:
                create_client = dummy_client
            else:
                create_client = ok_client

            create_rs = create_client.create_recordset(rs, status=202)
            to_delete.append(create_client.wait_until_recordset_change_status(create_rs, "Complete"))

        # Confirm that record set doesn't already exist
        ok_client.get_recordset(ok_zone["id"], "non-existent", status=404)

        response = ok_client.create_batch_change(batch_change_input, status=400)

        # valid changes
        assert_successful_change_in_error_response(response[0], input_name=rs_delete_fqdn, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], input_name=rs_update_fqdn, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[2], input_name=rs_update_fqdn, ttl=300)
        assert_successful_change_in_error_response(response[3], input_name=f"non-existent.{ok_zone_name}", change_type="DeleteRecordSet")

        # input validations failures
        assert_failed_change_in_error_response(response[4], input_name="$invalid.host.name.",
                                               change_type="DeleteRecordSet",
                                               error_messages=['Invalid domain name: "$invalid.host.name.", valid domain names must be letters, '
                                                               'numbers, underscores, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[5], input_name="reverse.zone.in-addr.arpa.",
                                               change_type="DeleteRecordSet",
                                               error_messages=['Invalid Record Type In Reverse Zone: record with name "reverse.zone.in-addr.arpa." and type "A" '
                                                               'is not allowed in a reverse zone.'])
        assert_failed_change_in_error_response(response[6], input_name="$another.invalid.host.name.", ttl=300,
                                               error_messages=['Invalid domain name: "$another.invalid.host.name.", valid domain names must be letters, '
                                                               'numbers, underscores, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[7], input_name="$another.invalid.host.name.",
                                               change_type="DeleteRecordSet",
                                               error_messages=['Invalid domain name: "$another.invalid.host.name.", valid domain names must be letters, '
                                                               'numbers, underscores, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[8], input_name="another.reverse.zone.in-addr.arpa.", ttl=10,
                                               error_messages=['Invalid Record Type In Reverse Zone: record with name "another.reverse.zone.in-addr.arpa." '
                                                               'and type "A" is not allowed in a reverse zone.',
                                                               'Invalid TTL: "10", must be a number between 30 and 2147483647.'])
        assert_failed_change_in_error_response(response[9], input_name="another.reverse.zone.in-addr.arpa.",
                                               change_type="DeleteRecordSet",
                                               error_messages=['Invalid Record Type In Reverse Zone: record with name "another.reverse.zone.in-addr.arpa." '
                                                               'and type "A" is not allowed in a reverse zone.'])

        # zone discovery failure
        assert_failed_change_in_error_response(response[10], input_name="zone.discovery.error.",
                                               change_type="DeleteRecordSet",
                                               error_messages=['Zone Discovery Failed: zone for "zone.discovery.error." does not exist in VinylDNS. '
                                                               'If zone exists, then it must be connected to in VinylDNS.'])

        # context validation failures: record does not exist, not authorized
        assert_failed_change_in_error_response(response[11], input_name=rs_delete_dummy_fqdn,
                                               change_type="DeleteRecordSet",
                                               error_messages=[f'User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes.'])
        assert_failed_change_in_error_response(response[12], input_name=rs_update_dummy_fqdn,
                                               change_type="DeleteRecordSet",
                                               error_messages=[f'User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes.'])
        assert_failed_change_in_error_response(response[13], input_name=rs_update_dummy_fqdn, ttl=300,
                                               error_messages=[f'User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes.'])
        assert_failed_change_in_error_response(response[14], input_name=rs_update_dummy_with_owner_fqdn, change_type="DeleteRecordSet",
                                               error_messages=[f'User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes.'])
        assert_failed_change_in_error_response(response[15], input_name=rs_update_dummy_with_owner_fqdn, ttl=300,
                                               error_messages=[f'User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes.'])
    finally:
        # Clean up updates
        dummy_deletes = [rs for rs in to_delete if rs["zone"]["id"] == dummy_zone["id"]]
        ok_deletes = [rs for rs in to_delete if rs["zone"]["id"] != dummy_zone["id"]]
        clear_recordset_list(dummy_deletes, dummy_client)
        clear_recordset_list(ok_deletes, ok_client)
        dummy_client.delete_group(group_to_delete["id"], status=200)


def test_aaaa_recordtype_add_checks(shared_zone_test_context):
    """
    Test all add validations performed on AAAA records submitted in batch changes
    """
    client = shared_zone_test_context.ok_vinyldns_client
    dummy_zone_name = shared_zone_test_context.dummy_zone["name"]
    parent_zone_name = shared_zone_test_context.parent_zone["name"]
    dummy_group_name = shared_zone_test_context.dummy_group["name"]

    existing_aaaa_name = generate_record_name()
    existing_aaaa_fqdn = existing_aaaa_name + "." + shared_zone_test_context.parent_zone["name"]
    existing_aaaa = create_recordset(shared_zone_test_context.parent_zone, existing_aaaa_name, "AAAA", [{"address": "1::1"}], 100)

    existing_cname_name = generate_record_name()
    existing_cname_fqdn = existing_cname_name + "." + shared_zone_test_context.parent_zone["name"]
    existing_cname = create_recordset(shared_zone_test_context.parent_zone, existing_cname_name, "CNAME", [{"cname": "cname.data."}], 100)

    good_record_name = generate_record_name()
    good_record_fqdn = good_record_name + "." + shared_zone_test_context.parent_zone["name"]
    batch_change_input = {
        "changes": [
            # valid changes
            get_change_A_AAAA_json(good_record_fqdn, record_type="AAAA", address="1::1"),

            # input validation failures
            get_change_A_AAAA_json(f"bad-ttl-and-invalid-name$.{parent_zone_name}", ttl=29, record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json("reverse-zone.1.2.3.ip6.arpa.", record_type="AAAA", address="1::1"),

            # zone discovery failures
            get_change_A_AAAA_json(f"no.subzone.{parent_zone_name}", record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json("no.zone.at.all.", record_type="AAAA", address="1::1"),

            # context validation failures
            get_change_CNAME_json(f"cname-duplicate.{parent_zone_name}"),
            get_change_A_AAAA_json(f"cname-duplicate.{parent_zone_name}", record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json(existing_aaaa_fqdn, record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json(existing_cname_fqdn, record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json(f"user-add-unauthorized.{dummy_zone_name}", record_type="AAAA", address="1::1")
        ]
    }

    to_create = [existing_aaaa, existing_cname]
    to_delete = []
    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, "Complete"))

        response = client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name=good_record_fqdn, record_type="AAAA", record_data="1::1")

        # ttl, domain name, reverse zone input validations
        assert_failed_change_in_error_response(response[1], input_name=f"bad-ttl-and-invalid-name$.{parent_zone_name}", ttl=29,
                                               record_type="AAAA", record_data="1::1",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               f'Invalid domain name: "bad-ttl-and-invalid-name$.{parent_zone_name}", '
                                                               "valid domain names must be letters, numbers, underscores, and hyphens, joined by dots, and terminated with a dot."])
        assert_failed_change_in_error_response(response[2], input_name="reverse-zone.1.2.3.ip6.arpa.",
                                               record_type="AAAA", record_data="1::1",
                                               error_messages=["Invalid Record Type In Reverse Zone: record with name \"reverse-zone.1.2.3.ip6.arpa.\" "
                                                               "and type \"AAAA\" is not allowed in a reverse zone."])

        # zone discovery failures
        assert_failed_change_in_error_response(response[3], input_name=f"no.subzone.{parent_zone_name}", record_type="AAAA",
                                               record_data="1::1",
                                               error_messages=[f'Zone Discovery Failed: zone for \"no.subzone.{parent_zone_name}\" does not exist in VinylDNS. '
                                                               f'If zone exists, then it must be connected to in VinylDNS.'])
        assert_failed_change_in_error_response(response[4], input_name="no.zone.at.all.", record_type="AAAA",
                                               record_data="1::1",
                                               error_messages=["Zone Discovery Failed: zone for \"no.zone.at.all.\" does not exist in VinylDNS. "
                                                               "If zone exists, then it must be connected to in VinylDNS."])

        # context validations: duplicate name failure (always on the cname), conflicting recordsets, unauthorized error
        assert_failed_change_in_error_response(response[5], input_name=f"cname-duplicate.{parent_zone_name}",
                                               record_type="CNAME", record_data="test.com.",
                                               error_messages=[f"Record Name \"cname-duplicate.{parent_zone_name}\" Not Unique In Batch Change: "
                                                               f"cannot have multiple \"CNAME\" records with the same name."])
        assert_successful_change_in_error_response(response[6], input_name=f"cname-duplicate.{parent_zone_name}",
                                               record_type="AAAA", record_data="1::1")
        assert_successful_change_in_error_response(response[7], input_name=existing_aaaa_fqdn, record_type="AAAA",
                                               record_data="1::1")
        assert_failed_change_in_error_response(response[8], input_name=existing_cname_fqdn, record_type="AAAA",
                                               record_data="1::1",
                                               error_messages=[f"CNAME Conflict: CNAME record names must be unique. Existing record with name \"{existing_cname_fqdn}\" "
                                                               f"and type \"CNAME\" conflicts with this record."])
        assert_failed_change_in_error_response(response[9], input_name=f"user-add-unauthorized.{dummy_zone_name}",
                                               record_type="AAAA", record_data="1::1",
                                               error_messages=[f"User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes."])
    finally:
        clear_recordset_list(to_delete, client)


def test_aaaa_recordtype_update_delete_checks(shared_zone_test_context):
    """
    Test all update and delete validations performed on AAAA records submitted in batch changes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    dummy_zone = shared_zone_test_context.dummy_zone
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    dummy_zone_name = shared_zone_test_context.dummy_zone["name"]
    dummy_group_name = shared_zone_test_context.dummy_group["name"]

    rs_delete_name = generate_record_name()
    rs_delete_fqdn = rs_delete_name + f".{ok_zone_name}"
    rs_delete_ok = create_recordset(ok_zone, rs_delete_name, "AAAA", [{"address": "1::4:5:6:7:8"}], 200)

    rs_update_name = generate_record_name()
    rs_update_fqdn = rs_update_name + f".{ok_zone_name}"
    rs_update_ok = create_recordset(ok_zone, rs_update_name, "AAAA", [{"address": "1:1:1:1:1:1:1:1"}], 200)

    rs_delete_dummy_name = generate_record_name()
    rs_delete_dummy_fqdn = rs_delete_dummy_name + f".{dummy_zone_name}"
    rs_delete_dummy = create_recordset(dummy_zone, rs_delete_dummy_name, "AAAA", [{"address": "1::1"}], 200)

    rs_update_dummy_name = generate_record_name()
    rs_update_dummy_fqdn = rs_update_dummy_name + f".{dummy_zone_name}"
    rs_update_dummy = create_recordset(dummy_zone, rs_update_dummy_name, "AAAA", [{"address": "1:2:3:4:5:6:7:8"}], 200)

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            # valid changes
            get_change_A_AAAA_json(rs_delete_fqdn, record_type="AAAA", change_type="DeleteRecordSet", address="1:0::4:5:6:7:8"),
            get_change_A_AAAA_json(rs_update_fqdn, record_type="AAAA", ttl=300, address="1:2:3:4:5:6:7:8"),
            get_change_A_AAAA_json(rs_update_fqdn, record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(f"delete-nonexistent.{ok_zone_name}", record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(f"update-nonexistent.{ok_zone_name}", record_type="AAAA", change_type="DeleteRecordSet"),

            # input validations failures
            get_change_A_AAAA_json(f"invalid-name$.{ok_zone_name}", record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("reverse.zone.in-addr.arpa.", record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(f"bad-ttl-and-invalid-name$-update.{ok_zone_name}", record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(f"bad-ttl-and-invalid-name$-update.{ok_zone_name}", ttl=29, record_type="AAAA", address="1:2:3:4:5:6:7:8"),

            # zone discovery failure
            get_change_A_AAAA_json("no.zone.at.all.", record_type="AAAA", change_type="DeleteRecordSet"),

            # context validation failures
            get_change_A_AAAA_json(f"update-nonexistent.{ok_zone_name}", record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json(rs_delete_dummy_fqdn, record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(rs_update_dummy_fqdn, record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json(rs_update_dummy_fqdn, record_type="AAAA", change_type="DeleteRecordSet")
        ]
    }

    to_create = [rs_delete_ok, rs_update_ok, rs_delete_dummy, rs_update_dummy]
    to_delete = []

    try:
        for rs in to_create:
            if rs["zoneId"] == dummy_zone["id"]:
                create_client = dummy_client
            else:
                create_client = ok_client

            create_rs = create_client.create_recordset(rs, status=202)
            to_delete.append(create_client.wait_until_recordset_change_status(create_rs, "Complete"))

        # Confirm that record set doesn't already exist
        ok_client.get_recordset(ok_zone["id"], "delete-nonexistent", status=404)

        response = ok_client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name=rs_delete_fqdn, record_type="AAAA",
                                                   record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], ttl=300, input_name=rs_update_fqdn, record_type="AAAA",
                                                   record_data="1:2:3:4:5:6:7:8")
        assert_successful_change_in_error_response(response[2], input_name=rs_update_fqdn, record_type="AAAA",
                                                   record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[3], input_name=f"delete-nonexistent.{ok_zone_name}", record_type="AAAA",
                                                   record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[4], input_name=f"update-nonexistent.{ok_zone_name}", record_type="AAAA",
                                                   record_data=None, change_type="DeleteRecordSet")

        # input validations failures: invalid input name, reverse zone error, invalid ttl
        assert_failed_change_in_error_response(response[5], input_name=f"invalid-name$.{ok_zone_name}", record_type="AAAA",
                                               record_data=None, change_type="DeleteRecordSet",
                                               error_messages=[f'Invalid domain name: "invalid-name$.{ok_zone_name}", '
                                                               f'valid domain names must be letters, numbers, underscores, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[6], input_name="reverse.zone.in-addr.arpa.", record_type="AAAA",
                                               record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Invalid Record Type In Reverse Zone: record with name \"reverse.zone.in-addr.arpa.\" and "
                                                               "type \"AAAA\" is not allowed in a reverse zone."])
        assert_failed_change_in_error_response(response[7], input_name=f"bad-ttl-and-invalid-name$-update.{ok_zone_name}",
                                               record_type="AAAA", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=[f'Invalid domain name: "bad-ttl-and-invalid-name$-update.{ok_zone_name}", '
                                                               f'valid domain names must be letters, numbers, underscores, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[8], input_name=f"bad-ttl-and-invalid-name$-update.{ok_zone_name}", ttl=29,
                                               record_type="AAAA", record_data="1:2:3:4:5:6:7:8",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               f'Invalid domain name: "bad-ttl-and-invalid-name$-update.{ok_zone_name}", '
                                                               f'valid domain names must be letters, numbers, underscores, and hyphens, joined by dots, and terminated with a dot.'])

        # zone discovery failure
        assert_failed_change_in_error_response(response[9], input_name="no.zone.at.all.", record_type="AAAA",
                                               record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Zone Discovery Failed: zone for \"no.zone.at.all.\" does not exist in VinylDNS. "
                                                               "If zone exists, then it must be connected to in VinylDNS."])

        # context validation failures: record does not exist, not authorized
        assert_successful_change_in_error_response(response[10], input_name=f"update-nonexistent.{ok_zone_name}", record_type="AAAA", record_data="1::1")
        assert_failed_change_in_error_response(response[11], input_name=rs_delete_dummy_fqdn,
                                               record_type="AAAA", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=[f"User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes."])
        assert_failed_change_in_error_response(response[12], input_name=rs_update_dummy_fqdn,
                                               record_type="AAAA", record_data="1::1",
                                               error_messages=[f"User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes."])
        assert_failed_change_in_error_response(response[13], input_name=rs_update_dummy_fqdn,
                                               record_type="AAAA", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=[f"User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes."])
    finally:
        # Clean up updates
        dummy_deletes = [rs for rs in to_delete if rs["zone"]["id"] == dummy_zone["id"]]
        ok_deletes = [rs for rs in to_delete if rs["zone"]["id"] != dummy_zone["id"]]
        clear_recordset_list(dummy_deletes, dummy_client)
        clear_recordset_list(ok_deletes, ok_client)


def test_cname_recordtype_add_checks(shared_zone_test_context):
    """
    Test all add validations performed on CNAME records submitted in batch changes
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    dummy_zone_name = shared_zone_test_context.dummy_zone["name"]
    dummy_group_name = shared_zone_test_context.dummy_group["name"]
    ip4_prefix = shared_zone_test_context.ip4_classless_prefix
    ip4_zone_name = shared_zone_test_context.classless_base_zone["name"]
    ip4_reverse_zone_name = shared_zone_test_context.ip4_reverse_zone["name"]
    parent_zone_name = shared_zone_test_context.parent_zone["name"]

    existing_forward_name = generate_record_name()
    existing_forward_fqdn = existing_forward_name + "." + shared_zone_test_context.parent_zone["name"]
    existing_forward = create_recordset(shared_zone_test_context.parent_zone, existing_forward_name, "A",
                                        [{"address": "1.2.3.4"}], 100)

    existing_reverse_fqdn = "0." + shared_zone_test_context.classless_base_zone["name"]
    existing_reverse = create_recordset(shared_zone_test_context.classless_base_zone, "0", "PTR",
                                        [{"ptrdname": "test.com. "}], 100)

    existing_cname_name = generate_record_name()
    existing_cname_fqdn = existing_cname_name + "." + shared_zone_test_context.parent_zone["name"]
    existing_cname = create_recordset(shared_zone_test_context.parent_zone, existing_cname_name, "CNAME",
                                      [{"cname": "cname.data. "}], 100)

    rs_a_to_cname_ok_name = generate_record_name()
    rs_a_to_cname_ok_fqdn = rs_a_to_cname_ok_name + f".{ok_zone_name}"
    rs_a_to_cname_ok = create_recordset(ok_zone, rs_a_to_cname_ok_name, "A", [{"address": "1.1.1.1"}])

    rs_cname_to_A_ok_name = generate_record_name()
    rs_cname_to_A_ok_fqdn = rs_cname_to_A_ok_name + f".{ok_zone_name}"
    rs_cname_to_A_ok = create_recordset(ok_zone, rs_cname_to_A_ok_name, "CNAME", [{"cname": "test.com."}])

    forward_fqdn = generate_record_name(parent_zone_name)
    reverse_fqdn = generate_record_name(ip4_reverse_zone_name)

    batch_change_input = {
        "changes": [
            # valid change
            get_change_CNAME_json(forward_fqdn),
            get_change_CNAME_json(reverse_fqdn),

            # valid changes - delete and add of same record name but different type
            get_change_A_AAAA_json(rs_a_to_cname_ok_fqdn, change_type="DeleteRecordSet"),
            get_change_CNAME_json(rs_a_to_cname_ok_fqdn),
            get_change_A_AAAA_json(rs_cname_to_A_ok_fqdn),
            get_change_CNAME_json(rs_cname_to_A_ok_fqdn, change_type="DeleteRecordSet"),

            # input validations failures
            get_change_CNAME_json(f"bad-ttl-and-invalid-name$.{parent_zone_name}", ttl=29, cname="also$bad.name"),

            # zone discovery failure
            get_change_CNAME_json("no.zone.com."),

            # cant be apex
            get_change_CNAME_json(parent_zone_name),

            # context validation failures
            get_change_PTR_json(f"{ip4_prefix}.15"),
            get_change_CNAME_json(f"15.{ip4_zone_name}", cname="duplicate.other.type.within.batch."),
            get_change_CNAME_json(f"cname-duplicate.{parent_zone_name}"),
            get_change_CNAME_json(f"cname-duplicate.{parent_zone_name}", cname="duplicate.cname.type.within.batch."),
            get_change_CNAME_json(existing_forward_fqdn),
            get_change_CNAME_json(existing_cname_fqdn),
            get_change_CNAME_json(f"0.{ip4_zone_name}", cname="duplicate.in.db."),
            get_change_CNAME_json(f"user-add-unauthorized.{dummy_zone_name}"),
            get_change_CNAME_json(f"invalid-ipv4-{parent_zone_name}", cname="1.2.3.4")
        ]
    }

    to_create = [existing_forward, existing_reverse, existing_cname, rs_a_to_cname_ok, rs_cname_to_A_ok]
    to_delete = []
    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, "Complete"))

        response = client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name=forward_fqdn, record_type="CNAME", record_data="test.com.")
        assert_successful_change_in_error_response(response[1], input_name=reverse_fqdn, record_type="CNAME", record_data="test.com.")

        # successful changes - delete and add of same record name but different type
        assert_successful_change_in_error_response(response[2], input_name=rs_a_to_cname_ok_fqdn, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[3], input_name=rs_a_to_cname_ok_fqdn, record_type="CNAME", record_data="test.com.")
        assert_successful_change_in_error_response(response[4], input_name=rs_cname_to_A_ok_fqdn)
        assert_successful_change_in_error_response(response[5], input_name=rs_cname_to_A_ok_fqdn, record_type="CNAME", change_type="DeleteRecordSet")

        # ttl, domain name, data
        assert_failed_change_in_error_response(response[6], input_name=f"bad-ttl-and-invalid-name$.{parent_zone_name}", ttl=29,
                                               record_type="CNAME", record_data="also$bad.name.",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               f'Invalid domain name: "bad-ttl-and-invalid-name$.{parent_zone_name}", '
                                                               "valid domain names must be letters, numbers, underscores, and hyphens, joined by dots, and terminated with a dot.",
                                                               'Invalid Cname: "also$bad.name.", valid cnames must be letters, numbers, underscores, and hyphens, '
                                                               "joined by dots, and terminated with a dot."])
        # zone discovery failure
        assert_failed_change_in_error_response(response[7], input_name="no.zone.com.", record_type="CNAME",
                                               record_data="test.com.",
                                               error_messages=["Zone Discovery Failed: zone for \"no.zone.com.\" does not exist in VinylDNS. "
                                                               "If zone exists, then it must be connected to in VinylDNS."])

        # CNAME cant be apex
        assert_failed_change_in_error_response(response[8], input_name=parent_zone_name, record_type="CNAME",
                                               record_data="test.com.",
                                               error_messages=[f"CNAME cannot be the same name as zone \"{parent_zone_name}\"."])

        # context validations: duplicates in batch
        assert_successful_change_in_error_response(response[9], input_name=f"{ip4_prefix}.15", record_type="PTR",
                                                   record_data="test.com.")
        assert_failed_change_in_error_response(response[10], input_name=f"15.{ip4_zone_name}", record_type="CNAME",
                                               record_data="duplicate.other.type.within.batch.",
                                               error_messages=[f"Record Name \"15.{ip4_zone_name}\" Not Unique In Batch Change: "
                                                               f"cannot have multiple \"CNAME\" records with the same name."])
        assert_failed_change_in_error_response(response[11], input_name=f"cname-duplicate.{parent_zone_name}",
                                               record_type="CNAME", record_data="test.com.",
                                               error_messages=[f"Record Name \"cname-duplicate.{parent_zone_name}\" Not Unique In Batch Change: "
                                                               f"cannot have multiple \"CNAME\" records with the same name."])
        assert_failed_change_in_error_response(response[12], input_name=f"cname-duplicate.{parent_zone_name}",
                                               record_type="CNAME", record_data="duplicate.cname.type.within.batch.",
                                               error_messages=[f"Record Name \"cname-duplicate.{parent_zone_name}\" Not Unique In Batch Change: "
                                                               f"cannot have multiple \"CNAME\" records with the same name."])

        # context validations: existing recordsets pre-request, unauthorized, failure on duplicate add
        assert_failed_change_in_error_response(response[13], input_name=existing_forward_fqdn,
                                               record_type="CNAME", record_data="test.com.",
                                               error_messages=[f"CNAME Conflict: CNAME record names must be unique. "
                                                               f"Existing record with name \"{existing_forward_fqdn}\" and type \"A\" conflicts with this record."])
        assert_failed_change_in_error_response(response[14], input_name=existing_cname_fqdn,
                                               record_type="CNAME", record_data="test.com.",
                                               error_messages=[f"RecordName \"{existing_cname_fqdn}\" already exists. "
                                                               f"If you intended to update this record, submit a DeleteRecordSet entry followed by an Add.",
                                                               f"CNAME Conflict: CNAME record names must be unique. "
                                                               f"Existing record with name \"{existing_cname_fqdn}\" and type \"CNAME\" conflicts with this record."])
        assert_failed_change_in_error_response(response[15], input_name=existing_reverse_fqdn, record_type="CNAME",
                                               record_data="duplicate.in.db.",
                                               error_messages=["CNAME Conflict: CNAME record names must be unique. "
                                                               f"Existing record with name \"{existing_reverse_fqdn}\" and type \"PTR\" conflicts with this record."])
        assert_failed_change_in_error_response(response[16], input_name=f"user-add-unauthorized.{dummy_zone_name}",
                                               record_type="CNAME", record_data="test.com.",
                                               error_messages=[f"User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes."])
        assert_failed_change_in_error_response(response[17], input_name=f"invalid-ipv4-{parent_zone_name}", record_type="CNAME", record_data="1.2.3.4.",
                                               error_messages=[f'Invalid Cname: "Fqdn(1.2.3.4.)", Valid CNAME record data should not be an IP address'])

    finally:
        clear_recordset_list(to_delete, client)


def test_cname_recordtype_update_delete_checks(shared_zone_test_context):
    """
    Test all update and delete validations performed on CNAME records submitted in batch changes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    dummy_zone = shared_zone_test_context.dummy_zone
    classless_base_zone = shared_zone_test_context.classless_base_zone
    dummy_zone_name = shared_zone_test_context.dummy_zone["name"]
    dummy_group_name = shared_zone_test_context.dummy_group["name"]
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    ip4_zone_name = shared_zone_test_context.classless_base_zone["name"]
    parent_zone_name = shared_zone_test_context.parent_zone["name"]

    rs_delete_ok = create_recordset(ok_zone, "delete3", "CNAME", [{"cname": "test.com."}])
    rs_update_ok = create_recordset(ok_zone, "update3", "CNAME", [{"cname": "test.com."}])
    rs_delete_dummy = create_recordset(dummy_zone, "delete-unauthorized3", "CNAME", [{"cname": "test.com."}])
    rs_update_dummy = create_recordset(dummy_zone, "update-unauthorized3", "CNAME", [{"cname": "test.com."}])
    rs_delete_base = create_recordset(classless_base_zone, "200", "CNAME",
                                      [{"cname": f"200.192/30.{ip4_zone_name}"}])
    rs_update_base = create_recordset(classless_base_zone, "201", "CNAME",
                                      [{"cname": f"201.192/30.{ip4_zone_name}"}])
    rs_update_duplicate_add = create_recordset(shared_zone_test_context.parent_zone, "Existing-Cname2", "CNAME",
                                               [{"cname": "cname.data. "}], 100)

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            # valid changes - forward zone
            get_change_CNAME_json(f"delete3.{ok_zone_name}", change_type="DeleteRecordSet"),
            get_change_CNAME_json(f"update3.{ok_zone_name}", change_type="DeleteRecordSet"),
            get_change_CNAME_json(f"update3.{ok_zone_name}", ttl=300),
            get_change_CNAME_json(f"non-existent-delete.{ok_zone_name}", change_type="DeleteRecordSet"),
            get_change_CNAME_json(f"non-existent-update.{ok_zone_name}", change_type="DeleteRecordSet"),

            # valid changes - reverse zone
            get_change_CNAME_json(f"200.{ip4_zone_name}", change_type="DeleteRecordSet"),
            get_change_CNAME_json(f"201.{ip4_zone_name}", change_type="DeleteRecordSet"),
            get_change_CNAME_json(f"201.{ip4_zone_name}", ttl=300),

            # input validation failures
            get_change_CNAME_json("$invalid.host.name.", change_type="DeleteRecordSet"),
            get_change_CNAME_json("$another.invalid.host.name", change_type="DeleteRecordSet"),
            get_change_CNAME_json("$another.invalid.host.name", ttl=20, cname="$another.invalid.cname."),

            # zone discovery failures
            get_change_CNAME_json("zone.discovery.error.", change_type="DeleteRecordSet"),

            # context validation failures: record does not exist, not authorized, failure on update with multiple adds
            get_change_CNAME_json(f"non-existent-update.{ok_zone_name}"),
            get_change_CNAME_json(f"delete-unauthorized3.{dummy_zone_name}", change_type="DeleteRecordSet"),
            get_change_CNAME_json(f"update-unauthorized3.{dummy_zone_name}", change_type="DeleteRecordSet"),
            get_change_CNAME_json(f"update-unauthorized3.{dummy_zone_name}", ttl=300),
            get_change_CNAME_json(f"existing-cname2.{parent_zone_name}", change_type="DeleteRecordSet"),
            get_change_CNAME_json(f"existing-cname2.{parent_zone_name}"),
            get_change_CNAME_json(f"existing-cname2.{parent_zone_name}", cname="test2.com.")
        ]
    }

    to_create = [rs_delete_ok, rs_update_ok, rs_delete_dummy, rs_update_dummy, rs_delete_base, rs_update_base,
                 rs_update_duplicate_add]
    to_delete = []

    try:
        for rs in to_create:
            if rs["zoneId"] == dummy_zone["id"]:
                create_client = dummy_client
            else:
                create_client = ok_client

            create_rs = create_client.create_recordset(rs, status=202)
            to_delete.append(create_client.wait_until_recordset_change_status(create_rs, "Complete"))

        # Confirm that record set doesn't already exist
        ok_client.get_recordset(ok_zone["id"], "non-existent", status=404)

        response = ok_client.create_batch_change(batch_change_input, status=400)

        # valid changes - forward zone
        assert_successful_change_in_error_response(response[0], input_name=f"delete3.{ok_zone_name}", record_type="CNAME",
                                                   change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], input_name=f"update3.{ok_zone_name}", record_type="CNAME",
                                                   change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[2], input_name=f"update3.{ok_zone_name}", record_type="CNAME", ttl=300,
                                                   record_data="test.com.")
        assert_successful_change_in_error_response(response[3], input_name=f"non-existent-delete.{ok_zone_name}", record_type="CNAME",
                                                   change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[4], input_name=f"non-existent-update.{ok_zone_name}", record_type="CNAME",
                                                   change_type="DeleteRecordSet")

        # valid changes - reverse zone
        assert_successful_change_in_error_response(response[5], input_name=f"200.{ip4_zone_name}",
                                                   record_type="CNAME", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[6], input_name=f"201.{ip4_zone_name}",
                                                   record_type="CNAME", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[7], input_name=f"201.{ip4_zone_name}",
                                                   record_type="CNAME", ttl=300, record_data="test.com.")

        # ttl, domain name, data
        assert_failed_change_in_error_response(response[8], input_name="$invalid.host.name.", record_type="CNAME",
                                               change_type="DeleteRecordSet",
                                               error_messages=['Invalid domain name: "$invalid.host.name.", valid domain names must be letters, numbers, '
                                                               'underscores, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[9], input_name="$another.invalid.host.name.",
                                               record_type="CNAME", change_type="DeleteRecordSet",
                                               error_messages=['Invalid domain name: "$another.invalid.host.name.", valid domain names must be letters, numbers, '
                                                               'underscores, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[10], input_name="$another.invalid.host.name.", ttl=20,
                                               record_type="CNAME", record_data="$another.invalid.cname.",
                                               error_messages=['Invalid TTL: "20", must be a number between 30 and 2147483647.',
                                                               'Invalid domain name: "$another.invalid.host.name.", valid domain names must be letters, numbers, '
                                                               'underscores, and hyphens, joined by dots, and terminated with a dot.',
                                                               'Invalid Cname: "$another.invalid.cname.", valid cnames must be letters, numbers, '
                                                               'underscores, and hyphens, joined by dots, and terminated with a dot.'])

        # zone discovery failure
        assert_failed_change_in_error_response(response[11], input_name="zone.discovery.error.", record_type="CNAME",
                                               change_type="DeleteRecordSet",
                                               error_messages=[
                                                   'Zone Discovery Failed: zone for "zone.discovery.error." does not exist in VinylDNS. If zone exists, then it must be connected to in VinylDNS.'])

        # context validation failures: record does not exist, not authorized
        assert_successful_change_in_error_response(response[12], input_name=f"non-existent-update.{ok_zone_name}",
                                                   record_type="CNAME", record_data="test.com.")
        assert_failed_change_in_error_response(response[13], input_name=f"delete-unauthorized3.{dummy_zone_name}",
                                               record_type="CNAME", change_type="DeleteRecordSet",
                                               error_messages=[f'User "ok" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes.'])
        assert_failed_change_in_error_response(response[14], input_name=f"update-unauthorized3.{dummy_zone_name}",
                                               record_type="CNAME", change_type="DeleteRecordSet",
                                               error_messages=[f'User "ok" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes.'])
        assert_failed_change_in_error_response(response[15], input_name=f"update-unauthorized3.{dummy_zone_name}",
                                               record_type="CNAME", ttl=300, record_data="test.com.",
                                               error_messages=[f'User "ok" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes.'])
        assert_successful_change_in_error_response(response[16], input_name=f"existing-cname2.{parent_zone_name}",
                                                   record_type="CNAME", change_type="DeleteRecordSet")
        assert_failed_change_in_error_response(response[17], input_name=f"existing-cname2.{parent_zone_name}",
                                               record_type="CNAME", record_data="test.com.",
                                               error_messages=[f"Record Name \"existing-cname2.{parent_zone_name}\" Not Unique In Batch Change: "
                                                               f"cannot have multiple \"CNAME\" records with the same name."])
        assert_failed_change_in_error_response(response[18], input_name=f"existing-cname2.{parent_zone_name}",
                                               record_type="CNAME", record_data="test2.com.",
                                               error_messages=[f"Record Name \"existing-cname2.{parent_zone_name}\" Not Unique In Batch Change: "
                                                               f"cannot have multiple \"CNAME\" records with the same name."])

    finally:
        # Clean up updates
        dummy_deletes = [rs for rs in to_delete if rs["zone"]["id"] == dummy_zone["id"]]
        ok_deletes = [rs for rs in to_delete if rs["zone"]["id"] != dummy_zone["id"]]
        clear_recordset_list(dummy_deletes, dummy_client)
        clear_recordset_list(ok_deletes, ok_client)


@pytest.mark.serial
def test_ptr_recordtype_auth_checks(shared_zone_test_context):
    """
    Test all authorization validations performed on PTR records submitted in batch changes
    """
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    ip4_prefix = shared_zone_test_context.ip4_classless_prefix
    ip6_prefix = shared_zone_test_context.ip6_prefix
    ok_group_name = shared_zone_test_context.ok_group["name"]

    no_auth_ipv4 = create_recordset(shared_zone_test_context.classless_base_zone, "25", "PTR",
                                    [{"ptrdname": "ptrdname.data."}], 200)
    no_auth_ipv6 = create_recordset(shared_zone_test_context.ip6_16_nibble_zone, "4.3.2.1.0.0.0.0.0.0.0.0.0.0.0.0",
                                    "PTR", [{"ptrdname": "ptrdname.data."}], 200)

    batch_change_input = {
        "changes": [
            get_change_PTR_json(f"{ip4_prefix}.5", ptrdname="not.authorized.ipv4.ptr.base."),
            get_change_PTR_json(f"{ip4_prefix}.193", ptrdname="not.authorized.ipv4.ptr.classless.delegation."),
            get_change_PTR_json(f"{ip6_prefix}:1000::1234", ptrdname="not.authorized.ipv6.ptr."),
            get_change_PTR_json(f"{ip4_prefix}.25", change_type="DeleteRecordSet"),
            get_change_PTR_json(f"{ip6_prefix}:1000::1234", change_type="DeleteRecordSet")
        ]
    }

    to_create = [no_auth_ipv4, no_auth_ipv6]
    to_delete = []

    try:
        for create_json in to_create:
            create_result = ok_client.create_recordset(create_json, status=202)
            to_delete.append(ok_client.wait_until_recordset_change_status(create_result, "Complete"))

        errors = dummy_client.create_batch_change(batch_change_input, status=400)

        assert_failed_change_in_error_response(errors[0], input_name=f"{ip4_prefix}.5", record_type="PTR",
                                               record_data="not.authorized.ipv4.ptr.base.",
                                               error_messages=[f"User \"dummy\" is not authorized. Contact zone owner group: {ok_group_name} at test@test.com to make DNS changes."])
        assert_failed_change_in_error_response(errors[1], input_name=f"{ip4_prefix}.193", record_type="PTR",
                                               record_data="not.authorized.ipv4.ptr.classless.delegation.",
                                               error_messages=[f"User \"dummy\" is not authorized. Contact zone owner group: {ok_group_name} at test@test.com to make DNS changes."])
        assert_failed_change_in_error_response(errors[2], input_name=f"{ip6_prefix}:1000::1234", record_type="PTR",
                                               record_data="not.authorized.ipv6.ptr.",
                                               error_messages=[f"User \"dummy\" is not authorized. Contact zone owner group: {ok_group_name} at test@test.com to make DNS changes."])
        assert_failed_change_in_error_response(errors[3], input_name=f"{ip4_prefix}.25", record_type="PTR", record_data=None,
                                               change_type="DeleteRecordSet",
                                               error_messages=[f"User \"dummy\" is not authorized. Contact zone owner group: {ok_group_name} at test@test.com to make DNS changes."])
        assert_failed_change_in_error_response(errors[4], input_name=f"{ip6_prefix}:1000::1234", record_type="PTR",
                                               record_data=None, change_type="DeleteRecordSet",
                                               error_messages=[f"User \"dummy\" is not authorized. Contact zone owner group: {ok_group_name} at test@test.com to make DNS changes."])
    finally:
        clear_recordset_list(to_delete, ok_client)


@pytest.mark.serial
def test_ipv4_ptr_recordtype_add_checks(shared_zone_test_context):
    """
    Perform all add, non-authorization validations performed on IPv4 PTR records submitted in batch changes
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ip4_prefix = shared_zone_test_context.ip4_classless_prefix
    ip4_zone_name = shared_zone_test_context.classless_base_zone["name"]

    existing_ipv4 = create_recordset(shared_zone_test_context.classless_zone_delegation_zone, "193", "PTR", [{"ptrdname": "ptrdname.data."}])
    existing_cname = create_recordset(shared_zone_test_context.classless_base_zone, "199", "CNAME", [{"cname": "cname.data. "}], 300)

    batch_change_input = {
        "changes": [
            # valid change
            get_change_PTR_json(f"{ip4_prefix}.44", ptrdname="base.vinyldns"),
            get_change_PTR_json(f"{ip4_prefix}.198", ptrdname="delegated.vinyldns"),

            # input validation failures
            get_change_PTR_json("invalidip.111."),
            get_change_PTR_json("4.5.6.7", ttl=29, ptrdname="-1.2.3.4"),

            # delegated and non-delegated PTR duplicate name checks
            get_change_PTR_json(f"{ip4_prefix}.196"),  # delegated zone
            get_change_CNAME_json(f"196.{ip4_zone_name}"),  # non-delegated zone
            get_change_CNAME_json(f"196.192/30.{ip4_zone_name}"),  # delegated zone

            get_change_PTR_json(f"{ip4_prefix}.55"),  # non-delegated zone
            get_change_CNAME_json(f"55.{ip4_zone_name}"),  # non-delegated zone
            get_change_CNAME_json(f"55.192/30.{ip4_zone_name}"),  # delegated zone

            # zone discovery failure
            get_change_PTR_json(f"192.1.1.100"),

            # context validation failures
            get_change_PTR_json(f"{ip4_prefix}.193", ptrdname="existing-ptr."),
            get_change_PTR_json(f"{ip4_prefix}.199", ptrdname="existing-cname.")
        ]
    }

    to_create = [existing_ipv4, existing_cname]
    to_delete = []
    try:
        # make sure 196 is cleared before continuing
        delete_recordset_by_name(shared_zone_test_context.classless_zone_delegation_zone["id"], "196", client)
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, "Complete"))

        response = client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name=f"{ip4_prefix}.44", record_type="PTR", record_data="base.vinyldns.")
        assert_successful_change_in_error_response(response[1], input_name=f"{ip4_prefix}.198", record_type="PTR", record_data="delegated.vinyldns.")

        # input validation failures: invalid ip, ttl, data
        assert_failed_change_in_error_response(response[2], input_name="invalidip.111.", record_type="PTR", record_data="test.com.",
                                               error_messages=['Invalid IP address: "invalidip.111.".'])
        assert_failed_change_in_error_response(response[3], input_name="4.5.6.7", ttl=29, record_type="PTR", record_data="-1.2.3.4.",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               'Invalid domain name: "-1.2.3.4.", '
                                                               "valid domain names must be letters, numbers, underscores, and hyphens, joined by dots, and terminated with a dot."])

        # delegated and non-delegated PTR duplicate name checks
        assert_successful_change_in_error_response(response[4], input_name=f"{ip4_prefix}.196", record_type="PTR", record_data="test.com.")
        assert_failed_change_in_error_response(response[5], input_name=f"196.{ip4_zone_name}", record_type="CNAME", record_data="test.com.",
                                               error_messages=[f'Record Name "196.{ip4_zone_name}" Not Unique In Batch Change: cannot have multiple "CNAME" records with the same name.'])
        assert_successful_change_in_error_response(response[6], input_name=f"196.192/30.{ip4_zone_name}", record_type="CNAME", record_data="test.com.")
        assert_successful_change_in_error_response(response[7], input_name=f"{ip4_prefix}.55", record_type="PTR", record_data="test.com.")
        assert_failed_change_in_error_response(response[8], input_name=f"55.{ip4_zone_name}", record_type="CNAME", record_data="test.com.",
                                               error_messages=[f'Record Name "55.{ip4_zone_name}" Not Unique In Batch Change: cannot have multiple "CNAME" records with the same name.'])
        assert_successful_change_in_error_response(response[9], input_name=f"55.192/30.{ip4_zone_name}", record_type="CNAME", record_data="test.com.")

        # zone discovery failure
        assert_failed_change_in_error_response(response[10], input_name="192.1.1.100", record_type="PTR", record_data="test.com.",
                                               error_messages=['Zone Discovery Failed: zone for "192.1.1.100" does not exist in VinylDNS. '
                                                               'If zone exists, then it must be connected to in VinylDNS.'])

        # context validations: existing cname recordset
        assert_failed_change_in_error_response(response[11], input_name=f"{ip4_prefix}.193", record_type="PTR", record_data="existing-ptr.",
                                               error_messages=[f'RecordName "{ip4_prefix}.193" already exists. '
                                                               f'If you intended to update this record, submit a DeleteRecordSet entry followed by an Add.'])
        assert_failed_change_in_error_response(response[12], input_name=f"{ip4_prefix}.199", record_type="PTR", record_data="existing-cname.",
                                               error_messages=[
                                                   f'CNAME Conflict: CNAME record names must be unique. Existing record with name "{ip4_prefix}.199" and type "CNAME" conflicts with this record.'])
    finally:
        clear_recordset_list(to_delete, client)


# TODO: Commenting this out as it deletes a zone that is used by other tests and recreates it which is messed up
# @pytest.mark.serial
# def test_ipv4_ptr_record_when_zone_discovery_only_finds_mismatched_delegated_zone_fails(shared_zone_test_context):
#     """
#     Test IPv4 PTR record discovery for only delegated zones that do not match the record name fails
#     """
#     # TODO: This is really strange, deleting a classless base zone and then re-creating it?
#     ok_client = shared_zone_test_context.ok_vinyldns_client
#     classless_base_zone = shared_zone_test_context.classless_base_zone
#
#     batch_change_input = {
#         "changes": [
#             get_change_PTR_json(f"{ip4_prefix}.1"),
#             # dummy change with too big TTL so ZD failure wont go to pending if enabled
#             get_change_A_AAAA_json("this.change.will.fail.", ttl=99999999999, address="1.1.1.1")
#         ]
#     }
#
#     try:
#         # delete classless base zone (2.0.192.in-addr.arpa); only remaining zone is delegated zone (192/30.2.0.192.in-addr.arpa)
#         ok_client.delete_zone(classless_base_zone["id"], status=202)
#         ok_client.wait_until_zone_deleted(classless_base_zone["id"])
#         response = ok_client.create_batch_change(batch_change_input, status=400)
#         assert_failed_change_in_error_response(response[0], input_name=f"{ip4_prefix}.1", record_type="PTR",
#                                                record_data="test.com.",
#                                                error_messages=[
#                                                    f'Zone Discovery Failed: zone for "{ip4_prefix}.1" does not exist in VinylDNS. If zone exists, then it must be connected to in VinylDNS.'])
#
#     finally:
#         # re-create classless base zone and update zone info in shared_zone_test_context for use in future tests
#         zone_create_change = ok_client.create_zone(shared_zone_test_context.classless_base_zone_json, status=202)
#         shared_zone_test_context.classless_base_zone = zone_create_change["zone"]
#         ok_client.wait_until_zone_active(zone_create_change[u"zone"][u"id"])


@pytest.mark.serial
def test_ipv4_ptr_recordtype_update_delete_checks(shared_zone_test_context):
    """
    Test all update and delete validations performed on ipv4 PTR records submitted in batch changes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    base_zone = shared_zone_test_context.classless_base_zone
    delegated_zone = shared_zone_test_context.classless_zone_delegation_zone
    ip4_prefix = shared_zone_test_context.ip4_classless_prefix
    ip4_zone_name = shared_zone_test_context.classless_base_zone["name"]

    rs_delete_ipv4 = create_recordset(base_zone, "25", "PTR", [{"ptrdname": "delete.ptr."}], 200)
    rs_update_ipv4 = create_recordset(delegated_zone, "193", "PTR", [{"ptrdname": "update.ptr."}], 200)
    rs_replace_cname = create_recordset(base_zone, "21", "CNAME", [{"cname": "replace.cname."}], 200)
    rs_replace_ptr = create_recordset(base_zone, "17", "PTR", [{"ptrdname": "replace.ptr."}], 200)
    rs_update_ipv4_fail = create_recordset(base_zone, "9", "PTR", [{"ptrdname": "failed-update.ptr."}], 200)

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            # valid changes ipv4
            get_change_PTR_json(f"{ip4_prefix}.25", change_type="DeleteRecordSet"),
            get_change_PTR_json(f"{ip4_prefix}.193", ttl=300, ptrdname="has-updated.ptr."),
            get_change_PTR_json(f"{ip4_prefix}.193", change_type="DeleteRecordSet"),
            get_change_PTR_json(f"{ip4_prefix}.199", change_type="DeleteRecordSet"),
            get_change_PTR_json(f"{ip4_prefix}.200", change_type="DeleteRecordSet"),

            # valid changes: delete and add of same record name but different type
            get_change_CNAME_json(f"21.{ip4_zone_name}", change_type="DeleteRecordSet"),
            get_change_PTR_json(f"{ip4_prefix}.21", ptrdname="replace-cname.ptr."),
            get_change_CNAME_json(f"17.{ip4_zone_name}", cname="replace-ptr.cname."),
            get_change_PTR_json(f"{ip4_prefix}.17", change_type="DeleteRecordSet"),

            # input validations failures
            get_change_PTR_json("1.1.1", change_type="DeleteRecordSet"),
            get_change_PTR_json("192.0.2.", change_type="DeleteRecordSet"),
            get_change_PTR_json("192.0.2.", ttl=29, ptrdname="failed-update$.ptr"),

            # zone discovery failure
            get_change_PTR_json("192.1.1.25", change_type="DeleteRecordSet"),

            # context validation failures
            get_change_PTR_json(f"{ip4_prefix}.200", ttl=300, ptrdname="has-updated.ptr."),
        ]
    }

    to_create = [rs_delete_ipv4, rs_update_ipv4, rs_replace_cname, rs_replace_ptr, rs_update_ipv4_fail]
    to_delete = []

    try:
        for rs in to_create:
            create_rs = ok_client.create_recordset(rs, status=202)
            to_delete.append(ok_client.wait_until_recordset_change_status(create_rs, "Complete"))

        response = ok_client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name=f"{ip4_prefix}.25", record_type="PTR",
                                                   record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], ttl=300, input_name=f"{ip4_prefix}.193", record_type="PTR",
                                                   record_data="has-updated.ptr.")
        assert_successful_change_in_error_response(response[2], input_name=f"{ip4_prefix}.193", record_type="PTR",
                                                   record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[3], input_name=f"{ip4_prefix}.199", record_type="PTR",
                                                   record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[4], input_name=f"{ip4_prefix}.200", record_type="PTR",
                                                   record_data=None, change_type="DeleteRecordSet")

        # successful changes: add and delete of same record name but different type
        assert_successful_change_in_error_response(response[5], input_name=f"21.{ip4_zone_name}",
                                                   record_type="CNAME", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[6], input_name=f"{ip4_prefix}.21", record_type="PTR",
                                                   record_data="replace-cname.ptr.")
        assert_successful_change_in_error_response(response[7], input_name=f"17.{ip4_zone_name}",
                                                   record_type="CNAME", record_data="replace-ptr.cname.")
        assert_successful_change_in_error_response(response[8], input_name=f"{ip4_prefix}.17", record_type="PTR",
                                                   record_data=None, change_type="DeleteRecordSet")

        # input validations failures: invalid IP, ttl, and record data
        assert_failed_change_in_error_response(response[9], input_name="1.1.1", record_type="PTR", record_data=None,
                                               change_type="DeleteRecordSet",
                                               error_messages=['Invalid IP address: "1.1.1".'])
        assert_failed_change_in_error_response(response[10], input_name="192.0.2.", record_type="PTR", record_data=None,
                                               change_type="DeleteRecordSet",
                                               error_messages=['Invalid IP address: "192.0.2.".'])
        assert_failed_change_in_error_response(response[11], ttl=29, input_name="192.0.2.", record_type="PTR",
                                               record_data="failed-update$.ptr.",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               'Invalid IP address: "192.0.2.".',
                                                               'Invalid domain name: "failed-update$.ptr.", valid domain names must be letters, numbers, underscores, and hyphens, '
                                                               'joined by dots, and terminated with a dot.'])

        # zone discovery failure
        assert_failed_change_in_error_response(response[12], input_name="192.1.1.25", record_type="PTR",
                                               record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Zone Discovery Failed: zone for \"192.1.1.25\" does not exist in VinylDNS. If zone exists, "
                                                               "then it must be connected to in VinylDNS."])

        # context validation failures: record does not exist
        assert_successful_change_in_error_response(response[13], ttl=300, input_name=f"{ip4_prefix}.200", record_type="PTR", record_data="has-updated.ptr.")
    finally:
        clear_recordset_list(to_delete, ok_client)


def test_ipv6_ptr_recordtype_add_checks(shared_zone_test_context):
    """
    Test all add, non-authorization validations performed on IPv6 PTR records submitted in batch changes
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ip6_prefix = shared_zone_test_context.ip6_prefix

    existing_ptr = create_recordset(shared_zone_test_context.ip6_16_nibble_zone, "b.b.b.b.0.0.0.0.0.0.0.0.0.0.0.0",
                                    "PTR", [{"ptrdname": "test.com."}], 100)

    batch_change_input = {
        "changes": [
            # valid change
            get_change_PTR_json(f"{ip6_prefix}:1000::1234"),

            # input validation failures
            get_change_PTR_json(f"{ip6_prefix}:1000::abe", ttl=29),
            get_change_PTR_json(f"{ip6_prefix}:1000::bae", ptrdname="$malformed.hostname."),
            get_change_PTR_json("fd69:27cc:fe91de::ab", ptrdname="malformed.ip.address."),

            # zone discovery failure
            get_change_PTR_json("fedc:ba98:7654::abc", ptrdname="zone.discovery.error."),

            # context validation failures
            get_change_PTR_json(f"{ip6_prefix}:1000::bbbb", ptrdname="existing.ptr.")
        ]
    }

    to_create = [existing_ptr]
    to_delete = []
    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, "Complete"))

        response = client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name=f"{ip6_prefix}:1000::1234",
                                                   record_type="PTR", record_data="test.com.")

        # independent validations: bad TTL, malformed host name/IP address, duplicate record
        assert_failed_change_in_error_response(response[1], input_name=f"{ip6_prefix}:1000::abe", ttl=29,
                                               record_type="PTR", record_data="test.com.",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.'])
        assert_failed_change_in_error_response(response[2], input_name=f"{ip6_prefix}:1000::bae", record_type="PTR",
                                               record_data="$malformed.hostname.",
                                               error_messages=['Invalid domain name: "$malformed.hostname.", valid domain names must be letters, numbers, '
                                                               'underscores, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[3], input_name="fd69:27cc:fe91de::ab", record_type="PTR",
                                               record_data="malformed.ip.address.",
                                               error_messages=['Invalid IP address: "fd69:27cc:fe91de::ab".'])

        # zone discovery failure
        assert_failed_change_in_error_response(response[4], input_name="fedc:ba98:7654::abc", record_type="PTR",
                                               record_data="zone.discovery.error.",
                                               error_messages=["Zone Discovery Failed: zone for \"fedc:ba98:7654::abc\" does not exist in VinylDNS. "
                                                               "If zone exists, then it must be connected to in VinylDNS."])

        # context validations: existing record sets pre-request
        assert_failed_change_in_error_response(response[5], input_name=f"{ip6_prefix}:1000::bbbb", record_type="PTR",
                                               record_data="existing.ptr.",
                                               error_messages=[f"RecordName \"{ip6_prefix}:1000::bbbb\" already exists. "
                                                               f"If you intended to update this record, submit a DeleteRecordSet entry followed by an Add."])
    finally:
        clear_recordset_list(to_delete, client)


def test_ipv6_ptr_recordtype_update_delete_checks(shared_zone_test_context):
    """
    Test all update and delete validations performed on ipv6 PTR records submitted in batch changes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    ip6_reverse_zone = shared_zone_test_context.ip6_16_nibble_zone
    ip6_prefix = shared_zone_test_context.ip6_prefix

    rs_delete_ipv6 = create_recordset(ip6_reverse_zone, "a.a.a.a.0.0.0.0.0.0.0.0.0.0.0.0", "PTR",
                                      [{"ptrdname": "delete.ptr."}], 200)
    rs_update_ipv6 = create_recordset(ip6_reverse_zone, "2.6.0.0.0.0.0.0.0.0.0.0.0.0.0.0", "PTR",
                                      [{"ptrdname": "update.ptr."}], 200)
    rs_update_ipv6_fail = create_recordset(ip6_reverse_zone, "8.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0", "PTR",
                                           [{"ptrdname": "failed-update.ptr."}], 200)

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            # valid changes ipv6
            get_change_PTR_json(f"{ip6_prefix}:1000::aaaa", change_type="DeleteRecordSet"),
            get_change_PTR_json(f"{ip6_prefix}:1000::62", ttl=300, ptrdname="has-updated.ptr."),
            get_change_PTR_json(f"{ip6_prefix}:1000::62", change_type="DeleteRecordSet"),
            get_change_PTR_json(f"{ip6_prefix}:1000::60", change_type="DeleteRecordSet"),
            get_change_PTR_json(f"{ip6_prefix}:1000::65", change_type="DeleteRecordSet"),

            # input validations failures
            get_change_PTR_json("fd69:27cc:fe91de::ab", change_type="DeleteRecordSet"),
            get_change_PTR_json("fd69:27cc:fe91de::ba", change_type="DeleteRecordSet"),
            get_change_PTR_json("fd69:27cc:fe91de::ba", ttl=29, ptrdname="failed-update$.ptr"),

            # zone discovery failures
            get_change_PTR_json("fedc:ba98:7654::abc", change_type="DeleteRecordSet"),

            # context validation failures
            get_change_PTR_json(f"{ip6_prefix}:1000::65", ttl=300, ptrdname="has-updated.ptr."),
        ]
    }

    to_create = [rs_delete_ipv6, rs_update_ipv6, rs_update_ipv6_fail]
    to_delete = []

    try:
        for rs in to_create:
            create_rs = ok_client.create_recordset(rs, status=202)
            to_delete.append(ok_client.wait_until_recordset_change_status(create_rs, "Complete"))

        response = ok_client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name=f"{ip6_prefix}:1000::aaaa",
                                                   record_type="PTR", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], ttl=300, input_name=f"{ip6_prefix}:1000::62",
                                                   record_type="PTR", record_data="has-updated.ptr.")
        assert_successful_change_in_error_response(response[2], input_name=f"{ip6_prefix}:1000::62", record_type="PTR",
                                                   record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[3], input_name=f"{ip6_prefix}:1000::60", record_type="PTR",
                                                   record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[4], input_name=f"{ip6_prefix}:1000::65", record_type="PTR",
                                                   record_data=None, change_type="DeleteRecordSet")

        # input validations failures: invalid IP, ttl, and record data
        assert_failed_change_in_error_response(response[5], input_name="fd69:27cc:fe91de::ab", record_type="PTR",
                                               record_data=None, change_type="DeleteRecordSet",
                                               error_messages=['Invalid IP address: "fd69:27cc:fe91de::ab".'])
        assert_failed_change_in_error_response(response[6], input_name="fd69:27cc:fe91de::ba", record_type="PTR",
                                               record_data=None, change_type="DeleteRecordSet",
                                               error_messages=['Invalid IP address: "fd69:27cc:fe91de::ba".'])
        assert_failed_change_in_error_response(response[7], ttl=29, input_name="fd69:27cc:fe91de::ba",
                                               record_type="PTR", record_data="failed-update$.ptr.",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               'Invalid IP address: "fd69:27cc:fe91de::ba".',
                                                               'Invalid domain name: "failed-update$.ptr.", valid domain names must be letters, numbers, underscores, '
                                                               'and hyphens, joined by dots, and terminated with a dot.'])

        # zone discovery failure
        assert_failed_change_in_error_response(response[8], input_name="fedc:ba98:7654::abc", record_type="PTR",
                                               record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Zone Discovery Failed: zone for \"fedc:ba98:7654::abc\" does not exist in VinylDNS. "
                                                               "If zone exists, then it must be connected to in VinylDNS."])

        # context validation failures: record does not exist, failure on update with double add
        assert_successful_change_in_error_response(response[9], ttl=300, input_name=f"{ip6_prefix}:1000::65",
                                                   record_type="PTR", record_data="has-updated.ptr.")

    finally:
        clear_recordset_list(to_delete, ok_client)


def test_txt_recordtype_add_checks(shared_zone_test_context):
    """
    Test all add validations performed on TXT records submitted in batch changes
    """
    client = shared_zone_test_context.ok_vinyldns_client
    dummy_zone_name = shared_zone_test_context.dummy_zone["name"]
    dummy_group_name = shared_zone_test_context.dummy_group["name"]
    ok_zone_name = shared_zone_test_context.ok_zone["name"]

    existing_txt_name = generate_record_name()
    existing_txt_fqdn = existing_txt_name + f".{ok_zone_name}"
    existing_txt = create_recordset(shared_zone_test_context.ok_zone, existing_txt_name, "TXT", [{"text": "test"}], 100)

    existing_cname_name = generate_record_name()
    existing_cname_fqdn = existing_cname_name + f".{ok_zone_name}"
    existing_cname = create_recordset(shared_zone_test_context.ok_zone, existing_cname_name, "CNAME",
                                      [{"cname": "test."}], 100)

    good_record_fqdn = generate_record_name(ok_zone_name)
    batch_change_input = {
        "changes": [
            # valid change
            get_change_TXT_json(good_record_fqdn),

            # input validation failures
            get_change_TXT_json(f"bad-ttl-and-invalid-name$.{ok_zone_name}", ttl=29),

            # zone discovery failures
            get_change_TXT_json("no.zone.at.all."),

            # context validation failures
            get_change_CNAME_json(f"cname-duplicate.{ok_zone_name}"),
            get_change_TXT_json(f"cname-duplicate.{ok_zone_name}"),
            get_change_TXT_json(existing_txt_fqdn),
            get_change_TXT_json(existing_cname_fqdn),
            get_change_TXT_json(f"user-add-unauthorized.{dummy_zone_name}")
        ]
    }

    to_create = [existing_txt, existing_cname]
    to_delete = []
    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, "Complete"))

        response = client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name=good_record_fqdn, record_type="TXT",
                                                   record_data="test")

        # ttl, domain name, record data
        assert_failed_change_in_error_response(response[1], input_name=f"bad-ttl-and-invalid-name$.{ok_zone_name}", ttl=29,
                                               record_type="TXT", record_data="test",
                                               error_messages=[
                                                   'Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                   f'Invalid domain name: "bad-ttl-and-invalid-name$.{ok_zone_name}", '
                                                   "valid domain names must be letters, numbers, underscores, and hyphens, joined by dots, and terminated with a dot."])

        # zone discovery failure
        assert_failed_change_in_error_response(response[2], input_name="no.zone.at.all.", record_type="TXT",
                                               record_data="test",
                                               error_messages=['Zone Discovery Failed: zone for "no.zone.at.all." does not exist in VinylDNS. '
                                                               'If zone exists, then it must be connected to in VinylDNS.'])

        # context validations: cname duplicate
        assert_failed_change_in_error_response(response[3], input_name=f"cname-duplicate.{ok_zone_name}", record_type="CNAME",
                                               record_data="test.com.",
                                               error_messages=[f"Record Name \"cname-duplicate.{ok_zone_name}\" Not Unique In Batch Change: "
                                                               f"cannot have multiple \"CNAME\" records with the same name."])

        # context validations: conflicting recordsets, unauthorized error
        assert_successful_change_in_error_response(response[5], input_name=existing_txt_fqdn, record_type="TXT",
                                               record_data="test")
        assert_failed_change_in_error_response(response[6], input_name=existing_cname_fqdn, record_type="TXT",
                                               record_data="test",
                                               error_messages=[f"CNAME Conflict: CNAME record names must be unique. "
                                                               f"Existing record with name \"{existing_cname_fqdn}\" and type \"CNAME\" conflicts with this record."])
        assert_failed_change_in_error_response(response[7], input_name=f"user-add-unauthorized.{dummy_zone_name}",
                                               record_type="TXT", record_data="test",
                                               error_messages=[f"User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes."])
    finally:
        clear_recordset_list(to_delete, client)


def test_txt_recordtype_update_delete_checks(shared_zone_test_context):
    """
    Test all update and delete validations performed on TXT records submitted in batch changes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    dummy_zone = shared_zone_test_context.dummy_zone
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    dummy_zone_name = shared_zone_test_context.dummy_zone["name"]
    dummy_group_name = shared_zone_test_context.dummy_group["name"]

    rs_delete_name = generate_record_name()
    rs_delete_fqdn = rs_delete_name + f".{ok_zone_name}"
    rs_delete_ok = create_recordset(ok_zone, rs_delete_name, "TXT", [{"text": "test"}], 200)

    rs_update_name = generate_record_name()
    rs_update_fqdn = rs_update_name + f".{ok_zone_name}"
    rs_update_ok = create_recordset(ok_zone, rs_update_name, "TXT", [{"text": "test"}], 200)

    rs_delete_dummy_name = generate_record_name()
    rs_delete_dummy_fqdn = rs_delete_dummy_name + f".{dummy_zone_name}"
    rs_delete_dummy = create_recordset(dummy_zone, rs_delete_dummy_name, "TXT", [{"text": "test"}], 200)

    rs_update_dummy_name = generate_record_name()
    rs_update_dummy_fqdn = rs_update_dummy_name + f".{dummy_zone_name}"
    rs_update_dummy = create_recordset(dummy_zone, rs_update_dummy_name, "TXT", [{"text": "test"}], 200)

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            # valid changes
            get_change_TXT_json(rs_delete_fqdn, change_type="DeleteRecordSet"),
            get_change_TXT_json(rs_update_fqdn, change_type="DeleteRecordSet"),
            get_change_TXT_json(rs_update_fqdn, ttl=300),
            get_change_TXT_json(f"delete-nonexistent.{ok_zone_name}", change_type="DeleteRecordSet"),
            get_change_TXT_json(f"update-nonexistent.{ok_zone_name}", change_type="DeleteRecordSet"),

            # input validations failures
            get_change_TXT_json(f"invalid-name$.{ok_zone_name}", change_type="DeleteRecordSet"),
            get_change_TXT_json(f"invalid-ttl.{ok_zone_name}", ttl=29, text="bad-ttl"),

            # zone discovery failure
            get_change_TXT_json("no.zone.at.all.", change_type="DeleteRecordSet"),

            # context validation failures
            get_change_TXT_json(f"update-nonexistent.{ok_zone_name}", text="test"),
            get_change_TXT_json(rs_delete_dummy_fqdn, change_type="DeleteRecordSet"),
            get_change_TXT_json(rs_update_dummy_fqdn, text="test"),
            get_change_TXT_json(rs_update_dummy_fqdn, change_type="DeleteRecordSet")
        ]
    }

    to_create = [rs_delete_ok, rs_update_ok, rs_delete_dummy, rs_update_dummy]
    to_delete = []

    try:
        for rs in to_create:
            if rs["zoneId"] == dummy_zone["id"]:
                create_client = dummy_client
            else:
                create_client = ok_client

            create_rs = create_client.create_recordset(rs, status=202)
            to_delete.append(create_client.wait_until_recordset_change_status(create_rs, "Complete"))

        # Confirm that record set doesn't already exist
        ok_client.get_recordset(ok_zone["id"], "delete-nonexistent", status=404)

        response = ok_client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name=rs_delete_fqdn, record_type="TXT", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], input_name=rs_update_fqdn, record_type="TXT", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[2], ttl=300, input_name=rs_update_fqdn, record_type="TXT", record_data="test")
        assert_successful_change_in_error_response(response[3], input_name=f"delete-nonexistent.{ok_zone_name}", record_type="TXT", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[4], input_name=f"update-nonexistent.{ok_zone_name}", record_type="TXT", record_data=None, change_type="DeleteRecordSet")

        # input validations failures: invalid input name, reverse zone error, invalid ttl
        assert_failed_change_in_error_response(response[5], input_name=f"invalid-name$.{ok_zone_name}", record_type="TXT", record_data="test", change_type="DeleteRecordSet",
                                               error_messages=[f'Invalid domain name: "invalid-name$.{ok_zone_name}", valid domain names must be '
                                                               f'letters, numbers, underscores, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[6], input_name=f"invalid-ttl.{ok_zone_name}", ttl=29, record_type="TXT", record_data="bad-ttl",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.'])

        # zone discovery failure
        assert_failed_change_in_error_response(response[7], input_name="no.zone.at.all.", record_type="TXT", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=[
                                                   "Zone Discovery Failed: zone for \"no.zone.at.all.\" does not exist in VinylDNS. "
                                                   "If zone exists, then it must be connected to in VinylDNS."])

        # context validation failures: record does not exist, not authorized
        assert_successful_change_in_error_response(response[8], input_name=f"update-nonexistent.{ok_zone_name}", record_type="TXT", record_data="test")
        assert_failed_change_in_error_response(response[9], input_name=rs_delete_dummy_fqdn, record_type="TXT", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=[f"User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes."])
        assert_failed_change_in_error_response(response[10], input_name=rs_update_dummy_fqdn, record_type="TXT", record_data="test",
                                               error_messages=[f"User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes."])
        assert_failed_change_in_error_response(response[11], input_name=rs_update_dummy_fqdn, record_type="TXT", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=[f"User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes."])
    finally:
        # Clean up updates
        dummy_deletes = [rs for rs in to_delete if rs["zone"]["id"] == dummy_zone["id"]]
        ok_deletes = [rs for rs in to_delete if rs["zone"]["id"] != dummy_zone["id"]]
        clear_recordset_list(dummy_deletes, dummy_client)
        clear_recordset_list(ok_deletes, ok_client)


def test_mx_recordtype_add_checks(shared_zone_test_context):
    """
    Test all add validations performed on MX records submitted in batch changes
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    dummy_zone_name = shared_zone_test_context.dummy_zone["name"]
    dummy_group_name = shared_zone_test_context.dummy_group["name"]
    ip4_zone_name = shared_zone_test_context.classless_base_zone["name"]

    existing_mx_name = generate_record_name()
    existing_mx_fqdn = f"{existing_mx_name}.{ok_zone_name}"
    existing_mx = create_recordset(shared_zone_test_context.ok_zone, existing_mx_name, "MX", [{"preference": 1, "exchange": "foo.bar."}], 100)

    existing_cname_name = generate_record_name()
    existing_cname_fqdn = f"{existing_cname_name}.{ok_zone_name}"
    existing_cname = create_recordset(shared_zone_test_context.ok_zone, existing_cname_name, "CNAME", [{"cname": "test."}], 100)

    good_record_fqdn = generate_record_name(ok_zone_name)
    batch_change_input = {
        "changes": [
            # valid change
            get_change_MX_json(good_record_fqdn),

            # input validation failures
            get_change_MX_json(f"bad-ttl-and-invalid-name$.{ok_zone_name}", ttl=29),
            get_change_MX_json(f"bad-exchange.{ok_zone_name}", exchange="foo$.bar."),
            get_change_MX_json(f"mx.{ip4_zone_name}"),

            # zone discovery failures
            get_change_MX_json(f"no.subzone.{ok_zone_name}"),
            get_change_MX_json("no.zone.at.all."),

            # context validation failures
            get_change_CNAME_json(f"cname-duplicate.{ok_zone_name}"),
            get_change_MX_json(f"cname-duplicate.{ok_zone_name}"),
            get_change_MX_json(existing_mx_fqdn),
            get_change_MX_json(existing_cname_fqdn),
            get_change_MX_json(f"user-add-unauthorized.{dummy_zone_name}")
        ]
    }

    to_create = [existing_mx, existing_cname]
    to_delete = []
    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, "Complete"))

        response = client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name=good_record_fqdn, record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."})

        # ttl, domain name, record data
        assert_failed_change_in_error_response(response[1], input_name=f"bad-ttl-and-invalid-name$.{ok_zone_name}", ttl=29, record_type="MX",
                                               record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               f'Invalid domain name: "bad-ttl-and-invalid-name$.{ok_zone_name}", '
                                                               "valid domain names must be letters, numbers, underscores, and hyphens, joined by dots, and terminated with a dot."])
        assert_failed_change_in_error_response(response[2], input_name=f"bad-exchange.{ok_zone_name}", record_type="MX",
                                               record_data={"preference": 1, "exchange": "foo$.bar."},
                                               error_messages=['Invalid domain name: "foo$.bar.", valid domain names must be letters, numbers, underscores, and hyphens, '
                                                               'joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[3], input_name=f"mx.{ip4_zone_name}", record_type="MX",
                                               record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=[f'Invalid Record Type In Reverse Zone: record with name "mx.{ip4_zone_name}" and type "MX" is not allowed in a reverse zone.'])

        # zone discovery failures
        assert_failed_change_in_error_response(response[4], input_name=f"no.subzone.{ok_zone_name}", record_type="MX",
                                               record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=[f'Zone Discovery Failed: zone for "no.subzone.{ok_zone_name}" does not exist in VinylDNS. '
                                                               f'If zone exists, then it must be connected to in VinylDNS.'])
        assert_failed_change_in_error_response(response[5], input_name="no.zone.at.all.", record_type="MX",
                                               record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=['Zone Discovery Failed: zone for "no.zone.at.all." does not exist in VinylDNS. '
                                                               'If zone exists, then it must be connected to in VinylDNS.'])

        # context validations: cname duplicate
        assert_failed_change_in_error_response(response[6], input_name=f"cname-duplicate.{ok_zone_name}", record_type="CNAME",
                                               record_data="test.com.",
                                               error_messages=[f"Record Name \"cname-duplicate.{ok_zone_name}\" Not Unique In Batch Change: "
                                                               f"cannot have multiple \"CNAME\" records with the same name."])

        # context validations: conflicting recordsets, unauthorized error
        assert_successful_change_in_error_response(response[8], input_name=existing_mx_fqdn, record_type="MX",
                                               record_data={"preference": 1, "exchange": "foo.bar."})
        assert_failed_change_in_error_response(response[9], input_name=existing_cname_fqdn, record_type="MX",
                                               record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=["CNAME Conflict: CNAME record names must be unique. "
                                                               f"Existing record with name \"{existing_cname_fqdn}\" and type \"CNAME\" conflicts with this record."])
        assert_failed_change_in_error_response(response[10], input_name=f"user-add-unauthorized.{dummy_zone_name}", record_type="MX",
                                               record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=[f"User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes."])
    finally:
        clear_recordset_list(to_delete, client)


def test_mx_recordtype_update_delete_checks(shared_zone_test_context):
    """
    Test all update and delete validations performed on MX records submitted in batch changes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    dummy_zone = shared_zone_test_context.dummy_zone
    dummy_zone_name = shared_zone_test_context.dummy_zone["name"]

    dummy_group_name = shared_zone_test_context.dummy_group["name"]
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    ip4_zone_name = shared_zone_test_context.classless_base_zone["name"]

    rs_delete_name = generate_record_name()
    rs_delete_fqdn = rs_delete_name + f".{ok_zone_name}"
    rs_delete_ok = create_recordset(ok_zone, rs_delete_name, "MX", [{"preference": 1, "exchange": "foo.bar."}], 200)

    rs_update_name = generate_record_name()
    rs_update_fqdn = rs_update_name + f".{ok_zone_name}"
    rs_update_ok = create_recordset(ok_zone, rs_update_name, "MX", [{"preference": 1, "exchange": "foo.bar."}], 200)

    rs_delete_dummy_name = generate_record_name()
    rs_delete_dummy_fqdn = rs_delete_dummy_name + f".{dummy_zone_name}"
    rs_delete_dummy = create_recordset(dummy_zone, rs_delete_dummy_name, "MX", [{"preference": 1, "exchange": "foo.bar."}], 200)

    rs_update_dummy_name = generate_record_name()
    rs_update_dummy_fqdn = rs_update_dummy_name + f".{dummy_zone_name}"
    rs_update_dummy = create_recordset(dummy_zone, rs_update_dummy_name, "MX", [{"preference": 1, "exchange": "foo.bar."}], 200)

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            # valid changes
            get_change_MX_json(rs_delete_fqdn, change_type="DeleteRecordSet"),
            get_change_MX_json(rs_update_fqdn, change_type="DeleteRecordSet"),
            get_change_MX_json(rs_update_fqdn, ttl=300),
            get_change_MX_json(f"delete-nonexistent.{ok_zone_name}", change_type="DeleteRecordSet"),
            get_change_MX_json(f"update-nonexistent.{ok_zone_name}", change_type="DeleteRecordSet"),

            # input validations failures
            get_change_MX_json(f"invalid-name$.{ok_zone_name}", change_type="DeleteRecordSet"),
            get_change_MX_json(f"delete.{ok_zone_name}", ttl=29),
            get_change_MX_json(f"bad-exchange.{ok_zone_name}", exchange="foo$.bar."),
            get_change_MX_json(f"mx.{ip4_zone_name}"),

            # zone discovery failures
            get_change_MX_json("no.zone.at.all.", change_type="DeleteRecordSet"),

            # context validation failures
            get_change_MX_json(f"update-nonexistent.{ok_zone_name}", preference=1000, exchange="foo.bar."),
            get_change_MX_json(rs_delete_dummy_fqdn, change_type="DeleteRecordSet"),
            get_change_MX_json(rs_update_dummy_fqdn, preference=1000, exchange="foo.bar."),
            get_change_MX_json(rs_update_dummy_fqdn, change_type="DeleteRecordSet")
        ]
    }

    to_create = [rs_delete_ok, rs_update_ok, rs_delete_dummy, rs_update_dummy]
    to_delete = []

    try:
        for rs in to_create:
            if rs["zoneId"] == dummy_zone["id"]:
                create_client = dummy_client
            else:
                create_client = ok_client

            create_rs = create_client.create_recordset(rs, status=202)
            to_delete.append(create_client.wait_until_recordset_change_status(create_rs, "Complete"))

        # Confirm that record set doesn't already exist
        ok_client.get_recordset(ok_zone["id"], "delete-nonexistent", status=404)

        response = ok_client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name=rs_delete_fqdn, record_type="MX", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], input_name=rs_update_fqdn, record_type="MX", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[2], ttl=300, input_name=rs_update_fqdn, record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."})
        assert_successful_change_in_error_response(response[3], input_name=f"delete-nonexistent.{ok_zone_name}", record_type="MX",
                                                   record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[4], input_name=f"update-nonexistent.{ok_zone_name}", record_type="MX",
                                                   record_data=None, change_type="DeleteRecordSet")

        # input validations failures: invalid input name, reverse zone error, invalid ttl
        assert_failed_change_in_error_response(response[5], input_name=f"invalid-name$.{ok_zone_name}", record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."},
                                               change_type="DeleteRecordSet",
                                               error_messages=[f'Invalid domain name: "invalid-name$.{ok_zone_name}", valid domain names must be letters, '
                                                               f'numbers, underscores, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[6], input_name=f"delete.{ok_zone_name}", ttl=29, record_type="MX",
                                               record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.'])
        assert_failed_change_in_error_response(response[7], input_name=f"bad-exchange.{ok_zone_name}", record_type="MX",
                                               record_data={"preference": 1, "exchange": "foo$.bar."},
                                               error_messages=['Invalid domain name: "foo$.bar.", valid domain names must be letters, numbers, '
                                                               'underscores, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[8], input_name=f"mx.{ip4_zone_name}", record_type="MX",
                                               record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=[f'Invalid Record Type In Reverse Zone: record with name "mx.{ip4_zone_name}" '
                                                               f'and type "MX" is not allowed in a reverse zone.'])

        # zone discovery failure
        assert_failed_change_in_error_response(response[9], input_name="no.zone.at.all.", record_type="MX",
                                               record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Zone Discovery Failed: zone for \"no.zone.at.all.\" does not exist in VinylDNS. "
                                                               "If zone exists, then it must be connected to in VinylDNS."])

        # context validation failures: record does not exist, not authorized
        assert_successful_change_in_error_response(response[10], input_name=f"update-nonexistent.{ok_zone_name}", record_type="MX",
                                                   record_data={"preference": 1000, "exchange": "foo.bar."})
        assert_failed_change_in_error_response(response[11], input_name=rs_delete_dummy_fqdn, record_type="MX",
                                               record_data=None, change_type="DeleteRecordSet",
                                               error_messages=[f"User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes."])
        assert_failed_change_in_error_response(response[12], input_name=rs_update_dummy_fqdn, record_type="MX",
                                               record_data={"preference": 1000, "exchange": "foo.bar."},
                                               error_messages=[f"User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes."])
        assert_failed_change_in_error_response(response[13], input_name=rs_update_dummy_fqdn, record_type="MX",
                                               record_data=None, change_type="DeleteRecordSet",
                                               error_messages=[f"User \"ok\" is not authorized. Contact zone owner group: {dummy_group_name} at test@test.com to make DNS changes."])
    finally:
        # Clean up updates
        dummy_deletes = [rs for rs in to_delete if rs["zone"]["id"] == dummy_zone["id"]]
        ok_deletes = [rs for rs in to_delete if rs["zone"]["id"] != dummy_zone["id"]]
        clear_recordset_list(dummy_deletes, dummy_client)
        clear_recordset_list(ok_deletes, ok_client)


def test_create_batch_change_does_not_save_owner_group_id_for_non_shared_zone(shared_zone_test_context):
    """
    Test successfully creating a batch change with owner group ID doesn't save value for records in non-shared zone
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    ok_group = shared_zone_test_context.ok_group
    ok_zone_name = shared_zone_test_context.ok_zone["name"]

    update_name = generate_record_name()
    update_fqdn = update_name + f".{ok_zone_name}"
    update_rs = create_recordset(ok_zone, update_name, "A", [{"address": "127.0.0.1"}], 300)

    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(f"no-owner-group-id.{ok_zone_name}", address="4.3.2.1"),
            get_change_A_AAAA_json(update_fqdn, address="1.2.3.4"),
            get_change_A_AAAA_json(update_fqdn, change_type="DeleteRecordSet")
        ],
        "ownerGroupId": ok_group["id"]
    }
    to_delete = []

    try:
        create_result = ok_client.create_recordset(update_rs, status=202)
        to_delete.append(ok_client.wait_until_recordset_change_status(create_result, "Complete"))

        result = ok_client.create_batch_change(batch_change_input, status=202)
        completed_batch = ok_client.wait_until_batch_change_completed(result)

        assert_that(completed_batch["ownerGroupId"], is_(batch_change_input["ownerGroupId"]))

        to_delete = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"]]

        assert_change_success(result["changes"], zone=ok_zone, index=0, record_name="no-owner-group-id",
                              input_name=f"no-owner-group-id.{ok_zone_name}", record_data="4.3.2.1")
        assert_change_success(result["changes"], zone=ok_zone, index=1, record_name=update_name,
                              input_name=update_fqdn, record_data="1.2.3.4")
        assert_change_success(result["changes"], zone=ok_zone, index=2, record_name=update_name,
                              input_name=update_fqdn, change_type="DeleteRecordSet", record_data=None)

        for (zoneId, recordSetId) in to_delete:
            get_recordset = ok_client.get_recordset(zoneId, recordSetId, status=200)
            assert_that(get_recordset["recordSet"], is_not(has_key("ownerGroupId")))
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, ok_client)


def test_create_batch_change_for_shared_zone_owner_group_applied_logic(shared_zone_test_context):
    """
    Test successfully creating a batch change with owner group ID in shared zone succeeds and sets owner group ID
    on creates and only updates without a pre-existing owner group ID
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone
    shared_zone_name = shared_zone_test_context.shared_zone["name"]
    shared_record_group = shared_zone_test_context.shared_record_group

    without_group_name = generate_record_name()
    without_group_fqdn = f"{without_group_name}.{shared_zone_name}"
    update_rs_without_owner_group = create_recordset(shared_zone, without_group_name, "A", [{"address": "127.0.0.1"}], 300)

    with_group_name = generate_record_name()
    with_group_fqdn = f"{with_group_name}.{shared_zone_name}"
    update_rs_with_owner_group = create_recordset(shared_zone, with_group_name, "A", [{"address": "127.0.0.1"}], 300, shared_record_group["id"])

    create_name = generate_record_name()
    create_fqdn = f"{create_name}.{shared_zone_name}"
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(create_fqdn, address="4.3.2.1"),
            get_change_A_AAAA_json(without_group_fqdn, address="1.2.3.4"),
            get_change_A_AAAA_json(without_group_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(with_group_fqdn, address="1.2.3.4"),
            get_change_A_AAAA_json(with_group_fqdn, change_type="DeleteRecordSet")
        ],
        "ownerGroupId": "shared-zone-group"
    }
    to_delete = []

    try:
        # Create first record for updating and verify that owner group ID is not set
        create_result = shared_client.create_recordset(update_rs_without_owner_group, status=202)
        to_delete.append(shared_client.wait_until_recordset_change_status(create_result, "Complete"))

        create_result = shared_client.get_recordset(create_result["recordSet"]["zoneId"], create_result["recordSet"]["id"], status=200)
        assert_that(create_result["recordSet"], is_not(has_key("ownerGroupId")))

        # Create second record for updating and verify that owner group ID is set
        create_result = shared_client.create_recordset(update_rs_with_owner_group, status=202)
        to_delete.append(shared_client.wait_until_recordset_change_status(create_result, "Complete"))

        create_result = shared_client.get_recordset(create_result["recordSet"]["zoneId"], create_result["recordSet"]["id"], status=200)
        assert_that(create_result["recordSet"]["ownerGroupId"], is_(shared_record_group["id"]))

        # Create batch
        result = shared_client.create_batch_change(batch_change_input, status=202)
        completed_batch = shared_client.wait_until_batch_change_completed(result)

        assert_that(completed_batch["ownerGroupId"], is_(batch_change_input["ownerGroupId"]))

        to_delete = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"]]

        assert_that(result["ownerGroupId"], is_("shared-zone-group"))
        assert_change_success(result["changes"], zone=shared_zone, index=0, record_name=create_name, input_name=create_fqdn, record_data="4.3.2.1")
        assert_change_success(result["changes"], zone=shared_zone, index=1, record_name=without_group_name, input_name=without_group_fqdn, record_data="1.2.3.4")
        assert_change_success(result["changes"], zone=shared_zone, index=2, record_name=without_group_name, input_name=without_group_fqdn, change_type="DeleteRecordSet", record_data=None)
        assert_change_success(result["changes"], zone=shared_zone, index=3, record_name=with_group_name, input_name=with_group_fqdn, record_data="1.2.3.4")
        assert_change_success(result["changes"], zone=shared_zone, index=4, record_name=with_group_name, input_name=with_group_fqdn, change_type="DeleteRecordSet", record_data=None)

        for (zoneId, recordSetId) in to_delete:
            get_recordset = shared_client.get_recordset(zoneId, recordSetId, status=200)
            if get_recordset["recordSet"]["name"] == with_group_name:
                assert_that(get_recordset["recordSet"]["ownerGroupId"], is_(shared_record_group["id"]))
            else:
                assert_that(get_recordset["recordSet"]["ownerGroupId"], is_(batch_change_input["ownerGroupId"]))
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, shared_client)


def test_create_batch_change_for_shared_zone_with_invalid_owner_group_id_fails(shared_zone_test_context):
    """
    Test creating a batch change with invalid owner group ID fails
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    shared_zone_name = shared_zone_test_context.shared_zone["name"]

    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(f"no-owner-group-id.{shared_zone_name}", address="4.3.2.1")
        ],
        "ownerGroupId": "non-existent-owner-group-id"
    }

    errors = shared_client.create_batch_change(batch_change_input, status=400)["errors"]
    assert_that(errors, contains_exactly('Group with ID "non-existent-owner-group-id" was not found'))


def test_create_batch_change_for_shared_zone_with_unauthorized_owner_group_id_fails(shared_zone_test_context):
    """
    Test creating a batch change with unauthorized owner group ID fails
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_group = shared_zone_test_context.ok_group
    shared_zone_name = shared_zone_test_context.shared_zone["name"]

    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(f"no-owner-group-id.{shared_zone_name}", address="4.3.2.1")
        ],
        "ownerGroupId": ok_group["id"]
    }

    errors = shared_client.create_batch_change(batch_change_input, status=400)["errors"]
    assert_that(errors, contains_exactly('User "sharedZoneUser" must be a member of group "' + ok_group["id"] + '" to apply this group to batch changes.'))


def test_create_batch_change_validation_with_owner_group_id(shared_zone_test_context):
    """
    Test creating a batch change should properly set owner group ID in the following circumstances:
    - create in shared zone
    - update in shared zone without existing owner group ID

    Owner group ID will be ignored in the following circumstances:
    - create in private zone
    - update in private zone
    - update in shared zone with pre-existing owner group ID
    - delete in either private or shared zone
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    shared_group = shared_zone_test_context.shared_record_group
    ok_group = shared_zone_test_context.ok_group
    shared_zone = shared_zone_test_context.shared_zone
    ok_zone = shared_zone_test_context.ok_zone
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    shared_zone_name = shared_zone_test_context.shared_zone["name"]

    # record sets to setup
    private_update_name = generate_record_name()
    private_update_fqdn = f"{private_update_name}.{ok_zone_name}"
    private_update = create_recordset(ok_zone, private_update_name, "A", [{"address": "1.1.1.1"}], 200)

    shared_update_no_group_name = generate_record_name()
    shared_update_no_group_fqdn = f"{shared_update_no_group_name}.{shared_zone_name}"
    shared_update_no_owner_group = create_recordset(shared_zone, shared_update_no_group_name, "A", [{"address": "1.1.1.1"}], 200)

    shared_update_group_name = generate_record_name()
    shared_update_group_fqdn = f"{shared_update_group_name}.{shared_zone_name}"
    shared_update_existing_owner_group = create_recordset(shared_zone, shared_update_group_name, "A", [{"address": "1.1.1.1"}], 200, shared_group["id"])

    private_delete_name = generate_record_name()
    private_delete_fqdn = f"{private_delete_name}.{ok_zone_name}"
    private_delete = create_recordset(ok_zone, private_delete_name, "A", [{"address": "1.1.1.1"}], 200)

    shared_delete_name = generate_record_name()
    shared_delete_fqdn = f"{shared_delete_name}.{shared_zone_name}"
    shared_delete = create_recordset(shared_zone, shared_delete_name, "A", [{"address": "1.1.1.1"}], 200)

    to_delete_ok = {}
    to_delete_shared = {}

    private_create_name = generate_record_name()
    private_create_fqdn = f"{private_create_name}.{ok_zone_name}"
    shared_create_name = generate_record_name()
    shared_create_fqdn = f"{shared_create_name}.{shared_zone_name}"
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(private_create_fqdn),
            get_change_A_AAAA_json(shared_create_fqdn),
            get_change_A_AAAA_json(private_update_fqdn, ttl=300),
            get_change_A_AAAA_json(private_update_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(shared_update_no_group_fqdn, ttl=300),
            get_change_A_AAAA_json(shared_update_no_group_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(shared_update_group_fqdn, ttl=300),
            get_change_A_AAAA_json(shared_update_group_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(private_delete_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(shared_delete_fqdn, change_type="DeleteRecordSet")
        ],
        "ownerGroupId": ok_group["id"]
    }

    try:
        for rs in [private_update, private_delete]:
            create_rs = ok_client.create_recordset(rs, status=202)
            ok_client.wait_until_recordset_change_status(create_rs, "Complete")

        for rs in [shared_update_no_owner_group, shared_update_existing_owner_group, shared_delete]:
            create_rs = shared_client.create_recordset(rs, status=202)
            shared_client.wait_until_recordset_change_status(create_rs, "Complete")

        result = ok_client.create_batch_change(batch_change_input, status=202)
        completed_batch = ok_client.wait_until_batch_change_completed(result)

        assert_that(completed_batch["ownerGroupId"], is_(ok_group["id"]))

        # set here because multiple items in the batch combine to one RS
        record_set_list = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"] if
                           private_delete_name not in change["recordName"] and change["zoneId"] == ok_zone["id"]]
        to_delete_ok = set(record_set_list)

        record_set_list = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"] if
                           shared_delete_name not in change["recordName"] and change["zoneId"] == shared_zone["id"]]
        to_delete_shared = set(record_set_list)

        assert_change_success(completed_batch["changes"], zone=ok_zone, index=0,
                              record_name=private_create_name,
                              input_name=private_create_fqdn, record_data="1.1.1.1")
        assert_change_success(completed_batch["changes"], zone=shared_zone, index=1,
                              record_name=shared_create_name,
                              input_name=shared_create_fqdn, record_data="1.1.1.1")
        assert_change_success(completed_batch["changes"], zone=ok_zone, index=2,
                              record_name=private_update_name,
                              input_name=private_update_fqdn, record_data="1.1.1.1", ttl=300)
        assert_change_success(completed_batch["changes"], zone=ok_zone, index=3,
                              record_name=private_update_name,
                              input_name=private_update_fqdn, record_data=None,
                              change_type="DeleteRecordSet")
        assert_change_success(completed_batch["changes"], zone=shared_zone, index=4,
                              record_name=shared_update_no_group_name,
                              input_name=shared_update_no_group_fqdn, record_data="1.1.1.1",
                              ttl=300)
        assert_change_success(completed_batch["changes"], zone=shared_zone, index=5,
                              record_name=shared_update_no_group_name,
                              input_name=shared_update_no_group_fqdn, record_data=None,
                              change_type="DeleteRecordSet")
        assert_change_success(completed_batch["changes"], zone=shared_zone, index=6,
                              record_name=shared_update_group_name,
                              input_name=shared_update_group_fqdn,
                              record_data="1.1.1.1", ttl=300)
        assert_change_success(completed_batch["changes"], zone=shared_zone, index=7,
                              record_name=shared_update_group_name,
                              input_name=shared_update_group_fqdn, record_data=None,
                              change_type="DeleteRecordSet")
        assert_change_success(completed_batch["changes"], zone=ok_zone, index=8,
                              record_name=private_delete_name,
                              input_name=private_delete_fqdn, record_data=None,
                              change_type="DeleteRecordSet")
        assert_change_success(completed_batch["changes"], zone=shared_zone, index=9,
                              record_name=shared_delete_name,
                              input_name=shared_delete_fqdn, record_data=None,
                              change_type="DeleteRecordSet")

        # verify record set owner group
        for result_rs in to_delete_ok:
            rs_result = ok_client.get_recordset(result_rs[0], result_rs[1], status=200)
            assert_that(rs_result["recordSet"], is_not(has_key("ownerGroupId")))

        for result_rs in to_delete_shared:
            rs_result = shared_client.get_recordset(result_rs[0], result_rs[1], status=200)
            if rs_result["recordSet"]["name"] == shared_update_group_name:
                assert_that(rs_result["recordSet"]["ownerGroupId"], is_(shared_group["id"]))
            else:
                assert_that(rs_result["recordSet"]["ownerGroupId"], is_(ok_group["id"]))
    finally:
        for tup in to_delete_ok:
            delete_result = ok_client.delete_recordset(tup[0], tup[1], status=202)
            ok_client.wait_until_recordset_change_status(delete_result, "Complete")

        for tup in to_delete_shared:
            delete_result = shared_client.delete_recordset(tup[0], tup[1], status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_create_batch_change_validation_without_owner_group_id(shared_zone_test_context):
    """
    Test creating a batch change without owner group ID should validate changes properly
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    shared_group = shared_zone_test_context.shared_record_group
    shared_zone = shared_zone_test_context.shared_zone
    ok_zone = shared_zone_test_context.ok_zone
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    shared_zone_name = shared_zone_test_context.shared_zone["name"]

    # record sets to setup
    private_update_name = generate_record_name()
    private_update_fqdn = f"{private_update_name}.{ok_zone_name}"
    private_update = create_recordset(ok_zone, private_update_name, "A", [{"address": "1.1.1.1"}], 200)

    shared_update_no_group_name = generate_record_name()
    shared_update_no_group_fqdn = f"{shared_update_no_group_name}.{shared_zone_name}"
    shared_update_no_owner_group = create_recordset(shared_zone, shared_update_no_group_name, "A", [{"address": "1.1.1.1"}], 200)

    shared_update_group_name = generate_record_name()
    shared_update_group_fqdn = f"{shared_update_group_name}.{shared_zone_name}"
    shared_update_existing_owner_group = create_recordset(shared_zone, shared_update_group_name, "A", [{"address": "1.1.1.1"}], 200, shared_group["id"])

    private_delete_name = generate_record_name()
    private_delete_fqdn = private_delete_name + f".{ok_zone_name}"
    private_delete = create_recordset(ok_zone, private_delete_name, "A", [{"address": "1.1.1.1"}], 200)

    shared_delete_name = generate_record_name()
    shared_delete_fqdn = f"{shared_delete_name}.{shared_zone_name}"
    shared_delete = create_recordset(shared_zone, shared_delete_name, "A", [{"address": "1.1.1.1"}], 200)

    to_delete_ok = []
    to_delete_shared = []

    private_create_name = generate_record_name()
    private_create_fqdn = f"{private_create_name}.{ok_zone_name}"
    shared_create_name = generate_record_name()
    shared_create_fqdn = f"{shared_create_name}.{shared_zone_name}"
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(private_create_fqdn),
            get_change_A_AAAA_json(shared_create_fqdn),
            get_change_A_AAAA_json(private_update_fqdn, ttl=300),
            get_change_A_AAAA_json(private_update_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(shared_update_no_group_fqdn, ttl=300),
            get_change_A_AAAA_json(shared_update_no_group_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(shared_update_group_fqdn, ttl=300),
            get_change_A_AAAA_json(shared_update_group_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(private_delete_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(shared_delete_fqdn, change_type="DeleteRecordSet")
        ]
    }

    try:
        for rs in [private_update, private_delete]:
            create_rs = ok_client.create_recordset(rs, status=202)
            to_delete_ok.append(ok_client.wait_until_recordset_change_status(create_rs, "Complete")["recordSet"]["id"])

        for rs in [shared_update_no_owner_group, shared_update_existing_owner_group, shared_delete]:
            create_rs = shared_client.create_recordset(rs, status=202)
            to_delete_shared.append(shared_client.wait_until_recordset_change_status(create_rs, "Complete")["recordSet"]["id"])

        response = ok_client.create_batch_change(batch_change_input, status=400)

        assert_successful_change_in_error_response(response[0], input_name=private_create_fqdn)
        assert_failed_change_in_error_response(response[1], input_name=shared_create_fqdn,
                                               error_messages=[f"Zone \"{shared_zone_name}\" is a shared zone, so owner group ID must be specified for record \"{shared_create_name}\"."])
        assert_successful_change_in_error_response(response[2], input_name=private_update_fqdn, ttl=300)
        assert_successful_change_in_error_response(response[3], change_type="DeleteRecordSet", input_name=private_update_fqdn)
        assert_failed_change_in_error_response(response[4], input_name=shared_update_no_group_fqdn,
                                               error_messages=[f"Zone \"{shared_zone_name}\" is a shared zone, so owner group ID must be specified for record \"{shared_update_no_group_name}\"."],
                                               ttl=300)
        assert_successful_change_in_error_response(response[5], change_type="DeleteRecordSet", input_name=shared_update_no_group_fqdn)
        assert_successful_change_in_error_response(response[6], input_name=shared_update_group_fqdn, ttl=300)
        assert_successful_change_in_error_response(response[7], change_type="DeleteRecordSet", input_name=shared_update_group_fqdn)
        assert_successful_change_in_error_response(response[8], change_type="DeleteRecordSet", input_name=private_delete_fqdn)
        assert_successful_change_in_error_response(response[9], change_type="DeleteRecordSet", input_name=shared_delete_fqdn)
    finally:
        for rsId in to_delete_ok:
            delete_result = ok_client.delete_recordset(ok_zone["id"], rsId, status=202)
            ok_client.wait_until_recordset_change_status(delete_result, "Complete")

        for rsId in to_delete_shared:
            delete_result = shared_client.delete_recordset(shared_zone["id"], rsId, status=202)
            shared_client.wait_until_recordset_change_status(delete_result, "Complete")


def test_create_batch_delete_recordset_for_unassociated_user_in_owner_group_succeeds(shared_zone_test_context):
    """
    Test delete change in batch for a record in a shared zone for an unassociated user belonging to the record owner group succeeds
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    shared_zone_name = shared_zone_test_context.shared_zone["name"]

    shared_delete_name = generate_record_name()
    shared_delete_fqdn = f"{shared_delete_name}.{shared_zone_name}"
    shared_delete = create_recordset(shared_zone, shared_delete_name, "A", [{"address": "1.1.1.1"}], 200, shared_group["id"])
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(shared_delete_fqdn, change_type="DeleteRecordSet")
        ]
    }

    create_rs = shared_client.create_recordset(shared_delete, status=202)
    shared_client.wait_until_recordset_change_status(create_rs, "Complete")

    result = ok_client.create_batch_change(batch_change_input, status=202)
    completed_batch = ok_client.wait_until_batch_change_completed(result)

    assert_change_success(completed_batch["changes"], zone=shared_zone, index=0,
                          record_name=shared_delete_name,
                          input_name=shared_delete_fqdn, record_data=None,
                          change_type="DeleteRecordSet")


def test_create_batch_delete_recordset_for_unassociated_user_not_in_owner_group_fails(shared_zone_test_context):
    """
    Test delete change in batch for a record in a shared zone for an unassociated user not belonging to the record owner group fails
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    unassociated_client = shared_zone_test_context.unassociated_client
    shared_zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group
    shared_zone_name = shared_zone_test_context.shared_zone["name"]
    shared_group_name = shared_group["name"]
    create_rs = None

    shared_delete_name = generate_record_name()
    shared_delete_fqdn = f"{shared_delete_name}.{shared_zone_name}"
    shared_delete = create_recordset(shared_zone, shared_delete_name, "A", [{"address": "1.1.1.1"}], 200, shared_group["id"])

    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(shared_delete_fqdn, change_type="DeleteRecordSet")
        ]
    }

    try:
        create_rs = shared_client.create_recordset(shared_delete, status=202)
        shared_client.wait_until_recordset_change_status(create_rs, "Complete")

        response = unassociated_client.create_batch_change(batch_change_input, status=400)

        assert_failed_change_in_error_response(response[0], input_name=shared_delete_fqdn,
                                               change_type="DeleteRecordSet",
                                               error_messages=[f'User "list-group-user" is not authorized. Contact record owner group: '
                                                               f'{shared_group_name} at test@test.com to make DNS changes.'])
    finally:
        if create_rs:
            delete_rs = shared_client.delete_recordset(shared_zone["id"], create_rs["recordSet"]["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_rs, "Complete")


def test_create_batch_delete_recordset_for_zone_admin_not_in_owner_group_succeeds(shared_zone_test_context):
    """
    Test delete change in batch for a record in a shared zone for a zone admin not belonging to the record owner group succeeds
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone
    ok_group = shared_zone_test_context.ok_group
    shared_zone_name = shared_zone_test_context.shared_zone["name"]

    shared_delete_name = generate_record_name()
    shared_delete_fqdn = f"{shared_delete_name}.{shared_zone_name}"
    shared_delete = create_recordset(shared_zone, shared_delete_name, "A", [{"address": "1.1.1.1"}], 200, ok_group["id"])

    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(shared_delete_fqdn, change_type="DeleteRecordSet")
        ]
    }

    create_rs = ok_client.create_recordset(shared_delete, status=202)
    shared_client.wait_until_recordset_change_status(create_rs, "Complete")

    result = shared_client.create_batch_change(batch_change_input, status=202)
    completed_batch = shared_client.wait_until_batch_change_completed(result)

    assert_change_success(completed_batch["changes"], zone=shared_zone, index=0,
                          record_name=shared_delete_name,
                          input_name=shared_delete_fqdn, record_data=None,
                          change_type="DeleteRecordSet")


def test_create_batch_update_record_in_shared_zone_for_unassociated_user_in_owner_group_succeeds(
        shared_zone_test_context):
    """
    Test update change in batch for a record for a user belonging to record owner group succeeds
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone
    shared_record_group = shared_zone_test_context.shared_record_group
    shared_zone_name = shared_zone_test_context.shared_zone["name"]
    create_rs = None

    shared_update_name = generate_record_name()
    shared_update_fqdn = f"{shared_update_name}.{shared_zone_name}"
    shared_update = create_recordset(shared_zone, shared_update_name, "MX", [{"preference": 1, "exchange": "foo.bar."}], 200,
                                     shared_record_group["id"])

    batch_change_input = {
        "changes": [
            get_change_MX_json(shared_update_fqdn, ttl=300),
            get_change_MX_json(shared_update_fqdn, change_type="DeleteRecordSet")
        ]
    }

    try:
        create_rs = shared_client.create_recordset(shared_update, status=202)
        shared_client.wait_until_recordset_change_status(create_rs, "Complete")

        result = ok_client.create_batch_change(batch_change_input, status=202)
        completed_batch = ok_client.wait_until_batch_change_completed(result)

        assert_change_success(completed_batch["changes"], zone=shared_zone, index=0, record_name=shared_update_name,
                              ttl=300,
                              record_type="MX", input_name=shared_update_fqdn,
                              record_data={"preference": 1, "exchange": "foo.bar."})
        assert_change_success(completed_batch["changes"], zone=shared_zone, index=1, record_name=shared_update_name,
                              record_type="MX", input_name=shared_update_fqdn, record_data=None,
                              change_type="DeleteRecordSet")
    finally:
        if create_rs:
            delete_rs = shared_client.delete_recordset(shared_zone["id"], create_rs["recordSet"]["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_rs, "Complete")


def test_create_batch_with_global_acl_rule_applied_succeeds(shared_zone_test_context):
    """
    Test that a user with a relevant global acl rule can update forward and reverse records, regardless of their current ownership
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone
    ok_client = shared_zone_test_context.ok_vinyldns_client
    classless_base_zone = shared_zone_test_context.classless_base_zone
    create_a_rs = None
    create_ptr_rs = None
    dummy_group_id = shared_zone_test_context.dummy_group["id"]
    dummy_group_name = shared_zone_test_context.dummy_group["name"]
    ip4_prefix = shared_zone_test_context.ip4_classless_prefix
    shared_zone_name = shared_zone_test_context.shared_zone["name"]

    a_name = generate_record_name()
    a_fqdn = f"{a_name}.{shared_zone_name}"
    a_record = create_recordset(shared_zone, a_name, "A", [{"address": "1.1.1.1"}], 200, "shared-zone-group")

    ptr_record = create_recordset(classless_base_zone, "44", "PTR", [{"ptrdname": "foo."}], 200, None)

    batch_change_input = {
        "ownerGroupId": dummy_group_id,
        "changes": [
            get_change_A_AAAA_json(a_fqdn, record_type="A", ttl=200, address=f"{ip4_prefix}.44"),
            get_change_PTR_json(f"{ip4_prefix}.44", ptrdname=a_fqdn),
            get_change_A_AAAA_json(a_fqdn, record_type="A", address="1.1.1.1", change_type="DeleteRecordSet"),
            get_change_PTR_json(f"{ip4_prefix}.44", change_type="DeleteRecordSet")
        ]
    }

    try:
        create_a_rs = shared_client.create_recordset(a_record, status=202)
        shared_client.wait_until_recordset_change_status(create_a_rs, "Complete")

        create_ptr_rs = ok_client.create_recordset(ptr_record, status=202)
        ok_client.wait_until_recordset_change_status(create_ptr_rs, "Complete")

        result = dummy_client.create_batch_change(batch_change_input, status=202)
        completed_batch = dummy_client.wait_until_batch_change_completed(result)

        assert_change_success(completed_batch["changes"], zone=shared_zone, index=0,
                              record_name=a_name, ttl=200,
                              record_type="A", input_name=a_fqdn, record_data=f"{ip4_prefix}.44")
        assert_change_success(completed_batch["changes"], zone=classless_base_zone, index=1,
                              record_name="44",
                              record_type="PTR", input_name=f"{ip4_prefix}.44",
                              record_data=a_fqdn)
        assert_change_success(completed_batch["changes"], zone=shared_zone, index=2,
                              record_name=a_name, ttl=200,
                              record_type="A", input_name=a_fqdn, record_data=None,
                              change_type="DeleteRecordSet")
        assert_change_success(completed_batch["changes"], zone=classless_base_zone, index=3,
                              record_name="44",
                              record_type="PTR", input_name=f"{ip4_prefix}.44", record_data=None,
                              change_type="DeleteRecordSet")
    finally:
        if create_a_rs:
            retrieved = shared_client.get_recordset(shared_zone["id"], create_a_rs["recordSet"]["id"])
            retrieved_rs = retrieved["recordSet"]

            assert_that(retrieved_rs["ownerGroupId"], is_("shared-zone-group"))
            assert_that(retrieved_rs["ownerGroupName"], is_("testSharedZoneGroup"))

            delete_a_rs = shared_client.delete_recordset(shared_zone["id"], create_a_rs["recordSet"]["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_a_rs, "Complete")

        if create_ptr_rs:
            retrieved = dummy_client.get_recordset(shared_zone["id"], create_ptr_rs["recordSet"]["id"])
            retrieved_rs = retrieved["recordSet"]

            assert_that(retrieved_rs, is_not(has_key("ownerGroupId")))
            assert_that(retrieved_rs, is_not(has_key({dummy_group_name})))

            delete_ptr_rs = ok_client.delete_recordset(classless_base_zone["id"], create_ptr_rs["recordSet"]["id"],
                                                       status=202)
            ok_client.wait_until_recordset_change_status(delete_ptr_rs, "Complete")


def test_create_batch_with_irrelevant_global_acl_rule_applied_fails(shared_zone_test_context):
    """
    Test that a user with an irrelevant global acl rule cannot update an owned records
    """
    test_user_client = shared_zone_test_context.test_user_client
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone
    ip4_prefix = shared_zone_test_context.ip4_classless_prefix
    shared_zone_name = shared_zone_test_context.shared_zone["name"]

    create_a_rs = None

    a_name = generate_record_name()
    a_fqdn = f"{a_name}.{shared_zone_name}"
    a_record = create_recordset(shared_zone, a_name, "A", [{"address": "1.1.1.1"}], 200, "shared-zone-group")

    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(a_fqdn, record_type="A", address=f"{ip4_prefix}.45"),
            get_change_A_AAAA_json(a_fqdn, record_type="A", change_type="DeleteRecordSet"),
        ]
    }

    try:
        create_a_rs = shared_client.create_recordset(a_record, status=202)
        shared_client.wait_until_recordset_change_status(create_a_rs, "Complete")

        response = test_user_client.create_batch_change(batch_change_input, status=400)
        assert_failed_change_in_error_response(response[0], input_name=a_fqdn, record_type="A",
                                               change_type="Add", record_data=f"{ip4_prefix}.45",
                                               error_messages=['User "testuser" is not authorized. Contact record owner group: testSharedZoneGroup at email to make DNS changes.'])
    finally:
        if create_a_rs:
            delete_a_rs = shared_client.delete_recordset(shared_zone["id"], create_a_rs["recordSet"]["id"], status=202)
            shared_client.wait_until_recordset_change_status(delete_a_rs, "Complete")


@pytest.mark.manual_batch_review
def test_create_batch_with_zone_name_requiring_manual_review(shared_zone_test_context):
    """
    Confirm that individual changes matching zone names requiring review get correctly flagged for manual review
    """
    rejecter = shared_zone_test_context.support_user_client
    client = shared_zone_test_context.ok_vinyldns_client
    review_zone_name = shared_zone_test_context.requires_review_zone["name"]
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(f"add-test-batch.{review_zone_name}"),
            get_change_A_AAAA_json(f"update-test-batch.{review_zone_name}", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(f"update-test-batch.{review_zone_name}"),
            get_change_A_AAAA_json(f"delete-test-batch.{review_zone_name}", change_type="DeleteRecordSet")
        ],
        "ownerGroupId": shared_zone_test_context.ok_group["id"]
    }

    response = None

    try:
        response = client.create_batch_change(batch_change_input, status=202)
        get_batch = client.get_batch_change(response["id"])
        assert_that(get_batch["status"], is_("PendingReview"))
        assert_that(get_batch["approvalStatus"], is_("PendingReview"))
        for i in range(0, 3):
            assert_that(get_batch["changes"][i]["status"], is_("NeedsReview"))
            assert_that(get_batch["changes"][i]["validationErrors"][0]["errorType"], is_("RecordRequiresManualReview"))
    finally:
        # Clean up so data doesn't change
        if response:
            rejecter.reject_batch_change(response["id"], status=200)


def test_create_batch_delete_record_that_does_not_exists_completes(shared_zone_test_context):
    """
    Test delete record set completes for non-existent record
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone_name = shared_zone_test_context.ok_zone["name"]

    batch_change_input = {
        "comments": "test delete record failures",
        "changes": [
            get_change_A_AAAA_json(f"delete-non-existent-record.{ok_zone_name}", change_type="DeleteRecordSet")
        ]
    }

    response = client.create_batch_change(batch_change_input, status=202)
    get_batch = client.get_batch_change(response["id"])

    assert_that(get_batch["changes"][0]["systemMessage"], is_("This record does not exist. " +
                                                              "No further action is required."))

    assert_successful_change_in_error_response(response["changes"][0], input_name=f"delete-non-existent-record.{ok_zone_name}", record_data="1.1.1.1", change_type="DeleteRecordSet")


@pytest.mark.serial
def test_create_batch_delete_record_access_checks(shared_zone_test_context):
    """
    Test access for full-delete DeleteRecord (delete) and non-full-delete DeleteRecord (update)
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    dummy_group_id = shared_zone_test_context.dummy_group["id"]
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    ok_group_name = shared_zone_test_context.ok_group["name"]

    a_delete_acl = generate_acl_rule("Delete", groupId=dummy_group_id, recordMask=".*", recordTypes=["A"])
    txt_write_acl = generate_acl_rule("Write", groupId=dummy_group_id, recordMask=".*", recordTypes=["TXT"])

    a_update_name = generate_record_name()
    a_update_fqdn = a_update_name + f".{ok_zone_name}"
    a_update = create_recordset(ok_zone, a_update_name, "A", [{"address": "1.1.1.1"}])

    a_delete_name = generate_record_name()
    a_delete_fqdn = a_delete_name + f".{ok_zone_name}"
    a_delete = create_recordset(ok_zone, a_delete_name, "A", [{"address": "1.1.1.1"}])

    txt_update_name = generate_record_name()
    txt_update_fqdn = txt_update_name + f".{ok_zone_name}"
    txt_update = create_recordset(ok_zone, txt_update_name, "TXT", [{"text": "test"}])

    txt_delete_name = generate_record_name()
    txt_delete_fqdn = txt_delete_name + f".{ok_zone_name}"
    txt_delete = create_recordset(ok_zone, txt_delete_name, "TXT", [{"text": "test"}])

    batch_change_input = {
        "comments": "Testing DeleteRecord access levels",
        "changes": [
            get_change_A_AAAA_json(a_update_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(a_update_fqdn, address="4.5.6.7"),
            get_change_A_AAAA_json(a_delete_fqdn, change_type="DeleteRecordSet"),
            get_change_TXT_json(txt_update_fqdn, change_type="DeleteRecordSet"),
            get_change_TXT_json(txt_update_fqdn, text="updated text"),
            get_change_TXT_json(txt_delete_fqdn, change_type="DeleteRecordSet")
        ]
    }

    to_delete = []
    try:
        add_ok_acl_rules(shared_zone_test_context, [a_delete_acl, txt_write_acl])

        for create_json in [a_update, a_delete, txt_update, txt_delete]:
            create_result = ok_client.create_recordset(create_json, status=202)
            to_delete.append(ok_client.wait_until_recordset_change_status(create_result, "Complete"))

        response = dummy_client.create_batch_change(batch_change_input, status=400)

        assert_successful_change_in_error_response(response[0], input_name=a_update_fqdn, record_data="1.1.1.1", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], input_name=a_update_fqdn, record_data="4.5.6.7")
        assert_successful_change_in_error_response(response[2], input_name=a_delete_fqdn, record_data="1.1.1.1", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[3], input_name=txt_update_fqdn, record_type="TXT", record_data="test", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[4], input_name=txt_update_fqdn, record_type="TXT", record_data="updated text")
        assert_failed_change_in_error_response(response[5], input_name=txt_delete_fqdn, record_type="TXT", record_data="test", change_type="DeleteRecordSet",
                                               error_messages=[f'User "dummy" is not authorized. Contact zone owner group: {ok_group_name} at test@test.com to make DNS changes.'])
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        clear_recordset_list(to_delete, ok_client)


@pytest.mark.skip_production
def test_create_batch_multi_record_update_succeeds(shared_zone_test_context):
    """
    Test record sets with multiple records can be added, updated and deleted in batch (relies on skip-prod)
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    ok_zone_name = shared_zone_test_context.ok_zone["name"]

    # record sets to setup
    a_update_record_set_name = generate_record_name()
    a_update_record_set_fqdn = a_update_record_set_name + f".{ok_zone_name}"
    a_update_record_set = create_recordset(ok_zone, a_update_record_set_name, "A", [{"address": "1.1.1.1"}, {"address": "1.1.1.2"}], 200)

    txt_update_record_set_name = generate_record_name()
    txt_update_record_set_fqdn = txt_update_record_set_name + f".{ok_zone_name}"
    txt_update_record_set = create_recordset(ok_zone, txt_update_record_set_name, "TXT", [{"text": "hello"}, {"text": "again"}], 200)

    a_update_record_full_name = generate_record_name()
    a_update_record_full_fqdn = a_update_record_full_name + f".{ok_zone_name}"
    a_update_record_full = create_recordset(ok_zone, a_update_record_full_name, "A", [{"address": "1.1.1.1"}, {"address": "1.1.1.2"}], 200)

    txt_update_record_full_name = generate_record_name()
    txt_update_record_full_fqdn = txt_update_record_full_name + f".{ok_zone_name}"
    txt_update_record_full = create_recordset(ok_zone, txt_update_record_full_name, "TXT", [{"text": "hello"}, {"text": "again"}], 200)

    a_update_record_name = generate_record_name()
    a_update_record_fqdn = a_update_record_name + f".{ok_zone_name}"
    a_update_record = create_recordset(ok_zone, a_update_record_name, "A", [{"address": "1.1.1.1"}, {"address": "1.1.1.2"}], 200)

    txt_update_record_name = generate_record_name()
    txt_update_record_fqdn = txt_update_record_name + f".{ok_zone_name}"
    txt_update_record = create_recordset(ok_zone, txt_update_record_name, "TXT", [{"text": "hello"}, {"text": "again"}], 200)

    a_update_record_only_name = generate_record_name()
    a_update_record_only_fqdn = a_update_record_only_name + f".{ok_zone_name}"
    a_update_record_only = create_recordset(ok_zone, a_update_record_only_name, "A", [{"address": "1.1.1.1"}, {"address": "1.1.1.2"}], 200)

    txt_update_record_only_name = generate_record_name()
    txt_update_record_only_fqdn = txt_update_record_only_name + f".{ok_zone_name}"
    txt_update_record_only = create_recordset(ok_zone, txt_update_record_only_name, "TXT", [{"text": "hello"}, {"text": "again"}], 200)

    a_delete_record_set_name = generate_record_name()
    a_delete_record_set_fqdn = a_delete_record_set_name + f".{ok_zone_name}"
    a_delete_record_set = create_recordset(ok_zone, a_delete_record_set_name, "A", [{"address": "1.1.1.1"}, {"address": "1.1.1.2"}], 200)

    txt_delete_record_set_name = generate_record_name()
    txt_delete_record_set_fqdn = txt_delete_record_set_name + f".{ok_zone_name}"
    txt_delete_record_set = create_recordset(ok_zone, txt_delete_record_set_name, "TXT", [{"text": "hello"}, {"text": "again"}], 200)

    a_delete_record_name = generate_record_name()
    a_delete_record_fqdn = a_delete_record_name + f".{ok_zone_name}"
    a_delete_record = create_recordset(ok_zone, a_delete_record_name, "A", [{"address": "1.1.1.1"}, {"address": "1.1.1.2"}], 200)

    txt_delete_record_name = generate_record_name()
    txt_delete_record_fqdn = txt_delete_record_name + f".{ok_zone_name}"
    txt_delete_record = create_recordset(ok_zone, txt_delete_record_name, "TXT", [{"text": "hello"}, {"text": "again"}], 200)

    cname_delete_record_name = generate_record_name()
    cname_delete_record_fqdn = cname_delete_record_name + f".{ok_zone_name}"
    cname_delete_record = create_recordset(ok_zone, cname_delete_record_name, "CNAME", [{"cname": "cAsEiNSeNsItIve.cNaMe."}], 200)

    a_delete_record_and_record_set_name = generate_record_name()
    a_delete_record_and_record_set_fqdn = a_delete_record_and_record_set_name + f".{ok_zone_name}"
    a_delete_record_and_record_set = create_recordset(ok_zone, a_delete_record_and_record_set_name, "A", [{"address": "1.1.1.1"}, {"address": "1.1.1.2"}], 200)

    txt_delete_record_and_record_set_name = generate_record_name()
    txt_delete_record_and_record_set_fqdn = txt_delete_record_and_record_set_name + f".{ok_zone_name}"
    txt_delete_record_and_record_set = create_recordset(ok_zone, txt_delete_record_and_record_set_name, "TXT", [{"text": "hello"}, {"text": "again"}], 200)

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            ## Updates
            # Add + DeleteRRSet
            get_change_A_AAAA_json(a_update_record_set_fqdn, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(a_update_record_set_fqdn, address="1.2.3.4"),
            get_change_A_AAAA_json(a_update_record_set_fqdn, address="4.5.6.7"),

            get_change_TXT_json(txt_update_record_set_fqdn, change_type="DeleteRecordSet"),
            get_change_TXT_json(txt_update_record_set_fqdn, text="some-multi-text"),
            get_change_TXT_json(txt_update_record_set_fqdn, text="more-multi-text"),

            # Add + DeleteRecord (full delete)
            get_change_A_AAAA_json(a_update_record_full_fqdn, address="1.1.1.1", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(a_update_record_full_fqdn, address="1.1.1.2", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(a_update_record_full_fqdn, address="1.2.3.4"),
            get_change_A_AAAA_json(a_update_record_full_fqdn, address="4.5.6.7"),

            get_change_TXT_json(txt_update_record_full_fqdn, text="hello", change_type="DeleteRecordSet"),
            get_change_TXT_json(txt_update_record_full_fqdn, text="again", change_type="DeleteRecordSet"),
            get_change_TXT_json(txt_update_record_full_fqdn, text="some-multi-text"),
            get_change_TXT_json(txt_update_record_full_fqdn, text="more-multi-text"),

            # Add + single DeleteRecord
            get_change_A_AAAA_json(a_update_record_fqdn, address="1.1.1.1", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(a_update_record_fqdn, address="1.2.3.4"),
            get_change_A_AAAA_json(a_update_record_fqdn, address="4.5.6.7"),

            get_change_TXT_json(txt_update_record_fqdn, text="hello", change_type="DeleteRecordSet"),
            get_change_TXT_json(txt_update_record_fqdn, text="some-multi-text"),
            get_change_TXT_json(txt_update_record_fqdn, text="more-multi-text"),

            # Single DeleteRecord
            get_change_A_AAAA_json(a_update_record_only_fqdn, address="1.1.1.1", change_type="DeleteRecordSet"),
            get_change_TXT_json(txt_update_record_only_fqdn, text="hello", change_type="DeleteRecordSet"),

            ## Full deletes
            # Delete RRSet
            get_change_A_AAAA_json(a_delete_record_set_fqdn, change_type="DeleteRecordSet"),
            get_change_TXT_json(txt_delete_record_set_fqdn, change_type="DeleteRecordSet"),

            # DeleteRecord (full delete)
            get_change_A_AAAA_json(a_delete_record_fqdn, address="1.1.1.1", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(a_delete_record_fqdn, address="1.1.1.2", change_type="DeleteRecordSet"),
            get_change_TXT_json(txt_delete_record_fqdn, text="hello", change_type="DeleteRecordSet"),
            get_change_TXT_json(txt_delete_record_fqdn, text="again", change_type="DeleteRecordSet"),
            get_change_CNAME_json(cname_delete_record_fqdn, cname="caseinsensitive.cname.", change_type="DeleteRecordSet"),

            # DeleteRecord + DeleteRRSet
            get_change_A_AAAA_json(a_delete_record_and_record_set_fqdn, address="1.1.1.1", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(a_delete_record_and_record_set_fqdn, change_type="DeleteRecordSet"),
            get_change_TXT_json(txt_delete_record_and_record_set_fqdn, text="hello", change_type="DeleteRecordSet"),
            get_change_TXT_json(txt_delete_record_and_record_set_fqdn, change_type="DeleteRecordSet"),
        ]
    }

    to_delete = []
    try:
        for rs in [a_update_record_set, txt_update_record_set, a_update_record_full, txt_update_record_full, a_update_record, txt_update_record, a_update_record_only, txt_update_record_only,
                   a_delete_record_set, txt_delete_record_set, a_delete_record, txt_delete_record, cname_delete_record, a_delete_record_and_record_set, txt_delete_record_and_record_set]:
            create_rs = client.create_recordset(rs, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_rs, "Complete"))

        initial_result = client.create_batch_change(batch_change_input, status=202)
        result = client.wait_until_batch_change_completed(initial_result)

        assert_that(result["status"], is_("Complete"))

        # Check batch change response
        assert_change_success(result["changes"], zone=ok_zone, index=0, input_name=a_update_record_set_fqdn, record_name=a_update_record_set_name, record_data=None,
                              change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=1, input_name=a_update_record_set_fqdn, record_name=a_update_record_set_name, record_data="1.2.3.4")
        assert_change_success(result["changes"], zone=ok_zone, index=2, input_name=a_update_record_set_fqdn, record_name=a_update_record_set_name, record_data="4.5.6.7")
        assert_change_success(result["changes"], zone=ok_zone, index=3, input_name=txt_update_record_set_fqdn, record_name=txt_update_record_set_name, record_type="TXT",
                              record_data=None, change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=4, input_name=txt_update_record_set_fqdn, record_name=txt_update_record_set_name, record_type="TXT",
                              record_data="some-multi-text")
        assert_change_success(result["changes"], zone=ok_zone, index=5, input_name=txt_update_record_set_fqdn, record_name=txt_update_record_set_name, record_type="TXT",
                              record_data="more-multi-text")

        assert_change_success(result["changes"], zone=ok_zone, index=6, input_name=a_update_record_full_fqdn, record_name=a_update_record_full_name, record_data="1.1.1.1",
                              change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=7, input_name=a_update_record_full_fqdn, record_name=a_update_record_full_name, record_data="1.1.1.2",
                              change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=8, input_name=a_update_record_full_fqdn, record_name=a_update_record_full_name, record_data="1.2.3.4")
        assert_change_success(result["changes"], zone=ok_zone, index=9, input_name=a_update_record_full_fqdn, record_name=a_update_record_full_name, record_data="4.5.6.7")
        assert_change_success(result["changes"], zone=ok_zone, index=10, input_name=txt_update_record_full_fqdn, record_name=txt_update_record_full_name, record_type="TXT",
                              record_data="hello", change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=11, input_name=txt_update_record_full_fqdn, record_name=txt_update_record_full_name, record_type="TXT",
                              record_data="again", change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=12, input_name=txt_update_record_full_fqdn, record_name=txt_update_record_full_name, record_type="TXT",
                              record_data="some-multi-text")
        assert_change_success(result["changes"], zone=ok_zone, index=13, input_name=txt_update_record_full_fqdn, record_name=txt_update_record_full_name, record_type="TXT",
                              record_data="more-multi-text")

        assert_change_success(result["changes"], zone=ok_zone, index=14, input_name=a_update_record_fqdn, record_name=a_update_record_name, record_data="1.1.1.1",
                              change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=15, input_name=a_update_record_fqdn, record_name=a_update_record_name, record_data="1.2.3.4")
        assert_change_success(result["changes"], zone=ok_zone, index=16, input_name=a_update_record_fqdn, record_name=a_update_record_name, record_data="4.5.6.7")
        assert_change_success(result["changes"], zone=ok_zone, index=17, input_name=txt_update_record_fqdn, record_name=txt_update_record_name, record_type="TXT", record_data="hello",
                              change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=18, input_name=txt_update_record_fqdn, record_name=txt_update_record_name, record_type="TXT",
                              record_data="some-multi-text")
        assert_change_success(result["changes"], zone=ok_zone, index=19, input_name=txt_update_record_fqdn, record_name=txt_update_record_name, record_type="TXT",
                              record_data="more-multi-text")

        assert_change_success(result["changes"], zone=ok_zone, index=20, input_name=a_update_record_only_fqdn, record_name=a_update_record_only_name, record_data="1.1.1.1",
                              change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=21, input_name=txt_update_record_only_fqdn, record_name=txt_update_record_only_name, record_type="TXT",
                              record_data="hello", change_type="DeleteRecordSet")

        assert_change_success(result["changes"], zone=ok_zone, index=22, input_name=a_delete_record_set_fqdn, record_name=a_delete_record_set_name, record_data=None,
                              change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=23, input_name=txt_delete_record_set_fqdn, record_name=txt_delete_record_set_name, record_type="TXT",
                              record_data=None, change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=24, input_name=a_delete_record_fqdn, record_name=a_delete_record_name, record_data="1.1.1.1",
                              change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=25, input_name=a_delete_record_fqdn, record_name=a_delete_record_name, record_data="1.1.1.2",
                              change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=26, input_name=txt_delete_record_fqdn, record_name=txt_delete_record_name, record_type="TXT", record_data="hello",
                              change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=27, input_name=txt_delete_record_fqdn, record_name=txt_delete_record_name, record_type="TXT", record_data="again",
                              change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=28, input_name=cname_delete_record_fqdn, record_name=cname_delete_record_name, record_type="CNAME",
                              record_data="caseinsensitive.cname.", change_type="DeleteRecordSet")

        assert_change_success(result["changes"], zone=ok_zone, index=29, input_name=a_delete_record_and_record_set_fqdn, record_name=a_delete_record_and_record_set_name,
                              record_data="1.1.1.1", change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=30, input_name=a_delete_record_and_record_set_fqdn, record_name=a_delete_record_and_record_set_name,
                              record_data=None, change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=31, input_name=txt_delete_record_and_record_set_fqdn, record_name=txt_delete_record_and_record_set_name,
                              record_type="TXT", record_data="hello", change_type="DeleteRecordSet")
        assert_change_success(result["changes"], zone=ok_zone, index=32, input_name=txt_delete_record_and_record_set_fqdn, record_name=txt_delete_record_and_record_set_name,
                              record_type="TXT", record_data=None, change_type="DeleteRecordSet")

        # Perform look up to verify record set data
        for rs in to_delete:
            rs_name = rs["recordSet"]["name"]
            rs_id = rs["recordSet"]["id"]
            zone_id = rs["zone"]["id"]

            # deletes should not exist
            if rs_name in [a_delete_record_set_name, txt_delete_record_set_name, a_delete_record_name,
                           txt_delete_record_name, cname_delete_record_name, a_delete_record_and_record_set_name, txt_delete_record_and_record_set_name]:
                client.get_recordset(zone_id, rs_id, status=404)
            else:
                result_rs = client.get_recordset(zone_id, rs_id, status=200)
                records = result_rs["recordSet"]["records"]

                # full deletes with updates
                if rs_name in [a_update_record_set_name, a_update_record_full_name]:
                    assert_that(records, contains_exactly({"address": "1.2.3.4"}, {"address": "4.5.6.7"}))
                    assert_that(records, is_not(contains_exactly({"address": "1.1.1.1"}, {"address": "1.1.1.2"})))
                elif rs_name in [txt_update_record_set_name, txt_update_record_full_name]:
                    assert_that(records, contains_exactly({"text": "some-multi-text"}, {"text": "more-multi-text"}))
                    assert_that(records, is_not(contains_exactly({"text": "hello"}, {"text": "again"})))
                # single entry delete with adds
                elif rs_name == a_update_record_name:
                    assert_that(records, contains_exactly({"address": "1.1.1.2"}, {"address": "1.2.3.4"}, {"address": "4.5.6.7"}))
                    assert_that(records, is_not(contains_exactly({"address": "1.1.1.1"})))
                elif rs_name == txt_update_record_name:
                    assert_that(records, contains_exactly({"text": "again"}, {"text": "some-multi-text"}, {"text": "more-multi-text"}))
                    assert_that(records, is_not(contains_exactly({"text": "hello"})))
                elif rs_name == a_update_record_only_name:
                    assert_that(records, contains_exactly({"address": "1.1.1.2"}))
                    assert_that(records, is_not(contains_exactly({"address": "1.1.1.1"})))
                elif rs_name == txt_update_record_only_name:
                    assert_that(records, contains_exactly({"text": "again"}))
                    assert_that(records, is_not(contains_exactly({"text": "hello"})))
    finally:
        clear_recordset_list(to_delete, client)


def test_create_batch_deletes_succeeds(shared_zone_test_context):
    """
    Test creating batch change with DeleteRecordSet with valid record data succeeds
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    ok_group = shared_zone_test_context.ok_group
    ok_zone_name = shared_zone_test_context.ok_zone["name"]

    rs_name = generate_record_name()
    rs_name_2 = generate_record_name()
    multi_rs_name = generate_record_name()
    multi_rs_name_2 = generate_record_name()
    rs_fqdn = rs_name + f".{ok_zone_name}"
    rs_fqdn_2 = rs_name_2 + f".{ok_zone_name}"
    multi_rs_fqdn = multi_rs_name + f".{ok_zone_name}"
    multi_rs_fqdn_2 = multi_rs_name_2 + f".{ok_zone_name}"
    rs_to_create = create_recordset(ok_zone, rs_name, "A", [{"address": "1.2.3.4"}], 200, ok_group["id"])
    rs_to_create_2 = create_recordset(ok_zone, rs_name_2, "A", [{"address": "1.2.3.4"}], 200, ok_group["id"])
    multi_record_rs_to_create = create_recordset(ok_zone, multi_rs_name, "A", [{"address": "1.2.3.4"}, {"address": "1.1.1.1"}], 200, ok_group["id"])
    multi_record_rs_to_create_2 = create_recordset(ok_zone, multi_rs_name_2, "A", [{"address": "1.2.3.4"}, {"address": "1.1.1.1"}], 200, ok_group["id"])

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json(rs_fqdn, address="1.2.3.4", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(rs_fqdn_2, change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(multi_rs_fqdn, address="1.2.3.4", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json(multi_rs_fqdn_2, change_type="DeleteRecordSet")
        ]
    }

    to_delete = []

    try:
        create_rs = client.create_recordset(rs_to_create, status=202)
        create_rs_2 = client.create_recordset(rs_to_create_2, status=202)
        create_multi_rs = client.create_recordset(multi_record_rs_to_create, status=202)
        create_multi_rs_2 = client.create_recordset(multi_record_rs_to_create_2, status=202)
        to_delete.append(client.wait_until_recordset_change_status(create_rs, "Complete"))
        to_delete.append(client.wait_until_recordset_change_status(create_rs_2, "Complete"))
        to_delete.append(client.wait_until_recordset_change_status(create_multi_rs, "Complete"))
        to_delete.append(client.wait_until_recordset_change_status(create_multi_rs_2, "Complete"))

        result = client.create_batch_change(batch_change_input, status=202)
        client.wait_until_batch_change_completed(result)

        client.get_recordset(create_rs["zone"]["id"], create_rs["recordSet"]["id"], status=404)
        client.get_recordset(create_rs_2["zone"]["id"], create_rs_2["recordSet"]["id"], status=404)
        updated_rs = client.get_recordset(create_multi_rs["zone"]["id"], create_multi_rs["recordSet"]["id"], status=200)["recordSet"]
        assert_that(updated_rs["records"], is_([{"address": "1.1.1.1"}]))
        client.get_recordset(create_multi_rs_2["zone"]["id"], create_multi_rs_2["recordSet"]["id"], status=404)
    finally:
        clear_recordset_list(to_delete, client)


@pytest.mark.serial
@pytest.mark.skip_production
def test_create_batch_change_with_multi_record_adds_with_multi_record_support(shared_zone_test_context):
    """
    Test new recordsets with multiple records can be added in batch.
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    ok_group = shared_zone_test_context.ok_group
    ok_zone_name = shared_zone_test_context.ok_zone["name"]
    ip4_prefix = shared_zone_test_context.ip4_classless_prefix

    to_delete = []

    rs_name = generate_record_name()
    rs_fqdn = rs_name + f".{ok_zone_name}"
    rs_to_create = create_recordset(ok_zone, rs_name, "A", [{"address": "1.2.3.4"}], 200, ok_group["id"])

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json(f"multi.{ok_zone_name}", address="1.2.3.4"),
            get_change_A_AAAA_json(f"multi.{ok_zone_name}", address="4.5.6.7"),
            get_change_PTR_json(f"{ip4_prefix}.44", ptrdname="multi.test"),
            get_change_PTR_json(f"{ip4_prefix}.44", ptrdname="multi2.test"),
            get_change_TXT_json(f"multi-txt.{ok_zone_name}", text="some-multi-text"),
            get_change_TXT_json(f"multi-txt.{ok_zone_name}", text="more-multi-text"),
            get_change_MX_json(f"multi-mx.{ok_zone_name}", preference=0),
            get_change_MX_json(f"multi-mx.{ok_zone_name}", preference=1000, exchange="bar.foo."),
            get_change_A_AAAA_json(rs_fqdn, address="1.2.3.4")
        ],
        "ownerGroupId": shared_zone_test_context.ok_group["id"]
    }

    try:
        create_rs = client.create_recordset(rs_to_create, status=202)
        to_delete.append(client.wait_until_recordset_change_status(create_rs, "Complete"))
        response = client.create_batch_change(batch_change_input, status=202)

        assert_successful_change_in_error_response(response["changes"][0], input_name=f"multi.{ok_zone_name}", record_data="1.2.3.4")
        assert_successful_change_in_error_response(response["changes"][1], input_name=f"multi.{ok_zone_name}", record_data="4.5.6.7")
        assert_successful_change_in_error_response(response["changes"][2], input_name=f"{ip4_prefix}.44", record_type="PTR", record_data="multi.test.")
        assert_successful_change_in_error_response(response["changes"][3], input_name=f"{ip4_prefix}.44", record_type="PTR", record_data="multi2.test.")
        assert_successful_change_in_error_response(response["changes"][4], input_name=f"multi-txt.{ok_zone_name}", record_type="TXT", record_data="some-multi-text")
        assert_successful_change_in_error_response(response["changes"][5], input_name=f"multi-txt.{ok_zone_name}", record_type="TXT", record_data="more-multi-text")
        assert_successful_change_in_error_response(response["changes"][6], input_name=f"multi-mx.{ok_zone_name}", record_type="MX", record_data={"preference": 0, "exchange": "foo.bar."})
        assert_successful_change_in_error_response(response["changes"][7], input_name=f"multi-mx.{ok_zone_name}", record_type="MX", record_data={"preference": 1000, "exchange": "bar.foo."})
        assert_successful_change_in_error_response(response["changes"][8], input_name=rs_fqdn, record_data="1.2.3.4")
    finally:
        clear_recordset_list(to_delete, client)
