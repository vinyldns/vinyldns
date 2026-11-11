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
            "admins": [{"id": "ok"}]
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
            "admins": [{"id": "ok"}]
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
            "admins": [{"id": "ok"}]
        }

        result_group = client.create_group(new_group, status=200)

        # Create zone with that group ID as admin
        zone = {
            "name": f"one-time{shared_zone_test_context.partition_id}.",
            "email": "test@test.com",
            "adminGroupId": result_group["id"],
            "backendId": "func-test-backend"
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


def test_delete_group_that_owns_a_generated_zone(shared_zone_test_context):
    """
    Tests that we cannot delete a group that is the owner of a generated zone that was never connected
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_group = None
    generated_zone = None

    try:
        new_group = {
            "name": f"test-delete-generated-zone-group{shared_zone_test_context.partition_id}",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}]
        }
        result_group = client.create_group(new_group, status=200)

        zone = {
            "groupId": result_group["id"],
            "email": "test@test.com",
            "provider": "powerdns",
            "zoneName": f"one-time-group-delete{shared_zone_test_context.partition_id}.",
            "providerParams": {
                "kind": "Master",
                "nameservers": [
                    "172.17.42.1.",
                    "ns1.mttest1.example.org."
                ]
            }
        }
        generated_zone = client.generate_zone(zone, status=202)
        client.wait_until_generate_zone_active(generated_zone["id"])

        # The group is only referenced by the generate_zone table, not the zone table
        client.delete_group(result_group["id"], status=400)

        client.delete_generated_zone(generated_zone["id"], status=202)
        client.wait_until_generated_zone_deleted(generated_zone["id"])
        generated_zone = None

        client.delete_group(result_group["id"], status=200)
    finally:
        if generated_zone:
            client.abandon_generated_zones([generated_zone["id"]], status=202)
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
            "admins": [{"id": "ok"}]
        }
        saved_group = ok_client.create_group(new_group, status=200)
        not_admin_client.delete_group(saved_group["id"], status=403)
    finally:
        if saved_group:
            ok_client.delete_group(saved_group["id"], status=(200, 404))
