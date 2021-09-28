from utils import *
from vinyldns_python import VinylDNSClient


class ListBatchChangeSummariesTestContext:
    to_delete: set = None
    completed_changes: list = []
    group: object = None
    is_setup: bool = False

    def __init__(self):
        self.client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "listBatchSummariesAccessKey", "listBatchSummariesSecretKey")

    def setup(self, shared_zone_test_context):
        self.completed_changes = []
        self.to_delete = None

        acl_rule = generate_acl_rule("Write", userId="list-batch-summaries-id")
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])

        initial_db_check = self.client.list_batch_change_summaries(status=200)
        self.group = self.client.get_group("list-summaries-group", status=200)

        ok_zone_name = shared_zone_test_context.ok_zone
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

        if len(initial_db_check["batchChanges"]) == 0:
            print("\r\n!!! CREATING NEW SUMMARIES")
            # make some batch changes
            for batch_change_input in batch_change_inputs:
                change = self.client.create_batch_change(batch_change_input, status=202)

                if "Review" not in change["status"]:
                    completed = self.client.wait_until_batch_change_completed(change)
                    assert_that(completed["comments"], equal_to(batch_change_input["comments"]))
                    record_set_list += [(change["zoneId"], change["recordSetId"]) for change in completed["changes"]]

                # sleep for consistent ordering of timestamps, must be at least one second apart
                time.sleep(1)

            self.completed_changes = self.client.list_batch_change_summaries(status=200)["batchChanges"]
            assert_that(len(self.completed_changes), equal_to(len(batch_change_inputs)))
        else:
            print("\r\n!!! USING EXISTING SUMMARIES")
            self.completed_changes = initial_db_check["batchChanges"]
        self.to_delete = set(record_set_list)
        self.is_setup = True

    def tear_down(self):
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
