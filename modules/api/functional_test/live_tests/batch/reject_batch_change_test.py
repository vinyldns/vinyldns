from hamcrest import *
from utils import *

@pytest.mark.manual_batch_review
def test_reject_batch_change_without_comments_succeeds(shared_zone_test_context):
    """
    Test rejecting a batch change without comments succeeds
    """

    client = shared_zone_test_context.ok_vinyldns_client

    #TODO Update to actual pending batch change
    client.reject_batch_change("some-id", status=200)

@pytest.mark.manual_batch_review
def test_reject_batch_change_with_comments_succeeds(shared_zone_test_context):
    """
    Test rejecting a batch change with comments succeeds
    """

    client = shared_zone_test_context.ok_vinyldns_client
    reject_batch_change_input = {
        "reviewComment": "some-comments"
    }
    #TODO Update to actual pending batch change
    client.reject_batch_change("some-id", reject_batch_change_input, status=200)

@pytest.mark.manual_batch_review
def test_reject_batch_change_with_comments_exceeding_max_length_fails(shared_zone_test_context):
    """
    Test rejecting a batch change with comments exceeding 1024 characters fails
    """

    client = shared_zone_test_context.ok_vinyldns_client
    reject_batch_change_input = {
        "reviewComment": "a"*1025
    }
    #TODO Update to actual pending batch change
    errors = client.reject_batch_change("some-id", reject_batch_change_input, status=400)['errors']
    assert_that(errors, contains_inanyorder("Comment length must not exceed 1024 characters."))

@skip_manual_review
def test_reject_batch_change_fails_with_forbidden_error(shared_zone_test_context):
    """
    Test successfully creating a batch change without owner group ID specified
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
