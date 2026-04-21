import pytest

from utils import *
from vinyldns_context import VinylDNSTestContext
from vinyldns_python import VinylDNSClient


# FIXME: this whole suite of tests is fragile as it relies on data ordered in a specific way
#        and that data cannot be cleaned up via the API (batchrecordchanges). This causes problems
#        with xdist and parallel execution. The xdist scheduler will only ever schedule this suite
#        on the first worker (gw0).

@pytest.fixture(scope="module")
def list_fixture(shared_zone_test_context, tmp_path_factory):
    ctx = shared_zone_test_context.list_batch_summaries_context
    ctx.setup(shared_zone_test_context, tmp_path_factory.getbasetemp().parent)
    yield ctx
    ctx.tear_down(shared_zone_test_context)


def test_list_batch_change_summaries_success(list_fixture):
    """
    Test successfully listing all of a user's batch change summaries with no parameters
    """
    client = list_fixture.client
    batch_change_summaries_result = client.list_batch_change_summaries(status=200)

    list_fixture.check_batch_change_summaries_page_accuracy(batch_change_summaries_result, size=len(list_fixture.completed_changes))


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

    all_changes = list_fixture.completed_changes
    list_fixture.check_batch_change_summaries_page_accuracy(batch_change_summaries_result, size=len(all_changes) - 1, start_from=1)


def test_list_batch_change_summaries_with_next_id(list_fixture):
    """
    Test getting user's batch change summaries with index of next batch change summary.
    Apply retrieved nextId to get second page of batch change summaries.
    """
    client = list_fixture.client

    batch_change_summaries_result = client.list_batch_change_summaries(status=200, start_from=1, max_items=1)

    list_fixture.check_batch_change_summaries_page_accuracy(batch_change_summaries_result, size=1, start_from=1, max_items=1, next_id=2)

    next_page_result = client.list_batch_change_summaries(status=200, start_from=batch_change_summaries_result["nextId"])

    all_changes = list_fixture.completed_changes
    list_fixture.check_batch_change_summaries_page_accuracy(next_page_result, size=len(all_changes) - int(batch_change_summaries_result["nextId"]), start_from=batch_change_summaries_result["nextId"])


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
        "ownerGroupId": group["id"]
    }

    pending_bc = None
    try:
        pending_bc = client.create_batch_change(batch_change_input, status=202)

        batch_change_summaries_result = client.list_batch_change_summaries(status=200, approval_status="PendingReview")

        for batchChange in batch_change_summaries_result["batchChanges"]:
            assert_that(batchChange["approvalStatus"], is_("PendingReview"))
            assert_that(batchChange["status"], is_("PendingReview"))
            assert_that(batchChange["totalChanges"], equal_to(1))
    finally:
        if pending_bc:
            rejecter = shared_zone_test_context.support_user_client
            rejecter.reject_batch_change(pending_bc["id"], status=200)


def test_list_batch_change_summaries_with_list_batch_change_summaries_with_no_changes_passes():
    """
    Test successfully getting an empty list of summaries when user has no batch changes
    """
    client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "listZeroSummariesAccessKey", "listZeroSummariesSecretKey")

    batch_change_summaries_result = client.list_batch_change_summaries(status=200)["batchChanges"]
    assert_that(batch_change_summaries_result, has_length(0))


def test_list_batch_change_summaries_with_deleted_record_owner_group_passes(shared_zone_test_context):
    """
    Test that getting a batch change summary with an record owner group that was deleted passes and return None
    for record owner group name
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    shared_zone_name = shared_zone_test_context.shared_zone["name"]

    temp_group = {
        "name": "test-list-summaries-deleted-owner-group",
        "email": "test@test.com",
        "description": "for testing that list summaries still works when record owner group is deleted",
        "members": [{"id": "sharedZoneUser"}],
        "admins": [{"id": "sharedZoneUser"}],
        "membershipAccessStatus": {
            "pendingReviewMember": [],
            "rejectedMember": [],
            "approvedMember": []
        }
    }

    record_to_delete = []

    try:
        group_to_delete = client.create_group(temp_group, status=200)

        batch_change_input = {
            "comments": '',
            "changes": [
                get_change_A_AAAA_json(f"list-batch-with-deleted-owner-group.{shared_zone_name}", address="1.1.1.1")
            ],
            "ownerGroupId": group_to_delete["id"]
        }

        batch_change = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(batch_change)

        record_set_list = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"]]
        record_to_delete = set(record_set_list)

        # delete records and owner group
        temp = record_to_delete.copy()
        for result_rs in temp:
            delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")
            record_to_delete.remove(result_rs)
        temp.clear()

        # delete group
        client.delete_group(group_to_delete["id"], status=200)

        batch_change_summaries_result = client.list_batch_change_summaries(status=200)["batchChanges"]

        under_test = [item for item in batch_change_summaries_result if item["id"] == completed_batch["id"]]
        assert_that(under_test, has_length(1))

        under_test = under_test[0]
        assert_that(under_test["ownerGroupId"], is_(group_to_delete["id"]))
        assert_that(under_test, is_not(has_key("ownerGroupName")))
    finally:
        for result_rs in record_to_delete:
            delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


def test_list_batch_change_summaries_with_ignore_access_true_only_shows_requesting_users_records(shared_zone_test_context):
    """
    Test that getting a batch change summary with list all set to true only returns the requesting user's batch changes
    if they are not a super user
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    group = shared_zone_test_context.shared_record_group
    shared_zone_name = shared_zone_test_context.shared_zone["name"]

    ok_batch_change_input = {
        "comments": '',
        "changes": [
            get_change_A_AAAA_json(f"ok-batch-with-owner-group.{shared_zone_name}", address="1.1.1.1")
        ],
        "ownerGroupId": group["id"]
    }
    ok_record_to_delete = []

    try:
        # Make OK user batch change, then list batch changes. Should not see the Shared user batch change.
        ok_batch_change = ok_client.create_batch_change(ok_batch_change_input, status=202)
        ok_completed_batch = ok_client.wait_until_batch_change_completed(ok_batch_change)

        ok_record_set_list = [(change["zoneId"], change["recordSetId"]) for change in ok_completed_batch["changes"]]
        ok_record_to_delete = set(ok_record_set_list)

        ok_batch_change_summaries_result = ok_client.list_batch_change_summaries(ignore_access=True, status=200)["batchChanges"]

        ok_under_test = [item for item in ok_batch_change_summaries_result if (item["id"] == ok_completed_batch["id"])]
        assert_that(ok_under_test, has_length(1))
    finally:
        for result_rs in ok_record_to_delete:
            delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.skip_production
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
        "ownerGroupId": group["id"]
    }

    pending_bc = None
    try:
        pending_bc = client.create_batch_change(batch_change_input, status=202)

        batch_change_summaries_result = client.list_batch_change_summaries(status=200, approval_status="PendingReview")

        for batchChange in batch_change_summaries_result["batchChanges"]:
            assert_that(batchChange["approvalStatus"], is_("PendingReview"))
            assert_that(batchChange["status"], is_("PendingReview"))
            assert_that(batchChange["totalChanges"], equal_to(1))
    finally:
        if pending_bc:
            rejecter = shared_zone_test_context.support_user_client
            rejecter.reject_batch_change(pending_bc["id"], status=200)
