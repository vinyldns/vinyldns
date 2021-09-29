from utils import *


def test_verify_production(shared_zone_test_context):
    """
    Test that production works.  This test sets up the shared context, which creates a lot of groups and zones
    and then really just creates a single recordset and delete it.
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None
    try:
        new_rs = {
            "zoneId": shared_zone_test_context.ok_zone["id"],
            "name": "test_create_recordset_with_dns_verify",
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
        print("\r\nCreating recordset in zone " + str(shared_zone_test_context.ok_zone) + "\r\n")
        result = client.create_recordset(new_rs, status=202)
        print(str(result))

        assert_that(result["changeType"], is_("Create"))
        assert_that(result["status"], is_("Pending"))
        assert_that(result["created"], is_not(none()))
        assert_that(result["userId"], is_not(none()))

        result_rs = result["recordSet"]
        result_rs = client.wait_until_recordset_change_status(result, "Complete")["recordSet"]
        print("\r\n\r\n!!!recordset is active!  Verifying...")

        verify_recordset(result_rs, new_rs)
        print("\r\n\r\n!!!recordset verified...")

        records = [x["address"] for x in result_rs["records"]]
        assert_that(records, has_length(2))
        assert_that("10.1.1.1", is_in(records))
        assert_that("10.2.2.2", is_in(records))

        print("\r\n\r\n!!!verifying recordset in dns backend")
        answers = dns_resolve(shared_zone_test_context.ok_zone, result_rs["name"], result_rs["type"])
        rdata_strings = rdata(answers)

        assert_that(answers, has_length(2))
        assert_that("10.1.1.1", is_in(rdata_strings))
        assert_that("10.2.2.2", is_in(rdata_strings))
    finally:
        if result_rs:
            try:
                delete_result = client.delete_recordset(result_rs["zoneId"], result_rs["id"], status=202)
                client.wait_until_recordset_deleted(delete_result["zoneId"], delete_result["id"])
            except Exception:
                traceback.print_exc()
                pass
