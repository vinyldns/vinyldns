import pytest
from hamcrest import *
from utils import create_unique_email_address


def test_create_group_success(shared_zone_test_context):
    """
    Tests that creating a group works
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = None

    try:
        new_group = {
            "name": f"test-create-group-success{shared_zone_test_context.partition_id}",
            "email": create_unique_email_address(),
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}]
        }
        result = client.create_group(new_group, status=200)

        assert_that(result["name"], is_(new_group["name"]))
        assert_that(result["email"], is_(new_group["email"]))
        assert_that(result["description"], is_(new_group["description"]))
        assert_that(result["status"], is_("Active"))
        assert_that(result["created"], not_none())
        assert_that(result["id"], not_none())
        assert_that(result["members"], has_length(1))
        assert_that(result["members"][0]["id"], is_("ok"))
        assert_that(result["admins"], has_length(1))
        assert_that(result["admins"][0]["id"], is_("ok"))
    finally:
        if result:
            client.delete_group(result["id"], status=(200, 404))


def test_creator_is_an_admin(shared_zone_test_context):
    """
    Tests that the creator is an admin
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = None

    try:
        new_group = {
            "name": "test-create-group-success",
            "email": create_unique_email_address(),
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": []
        }
        result = client.create_group(new_group, status=200)

        assert_that(result["name"], is_(new_group["name"]))
        assert_that(result["email"], is_(new_group["email"]))
        assert_that(result["description"], is_(new_group["description"]))
        assert_that(result["status"], is_("Active"))
        assert_that(result["created"], not_none())
        assert_that(result["id"], not_none())
        assert_that(result["members"], has_length(1))
        assert_that(result["members"][0]["id"], is_("ok"))
        assert_that(result["admins"], has_length(1))
        assert_that(result["admins"][0]["id"], is_("ok"))
    finally:
        if result:
            client.delete_group(result["id"], status=(200, 404))


def test_create_group_without_name(shared_zone_test_context):
    """
    Tests that creating a group without a name fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    new_group = {
        "email": create_unique_email_address(),
        "description": "this is a description",
        "members": [{"id": "ok"}],
        "admins": [{"id": "ok"}]
    }
    errors = client.create_group(new_group, status=400)["errors"]
    assert_that(errors[0], is_("Missing Group.name"))


def test_create_group_without_email(shared_zone_test_context):
    """
    Tests that creating a group without an email fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    new_group = {
        "name": "without-email",
        "description": "this is a description",
        "members": [{"id": "ok"}],
        "admins": [{"id": "ok"}]
    }
    errors = client.create_group(new_group, status=400)["errors"]
    assert_that(errors[0], is_("Missing Group.email"))


def test_create_group_without_name_or_email(shared_zone_test_context):
    """
    Tests that creating a group without name or an email fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    new_group = {
        "description": "this is a description",
        "members": [{"id": "ok"}],
        "admins": [{"id": "ok"}]
    }
    errors = client.create_group(new_group, status=400)["errors"]
    assert_that(errors, has_length(2))
    assert_that(errors, contains_inanyorder(
        "Missing Group.name",
        "Missing Group.email"
    ))


def test_create_group_without_members_or_admins(shared_zone_test_context):
    """
    Tests that creating a group without members or admins fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    new_group = {
        "name": "some-group-name",
        "email": create_unique_email_address(),
        "description": "this is a description"
    }
    errors = client.create_group(new_group, status=400)["errors"]
    assert_that(errors, has_length(2))
    assert_that(errors, contains_inanyorder(
        "Missing Group.members",
        "Missing Group.admins"
    ))


def test_create_group_adds_admins_as_members(shared_zone_test_context):
    """
    Tests that creating a group adds admins as members
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = None
    try:

        new_group = {
            "name": "test-create-group-add-admins-as-members",
            "email": create_unique_email_address(),
            "description": "this is a description",
            "members": [],
            "admins": [{"id": "ok"}]
        }
        result = client.create_group(new_group, status=200)

        assert_that(result["name"], is_(new_group["name"]))
        assert_that(result["email"], is_(new_group["email"]))
        assert_that(result["description"], is_(new_group["description"]))
        assert_that(result["status"], is_("Active"))
        assert_that(result["created"], not_none())
        assert_that(result["id"], not_none())
        assert_that(result["members"][0]["id"], is_("ok"))
        assert_that(result["admins"][0]["id"], is_("ok"))
    finally:
        if result:
            client.delete_group(result["id"], status=(200, 404))


def test_create_group_duplicate(shared_zone_test_context):
    """
    Tests that creating a group that has already been created fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = None
    try:
        new_group = {
            "name": "test-create-group-duplicate",
            "email": create_unique_email_address(),
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}]
        }

        result = client.create_group(new_group, status=200)
        client.create_group(new_group, status=409)
    finally:
        if result:
            client.delete_group(result["id"], status=(200, 404))


# `xfail` is used to avoid traceback, as we expect the test to fail when unique-email config was set true which is
# the purpose of this test.
@pytest.mark.xfail(reason="Fails when unique-email config was set true, else will pass the test.")
def test_create_group_email_conflict(shared_zone_test_context):
    """
    Tests that we cannot create a group with an email already in use, when unique email configuration is set true
    """
    client = shared_zone_test_context.ok_vinyldns_client
    conflict_group = None
    try:
        new_group = {
            'name': 'test_create_group_email_conflict',
            'email': create_unique_email_address(),
            'description': 'this is a description',
            'members': [{'id': 'ok'}],
            'admins': [{'id': 'ok'}]
        }
        conflict_group = client.create_group(new_group, status=200)
        assert_that(conflict_group['email'], is_(new_group['email']))
        old_email = conflict_group['email']
        other_group = {
            'name': 'create_other_group',
            'email': old_email,
            'description': 'this is a description',
            'members': [{'id': 'ok'}],
            'admins': [{'id': 'ok'}]
        }
        # Status code will be 409 and test fails if unique email configuration was enabled in reference.conf,
        # else it'll be 200
        client.create_group(other_group, status=200)
    finally:
        if conflict_group:
            client.delete_group(conflict_group['id'], status=(200, 404))


def test_create_group_no_members(shared_zone_test_context):
    """
    Tests that creating a group that has no members adds current user as a member and an admin
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = None

    try:
        new_group = {
            "name": "test-create-group-no-members",
            "email": create_unique_email_address(),
            "description": "this is a description",
            "members": [],
            "admins": []
        }

        result = client.create_group(new_group, status=200)
        assert_that(result["members"][0]["id"], is_("ok"))
        assert_that(result["admins"][0]["id"], is_("ok"))
    finally:
        if result:
            client.delete_group(result["id"], status=(200, 404))


def test_create_group_adds_admins_to_member_list(shared_zone_test_context):
    """
    Tests that creating a group adds admins to member list
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = None

    try:
        new_group = {
            "name": "test-create-group-add-admins-to-members",
            "email": create_unique_email_address(),
            "description": "this is a description",
            "members": [{"id": "ok"}],
            "admins": [{"id": "dummy"}]
        }

        result = client.create_group(new_group, status=200)
        assert_that([x["id"] for x in result["members"]], contains_exactly("ok", "dummy"))
        assert_that(result["admins"][0]["id"], is_("dummy"))
    finally:
        if result:
            client.delete_group(result["id"], status=(200, 404))
