import pytest
import json

from hamcrest import *
from vinyldns_python import VinylDNSClient
from utils import *
from vinyldns_context import VinylDNSTestContext

class ListGroupsSearchContext(object):
    def __init__(self):
        self.client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, access_key='listGroupAccessKey', secret_key='listGroupSecretKey')
        self.support_user_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'supportUserAccessKey', 'supportUserSecretKey')
        self.tear_down() # ensures that the environment is clean before starting

        try:
            for runner in range(0, 50):
                new_group = {
                    'name': "test-list-my-groups-{0:0>3}".format(runner),
                    'email': 'test@test.com',
                    'members': [ { 'id': 'list-group-user'} ],
                    'admins': [ { 'id': 'list-group-user'} ]
                }
                self.client.create_group(new_group, status=200)

        except:
            # teardown if there was any issue in setup
            try:
                self.tear_down()
            except:
                pass
            raise

    def tear_down(self):
        clear_zones(self.client)
        clear_groups(self.client)

@pytest.fixture(scope="module")
def list_my_groups_context(request):
    ctx = ListGroupsSearchContext()

    def fin():
        ctx.tear_down()

    request.addfinalizer(fin)

    return ctx

def test_list_my_groups_no_parameters(list_my_groups_context):
    """
    Test that we can get all the groups where a user is a member
    """

    results = list_my_groups_context.client.list_my_groups(status=200)

    assert_that(results, has_length(3))  # 3 fields

    assert_that(results['groups'], has_length(50))
    assert_that(results, is_not(has_key('groupNameFilter')))
    assert_that(results, is_not(has_key('startFrom')))
    assert_that(results, is_not(has_key('nextId')))
    assert_that(results['maxItems'], is_(100))

    results['groups'] = sorted(results['groups'], key=lambda x: x['name'])

    for i in range(0, 50):
        assert_that(results['groups'][i]['name'], is_("test-list-my-groups-{0:0>3}".format(i)))


def test_get_my_groups_using_old_account_auth(list_my_groups_context):
    """
    Test passing in an account will return an empty set
    """
    results = list_my_groups_context.client.list_my_groups(status=200)
    assert_that(results, has_length(3))
    assert_that(results, is_not(has_key('groupNameFilter')))
    assert_that(results, is_not(has_key('startFrom')))
    assert_that(results, is_not(has_key('nextId')))
    assert_that(results['maxItems'], is_(100))


def test_list_my_groups_max_items(list_my_groups_context):
    """
    Tests that when maxItem is set, only return #maxItems items
    """
    results = list_my_groups_context.client.list_my_groups(max_items=5, status=200)

    assert_that(results, has_length(4))  # 4 fields

    assert_that(results, has_key('groups'))
    assert_that(results, is_not(has_key('groupNameFilter')))
    assert_that(results, is_not(has_key('startFrom')))
    assert_that(results, has_key('nextId'))
    assert_that(results['maxItems'], is_(5))


def test_list_my_groups_paging(list_my_groups_context):
    """
    Tests that we can return all items by paging
    """
    results=list_my_groups_context.client.list_my_groups(max_items=20, status=200)

    assert_that(results, has_length(4))  # 4 fields
    assert_that(results, has_key('groups'))
    assert_that(results, is_not(has_key('groupNameFilter')))
    assert_that(results, is_not(has_key('startFrom')))
    assert_that(results, has_key('nextId'))
    assert_that(results['maxItems'], is_(20))

    while 'nextId' in results:
        prev = results
        results = list_my_groups_context.client.list_my_groups(max_items=20, start_from=results['nextId'], status=200)

        if 'nextId' in results:
            assert_that(results, has_length(5))  # 5 fields
            assert_that(results, has_key('groups'))
            assert_that(results, is_not(has_key('groupNameFilter')))
            assert_that(results['startFrom'], is_(prev['nextId']))
            assert_that(results, has_key('nextId'))
            assert_that(results['maxItems'], is_(20))

        else:
            assert_that(results, has_length(4))  # 4 fields
            assert_that(results, has_key('groups'))
            assert_that(results, is_not(has_key('groupNameFilter')))
            assert_that(results['startFrom'], is_(prev['nextId']))
            assert_that(results, is_not(has_key('nextId')))
            assert_that(results['maxItems'], is_(20))


def test_list_my_groups_filter_matches(list_my_groups_context):
    """
    Tests that only matched groups are returned
    """
    results = list_my_groups_context.client.list_my_groups(group_name_filter="test-list-my-groups-01", status=200)

    assert_that(results, has_length(4))  # 4 fields

    assert_that(results['groups'], has_length(10))
    assert_that(results['groupNameFilter'], is_('test-list-my-groups-01'))
    assert_that(results, is_not(has_key('startFrom')))
    assert_that(results, is_not(has_key('nextId')))
    assert_that(results['maxItems'], is_(100))

    results['groups'] = sorted(results['groups'], key=lambda x: x['name'])

    for i in range(0, 10):
        assert_that(results['groups'][i]['name'], is_("test-list-my-groups-{0:0>3}".format(i+10)))


def test_list_my_groups_no_deleted(list_my_groups_context):
    """
    Tests that no deleted groups are returned
    """
    results=list_my_groups_context.client.list_my_groups(max_items=100, status=200)

    assert_that(results, has_key('groups'))
    for g in results['groups']:
        assert_that(g['status'], is_not('Deleted'))

    while 'nextId' in results:
        results = client.list_my_groups(max_items=20, group_name_filter="test-list-my-groups-", start_from=results['nextId'], status=200)

        assert_that(results, has_key('groups'))
        for g in results['groups']:
            assert_that(g['status'], is_not('Deleted'))

def test_list_my_groups_with_ignore_access_true(list_my_groups_context):
    """
    Test that we can get all the groups whether a user is a member or not
    """

    results = list_my_groups_context.client.list_my_groups(ignore_access=True, status=200)

    assert_that(len(results['groups']), greater_than(50))
    assert_that(results['maxItems'], is_(100))
    assert_that(results['ignoreAccess'], is_(True))

    my_results = list_my_groups_context.client.list_my_groups(status=200)
    my_results['groups'] = sorted(my_results['groups'], key=lambda x: x['name'])

    for i in range(0, 50):
        assert_that(my_results['groups'][i]['name'], is_("test-list-my-groups-{0:0>3}".format(i)))

def test_list_my_groups_as_support_user(list_my_groups_context):
    """
    Test that we can get all the groups as a support user, even without ignore_access
    """

    results = list_my_groups_context.support_user_client.list_my_groups(status=200)

    assert_that(len(results['groups']), greater_than(50))
    assert_that(results['maxItems'], is_(100))
    assert_that(results['ignoreAccess'], is_(False))

def test_list_my_groups_as_support_user_with_ignore_access_true(list_my_groups_context):
    """
    Test that we can get all the groups as a support user
    """

    results = list_my_groups_context.support_user_client.list_my_groups(ignore_access=True, status=200)

    assert_that(len(results['groups']), greater_than(50))
    assert_that(results['maxItems'], is_(100))
    assert_that(results['ignoreAccess'], is_(True))
