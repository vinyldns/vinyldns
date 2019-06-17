import pytest
import uuid

from hamcrest import *
from vinyldns_python import VinylDNSClient
from vinyldns_context import VinylDNSTestContext
from utils import *


class ListZonesTestContext(object):
    def __init__(self):
        self.client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'listZonesAccessKey', 'listZonesSecretKey')
        self.tear_down() # ensures that the environment is clean before starting

        try:
            group = {
                'name': 'list-zones-group',
                'email': 'test@test.com',
                'description': 'this is a description',
                'members': [ { 'id': 'list-zones-user'} ],
                'admins': [ { 'id': 'list-zones-user'} ]
            }

            self.list_zones_group = self.client.create_group(group, status=200)

            search_zone_1_change = self.client.create_zone(
                {
                    'name': 'list-zones-test-searched-1.',
                    'email': 'test@test.com',
                    'shared': False,
                    'adminGroupId': self.list_zones_group['id'],
                    'isTest': True,
                    'backendId': 'func-test-backend'
                }, status=202)
            self.search_zone_1 = search_zone_1_change['zone']

            search_zone_2_change = self.client.create_zone(
                {
                    'name': 'list-zones-test-searched-2.',
                    'email': 'test@test.com',
                    'shared': False,
                    'adminGroupId': self.list_zones_group['id'],
                    'isTest': True,
                    'backendId': 'func-test-backend'
                }, status=202)
            self.search_zone_2 = search_zone_2_change['zone']


            search_zone_3_change = self.client.create_zone(
                {
                    'name': 'list-zones-test-searched-3.',
                    'email': 'test@test.com',
                    'shared': False,
                    'adminGroupId': self.list_zones_group['id'],
                    'isTest': True,
                    'backendId': 'func-test-backend'
                }, status=202)
            self.search_zone_3 = search_zone_3_change['zone']

            non_search_zone_1_change = self.client.create_zone(
                {
                    'name': 'list-zones-test-unfiltered-1.',
                    'email': 'test@test.com',
                    'shared': False,
                    'adminGroupId': self.list_zones_group['id'],
                    'isTest': True,
                    'backendId': 'func-test-backend'
                }, status=202)
            self.non_search_zone_1 = non_search_zone_1_change['zone']

            non_search_zone_2_change = self.client.create_zone(
                {
                    'name': 'list-zones-test-unfiltered-2.',
                    'email': 'test@test.com',
                    'shared': False,
                    'adminGroupId': self.list_zones_group['id'],
                    'isTest': True,
                    'backendId': 'func-test-backend'
                }, status=202)
            self.non_search_zone_2 = non_search_zone_2_change['zone']

            self.zone_ids = [self.search_zone_1['id'], self.search_zone_2['id'], self.search_zone_3['id'], self.non_search_zone_1['id'], self.non_search_zone_2['id']]
            zone_changes = [search_zone_1_change, search_zone_2_change, search_zone_3_change, non_search_zone_1_change, non_search_zone_2_change]
            for change in zone_changes:
                self.client.wait_until_zone_active(change[u'zone'][u'id'])
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
def list_zones_context(request):
    ctx = ListZonesTestContext()

    def fin():
        ctx.tear_down()

    request.addfinalizer(fin)

    return ctx

def test_list_zones_success(list_zones_context):
    """
    Test that we can retrieve a list of the user's zones
    """
    result = list_zones_context.client.list_zones(status=200)
    retrieved = result['zones']

    assert_that(retrieved, has_length(5))
    assert_that(retrieved, has_item(has_entry('name', 'list-zones-test-searched-1.')))
    assert_that(retrieved, has_item(has_entry('adminGroupName', 'list-zones-group')))
    assert_that(retrieved, has_item(has_entry('backendId', 'func-test-backend')))


def test_list_zones_max_items_100(list_zones_context):
    """
    Test that the default max items for a list zones request is 100
    """
    result = list_zones_context.client.list_zones(status=200)
    assert_that(result['maxItems'], is_(100))

def test_list_zones_list_all_default_false(list_zones_context):
    """
    Test that the default list all value for a list zones request is false
    """
    result = list_zones_context.client.list_zones(status=200)
    assert_that(result['listAll'], is_(False))

def test_list_zones_invalid_max_items_fails(list_zones_context):
    """
    Test that passing in an invalid value for max items fails
    """
    errors = list_zones_context.client.list_zones(max_items=700, status=400)
    assert_that(errors, contains_string("maxItems was 700, maxItems must be between 0 and 100"))


def test_list_zones_no_authorization(list_zones_context):
    """
    Test that we cannot retrieve a list of zones without authorization
    """
    list_zones_context.client.list_zones(sign_request=False, status=401)


