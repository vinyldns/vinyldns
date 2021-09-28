import pytest

from utils import *


@pytest.mark.serial
@pytest.mark.manual_batch_review
def test_approve_pending_batch_change_success(shared_zone_test_context):
    """
    Test approving a batch change succeeds for a support user
    """
    client = shared_zone_test_context.ok_vinyldns_client
    approver = shared_zone_test_context.support_user_client
    partition_id = shared_zone_test_context.partition_id
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(f"test-approve-success.not.loaded{partition_id}.", address="4.3.2.1"),
            get_change_A_AAAA_json(f"needs-review.not.loaded{partition_id}.", address="4.3.2.1"),
            get_change_A_AAAA_json(f"zone-name-flagged-for-manual-review.zone.requires.review{partition_id}.")
        ],
        "ownerGroupId": shared_zone_test_context.ok_group["id"]
    }

    to_delete = []
    to_disconnect = None
    try:
        result = client.create_batch_change(batch_change_input, status=202)
        get_batch = client.get_batch_change(result["id"])
        assert_that(get_batch["status"], is_("PendingReview"))
        assert_that(get_batch["approvalStatus"], is_("PendingReview"))
        assert_that(get_batch["changes"][0]["status"], is_("NeedsReview"))
        assert_that(get_batch["changes"][0]["validationErrors"][0]["errorType"], is_("ZoneDiscoveryError"))
        assert_that(get_batch["changes"][1]["status"], is_("NeedsReview"))
        assert_that(get_batch["changes"][1]["validationErrors"][0]["errorType"], is_("RecordRequiresManualReview"))
        assert_that(get_batch["changes"][2]["status"], is_("NeedsReview"))
        assert_that(get_batch["changes"][2]["validationErrors"][0]["errorType"], is_("RecordRequiresManualReview"))

        # need to create the zone so the change can succeed
        zone = {
            "name": f"not.loaded{partition_id}.",
            "email": "test@test.com",
            "adminGroupId": shared_zone_test_context.ok_group["id"],
            "backendId": "func-test-backend",
            "shared": True
        }
        zone_create = approver.create_zone(zone, status=202)
        to_disconnect = zone_create["zone"]
        approver.wait_until_zone_active(to_disconnect["id"])

        approved = approver.approve_batch_change(result["id"], status=202)
        completed_batch = client.wait_until_batch_change_completed(approved)
        to_delete = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"]]

        assert_that(completed_batch["status"], is_("Complete"))
        for change in completed_batch["changes"]:
            assert_that(change["status"], is_("Complete"))
            assert_that(len(change["validationErrors"]), is_(0))
        assert_that(completed_batch["approvalStatus"], is_("ManuallyApproved"))
        assert_that(completed_batch["reviewerId"], is_("support-user-id"))
        assert_that(completed_batch["reviewerUserName"], is_("support-user"))
        assert_that(completed_batch, has_key("reviewTimestamp"))
        assert_that(get_batch, not (has_key("cancelledTimestamp")))
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)
        if to_disconnect:
            approver.abandon_zones(to_disconnect["id"], status=202)


@pytest.mark.manual_batch_review
def test_approve_pending_batch_change_fails_if_there_are_still_errors(shared_zone_test_context):
    """
    Test approving a batch change fails if there are still errors
    """
    client = shared_zone_test_context.ok_vinyldns_client
    approver = shared_zone_test_context.support_user_client
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json("needs-review.nonexistent.", address="4.3.2.1"),
            get_change_A_AAAA_json("zone.does.not.exist.")
        ],
        "ownerGroupId": shared_zone_test_context.ok_group["id"]
    }
    complete_rs = None

    try:
        result = client.create_batch_change(batch_change_input, status=202)
        get_batch = client.get_batch_change(result["id"])
        assert_that(get_batch["status"], is_("PendingReview"))
        assert_that(get_batch["approvalStatus"], is_("PendingReview"))
        assert_that(get_batch["changes"][0]["status"], is_("NeedsReview"))
        assert_that(get_batch["changes"][0]["validationErrors"][0]["errorType"], is_("RecordRequiresManualReview"))
        assert_that(get_batch["changes"][1]["status"], is_("NeedsReview"))
        assert_that(get_batch["changes"][1]["validationErrors"][0]["errorType"], is_("ZoneDiscoveryError"))

        approval_response = approver.approve_batch_change(result["id"], status=400)
        assert_that((approval_response[0]["errors"][0]), contains_string("Zone Discovery Failed"))
        assert_that((approval_response[1]["errors"][0]), contains_string("Zone Discovery Failed"))

        updated_batch = client.get_batch_change(result["id"], status=200)
        assert_that(updated_batch["status"], is_("PendingReview"))
        assert_that(updated_batch["approvalStatus"], is_("PendingReview"))
        assert_that(updated_batch, not (has_key("reviewerId")))
        assert_that(updated_batch, not (has_key("reviewerUserName")))
        assert_that(updated_batch, not (has_key("reviewTimestamp")))
        assert_that(updated_batch, not (has_key("cancelledTimestamp")))
        assert_that(updated_batch["changes"][0]["status"], is_("NeedsReview"))
        assert_that(updated_batch["changes"][0]["validationErrors"][0]["errorType"], is_("ZoneDiscoveryError"))
        assert_that(updated_batch["changes"][1]["status"], is_("NeedsReview"))
        assert_that(updated_batch["changes"][1]["validationErrors"][0]["errorType"], is_("ZoneDiscoveryError"))
    finally:
        if complete_rs:
            delete_result = client.delete_recordset(complete_rs["zoneId"], complete_rs["id"], status=202)
            client.wait_until_recordset_change_status(delete_result, "Complete")


@pytest.mark.manual_batch_review
def test_approve_batch_change_with_invalid_batch_change_id_fails(shared_zone_test_context):
    """
    Test approving a batch change with invalid batch change ID
    """

    client = shared_zone_test_context.ok_vinyldns_client

    error = client.approve_batch_change("some-id", status=404)
    assert_that(error, is_("Batch change with id some-id cannot be found"))


@pytest.mark.manual_batch_review
def test_approve_batch_change_with_comments_exceeding_max_length_fails(shared_zone_test_context):
    """
    Test approving a batch change with comments exceeding 1024 characters fails
    """

    client = shared_zone_test_context.ok_vinyldns_client
    approve_batch_change_input = {
        "reviewComment": "a" * 1025
    }
    errors = client.approve_batch_change("some-id", approve_batch_change_input, status=400)["errors"]
    assert_that(errors, contains_inanyorder("Comment length must not exceed 1024 characters."))


@pytest.mark.manual_batch_review
def test_approve_batch_change_fails_with_forbidden_error_for_non_system_admins(shared_zone_test_context):
    """
    Test approving a batch change if the reviewer is not a super user or support user
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json(f"no-owner-group-id.ok{shared_zone_test_context.partition_id}.", address="4.3.2.1")
        ]
    }
    to_delete = []

    try:
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        to_delete = [(change["zoneId"], change["recordSetId"]) for change in completed_batch["changes"]]
        error = client.approve_batch_change(completed_batch["id"], status=403)
        assert_that(error, is_("User does not have access to item " + completed_batch["id"]))
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)
