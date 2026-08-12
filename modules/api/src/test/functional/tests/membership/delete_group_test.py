from hamcrest import *

from vinyldns_context import VinylDNSTestContext


def test_delete_group_success(shared_zone_test_context):
    """
    Tests that we can delete a group that has been created
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None
    try:
        new_group = {
            "name": "test-delete-group-success",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)
        result = client.delete_group(saved_group["id"], status=200)
        assert_that(result["status"], is_("Deleted"))
    finally:
        if result:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_delete_group_not_found(shared_zone_test_context):
    """
    Tests that deleting a group that does not exist returns a 404
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.delete_group("doesntexist", status=404)


def test_delete_group_that_is_already_deleted(shared_zone_test_context):
    """
    Tests that deleting a group that is already deleted
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        new_group = {
            "name": f"test-delete-group-already{shared_zone_test_context.partition_id}",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)

        client.delete_group(saved_group["id"], status=200)
        client.delete_group(saved_group["id"], status=404)
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_delete_admin_group(shared_zone_test_context):
    """
    Tests that we cannot delete a group that is the admin of a zone
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_group = None
    result_zone = None

    try:
        # Create group
        new_group = {
            "name": "test-delete-group-already",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }

        result_group = client.create_group(new_group, status=200)

        # Create zone with that group ID as admin
        zone = {
            "name": f"one-time{shared_zone_test_context.partition_id}.",
            "email": "test@test.com",
            "adminGroupId": result_group["id"],
            "connection": {
                "name": "vinyldns.",
                "keyName": VinylDNSTestContext.dns_key_name,
                "key": VinylDNSTestContext.dns_key,
                "primaryServer": VinylDNSTestContext.name_server_ip
            },
            "transferConnection": {
                "name": "vinyldns.",
                "keyName": VinylDNSTestContext.dns_key_name,
                "key": VinylDNSTestContext.dns_key,
                "primaryServer": VinylDNSTestContext.name_server_ip
            }
        }

        result = client.create_zone(zone, status=202)
        result_zone = result["zone"]
        client.wait_until_zone_active(result["zone"]["id"])

        client.delete_group(result_group["id"], status=400)

        # Delete zone
        client.delete_zone(result_zone["id"], status=202)
        client.wait_until_zone_deleted(result_zone["id"])

        # Should now be able to delete group
        client.delete_group(result_group["id"], status=200)
    finally:
        if result_zone:
            client.delete_zone(result_zone["id"], status=(202, 404))
        if result_group:
            client.delete_group(result_group["id"], status=(200, 404))


def test_delete_group_not_authorized(shared_zone_test_context):
    """
    Tests that only the admins can delete a zone
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    not_admin_client = shared_zone_test_context.dummy_vinyldns_client
    try:
        new_group = {
            "name": "test-delete-group-not-authorized",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = ok_client.create_group(new_group, status=200)
        not_admin_client.delete_group(saved_group["id"], status=403)
    finally:
        if saved_group:
            ok_client.delete_group(saved_group["id"], status=(200, 404))
