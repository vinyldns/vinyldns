from pathlib import Path

from utils import *
from vinyldns_python import VinylDNSClient

# FIXME: this context is fragile as it depends on creating batch changes carefully created with a time delay.

class ListBatchChangeSummariesTestContext:

    def __init__(self, partition_id: str):
        self.to_delete: set = set()
        self.completed_changes: list = []
        self.setup_started = False
        self.partition_id = partition_id
        self.client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "listBatchSummariesAccessKey", "listBatchSummariesSecretKey")

    def setup(self, shared_zone_test_context, temp_directory: Path):
        if self.setup_started:
            # Safeguard against reentrance
            return

        self.setup_started = True
        self.completed_changes = []
        self.to_delete = set()

        acl_rule = generate_acl_rule("Write", userId="list-batch-summaries-id")
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])

        ok_zone_name = shared_zone_test_context.ok_zone["name"]
        batch_change_input_one = {
            "comments": "first",
            "changes": [
                get_change_CNAME_json(f"test-first.{ok_zone_name}", cname="one.")
            ]
        }

        batch_change_input_two = {
            "comments": "second",
            "changes": [
                get_change_CNAME_json(f"test-second.{ok_zone_name}", cname="two.")
            ]
        }

        batch_change_input_three = {
            "comments": "last",
            "changes": [
                get_change_CNAME_json(f"test-last.{ok_zone_name}", cname="three.")
            ]
        }

        batch_change_inputs = [batch_change_input_one, batch_change_input_two, batch_change_input_three]

        record_set_list = []
        self.completed_changes = []

        # make some batch changes
        for batch_change_input in batch_change_inputs:
            change = self.client.create_batch_change(batch_change_input, status=202)

            if "Review" not in change["status"]:
                completed = self.client.wait_until_batch_change_completed(change)
                assert_that(completed["comments"], equal_to(batch_change_input["comments"]))
                record_set_list += [(change["zoneId"], change["recordSetId"]) for change in completed["changes"]]
                self.to_delete = set(record_set_list)

            # Sleep for consistent ordering of timestamps, must be at least one second apart
            time.sleep(1.1)

        self.completed_changes = self.client.list_batch_change_summaries(status=200)["batchChanges"]

    def tear_down(self, shared_zone_test_context):
        for result_rs in self.to_delete:
            delete_result = shared_zone_test_context.ok_vinyldns_client.delete_recordset(result_rs[0], result_rs[1], status=(202, 404))
            if type(delete_result) != str:
                shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_change_status(delete_result, 'Complete')
        self.to_delete.clear()
        clear_ok_acl_rules(shared_zone_test_context)
        self.client.clear_zones()
        self.client.clear_groups()
        self.client.tear_down()

    def check_batch_change_summaries_page_accuracy(self, summaries_page, size, next_id=False, start_from=False, max_items=100, approval_status=False):
        # validate fields
        if next_id:
            assert_that(summaries_page, has_key("nextId"))
        else:
            assert_that(summaries_page, is_not(has_key("nextId")))
        if start_from:
            assert_that(summaries_page["startFrom"], is_(start_from))
        else:
            assert_that(summaries_page, is_not(has_key("startFrom")))
        if approval_status:
            assert_that(summaries_page, has_key("approvalStatus"))
        else:
            assert_that(summaries_page, is_not(has_key("approvalStatus")))
        assert_that(summaries_page["maxItems"], is_(max_items))

        # validate actual page
        list_batch_change_summaries = summaries_page["batchChanges"]
        assert_that(list_batch_change_summaries, has_length(size))

        for i, summary in enumerate(list_batch_change_summaries):
            assert_that(summary["userId"], equal_to("list-batch-summaries-id"))
            assert_that(summary["userName"], equal_to("list-batch-summaries-user"))
            assert_that(summary["comments"], equal_to(self.completed_changes[i + start_from]["comments"]))
            assert_that(summary["createdTimestamp"], equal_to(self.completed_changes[i + start_from]["createdTimestamp"]))
            assert_that(summary["totalChanges"], equal_to(self.completed_changes[i + start_from]["totalChanges"]))
            assert_that(summary["status"], equal_to(self.completed_changes[i + start_from]["status"]))
            assert_that(summary["id"], equal_to(self.completed_changes[i + start_from]["id"]))
            assert_that(summary, is_not(has_key("reviewerId")))
