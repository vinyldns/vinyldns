import time
from hamcrest import *
from utils import *
from vinyldns_context import VinylDNSTestContext
from vinyldns_python import VinylDNSClient


class ListBatchChangeSummariesTestContext():
    def __init__(self, shared_zone_test_context):
        # Note: this fixture is designed so it will load summaries instead of creating them
        self.client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'listBatchSummariesAccessKey', 'listBatchSummariesSecretKey')
        self.completed_changes = []
        self.to_delete = None

        acl_rule = generate_acl_rule('Write', userId='list-batch-summaries-id')
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])

        initial_db_check = self.client.list_batch_change_summaries(status=200)
        self.group = self.client.get_group('list-summaries-group', status=200)

        batch_change_input_one = {
            "comments": "first",
            "changes": [
                get_change_CNAME_json("test-first.ok.", cname="one.")
            ]
        }

        batch_change_input_two = {
            "comments": "second",
            "changes": [
                get_change_CNAME_json("test-second.ok.", cname="two.")
            ]
        }

        batch_change_input_three = {
            "comments": "last",
            "changes": [
                get_change_CNAME_json("test-last.ok.", cname="three.")
            ]
        }

        batch_change_input_pending = {
            "comments": '',
            "changes": [
                get_change_A_AAAA_json("listing-batch-with-owner-group.non-existent-zone.", address="1.1.1.1")
            ],
            "ownerGroupId": self.group['id']
        }

        batch_change_input_with_owner = {
            "comments": '',
            "changes": [
                get_change_A_AAAA_json("listing-batch-with-owner-group.shared.", address="1.1.1.1")
            ],
            "ownerGroupId": self.group['id']
        }

        batch_change_inputs = [batch_change_input_one, batch_change_input_two, batch_change_input_three, batch_change_input_pending, batch_change_input_with_owner]

        record_set_list = []
        self.completed_changes = []

        if len(initial_db_check['batchChanges']) == 0:
            # make some batch changes
            for input in batch_change_inputs:
                change = self.client.create_batch_change(input, status=202)

                if 'Review' not in change['status']:
                    completed = self.client.wait_until_batch_change_completed(change)
                    assert_that(completed["comments"], equal_to(input["comments"]))
                    record_set_list += [(change['zoneId'], change['recordSetId']) for change in completed['changes']]

                # sleep for consistent ordering of timestamps, must be at least one second apart
                time.sleep(1)

            self.completed_changes = self.client.list_batch_change_summaries(status=200)['batchChanges']

            assert_that(len(self.completed_changes), equal_to(len(batch_change_inputs)))
        else:
            self.completed_changes = initial_db_check['batchChanges']

        self.to_delete = set(record_set_list)

    def tear_down(self, shared_zone_test_context):
        for result_rs in self.to_delete:
            delete_result = shared_zone_test_context.ok_vinyldns_client.delete_recordset(result_rs[0], result_rs[1], status=202)
            shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_change_status(delete_result, 'Complete')
        clear_ok_acl_rules(shared_zone_test_context)

    def check_batch_change_summaries_page_accuracy(self, summaries_page, size, next_id=False, start_from=False,
                                                   max_items=100, approval_status=False):
        # validate fields
        if next_id:
            assert_that(summaries_page, has_key('nextId'))
        else:
            assert_that(summaries_page, is_not(has_key('nextId')))
        if start_from:
            assert_that(summaries_page['startFrom'], is_(start_from))
        else:
            assert_that(summaries_page, is_not(has_key('startFrom')))
        if approval_status:
            assert_that(summaries_page, has_key('approvalStatus'))
        else:
            assert_that(summaries_page, is_not(has_key('approvalStatus')))
        assert_that(summaries_page['maxItems'], is_(max_items))

        # validate actual page
        list_batch_change_summaries = summaries_page['batchChanges']
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