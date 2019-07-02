from hamcrest import *
from utils import *

@pytest.mark.manual_batch_review
def test_approve_batch_change_with_invalid_batch_change_id_fails(shared_zone_test_context):
    """
    Test approving a batch change with invalid batch change ID
    """

    client = shared_zone_test_context.ok_vinyldns_client

    error = client.reject_batch_change("some-id", status=404)
    assert_that(error, is_("Batch change with id some-id cannot be found"))

@pytest.mark.manual_batch_review
def test_approve_batch_change_with_comments_exceeding_max_length_fails(shared_zone_test_context):
    """
    Test approving a batch change with comments exceeding 1024 characters fails
    """

    client = shared_zone_test_context.ok_vinyldns_client
    reject_batch_change_input = {
        "reviewComment": "a"*1025
    }
    errors = client.reject_batch_change("some-id", reject_batch_change_input, status=400)['errors']
    assert_that(errors, contains_inanyorder("Comment length must not exceed 1024 characters."))

@pytest.mark.manual_batch_review
def test_approve_batch_change_fails_with_forbidden_error_for_non_system_admins(shared_zone_test_context):
    """
    Test approving a batch change if the reviewer is not a super user or support user
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
