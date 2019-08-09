from hamcrest import *
from utils import *


@pytest.mark.manual_batch_review
def test_cancel_batch_change_success(shared_zone_test_context):
    """
    Test cancelling a batch change succeeds for the user who made the batch change
    """
    client = shared_zone_test_context.ok_vinyldns_client
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

    client.cancel_batch_change(result['id'], status=200)
    get_batch = client.get_batch_change(result['id'])

    assert_that(get_batch['status'], is_('Cancelled'))
    assert_that(get_batch['approvalStatus'], is_('Cancelled'))
    assert_that(get_batch['changes'][0]['status'], is_('Cancelled'))
    assert_that(get_batch, has_key('cancelledTimestamp'))
    assert_that(get_batch, not(has_key('reviewTimestamp')))
    assert_that(get_batch, not(has_key('reviewerId')))
    assert_that(get_batch, not(has_key('reviewerUserName')))
    assert_that(get_batch, not(has_key('reviewComment')))

@pytest.mark.manual_batch_review
def test_cancel_batch_change_fails_for_non_creator(shared_zone_test_context):
    """
    Test cancelling a batch change fails for a user who didn't make the batch change
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

    error = rejector.cancel_batch_change(get_batch['id'], status=403)
    assert_that(error, is_("User does not have access to item " + get_batch['id']))

@pytest.mark.manual_batch_review
def test_cancel_batch_change_fails_when_not_pending_approval(shared_zone_test_context):
    """
    Test rejecting a batch change fails if the batch is not PendingReview
    """
    client = shared_zone_test_context.ok_vinyldns_client
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
        error = client.cancel_batch_change(completed_batch['id'], status=400)
        assert_that(error, is_("Batch change " + completed_batch['id'] +
                               " is not pending review, so it cannot be rejected."))
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)
