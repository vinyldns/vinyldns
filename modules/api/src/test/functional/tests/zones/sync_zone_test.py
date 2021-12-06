import pytest

from utils import *

# This is set in the API service's configuration
API_SYNC_DELAY = 10
MAX_RETRIES = 30
RETRY_WAIT = 0.05


@pytest.mark.skip_production
def test_sync_zone_success(shared_zone_test_context):
    """
    Test syncing a zone
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone_name = f"sync-test{shared_zone_test_context.partition_id}"
    updated_rs_id = None
    check_rs = None

    zone = {
        "name": zone_name,
        "email": "test@test.com",
        "adminGroupId": shared_zone_test_context.ok_group["id"],
        "isTest": True,
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
    try:
        zone_change = client.create_zone(zone, status=202)
        zone = zone_change["zone"]
        client.wait_until_zone_active(zone["id"])

        # Confirm zone has been synced
        get_result = client.get_zone(zone["id"])
        synced_zone = get_result["zone"]
        latest_sync = synced_zone["latestSync"]
        assert_that(latest_sync, is_not(none()))

        # Confirm that the recordsets in DNS have been saved in vinyldns
        recordsets = client.list_recordsets_by_zone(zone["id"])["recordSets"]

        records_in_dns = build_records_in_dns(shared_zone_test_context)
        assert_that(len(recordsets), is_(len(records_in_dns)))
        for rs in recordsets:
            if rs["name"] == "foo":
                # get the ID for recordset with name "foo"
                updated_rs_id = rs["id"]
            small_rs = dict((k, rs[k]) for k in ["name", "type", "records"])
            if small_rs["type"] == "SOA":
                assert_that(small_rs["name"], is_(f"{zone_name}."))
            else:
                assert_that(records_in_dns, has_item(small_rs))

        # Give the "foo" record an ownerGroupID to confirm it's still present after the zone sync
        foo_rs = client.get_recordset(zone["id"], updated_rs_id)["recordSet"]
        foo_rs["ownerGroupId"] = shared_zone_test_context.ok_group["id"]
        update_response = client.update_recordset(foo_rs, status=202)
        foo_rs_change = client.wait_until_recordset_change_status(update_response, "Complete")
        assert_that(foo_rs_change["recordSet"]["ownerGroupId"], is_(shared_zone_test_context.ok_group["id"]))

        # Make changes to the dns backend
        dns_update(zone, "foo", 38400, "A", "1.2.3.4")
        dns_add(zone, "newrs", 38400, "A", "2.3.4.5")
        dns_delete(zone, "jenkins", "A")

        # Add unknown this should not be synced
        dns_add(zone, "dnametest", 38400, "DNAME", "test.com.")

        # Add dotted hosts, this should be synced, so we will have 10 records ( +2 )
        dns_add(zone, "dott.ed", 38400, "A", "6.7.8.9")
        dns_add(zone, "dott.ed-two", 38400, "A", "6.7.8.9")

        # Wait until we can safely sync again (from the original caused by "importing"/creating the zone)
        time.sleep(API_SYNC_DELAY)

        # Perform the sync
        change = client.sync_zone(zone["id"], status=202)
        client.wait_until_zone_change_status_synced(change)

        # Confirm cannot again sync without waiting
        client.sync_zone(zone["id"], status=403)

        # Validate zone
        get_result = client.get_zone(zone["id"])
        synced_zone = get_result["zone"]
        assert_that(synced_zone["latestSync"], is_not(latest_sync))
        assert_that(synced_zone["status"], is_("Active"))
        assert_that(synced_zone["updated"], is_not(none()))

        # confirm that the updated recordsets in DNS have been saved in vinyldns
        recordsets = client.list_recordsets_by_zone(zone["id"])["recordSets"]
        records_post_update = build_records_post_update(shared_zone_test_context)
        assert_that(len(recordsets), is_(len(records_post_update)))
        for rs in recordsets:
            small_rs = dict((k, rs[k]) for k in ["name", "type", "records"])
            small_rs["records"] = small_rs["records"]
            if small_rs["type"] == "SOA":
                small_rs["records"][0]["serial"] = 0
            # records_post_update does not contain dnametest
            assert_that(records_post_update, has_item(small_rs))

        changes = client.list_recordset_changes(zone["id"])
        for c in changes["recordSetChanges"]:
            if c["id"] != foo_rs_change["id"]:
                assert_that(c["systemMessage"], is_("Change applied via zone sync"))

        check_rs = client.get_recordset(zone["id"], updated_rs_id)["recordSet"]
        assert_that(check_rs["ownerGroupId"], is_(shared_zone_test_context.ok_group["id"]))

        for rs in recordsets:
            # confirm that we can update the dotted host if the name is the same
            if rs["name"] == "dott.ed":
                attempt_update = rs
                attempt_update["ttl"] = attempt_update["ttl"] + 100
                change = client.update_recordset(attempt_update, status=202)
                client.wait_until_recordset_change_status(change, "Complete")

                # we should be able to delete the record
                client.delete_recordset(rs["zoneId"], rs["id"], status=202)
                client.wait_until_recordset_deleted(rs["zoneId"], rs["id"])

            # confirm that we cannot update the dotted host if the name changes
            if rs["name"] == "dott.ed-two":
                attempt_update = rs
                attempt_update["name"] = "new.dotted"
                errors = client.update_recordset(attempt_update, status=422)
                assert_that(errors, is_("Cannot update RecordSet's name."))

                # we should be able to delete the record
                client.delete_recordset(rs["zoneId"], rs["id"], status=202)
                client.wait_until_recordset_deleted(rs["zoneId"], rs["id"])

            if rs["name"] == "example.dotted":
                # confirm that we can modify the example dotted
                good_update = rs
                good_update["name"] = "example-dotted"
                change = client.update_recordset(good_update, status=202)
                client.wait_until_recordset_change_status(change, "Complete")
    finally:
        # reset the ownerGroupId for foo record
        if check_rs:
            check_rs["ownerGroupId"] = None
            update_response = client.update_recordset(check_rs, status=202)
            client.wait_until_recordset_change_status(update_response, "Complete")
        if "id" in zone:
            dns_update(zone, "foo", 38400, "A", "2.2.2.2")
            dns_delete(zone, "newrs", "A")
            dns_add(zone, "jenkins", 38400, "A", "10.1.1.1")
            dns_delete(zone, "example-dotted", "A")
            dns_delete(zone, "dott.ed", "A")
            dns_delete(zone, "dott.ed-two", "A")
            client.abandon_zones([zone["id"]], status=202)

def build_records_in_dns(shared_zone_test_context):
    partition_id = shared_zone_test_context.partition_id
    return [
        {"name": f"sync-test{partition_id}.",
         "type": "SOA",
         "records": [{"mname": "172.17.42.1.",
                      "rname": "admin.test.com.",
                      "retry": 3600,
                      "refresh": 10800,
                      "minimum": 38400,
                      "expire": 604800,
                      "serial": 1439234395}]},
        {"name": f"sync-test{partition_id}.",
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
        {"name": f"sync-test{partition_id}.",
         "type": "A",
         "records": [{"address": "5.5.5.5"}]},
        {"name": "already-exists",
         "type": "A",
         "records": [{"address": "6.6.6.6"}]},
        {"name": "fqdn",
         "type": "A",
         "records": [{"address": "7.7.7.7"}]},
        {"name": "_sip._tcp",
         "type": "SRV",
         "records": [{"priority": 10, "weight": 60, "port": 5060, "target": "foo.sync-test."}]},
        {"name": "existing.dotted",
         "type": "A",
         "records": [{"address": "9.9.9.9"}]}]


def build_records_post_update(shared_zone_test_context):
    partition_id = shared_zone_test_context.partition_id
    return [
        {"name": f"sync-test{partition_id}.",
         "type": "SOA",
         "records": [{"mname": "172.17.42.1.",
                      "rname": "admin.test.com.",
                      "retry": 3600,
                      "refresh": 10800,
                      "minimum": 38400,
                      "expire": 604800,
                      "serial": 0}]},
        {"name": f"sync-test{partition_id}.",
         "type": "NS",
         "records": [{"nsdname": "172.17.42.1."}]},
        {"name": "foo",
         "type": "A",
         "records": [{"address": "1.2.3.4"}]},
        {"name": "test",
         "type": "A",
         "records": [{"address": "3.3.3.3"}, {"address": "4.4.4.4"}]},
        {"name": f"sync-test{partition_id}.",
         "type": "A",
         "records": [{"address": "5.5.5.5"}]},
        {"name": "already-exists",
         "type": "A",
         "records": [{"address": "6.6.6.6"}]},
        {"name": "newrs",
         "type": "A",
         "records": [{"address": "2.3.4.5"}]},
        {"name": "fqdn",
         "type": "A",
         "records": [{"address": "7.7.7.7"}]},
        {"name": "_sip._tcp",
         "type": "SRV",
         "records": [{"priority": 10, "weight": 60, "port": 5060, "target": "foo.sync-test."}]},
        {"name": "existing.dotted",
         "type": "A",
         "records": [{"address": "9.9.9.9"}]},
        {"name": "dott.ed",
         "type": "A",
         "records": [{"address": "6.7.8.9"}]},
        {"name": "dott.ed-two",
         "type": "A",
         "records": [{"address": "6.7.8.9"}]}]
