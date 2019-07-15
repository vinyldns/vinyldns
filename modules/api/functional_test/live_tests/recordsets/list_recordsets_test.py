import pytest
import sys
from utils import *

from hamcrest import *
from vinyldns_python import VinylDNSClient
from test_data import TestData


class ListRecordSetsFixture():
    def __init__(self, shared_zone_test_context):
        self.test_context = shared_zone_test_context.ok_zone
        self.client = shared_zone_test_context.ok_vinyldns_client
        self.shared_group = shared_zone_test_context.shared_record_group
        self.new_rs = {}
        existing_records = self.client.list_recordsets(self.test_context['id'])['recordSets']
        assert_that(existing_records, has_length(9))
        rs_template = {
            'zoneId': self.test_context['id'],
            'name': '00-test-list-recordsets-',
            'type': '',
            'ttl': 100,
            'records': [0]
        }
        rs_types = [
            ['A',
             [
                 {
                     'address': '10.1.1.1'
                 },
                 {
                     'address': '10.2.2.2'
                 }
             ]
             ],
            ['CNAME',
             [
                 {
                     'cname': 'cname1.'
                 }
             ]
             ]
        ]
        self.all_records = {}
        result_list = {}
        for i in range(10):
            self.all_records[i] = copy.deepcopy(rs_template)
            self.all_records[i]['type'] = rs_types[(i % 2)][0]
            self.all_records[i]['records'] = rs_types[(i % 2)][1]
            self.all_records[i]['name'] = "{0}{1}-{2}".format(self.all_records[i]['name'], i, self.all_records[i]['type'])
            result_list[i] = self.client.create_recordset(self.all_records[i], status=202)
            self.client.wait_until_recordset_change_status(result_list[i], 'Complete')
            self.new_rs[i] = result_list[i]['recordSet']

        for i in range(7):
            self.all_records[i + 10] = existing_records[i]

    def tear_down(self):
        for key in self.new_rs:
            self.client.delete_recordset(self.new_rs[key]['zoneId'], self.new_rs[key]['id'], status=202)
        for key in self.new_rs:
            self.client.wait_until_recordset_deleted(self.new_rs[key]['zoneId'], self.new_rs[key]['id'])

    def check_recordsets_page_accuracy(self, list_results_page, size, offset, nextId=False, startFrom=False, maxItems=100):
        # validate fields
        if nextId:
            assert_that(list_results_page, has_key('nextId'))
        else:
            assert_that(list_results_page, is_not(has_key('nextId')))
        if startFrom:
            assert_that(list_results_page['startFrom'], is_(startFrom))
        else:
            assert_that(list_results_page, is_not(has_key('startFrom')))
        assert_that(list_results_page['maxItems'], is_(maxItems))

        # validate actual page
        list_results_recordSets_page = list_results_page['recordSets']
        assert_that(list_results_recordSets_page, has_length(size))
        for i in range(len(list_results_recordSets_page)):
            if i < 17:
                assert_that(list_results_recordSets_page[i]['name'], is_(self.all_records[i+offset]['name']))
                verify_recordset(list_results_recordSets_page[i], self.all_records[i+offset])
            assert_that(list_results_recordSets_page[i]['accessLevel'], is_('Delete'))


@pytest.fixture(scope = "module")
def rs_fixture(request, shared_zone_test_context):

    fix = ListRecordSetsFixture(shared_zone_test_context)
    def fin():
        fix.tear_down()

    request.addfinalizer(fin)

    return fix


def test_list_recordsets_no_start(rs_fixture):
    """
    Test listing all recordsets
    """
    client = rs_fixture.client
    ok_zone = rs_fixture.test_context

    list_results = client.list_recordsets(ok_zone['id'], status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results, size=19, offset=0)

