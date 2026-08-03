import pytest

from utils import *
from vinyldns_context import VinylDNSTestContext
from vinyldns_python import VinylDNSClient

# Status buckets returned by the batch change count endpoint
COUNT_STATUS_KEYS = [
    "complete",
    "failed",
    "partialFailure",
    "rejected",
    "cancelled",
    "pendingReview",
    "scheduled",
    "pendingProcessing",
]


def assert_count_is_consistent(count):
    """
    The count response should expose all status buckets plus a total, and the total
    should always equal the sum of the individual status buckets.
    """
    assert_that(count, has_key("total"))
    for key in COUNT_STATUS_KEYS:
        assert_that(count, has_key(key))
        assert_that(count[key], is_(greater_than_or_equal_to(0)))

    assert_that(count["total"], is_(sum(count[key] for key in COUNT_STATUS_KEYS)))


def test_get_batch_change_count_with_no_changes_is_zero():
    """
    Test that a user with no batch changes gets a count of zero for every status bucket
    """
    client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "listZeroSummariesAccessKey", "listZeroSummariesSecretKey")

    count = client.get_batch_change_count(status=200)

    assert_count_is_consistent(count)
    assert_that(count["total"], is_(0))
    for key in COUNT_STATUS_KEYS:
        assert_that(count[key], is_(0))