def test_list_zones_no_search_first_page(list_zones_context):
    """
    Test that the first page of listing zones returns correctly when no name filter is provided
    """
    result = list_zones_context.client.list_zones(max_items=3)
    zones = result['zones']

    assert_that(zones, has_length(3))
    assert_that(zones[0]['name'], is_('list-zones-test-searched-1.'))
    assert_that(zones[1]['name'], is_('list-zones-test-searched-2.'))
    assert_that(zones[2]['name'], is_('list-zones-test-searched-3.'))

    assert_that(result['nextId'], is_('list-zones-test-searched-3.'))
    assert_that(result['maxItems'], is_(3))
    assert_that(result, is_not(has_key('startFrom')))
    assert_that(result, is_not(has_key('nameFilter')))


def test_list_zones_no_search_second_page(list_zones_context):
    """
    Test that the second page of listing zones returns correctly when no name filter is provided
    """
    result = list_zones_context.client.list_zones(start_from="list-zones-test-searched-2.", max_items=2, status=200)
    zones = result['zones']

    assert_that(zones, has_length(2))
    assert_that(zones[0]['name'], is_('list-zones-test-searched-3.'))
    assert_that(zones[1]['name'], is_('list-zones-test-unfiltered-1.'))

    assert_that(result['nextId'], is_("list-zones-test-unfiltered-1."))
    assert_that(result['maxItems'], is_(2))
    assert_that(result['startFrom'], is_("list-zones-test-searched-2."))
    assert_that(result, is_not(has_key('nameFilter')))


def test_list_zones_no_search_last_page(list_zones_context):
    """
    Test that the last page of listing zones returns correctly when no name filter is provided
    """
    result = list_zones_context.client.list_zones(start_from="list-zones-test-searched-3.", max_items=4, status=200)
    zones = result['zones']

    assert_that(zones, has_length(2))
    assert_that(zones[0]['name'], is_('list-zones-test-unfiltered-1.'))
    assert_that(zones[1]['name'], is_('list-zones-test-unfiltered-2.'))

    assert_that(result, is_not(has_key('nextId')))
    assert_that(result['maxItems'], is_(4))
    assert_that(result['startFrom'], is_('list-zones-test-searched-3.'))
    assert_that(result, is_not(has_key('nameFilter')))


def test_list_zones_with_search_first_page(list_zones_context):
    """
    Test that the first page of listing zones returns correctly when a name filter is provided
    """
    result = list_zones_context.client.list_zones(name_filter='*searched*', max_items=2, status=200)
    zones = result['zones']

    assert_that(zones, has_length(2))
    assert_that(zones[0]['name'], is_('list-zones-test-searched-1.'))
    assert_that(zones[1]['name'], is_('list-zones-test-searched-2.'))

    assert_that(result['nextId'], is_('list-zones-test-searched-2.'))
    assert_that(result['maxItems'], is_(2))
    assert_that(result['nameFilter'], is_('*searched*'))
    assert_that(result, is_not(has_key('startFrom')))


def test_list_zones_with_no_results(list_zones_context):
    """
    Test that the response is formed correctly when no results are found
    """
    result = list_zones_context.client.list_zones(name_filter='this-wont-be-found', max_items=2, status=200)
    zones = result['zones']

    assert_that(zones, has_length(0))

    assert_that(result['maxItems'], is_(2))
    assert_that(result['nameFilter'], is_('this-wont-be-found'))
    assert_that(result, is_not(has_key('startFrom')))
    assert_that(result, is_not(has_key('nextId')))


def test_list_zones_with_search_last_page(list_zones_context):
    """
    Test that the second page of listing zones returns correctly when a name filter is provided
    """
    result = list_zones_context.client.list_zones(name_filter='*test-searched-3', start_from="list-zones-test-searched-2.", max_items=2, status=200)
    zones = result['zones']

    assert_that(zones, has_length(1))
    assert_that(zones[0]['name'], is_('list-zones-test-searched-3.'))

    assert_that(result, is_not(has_key('nextId')))
    assert_that(result['maxItems'], is_(2))
    assert_that(result['nameFilter'], is_('*test-searched-3'))
    assert_that(result['startFrom'], is_('list-zones-test-searched-2.'))

def test_list_zones_list_all_success(list_zones_context):
    """
    Test that we can retrieve a list of all zones
    """
    result = list_zones_context.client.list_zones(list_all=True, status=200)
    retrieved = result['zones']

    assert_that(retrieved, has_length(7))
    assert_that(retrieved, has_item(has_entry('name', 'list-zones-test-searched-1.')))
    assert_that(retrieved, has_item(has_entry('adminGroupName', 'list-zones-group')))
    assert_that(retrieved, has_item(has_entry('backendId', 'func-test-backend')))
    assert_that(retrieved, has_item(has_entry('accessLevel', 'Read')))
