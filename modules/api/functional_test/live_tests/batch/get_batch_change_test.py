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
        assert_that(result['approvalStatus'], is_('AutoApproved'))
        assert_that(result, is_not(has_key('reviewerId')))
        assert_that(result, is_not(has_key('reviewerUserName')))
        assert_that(result, is_not(has_key('reviewComment')))
        assert_that(result, is_not(has_key('reviewTimestamp')))
    finally:
        for result_rs in to_delete:
            try:
                delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=202)
                client.wait_until_recordset_change_status(delete_result, 'Complete')
            except:
                pass


def test_get_batch_change_with_record_owner_group_success(shared_zone_test_context):
    """
    Test successfully getting a batch change with an ownerGroupId and ownerGroupName
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    group = shared_zone_test_context.shared_record_group
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("testing-get-batch-with-owner-group.shared.", address="1.1.1.1")
        ],
        "ownerGroupId": group['id']
    }
    to_delete = []
    try:
        batch_change = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(batch_change)

        record_set_list = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]
        to_delete = set(record_set_list)

        result = client.get_batch_change(batch_change['id'], status=200)
        assert_that(result, is_(completed_batch))
        assert_that(result['ownerGroupId'], is_(group['id']))
        assert_that(result['ownerGroupName'], is_(group['name']))

    finally:
        for result_rs in to_delete:
            delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')


def test_get_batch_change_with_deleted_record_owner_group_success(shared_zone_test_context):
    """
    Test that if the owner group no longer exists that getting a batch change will still succeed,
    with the ownerGroupName attribute set to None
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    temp_group = {
        'name': 'test-get-batch-record-owner-group',
        'email': 'test@test.com',
        'description': 'for testing that a get batch change still works when record owner group is deleted',
        'members': [ { 'id': 'sharedZoneUser'} ],
        'admins': [ { 'id': 'sharedZoneUser'} ]
    }

    record_to_delete = []
    try:

        group_to_delete = client.create_group(temp_group, status=200)

        batch_change_input = {
            "comments": "this is optional",
            "changes": [
                get_change_A_AAAA_json("testing-get-batch-with-owner-group.shared.", address="1.1.1.1")
            ],
            "ownerGroupId": group_to_delete['id']
        }

        batch_change = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(batch_change)

        record_set_list = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]
        record_to_delete = set(record_set_list)

        #delete records and owner group
        temp = record_to_delete.copy()
        for result_rs in temp:
            delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')
            record_to_delete.remove(result_rs)
        temp.clear()

        client.delete_group(group_to_delete['id'], status=200)
        del completed_batch['ownerGroupName']

        #the batch should not be updated with deleted group data
        result = client.get_batch_change(batch_change['id'], status=200)
        assert_that(result, is_(completed_batch))
        assert_that(result['ownerGroupId'], is_(group_to_delete['id']))
        assert_that(result, is_not(has_key('ownerGroupName')))

    finally:
        for result_rs in record_to_delete:
            delete_result = client.delete_recordset(result_rs[0], result_rs[1], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')


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
