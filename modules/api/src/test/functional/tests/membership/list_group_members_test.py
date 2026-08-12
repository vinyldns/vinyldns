from hamcrest import *


def test_list_group_members_success(shared_zone_test_context):
    """
    Test that we can list all the members of a group
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None

    try:
        new_group = {
            "name": "test-list-group-members-success",
            "email": "test@test.com",
            "members": [{"id": "ok"}, {"id": "dummy"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }

        members = ["dummy", "ok"]
        saved_group = client.create_group(new_group, status=200)
        result = client.get_group(saved_group["id"], status=200)
        assert_that(result["members"], has_length(len(members)))

        result_member_ids = [member["id"] for member in result["members"]]
        for identifier in members:
            assert_that(result_member_ids, has_item(identifier))

        result = client.list_members_group(saved_group["id"], status=200)
        result = sorted(result["members"], key=lambda user: user["id"])

        assert_that(result, has_length(len(members)))
        dummy = result[0]
        assert_that(dummy["id"], is_("dummy"))
        assert_that(dummy["userName"], is_("dummy"))
        assert_that(dummy["isAdmin"], is_(False))
        assert_that(dummy["lockStatus"], is_("Unlocked"))
        assert_that(dummy, is_not(has_key("firstName")))
        assert_that(dummy, is_not(has_key("lastName")))
        assert_that(dummy, is_not(has_key("email")))
        assert_that(dummy["created"], is_not(none()))

        ok = result[1]
        assert_that(ok["id"], is_("ok"))
        assert_that(ok["userName"], is_("ok"))
        assert_that(ok["isAdmin"], is_(True))
        assert_that(ok["firstName"], is_("ok"))
        assert_that(ok["lastName"], is_("ok"))
        assert_that(ok["email"], is_("test@test.com"))
        assert_that(ok["created"], is_not(none()))
        assert_that(ok["lockStatus"], is_("Unlocked"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_list_group_members_not_found(shared_zone_test_context):
    """
    Tests that we can not list the members of a non-existent group
    """
    client = shared_zone_test_context.ok_vinyldns_client

    client.list_members_group("not_found", status=404)


def test_list_group_members_start_from(shared_zone_test_context):
    """
    Test that we can list the members starting from a given user
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None
    try:
        members = []
        for runner in range(0, 200):
            member_id = "dummy{0:0>3}".format(runner)
            members.append({"id": member_id})

        new_group = {
            "name": "test-list-group-members-start-from",
            "email": "test@test.com",
            "members": members,
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }

        saved_group = client.create_group(new_group, status=200)
        result = client.get_group(saved_group["id"], status=200)

        # members has one more because admins are added as members
        assert_that(result["members"], has_length(len(members) + 1))
        assert_that(result["members"], has_item({"id": "ok"}))
        result_member_ids = [member["id"] for member in result["members"]]
        for user in members:
            assert_that(result_member_ids, has_item(user["id"]))

        result = client.list_members_group(saved_group["id"], start_from="dummy050", status=200)

        group_members = sorted(result["members"], key=lambda user: user["id"])

        assert_that(result["startFrom"], is_("dummy050"))
        assert_that(result["nextId"], is_("dummy150"))

        assert_that(group_members, has_length(100))
        for i in range(0, len(group_members) - 1):
            dummy = group_members[i]
            member_id = "dummy{0:0>3}".format(i + 51)  # starts from dummy051
            user_name = "name-" + member_id
            assert_that(dummy["id"], is_(member_id))
            assert_that(dummy["userName"], is_(user_name))
            assert_that(dummy["isAdmin"], is_(False))
            assert_that(dummy, is_not(has_key("firstName")))
            assert_that(dummy, is_not(has_key("lastName")))
            assert_that(dummy, is_not(has_key("email")))
            assert_that(dummy["created"], is_not(none()))
            assert_that(dummy["lockStatus"], is_("Unlocked"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_list_group_members_start_from_non_user(shared_zone_test_context):
    """
    Test that we can list the members starting from a non existent username
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None
    try:
        members = []
        for runner in range(0, 200):
            member_id = "dummy{0:0>3}".format(runner)
            members.append({"id": member_id})

        new_group = {
            "name": "test-list-group-members-start-from-nonexistent",
            "email": "test@test.com",
            "members": members,
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }

        saved_group = client.create_group(new_group, status=200)
        result = client.get_group(saved_group["id"], status=200)

        # members has one more because admins are added as members
        assert_that(result["members"], has_length(len(members) + 1))
        result_member_ids = [member["id"] for member in result["members"]]
        assert_that(result_member_ids, has_item("ok"))
        for user in members:
            assert_that(result_member_ids, has_item(user["id"]))

        result = client.list_members_group(saved_group["id"], start_from="abc", status=200)

        group_members = sorted(result["members"], key=lambda user: user["id"])

        assert_that(result["startFrom"], is_("abc"))
        assert_that(result["nextId"], is_("dummy099"))

        assert_that(group_members, has_length(100))
        for i in range(0, len(group_members) - 1):
            dummy = group_members[i]
            member_id = "dummy{0:0>3}".format(i)
            user_name = "name-" + member_id
            assert_that(dummy["id"], is_(member_id))
            assert_that(dummy["userName"], is_(user_name))
            assert_that(dummy["isAdmin"], is_(False))
            assert_that(dummy, is_not(has_key("firstName")))
            assert_that(dummy, is_not(has_key("lastName")))
            assert_that(dummy, is_not(has_key("email")))
            assert_that(dummy["created"], is_not(none()))
            assert_that(dummy["lockStatus"], is_("Unlocked"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_list_group_members_max_item(shared_zone_test_context):
    """
    Test that we can chose the number of items to list
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None
    try:
        members = []
        for runner in range(0, 200):
            members.append({"id": "dummy{0:0>3}".format(runner)})

        new_group = {
            "name": "test-list-group-members-max-items",
            "email": "test@test.com",
            "members": members,
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }

        saved_group = client.create_group(new_group, status=200)
        result = client.get_group(saved_group["id"], status=200)

        # members has one more because admins are added as members
        assert_that(result["members"], has_length(len(members) + 1))
        result_member_ids = [member["id"] for member in result["members"]]
        assert_that(result_member_ids, has_item("ok"))
        for user in members:
            assert_that(result_member_ids, has_item(user["id"]))

        result = client.list_members_group(saved_group["id"], max_items=10, status=200)

        group_members = sorted(result["members"], key=lambda user: user["id"])

        assert_that(result["nextId"], is_("dummy009"))
        assert_that(result["maxItems"], is_(10))

        assert_that(group_members, has_length(10))
        for i in range(0, len(group_members) - 1):
            dummy = group_members[i]
            member_id = "dummy{0:0>3}".format(i)
            user_name = "name-" + member_id
            assert_that(dummy["id"], is_(member_id))
            assert_that(dummy["userName"], is_(user_name))
            assert_that(dummy["isAdmin"], is_(False))
            assert_that(dummy, is_not(has_key("firstName")))
            assert_that(dummy, is_not(has_key("lastName")))
            assert_that(dummy, is_not(has_key("email")))
            assert_that(dummy["created"], is_not(none()))
            assert_that(dummy["lockStatus"], is_("Unlocked"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_list_group_members_max_item_default(shared_zone_test_context):
    """
    Test that the default for max_item is 100 items
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None
    try:
        members = []
        for runner in range(0, 200):
            members.append({"id": "dummy{0:0>3}".format(runner)})

        new_group = {
            "name": "test-list-group-members-max-items-default",
            "email": "test@test.com",
            "members": members,
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }

        saved_group = client.create_group(new_group, status=200)
        result = client.get_group(saved_group["id"], status=200)

        # members has one more because admins are added as members
        assert_that(result["members"], has_length(len(members) + 1))
        result_member_ids = [member["id"] for member in result["members"]]
        assert_that(result_member_ids, has_item("ok"))
        for user in members:
            assert_that(result_member_ids, has_item(user["id"]))

        result = client.list_members_group(saved_group["id"], status=200)

        group_members = sorted(result["members"], key=lambda user: user["id"])

        assert_that(result["nextId"], is_("dummy099"))

        assert_that(group_members, has_length(100))
        for i in range(0, len(group_members) - 1):
            dummy = group_members[i]
            id = "dummy{0:0>3}".format(i)
            user_name = "name-" + id
            assert_that(dummy["id"], is_(id))
            assert_that(dummy["userName"], is_(user_name))
            assert_that(dummy["isAdmin"], is_(False))
            assert_that(dummy, is_not(has_key("firstName")))
            assert_that(dummy, is_not(has_key("lastName")))
            assert_that(dummy, is_not(has_key("email")))
            assert_that(dummy["created"], is_not(none()))
            assert_that(dummy["lockStatus"], is_("Unlocked"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_list_group_members_max_item_zero(shared_zone_test_context):
    """
    Test that the call fails when max_item is 0
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None
    try:
        members = []
        for runner in range(0, 200):
            members.append({"id": "dummy{0:0>3}".format(runner)})

        new_group = {
            "name": "test-list-group-members-max-items-zero",
            "email": "test@test.com",
            "members": members,
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }

        saved_group = client.create_group(new_group, status=200)
        result = client.get_group(saved_group["id"], status=200)

        # members has one more because admins are added as members
        assert_that(result["members"], has_length(len(members) + 1))
        result_member_ids = [member["id"] for member in result["members"]]
        assert_that(result_member_ids, has_item("ok"))
        for user in members:
            assert_that(result_member_ids, has_item(user["id"]))

        client.list_members_group(saved_group["id"], max_items=0, status=400)
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_list_group_members_max_item_over_1000(shared_zone_test_context):
    """
    Test that the call fails when max_item is over 1000
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None
    try:
        members = []
        for runner in range(0, 200):
            members.append({"id": "dummy{0:0>3}".format(runner)})

        new_group = {
            "name": "test-list-group-members-max-items-over-limit",
            "email": "test@test.com",
            "members": members,
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }

        saved_group = client.create_group(new_group, status=200)
        result = client.get_group(saved_group["id"], status=200)

        # members has one more because admins are added as members
        assert_that(result["members"], has_length(len(members) + 1))
        result_member_ids = [member["id"] for member in result["members"]]
        assert_that(result_member_ids, has_item("ok"))
        for user in members:
            assert_that(result_member_ids, has_item(user["id"]))

        client.list_members_group(saved_group["id"], max_items=1001, status=400)
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_list_group_members_next_id_correct(shared_zone_test_context):
    """
    Test that the correct next_id is returned
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None
    try:
        members = []
        for runner in range(0, 200):
            members.append({"id": "dummy{0:0>3}".format(runner)})

        new_group = {
            "name": "test-list-group-members-next-id",
            "email": "test@test.com",
            "members": members,
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }

        saved_group = client.create_group(new_group, status=200)
        result = client.get_group(saved_group["id"], status=200)

        # members has one more because admins are added as members
        assert_that(result["members"], has_length(len(members) + 1))
        result_member_ids = [member["id"] for member in result["members"]]
        assert_that(result_member_ids, has_item("ok"))
        for user in members:
            assert_that(result_member_ids, has_item(user["id"]))

        result = client.list_members_group(saved_group["id"], status=200)

        group_members = sorted(result["members"], key=lambda user: user["id"])

        assert_that(result["nextId"], is_("dummy099"))

        assert_that(group_members, has_length(100))
        for i in range(0, len(group_members) - 1):
            dummy = group_members[i]
            member_id = "dummy{0:0>3}".format(i)
            user_name = "name-" + member_id
            assert_that(dummy["id"], is_(member_id))
            assert_that(dummy["userName"], is_(user_name))
            assert_that(dummy["isAdmin"], is_(False))
            assert_that(dummy, is_not(has_key("firstName")))
            assert_that(dummy, is_not(has_key("lastName")))
            assert_that(dummy, is_not(has_key("email")))
            assert_that(dummy["created"], is_not(none()))
            assert_that(dummy["lockStatus"], is_("Unlocked"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_list_group_members_next_id_exhausted(shared_zone_test_context):
    """
    Test that the next_id is null when the list is exhausted
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None
    try:
        members = []
        for runner in range(0, 5):
            members.append({"id": "dummy{0:0>3}".format(runner)})

        new_group = {
            "name": "test-list-group-members-next-id-exhausted",
            "email": "test@test.com",
            "members": members,
            "admins": [{"id": "ok"}]
        }

        saved_group = client.create_group(new_group, status=200)
        result = client.get_group(saved_group["id"], status=200)

        # members has one more because admins are added as members
        assert_that(result["members"], has_length(len(members) + 1))
        result_member_ids = [member["id"] for member in result["members"]]
        assert_that(result_member_ids, has_item("ok"))
        for user in members:
            assert_that(result_member_ids, has_item(user["id"]))

        result = client.list_members_group(saved_group["id"], status=200)

        group_members = sorted(result["members"], key=lambda user: user["id"])

        assert_that(result, is_not(has_key("nextId")))

        assert_that(group_members, has_length(6))  # add one more for the admin
        for i in range(0, len(group_members) - 1):
            dummy = group_members[i]
            member_id = "dummy{0:0>3}".format(i)
            user_name = "name-" + member_id
            assert_that(dummy["id"], is_(member_id))
            assert_that(dummy["userName"], is_(user_name))
            assert_that(dummy, is_not(has_key("firstName")))
            assert_that(dummy, is_not(has_key("lastName")))
            assert_that(dummy, is_not(has_key("email")))
            assert_that(dummy["created"], is_not(none()))
            assert_that(dummy["lockStatus"], is_("Unlocked"))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_list_group_members_next_id_exhausted_two_pages(shared_zone_test_context):
    """
    Test that the next_id is null when the list is exhausted over 2 pages
    """
    client = shared_zone_test_context.ok_vinyldns_client
    saved_group = None
    try:
        members = []
        for runner in range(0, 19):
            member_id = "dummy{0:0>3}".format(runner)
            members.append({"id": member_id})

        new_group = {
            "name": "test-list-group-members-next-id-exhausted-two-pages",
            "email": "test@test.com",
            "members": members,
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }

        saved_group = client.create_group(new_group, status=200)
        result = client.get_group(saved_group["id"], status=200)

        # members has one more because admins are added as members
        assert_that(result["members"], has_length(len(members) + 1))
        result_member_ids = [member["id"] for member in result["members"]]
        assert_that(result_member_ids, has_item("ok"))
        for user in members:
            assert_that(result_member_ids, has_item(user["id"]))

        first_page = client.list_members_group(saved_group["id"], max_items=10, status=200)

        group_members = sorted(first_page["members"], key=lambda user: user["id"])

        assert_that(first_page["nextId"], is_("dummy009"))
        assert_that(first_page["maxItems"], is_(10))

        assert_that(group_members, has_length(10))
        for i in range(0, len(group_members) - 1):
            dummy = group_members[i]
            member_id = "dummy{0:0>3}".format(i)
            user_name = "name-" + member_id
            assert_that(dummy["id"], is_(member_id))
            assert_that(dummy["userName"], is_(user_name))
            assert_that(dummy, is_not(has_key("firstName")))
            assert_that(dummy, is_not(has_key("lastName")))
            assert_that(dummy, is_not(has_key("email")))
            assert_that(dummy["created"], is_not(none()))
            assert_that(dummy["lockStatus"], is_("Unlocked"))

        second_page = client.list_members_group(saved_group["id"],
                                                start_from=first_page["nextId"],
                                                max_items=10,
                                                status=200)

        group_members = sorted(second_page["members"], key=lambda user: user["id"])

        assert_that(second_page, is_not(has_key("nextId")))
        assert_that(second_page["maxItems"], is_(10))

        assert_that(group_members, has_length(10))
        for i in range(0, len(group_members) - 1):
            dummy = group_members[i]
            member_id = "dummy{0:0>3}".format(i + 10)
            user_name = "name-" + member_id
            assert_that(dummy["id"], is_(member_id))
            assert_that(dummy["userName"], is_(user_name))
            assert_that(dummy, is_not(has_key("firstName")))
            assert_that(dummy, is_not(has_key("lastName")))
            assert_that(dummy, is_not(has_key("email")))
            assert_that(dummy["created"], is_not(none()))
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))


def test_list_group_members_unauthed(shared_zone_test_context):
    """
    Tests that non-group members can still list group members
    """
    client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    saved_group = None
    try:
        new_group = {
            "name": "test-list-group-members-unauthed",
            "email": "test@test.com",
            "members": [{"id": "ok"}],
            "admins": [{"id": "ok"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        saved_group = client.create_group(new_group, status=200)

        dummy_client.list_members_group(saved_group["id"], status=200)
        client.list_members_group(saved_group["id"], status=200)
    finally:
        if saved_group:
            client.delete_group(saved_group["id"], status=(200, 404))
