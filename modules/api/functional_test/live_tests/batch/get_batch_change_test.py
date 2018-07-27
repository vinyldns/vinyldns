from hamcrest import *
from utils import *

def test_get_batch_change_success(shared_zone_test_context):
    """
    Test successfully getting a batch change
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("parent.com.", address="4.5.6.7"),
            get_change_A_AAAA_json("ok.", record_type="AAAA", address="fd69:27cc:fe91::60")
        ]
    }
    to_delete = []
    try:
        batch_change = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(batch_change)

        record_set_list = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]
        to_delete = set(record_set_list)

        result = client.get_batch_change(batch_change['id'], status=200)
        assert_that(result, is_(completed_batch))
    finally:
        for result_rs in to_delete:
            try:
                delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=202)
                client.wait_until_recordset_change_status(delete_result, 'Complete')
            except:
                pass


def test_get_batch_change_failure(shared_zone_test_context):
    """
    Test that getting a batch change with invalid id returns a Not Found error
    """
    client = shared_zone_test_context.ok_vinyldns_client

    error = client.get_batch_change("invalidId", status=404)

    assert_that(error, is_("Batch change with id invalidId cannot be found"))


def test_get_batch_change_with_unauthorized_user_fails(shared_zone_test_context):
    """
    Test that getting a batch change with a user that didn't create the batch change fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("parent.com.", address="4.5.6.7"),
            get_change_A_AAAA_json("ok.", record_type="AAAA", address="fd69:27cc:fe91::60")
        ]
    }
    to_delete = []
    try:
        batch_change = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(batch_change)

        record_set_list = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]
        to_delete = set(record_set_list)

        error = dummy_client.get_batch_change(batch_change['id'], status=403)
        assert_that(error, is_("User does not have access to item " + batch_change['id']))
    finally:
        for result_rs in to_delete:
            try:
                delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=202)
                client.wait_until_recordset_change_status(delete_result, 'Complete')
            except:
                pass
