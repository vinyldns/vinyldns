from hamcrest import *
from utils import *
import time
import pytest
from vinyldns_python import VinylDNSClient
from vinyldns_context import VinylDNSTestContext

class ListBatchChangeSummariesFixture():
    def __init__(self, shared_zone_test_context):
        self.client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'listBatchSummariesAccessKey', 'listBatchSummariesSecretKey')
        acl_rule = generate_acl_rule('Write', userId='list-batch-summaries-id')
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])

        initial_db_check = self.client.list_batch_change_summaries(status=200)

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

        batch_change_inputs = [batch_change_input_one, batch_change_input_two, batch_change_input_three]

        record_set_list = []
        self.completed_changes = []

        if len(initial_db_check['batchChanges']) == 0:
            # make some batch changes
            for input in batch_change_inputs:
                change = self.client.create_batch_change(input, status=202)
                completed = self.client.wait_until_batch_change_completed(change)
                assert_that(completed["comments"], equal_to(input["comments"]))
                record_set_list += [(change['zoneId'], change['recordSetId']) for change in completed['changes']]
                # sleep for consistent ordering of timestamps, must be at least one second apart
                time.sleep(1)

            self.completed_changes = self.client.list_batch_change_summaries(status=200)['batchChanges']

            assert_that(len(self.completed_changes), equal_to(3))
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
            assert_that(summary["approvalStatus"], equal_to("AutoApproved"))
            assert_that(summary, is_not(has_key("reviewerId")))


@pytest.fixture(scope = "module")
def list_fixture(request, shared_zone_test_context):
    fix = ListBatchChangeSummariesFixture(shared_zone_test_context)
    def fin():
        fix.tear_down(shared_zone_test_context)

    request.addfinalizer(fin)

    return fix

def test_list_batch_change_summaries_success(list_fixture):
    """
    Test successfully listing all of a user's batch change summaries with no parameters
    """
    client = list_fixture.client
    batch_change_summaries_result = client.list_batch_change_summaries(status=200)

    list_fixture.check_batch_change_summaries_page_accuracy(batch_change_summaries_result, size=3)


def test_list_batch_change_summaries_with_max_items(list_fixture):
    """
    Test listing a limited number of user's batch change summaries with maxItems parameter
    """
    client = list_fixture.client
    batch_change_summaries_result = client.list_batch_change_summaries(status=200, max_items=1)

    list_fixture.check_batch_change_summaries_page_accuracy(batch_change_summaries_result, size=1, max_items=1, next_id=1)


def test_list_batch_change_summaries_with_start_from(list_fixture):
    """
    Test listing a limited number of user's batch change summaries with startFrom parameter
    """
    client = list_fixture.client
    batch_change_summaries_result = client.list_batch_change_summaries(status=200, start_from=1)

    list_fixture.check_batch_change_summaries_page_accuracy(batch_change_summaries_result, size=2, start_from=1)


def test_list_batch_change_summaries_with_next_id(list_fixture):
    """
    Test getting user's batch change summaries with index of next batch change summary.
    Apply retrieved nextId to get second page of batch change summaries.
    """
    client = list_fixture.client
    batch_change_summaries_result = client.list_batch_change_summaries(status=200, start_from=1, max_items=1)

    list_fixture.check_batch_change_summaries_page_accuracy(batch_change_summaries_result, size=1, start_from=1, max_items=1, next_id=2)

    next_page_result = client.list_batch_change_summaries(status=200, start_from=batch_change_summaries_result['nextId'])

    list_fixture.check_batch_change_summaries_page_accuracy(next_page_result, size=1, start_from=batch_change_summaries_result['nextId'])


@pytest.mark.manual_batch_review
def test_list_batch_change_summaries_with_pending_status(shared_zone_test_context):
    """
    Test listing a limited number of user's batch change summaries with approvalStatus filter
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    group = shared_zone_test_context.shared_record_group
    batch_change_input = {
        "comments": '',
        "changes": [
            get_change_A_AAAA_json("listing-batch-with-owner-group.non-existent-zone.", address="1.1.1.1")
        ],
        "ownerGroupId": group['id']
    }

    pending_bc = None
    try:
        pending_bc = client.create_batch_change(batch_change_input, status=202)

        batch_change_summaries_result = client.list_batch_change_summaries(status=200, approval_status="PendingReview")

        for batchChange in batch_change_summaries_result['batchChanges']:
            assert_that(batchChange['approvalStatus'], is_('PendingReview'))
            assert_that(batchChange['status'], is_('PendingReview'))
            assert_that(batchChange['totalChanges'], equal_to(1))
    finally:
        if pending_bc:
            rejecter = shared_zone_test_context.support_user_client
            rejecter.reject_batch_change(pending_bc['id'], status=200)


def test_list_batch_change_summaries_with_list_batch_change_summaries_with_no_changes_passes():
    """
    Test successfully getting an empty list of summaries when user has no batch changes
    """
    client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'listZeroSummariesAccessKey', 'listZeroSummariesSecretKey')

    batch_change_summaries_result = client.list_batch_change_summaries(status=200)["batchChanges"]
    assert_that(batch_change_summaries_result, has_length(0))


def test_list_batch_change_summaries_with_record_owner_group_passes(shared_zone_test_context):
    """
    Test that getting a batch change summary with an record owner group set returns the record owner group name and id
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    group = shared_zone_test_context.shared_record_group
    batch_change_input = {
        "comments": '',
        "changes": [
            get_change_A_AAAA_json("listing-batch-with-owner-group.shared.", address="1.1.1.1")
        ],
        "ownerGroupId": group['id']
    }

    record_to_delete = []

    try:
        batch_change = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(batch_change)

        record_set_list = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]
        record_to_delete = set(record_set_list)

        batch_change_summaries_result = client.list_batch_change_summaries(status=200)["batchChanges"]

        under_test = [item for item in batch_change_summaries_result if item['id'] == completed_batch['id']]
        assert_that(under_test, has_length(1))

        under_test = under_test[0]
        assert_that(under_test['ownerGroupId'], is_(group['id']))
        assert_that(under_test['ownerGroupName'], is_(group['name']))

    finally:
        for result_rs in record_to_delete:
            delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')


