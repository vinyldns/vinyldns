from hamcrest import *
from utils import *

def test_reject_batch_change_without_comments_succeeds(shared_zone_test_context):
    """
    Test rejecting a batch change without comments succeeds
    """

    client = shared_zone_test_context.ok_vinyldns_client

    #TODO Update to actual pending batch change
    client.reject_batch_change("some-id", status=200)

def test_reject_batch_change_with_comments_succeeds(shared_zone_test_context):
    """
    Test rejecting a batch change with comments succeeds
    """

    client = shared_zone_test_context.ok_vinyldns_client
    reject_batch_change_input = {
        "comments": "some-comments"
    }
    #TODO Update to actual pending batch change
    client.reject_batch_change("some-id", reject_batch_change_input, status=200)