def test_list_recordsets_with_owner_group_id_and_owner_group_name(rs_fixture):
    """
    Test that record sets with an owner group return the owner group ID and name
    """
    client = rs_fixture.client
    ok_zone = rs_fixture.test_context
    shared_group = rs_fixture.shared_group
    result_rs = None
    try:
        # create a record in the zone with an owner group ID
        new_rs = get_recordset_json(ok_zone,
                                    "test-owned-recordset", "TXT", [{'text':'should-work'}],
                                    100,
                                    shared_group['id'])

        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']

        list_results = client.list_recordsets(ok_zone['id'], status=200)
        assert_that(list_results['recordSets'], has_length(20))

        # confirm the created recordset is in the list of recordsets
        rs_from_list = (r for r in list_results['recordSets'] if r['id'] == result_rs['id']).next()
        assert_that(rs_from_list['name'], is_("test-owned-recordset"))
        assert_that(rs_from_list['ownerGroupId'], is_(shared_group['id']))
        assert_that(rs_from_list['ownerGroupName'], is_("record-ownergroup"))

    finally:
        if result_rs:
            delete_result = client.delete_recordset(ok_zone['id'], result_rs['id'], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')
            list_results = client.list_recordsets(ok_zone['id'], status=200)
            rs_fixture.check_recordsets_page_accuracy(list_results, size=19, offset=0)


def test_list_recordsets_multiple_pages(rs_fixture):
    """
    Test listing record sets in pages, using nextId from previous page for new one
    """
    client = rs_fixture.client
    ok_zone = rs_fixture.test_context

    # first page of 2 items
    list_results_page = client.list_recordsets(ok_zone['id'], max_items=2, status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results_page, size=2, offset=0, nextId=True, maxItems=2)

    # second page of 5 items
    start = list_results_page['nextId']
    list_results_page = client.list_recordsets(ok_zone['id'], start_from=start, max_items=5, status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results_page, size=5, offset=2, nextId=True, startFrom=start, maxItems=5)

    # third page of 6 items
    start = list_results_page['nextId']
    # nextId differs local/in dev where we get exactly the last item
    # If you put 3 items in local in memory dynamo and request three items, you always get an exclusive start key,
    # but in real dynamo you don't. Requesting something over 4 will force consistent behavior
    list_results_page = client.list_recordsets(ok_zone['id'], start_from=start, max_items=13, status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results_page, size=12, offset=7, nextId=False, startFrom=start, maxItems=13)


def test_list_recordsets_excess_page_size(rs_fixture):
    """
    Test listing record set with page size larger than record sets count returns all records and nextId of None
    """
    client = rs_fixture.client
    ok_zone = rs_fixture.test_context

    #page of 19 items
    list_results_page = client.list_recordsets(ok_zone['id'], max_items=20, status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results_page, size=19, offset=0, maxItems=20, nextId=False)


def test_list_recordsets_fails_max_items_too_large(rs_fixture):
    """
    Test listing record set with page size larger than max page size
    """
    client = rs_fixture.client
    ok_zone = rs_fixture.test_context

    client.list_recordsets(ok_zone['id'], max_items=200, status=400)


def test_list_recordsets_fails_max_items_too_small(rs_fixture):
    """
    Test listing record set with page size of zero
    """
    client = rs_fixture.client
    ok_zone = rs_fixture.test_context

    client.list_recordsets(ok_zone['id'], max_items=0, status=400)


def test_list_recordsets_default_size_is_100(rs_fixture):
    """
    Test default page size is 100
    """
    client = rs_fixture.client
    ok_zone = rs_fixture.test_context

    list_results = client.list_recordsets(ok_zone['id'], status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results, size=19, offset=0, maxItems=100)


def test_list_recordsets_duplicate_names(rs_fixture):
    """
    Test that paging keys work for records with duplicate names
    """
    client = rs_fixture.client
    ok_zone = rs_fixture.test_context

    created = []

    try:
        record_data_a = [{'address': '1.1.1.1'}]
        record_data_txt = [{'text': 'some=value'}]

        record_json_a = get_recordset_json(ok_zone, '0', 'A', record_data_a, ttl=100)
        record_json_txt = get_recordset_json(ok_zone, '0', 'TXT', record_data_txt, ttl=100)

        create_response = client.create_recordset(record_json_a, status=202)
        created.append(client.wait_until_recordset_change_status(create_response, 'Complete')['recordSet']['id'])

        create_response = client.create_recordset(record_json_txt, status=202)
        created.append(client.wait_until_recordset_change_status(create_response, 'Complete')['recordSet']['id'])

        list_results = client.list_recordsets(ok_zone['id'], status=200, start_from=None, max_items=1)
        assert_that(list_results['recordSets'][0]['id'], is_(created[0]))

        list_results = client.list_recordsets(ok_zone['id'], status=200, start_from=list_results['nextId'], max_items=1)
        assert_that(list_results['recordSets'][0]['id'], is_(created[1]))

    finally:
        for id in created:
            client.delete_recordset(ok_zone['id'], id, status=202)
            client.wait_until_recordset_deleted(ok_zone['id'], id)


def test_list_recordsets_with_record_name_filter_all(rs_fixture):
    """
    Test listing all recordsets whose name contains a substring, all recordsets have substring 'list' in name
    """
    client = rs_fixture.client
    ok_zone = rs_fixture.test_context

    list_results = client.list_recordsets(ok_zone['id'], record_name_filter="*list*", status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results, size=10, offset=0)


def test_list_recordsets_with_record_name_filter_and_page_size(rs_fixture):
    """
    First Listing 4 out of 5 recordsets with substring 'CNAME' in name
    Second Listing 5 out of 5 recordsets with substring 'CNAME' in name with an excess page size of 7
    """
    client = rs_fixture.client
    ok_zone = rs_fixture.test_context

    #page of 4 items
    list_results = client.list_recordsets(ok_zone['id'], max_items=4, record_name_filter="*CNAME*", status=200)
    assert_that(list_results['recordSets'], has_length(4))

    list_results_records = list_results['recordSets'];
    for i in range(len(list_results_records)):
        assert_that(list_results_records[i]['name'], contains_string('CNAME'))

    #page of 5 items but excess max items
    list_results = client.list_recordsets(ok_zone['id'], max_items=7, record_name_filter="*CNAME*", status=200)
    assert_that(list_results['recordSets'], has_length(5))

    list_results_records = list_results['recordSets'];
    for i in range(len(list_results_records)):
        assert_that(list_results_records[i]['name'], contains_string('CNAME'))


def test_list_recordsets_with_record_name_filter_and_chaining_pages_with_nextId(rs_fixture):
    """
    First Listing 2 out 5 recordsets with substring 'CNAME' in name, then using next Id of
    previous page to be the start key of next page
    """
    client = rs_fixture.client
    ok_zone = rs_fixture.test_context

    #page of 2 items
    list_results = client.list_recordsets(ok_zone['id'], max_items=2, record_name_filter="*CNAME*", status=200)
    assert_that(list_results['recordSets'], has_length(2))
    start_key = list_results['nextId']

    #page of 2 items
    list_results = client.list_recordsets(ok_zone['id'], start_from=start_key, max_items=2, record_name_filter="*CNAME*", status=200)
    assert_that(list_results['recordSets'], has_length(2))

    list_results_records = list_results['recordSets'];
    assert_that(list_results_records[0]['name'], contains_string('5'))
    assert_that(list_results_records[1]['name'], contains_string('7'))


def test_list_recordsets_with_record_name_filter_one(rs_fixture):
    """
    Test listing all recordsets whose name contains a substring, only one record set has substring '8' in name
    """
    client = rs_fixture.client
    ok_zone = rs_fixture.test_context

    list_results = client.list_recordsets(ok_zone['id'], record_name_filter="*8*", status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results, size=1, offset=8)


def test_list_recordsets_with_record_name_filter_none(rs_fixture):
    """
    Test listing all recordsets whose name contains a substring, no record set has substring 'Dummy' in name
    """
    client = rs_fixture.client
    ok_zone = rs_fixture.test_context

    list_results = client.list_recordsets(ok_zone['id'], record_name_filter="*Dummy*", status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results, size=0, offset=0)


def test_list_recordsets_no_authorization(rs_fixture):
    """
    Test listing record sets without authorization
    """
    client = rs_fixture.client
    ok_zone = rs_fixture.test_context
    client.list_recordsets(ok_zone['id'], sign_request=False, status=401)


def test_list_recordsets_with_acl(shared_zone_test_context):
    """
    Test listing all recordsets
    """
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    new_rs = []

    try:
        acl_rule1 = generate_acl_rule('Read', groupId=shared_zone_test_context.dummy_group['id'], recordMask='test.*')
        acl_rule2 = generate_acl_rule('Write', userId='dummy', recordMask='test-list-recordsets-with-acl1')

        rec1 = seed_text_recordset(client, "test-list-recordsets-with-acl1", ok_zone)
        rec2 = seed_text_recordset(client, "test-list-recordsets-with-acl2", ok_zone)
        rec3 = seed_text_recordset(client, "BAD-test-list-recordsets-with-acl", ok_zone)

        new_rs = [rec1, rec2, rec3]

        add_ok_acl_rules(shared_zone_test_context, [acl_rule1, acl_rule2])

        result = shared_zone_test_context.dummy_vinyldns_client.list_recordsets(ok_zone['id'], status=200)
        result = result['recordSets']

        for rs in result:
            if rs['name'] == rec1['name']:
                verify_recordset(rs, rec1)
                assert_that(rs['accessLevel'], is_('Write'))
            elif rs['name'] == rec2['name']:
                verify_recordset(rs, rec2)
                assert_that(rs['accessLevel'], is_('Read'))
            elif rs['name'] == rec3['name']:
                verify_recordset(rs, rec3)
                assert_that(rs['accessLevel'], is_('NoAccess'))

    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        for rs in new_rs:
            client.delete_recordset(rs['zoneId'], rs['id'], status=202)
        for rs in new_rs:
            client.wait_until_recordset_deleted(rs['zoneId'], rs['id'])
