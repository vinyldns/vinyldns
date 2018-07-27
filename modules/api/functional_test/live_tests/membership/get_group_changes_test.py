import pytest
import datetime

from hamcrest import *

from vinyldns_python import VinylDNSClient

@pytest.fixture(scope="module")
def group_activity_context(request, shared_zone_test_context):
    client = shared_zone_test_context.ok_vinyldns_client
    created_group = None

    group_name = 'test-list-group-activity-max-item-success'

    # cleanup existing group if it's already in there
    groups = client.list_all_my_groups()
    existing = [grp for grp in groups if grp['name'] == group_name]
    for grp in existing:
        client.delete_group(grp['id'], status=200)


    members = [ { 'id': 'ok'} ]
    new_group = {
        'name': group_name,
        'email': 'test@test.com',
        'members': members,
        'admins': [ { 'id': 'ok'} ]
    }
    created_group = client.create_group(new_group, status=200)

    update_groups = []
    updated_groups = []
    # each update changes the member
    for runner in range(0, 200):
        id = "dummy{0:0>3}".format(runner)
        members = [{ 'id': id }]
        update_groups.append({
            'id': created_group['id'],
            'name': group_name,
            'email': 'test@test.com',
            'members': members,
            'admins': [ { 'id': 'ok'} ]
        })
        updated_groups.append(client.update_group(update_groups[runner]['id'], update_groups[runner], status=200))

    def fin():
        if created_group:
            client.delete_group(created_group['id'], status=(200,404))

    request.addfinalizer(fin)

    return {
        'created_group': created_group,
        'updated_groups': updated_groups
    }


def test_list_group_activity_start_from_success(group_activity_context, shared_zone_test_context):
    """
    Test that we can list the changes starting from a given timestamp
    """

    client = shared_zone_test_context.ok_vinyldns_client
    created_group = group_activity_context['created_group']
    updated_groups = group_activity_context['updated_groups']

    page_one = client.get_group_changes(created_group['id'], status=200)

    start_from_index = 50
    start_from = page_one['changes'][start_from_index]['created']  # start from a known good timestamp

    result = client.get_group_changes(created_group['id'], start_from=start_from, status=200)

    assert_that(result['changes'], has_length(100))
    assert_that(result['maxItems'], is_(100))
    assert_that(result['startFrom'], is_(start_from))
    assert_that(result['nextId'], is_not(none()))

    for i in range(0,100):
        assert_that(result['changes'][i]['newGroup'], is_(updated_groups[199-start_from_index-i-1]))
        assert_that(result['changes'][i]['oldGroup'], is_(updated_groups[199-start_from_index-i-2]))

def test_list_group_activity_start_from_fake_time(group_activity_context, shared_zone_test_context):
    """
    Test that we can start from a fake time stamp
    """

    client = shared_zone_test_context.ok_vinyldns_client
    created_group = group_activity_context['created_group']
    updated_groups = group_activity_context['updated_groups']
    start_from = '9999999999999'  # start from a random timestamp far in the future

    result = client.get_group_changes(created_group['id'], start_from=start_from, status=200)

    # there are 200 updates, and 1 create
    assert_that(result['changes'], has_length(100))
    assert_that(result['maxItems'], is_(100))
    assert_that(result['startFrom'], is_(start_from))
    assert_that(result['nextId'], is_not(none()))

    for i in range(0,100):
        assert_that(result['changes'][i]['newGroup'], is_(updated_groups[199-i]))
        assert_that(result['changes'][i]['oldGroup'], is_(updated_groups[199-i-1]))


def test_list_group_activity_max_item_success(group_activity_context, shared_zone_test_context):
    """
    Test that we can set the max_items returned
    """

    client = shared_zone_test_context.ok_vinyldns_client
    created_group = group_activity_context['created_group']
    updated_groups = group_activity_context['updated_groups']

    result = client.get_group_changes(created_group['id'], max_items=50, status=200)

    # there are 200 updates, and 1 create
    assert_that(result['changes'], has_length(50))
    assert_that(result['maxItems'], is_(50))
    assert_that(result, is_not(has_key('startFrom')))
    assert_that(result['nextId'], is_not(none()))

    for i in range(0,50):
        assert_that(result['changes'][i]['newGroup'], is_(updated_groups[199-i]))
        assert_that(result['changes'][i]['oldGroup'], is_(updated_groups[199-i-1]))


def test_list_group_activity_max_item_zero(group_activity_context, shared_zone_test_context):
    """
    Test that max_item set to zero fails
    """

    client = shared_zone_test_context.ok_vinyldns_client
    created_group = group_activity_context['created_group']
    client.get_group_changes(created_group['id'], max_items=0, status=400)


def test_list_group_activity_max_item_over_1000(group_activity_context, shared_zone_test_context):
    """
    Test that when max_item is over 1000 fails
    """

    client = shared_zone_test_context.ok_vinyldns_client
    created_group = group_activity_context['created_group']
    client.get_group_changes(created_group['id'], max_items=1001, status=400)


def test_get_group_changes_paging(group_activity_context, shared_zone_test_context):
    """
    Test that we can page through multiple pages of group changes
    """

    client = shared_zone_test_context.ok_vinyldns_client
    created_group = group_activity_context['created_group']
    updated_groups = group_activity_context['updated_groups']

    page_one = client.get_group_changes(created_group['id'], max_items=100, status=200)
    page_two = client.get_group_changes(created_group['id'], start_from=page_one['nextId'], max_items=100, status=200)
    page_three = client.get_group_changes(created_group['id'], start_from=page_two['nextId'], max_items=100, status=200)

    assert_that(page_one['changes'], has_length(100))
    assert_that(page_one['maxItems'], is_(100))
    assert_that(page_one, is_not(has_key('startFrom')))
    assert_that(page_one['nextId'], is_not(none()))

    for i in range(0, 100):
        assert_that(page_one['changes'][i]['newGroup'], is_(updated_groups[199-i]))
        assert_that(page_one['changes'][i]['oldGroup'], is_(updated_groups[199-i-1]))

    assert_that(page_two['changes'], has_length(100))
    assert_that(page_two['maxItems'], is_(100))
    assert_that(page_two['startFrom'], is_(page_one['nextId']))
    assert_that(page_two['nextId'], is_not(none()))

    for i in range(100, 199):
        assert_that(page_two['changes'][i-100]['newGroup'], is_(updated_groups[199-i]))
        assert_that(page_two['changes'][i-100]['oldGroup'], is_(updated_groups[199-i-1]))
    assert_that(page_two['changes'][99]['oldGroup'], is_(created_group))

    assert_that(page_three['changes'], has_length(1))
    assert_that(page_three['maxItems'], is_(100))
    assert_that(page_three['startFrom'], is_(page_two['nextId']))
    assert_that(page_three, is_not(has_key('nextId')))

    assert_that(page_three['changes'][0]['newGroup'], is_(created_group))

def test_get_group_changes_unauthed(shared_zone_test_context):
    """
    Tests that we cant get group changes without access
    """

    client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    saved_group = None
    try:
        new_group = {
            'name': 'test-list-group-admins-unauthed',
            'email': 'test@test.com',
            'members': [ { 'id': 'ok'} ],
            'admins': [ { 'id': 'ok'} ]
        }
        saved_group = client.create_group(new_group, status=200)

        dummy_client.get_group_changes(saved_group['id'], status=403)
        client.get_group_changes(saved_group['id'], status=200)
    finally:
        if saved_group:
            client.delete_group(saved_group['id'], status=(200,404))

