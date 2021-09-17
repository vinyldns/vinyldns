import pytest
from hamcrest import *
from utils import *

from functional_test.utils import get_change_A_AAAA_json, clear_zoneid_rsid_tuple_list


@pytest.mark.manual_batch_review
def test_reject_pending_batch_change_success(shared_zone_test_context):
    """
    Test rejecting a batch change succeeds for a support user
    """
    client = shared_zone_test_context.ok_vinyldns_client
    rejector = shared_zone_test_context.support_user_client
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json("zone.discovery.failure.", address="4.3.2.1")
        ],
        "ownerGroupId": shared_zone_test_context.ok_group['id']
    }
    result = client.create_batch_change(batch_change_input, status=202)
    get_batch = client.get_batch_change(result['id'])
    assert_that(get_batch['status'], is_('PendingReview'))
    assert_that(get_batch['approvalStatus'], is_('PendingReview'))
    assert_that(get_batch['changes'][0]['status'], is_('NeedsReview'))
    assert_that(get_batch['changes'][0]['validationErrors'][0]['errorType'], is_('ZoneDiscoveryError'))

    rejector.reject_batch_change(result['id'], status=200)
    get_batch = client.get_batch_change(result['id'])

    assert_that(get_batch['status'], is_('Rejected'))
    assert_that(get_batch['approvalStatus'], is_('ManuallyRejected'))
    assert_that(get_batch['reviewerId'], is_('support-user-id'))
    assert_that(get_batch['reviewerUserName'], is_('support-user'))
    assert_that(get_batch, has_key('reviewTimestamp'))
    assert_that(get_batch['changes'][0]['status'], is_('Rejected'))
    assert_that(get_batch, not(has_key('cancelledTimestamp')))

@pytest.mark.manual_batch_review
def test_reject_batch_change_with_invalid_batch_change_id_fails(shared_zone_test_context):
    """
    Test rejecting a batch change with invalid batch change ID
    """

    client = shared_zone_test_context.ok_vinyldns_client

    error = client.reject_batch_change("some-id", status=404)
    assert_that(error, is_("Batch change with id some-id cannot be found"))

@pytest.mark.manual_batch_review
def test_reject_batch_change_with_comments_exceeding_max_length_fails(shared_zone_test_context):
    """
    Test rejecting a batch change with comments exceeding 1024 characters fails
    """

    client = shared_zone_test_context.ok_vinyldns_client
    reject_batch_change_input = {
        "reviewComment": "a"*1025
    }
    errors = client.reject_batch_change("some-id", reject_batch_change_input, status=400)['errors']
    assert_that(errors, contains_inanyorder("Comment length must not exceed 1024 characters."))

@pytest.mark.manual_batch_review
def test_reject_batch_change_fails_with_forbidden_error_for_non_system_admins(shared_zone_test_context):
    """
    Test rejecting a batch change if the reviewer is not a super user or support user
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json("no-owner-group-id.ok.", address="4.3.2.1")
        ]
    }
    to_delete = []

    try:
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        to_delete = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]
        error = client.reject_batch_change(completed_batch['id'], status=403)
        assert_that(error, is_("User does not have access to item " + completed_batch['id']))
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)

@pytest.mark.manual_batch_review
def test_reject_batch_change_fails_when_not_pending_approval(shared_zone_test_context):
    """
    Test rejecting a batch change fails if the batch is not PendingReview
    """
    client = shared_zone_test_context.ok_vinyldns_client
    rejector = shared_zone_test_context.support_user_client
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json("reject-completed-change-test.ok.", address="4.3.2.1")
        ]
    }
    to_delete = []

    try:
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        to_delete = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]
        error = rejector.reject_batch_change(completed_batch['id'], status=400)
        assert_that(error, is_("Batch change " + completed_batch['id'] +
                               " is not pending review, so it cannot be rejected."))
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)