def test_get_batch_change_count_reflects_completed_changes(shared_zone_test_context):
    """
    Test that completing batch changes increments the complete and total counts
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone_name = shared_zone_test_context.ok_zone["name"]

    before = client.get_batch_change_count(status=200)
    assert_count_is_consistent(before)

    to_delete = set()
    try:
        for i in range(2):
            batch_change_input = {
                "comments": f"count-test-complete-{i}",
                "changes": [
                    get_change_A_AAAA_json(generate_record_name(ok_zone_name), address="1.2.3.4")
                ]
            }
            batch_change = client.create_batch_change(batch_change_input, status=202)
            completed = client.wait_until_batch_change_completed(batch_change)
            assert_that(completed["status"], is_("Complete"))
            to_delete.update((change["zoneId"], change["recordSetId"]) for change in completed["changes"])

        after = client.get_batch_change_count(status=200)

        assert_count_is_consistent(after)
        # Counts are cumulative (batch changes are never deleted), so the delta is at least
        # the number of changes we just completed
        assert_that(after["total"], is_(greater_than_or_equal_to(before["total"] + 2)))
        assert_that(after["complete"], is_(greater_than_or_equal_to(before["complete"] + 2)))
    finally:
        for result_rs in to_delete:
            delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=(202, 404))
            if not isinstance(delete_result, str):
                client.wait_until_recordset_change_status(delete_result, "Complete")


def test_get_batch_change_count_only_includes_callers_changes(shared_zone_test_context):
    """
    Test that a batch change created by one user is not counted for a different user
    (access is scoped to the authenticated user when ignoreAccess is not set)
    """
    creator = shared_zone_test_context.ok_vinyldns_client
    other = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "listZeroSummariesAccessKey", "listZeroSummariesSecretKey")
    ok_zone_name = shared_zone_test_context.ok_zone["name"]

    # The zero-summaries user never owns any batch changes
    assert_that(other.get_batch_change_count(status=200)["total"], is_(0))

    to_delete = set()
    try:
        batch_change_input = {
            "comments": "count-test-access-scoping",
            "changes": [
                get_change_A_AAAA_json(generate_record_name(ok_zone_name), address="1.2.3.4")
            ]
        }
        batch_change = creator.create_batch_change(batch_change_input, status=202)
        completed = creator.wait_until_batch_change_completed(batch_change)
        to_delete.update((change["zoneId"], change["recordSetId"]) for change in completed["changes"])

        # The other user's count is unaffected by the creator's batch change
        other_count = other.get_batch_change_count(status=200)
        assert_count_is_consistent(other_count)
        assert_that(other_count["total"], is_(0))
    finally:
        for result_rs in to_delete:
            delete_result = creator.delete_recordset(result_rs[0], result_rs[1], status=(202, 404))
            if not isinstance(delete_result, str):
                creator.wait_until_recordset_change_status(delete_result, "Complete")


def test_get_batch_change_count_ignore_access_for_super_user(shared_zone_test_context):
    """
    Test that a super user requesting with ignoreAccess=true gets counts across all users,
    which is always at least as large as the count scoped to their own changes
    """
    super_user = shared_zone_test_context.super_user_client
    creator = shared_zone_test_context.ok_vinyldns_client
    ok_zone_name = shared_zone_test_context.ok_zone["name"]

    to_delete = set()
    try:
        batch_change_input = {
            "comments": "count-test-ignore-access",
            "changes": [
                get_change_A_AAAA_json(generate_record_name(ok_zone_name), address="1.2.3.4")
            ]
        }
        batch_change = creator.create_batch_change(batch_change_input, status=202)
        completed = creator.wait_until_batch_change_completed(batch_change)
        to_delete.update((change["zoneId"], change["recordSetId"]) for change in completed["changes"])

        scoped_count = super_user.get_batch_change_count(status=200)
        all_users_count = super_user.get_batch_change_count(ignore_access=True, status=200)

        assert_count_is_consistent(scoped_count)
        assert_count_is_consistent(all_users_count)

        # ignoreAccess includes every user's batch changes, so it must include the change
        # the ok user just created and be at least as large as the super user's own scope
        assert_that(all_users_count["total"], is_(greater_than_or_equal_to(scoped_count["total"])))
        assert_that(all_users_count["complete"], is_(greater_than_or_equal_to(1)))
    finally:
        for result_rs in to_delete:
            delete_result = creator.delete_recordset(result_rs[0], result_rs[1], status=(202, 404))
            if not isinstance(delete_result, str):
                creator.wait_until_recordset_change_status(delete_result, "Complete")


def test_get_batch_change_count_ignore_access_is_ignored_for_non_super_user(shared_zone_test_context):
    """
    Test that a non-privileged user requesting with ignoreAccess=true still only gets their
    own counts (ignoreAccess only applies to system admins)
    """
    other = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "listZeroSummariesAccessKey", "listZeroSummariesSecretKey")

    count = other.get_batch_change_count(ignore_access=True, status=200)

    assert_count_is_consistent(count)
    assert_that(count["total"], is_(0))


@pytest.mark.manual_batch_review
def test_get_batch_change_count_filters_by_approval_status(shared_zone_test_context):
    """
    Test that filtering the count by approvalStatus returns only batch changes in that status
    """
    client = shared_zone_test_context.ok_vinyldns_client

    before = client.get_batch_change_count(approval_status="PendingReview", status=200)
    assert_count_is_consistent(before)

    pending_bc = None
    try:
        batch_change_input = {
            "changes": [
                get_change_A_AAAA_json("zone.discovery.failure.", address="4.3.2.1")
            ],
            "ownerGroupId": shared_zone_test_context.ok_group["id"]
        }
        pending_bc = client.create_batch_change(batch_change_input, status=202)
        get_batch = client.get_batch_change(pending_bc["id"])
        assert_that(get_batch["status"], is_("PendingReview"))

        after = client.get_batch_change_count(approval_status="PendingReview", status=200)
        assert_count_is_consistent(after)

        # The filtered count should only ever reflect pending review batch changes
        assert_that(after["pendingReview"], is_(greater_than_or_equal_to(before["pendingReview"] + 1)))
        assert_that(after["total"], is_(after["pendingReview"] + after["scheduled"]))
        assert_that(after["complete"], is_(0))
    finally:
        if pending_bc:
            rejecter = shared_zone_test_context.support_user_client
            rejecter.reject_batch_change(pending_bc["id"], status=200)


def test_get_batch_change_count_unrecognized_approval_status_is_ignored(shared_zone_test_context):
    """
    Test that an unrecognized approvalStatus query value is rejected with HTTP 400
    """
    client = shared_zone_test_context.ok_vinyldns_client

    error = client.get_batch_change_count(approval_status="NotAStatus", status=400)

    assert_that(error, contains_string("NotAStatus"))
