import copy

import pytest

from utils import *


def test_get_status_success(shared_zone_test_context):
    """
    Tests that the status endpoint returns the current processing status, color, key name and version
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = client.get_status()

    assert_that([True, False], has_item(result["processingDisabled"]))
    assert_that(["green", "blue"], has_item(result["color"]))
    assert_that(result["keyName"], not_none())
    assert_that(result["version"], not_none())


def test_post_status_fails_for_non_admin_users(shared_zone_test_context):
    """
    Tests that the post request to status endpoint fails for non-admin users
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = client.post_status(True)

    assert_that(result, is_("Not authorized. User 'ok' cannot make the requested change."))


def test_post_status_fails_for_non_users(shared_zone_test_context):
    """
    Tests that the post request to status endpoint fails for non-users with fake access and secret key
    """
    client = shared_zone_test_context.non_user_client
    result = client.post_status(True)

    assert_that(result, is_("Authentication Failed: Account with accessKey not-exist-key specified was not found"))


def test_post_status_pass_for_admin_users(shared_zone_test_context):
    """
    Tests that the post request to status endpoint pass for admin users
    """
    client = shared_zone_test_context.support_user_client

    client.post_status(True)

    status = client.get_status()
    assert_that(status["processingDisabled"], is_(True))

    client.post_status(False)

    status = client.get_status()
    assert_that(status["processingDisabled"], is_(False))


@pytest.mark.serial
@pytest.mark.skip_production
def test_toggle_processing(shared_zone_test_context):
    """
    Test that updating a zone when processing is disabled does not happen
    """
    client = shared_zone_test_context.ok_vinyldns_client
    admin_client = shared_zone_test_context.support_user_client
    ok_zone = copy.deepcopy(shared_zone_test_context.ok_zone)

    # disable processing
    admin_client.post_status(True)

    status = client.get_status()
    assert_that(status["processingDisabled"], is_(True))

    admin_client.post_status(False)
    status = client.get_status()
    assert_that(status["processingDisabled"], is_(False))

    # Create changes to make sure we can process after the toggle
    # attempt to perform an update
    ok_zone["email"] = "test@test.com"
    zone_change_result = client.update_zone(ok_zone, status=202)

    # attempt to a create a record
    new_rs = {
        "zoneId": ok_zone["id"],
        "name": "test-status-disable-processing",
        "type": "A",
        "ttl": 100,
        "records": [
            {
                "address": "10.1.1.1"
            },
            {
                "address": "10.2.2.2"
            }
        ]
    }

    record_change = client.create_recordset(new_rs, status=202)
    assert_that(record_change["status"], is_("Pending"))

    # Make sure that the changes are processed
    client.wait_until_zone_change_status_synced(zone_change_result)
    client.wait_until_recordset_change_status(record_change, "Complete")

    recordset_length = len(client.list_recordsets_by_zone(ok_zone["id"])["recordSets"])

    client.delete_recordset(ok_zone["id"], record_change["recordSet"]["id"], status=202)
    client.wait_until_recordset_deleted(ok_zone["id"], record_change["recordSet"]["id"])
    assert_that(client.list_recordsets_by_zone(ok_zone["id"])["recordSets"], has_length(recordset_length - 1))
