from hamcrest import *
from utils import *

def run_manual_batch_review_test():
    manual_batch_review_enabled = False
    flag = pytest.config.getoption("manual_batch_review_enabled", default=False)
    if flag == "true" or flag == "True":
        manual_batch_review_enabled = True
    return manual_batch_review_enabled

skip_manual_review = pytest.mark.skipif(not run_manual_batch_review_test(),
                                        reason="Skipping manual batch review tests")

@skip_manual_review
def test_reject_batch_change_without_comments_succeeds(shared_zone_test_context):
    """
    Test rejecting a batch change without comments succeeds
    """

    client = shared_zone_test_context.ok_vinyldns_client

    #TODO Update to actual pending batch change
    client.reject_batch_change("some-id", status=200)

@skip_manual_review
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

@skip_manual_review
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