def test_list_batch_change_summaries_with_deleted_record_owner_group_passes(shared_zone_test_context):
    """
    Test that getting a batch change summary with an record owner group that was deleted passes and return None
    for record owner group name
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    temp_group = {
    'name': 'test-list-summaries-deleted-owner-group',
    'email': 'test@test.com',
    'description': 'for testing that list summaries still works when record owner group is deleted',
    'members': [ { 'id': 'sharedZoneUser'} ],
    'admins': [ { 'id': 'sharedZoneUser'} ]
    }

    record_to_delete = []

    try:
        group_to_delete = client.create_group(temp_group, status=200)

        batch_change_input = {
            "comments": '',
            "changes": [
                get_change_A_AAAA_json("list-batch-with-deleted-owner-group.shared.", address="1.1.1.1")
            ],
            "ownerGroupId": group_to_delete['id']
        }

        batch_change = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(batch_change)

        record_set_list = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]
        record_to_delete = set(record_set_list)

        #delete records and owner group
        temp = record_to_delete.copy()
        for result_rs in temp:
            delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')
            record_to_delete.remove(result_rs)
        temp.clear()

        # delete group
        client.delete_group(group_to_delete['id'], status=200)

        batch_change_summaries_result = client.list_batch_change_summaries(status=200)["batchChanges"]

        under_test = [item for item in batch_change_summaries_result if item['id'] == completed_batch['id']]
        assert_that(under_test, has_length(1))

        under_test = under_test[0]
        assert_that(under_test['ownerGroupId'], is_(group_to_delete['id']))
        assert_that(under_test, is_not(has_key('ownerGroupName')))

    finally:
        for result_rs in record_to_delete:
            delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')


def test_list_batch_change_summaries_with_ignore_access_true_only_shows_requesting_users_records(shared_zone_test_context):
    """
    Test that getting a batch change summary with list all set to true only returns the requesting user's batch changes
    if they are not a super user
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    group = shared_zone_test_context.shared_record_group

    batch_change_input = {
        "comments": '',
        "changes": [
            get_change_A_AAAA_json("listing-batch-with-owner-group.shared.", address="1.1.1.1")
        ],
        "ownerGroupId": group['id']
    }

    ok_batch_change_input = {
        "comments": '',
        "changes": [
            get_change_A_AAAA_json("ok-batch-with-owner-group.shared.", address="1.1.1.1")
        ],
        "ownerGroupId": group['id']
    }

    record_to_delete = []
    ok_record_to_delete = []

    try:
        batch_change = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(batch_change)

        record_set_list = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]
        record_to_delete = set(record_set_list)

        batch_change_summaries_result = client.list_batch_change_summaries(ignore_access=True, status=200)["batchChanges"]

        under_test = [item for item in batch_change_summaries_result if item['id'] == completed_batch['id']]
        assert_that(under_test, has_length(1))

        # Make OK user batch change, then list batch changes. Should not see the Shared user batch change.
        ok_batch_change = ok_client.create_batch_change(ok_batch_change_input, status=202)
        ok_completed_batch = ok_client.wait_until_batch_change_completed(ok_batch_change)

        ok_record_set_list = [(change['zoneId'], change['recordSetId']) for change in ok_completed_batch['changes']]
        ok_record_to_delete = set(ok_record_set_list)

        ok_batch_change_summaries_result = ok_client.list_batch_change_summaries(ignore_access=True, status=200)["batchChanges"]

        ok_under_test = [item for item in ok_batch_change_summaries_result if (item['id'] == ok_completed_batch['id'] or item['id'] == completed_batch['id']) ]
        assert_that(ok_under_test, has_length(1))

    finally:
        for result_rs in record_to_delete:
            delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')
        for result_rs in ok_record_to_delete:
            delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')

