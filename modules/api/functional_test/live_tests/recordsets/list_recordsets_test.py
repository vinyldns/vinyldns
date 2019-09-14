import pytest
import sys
from utils import *

from hamcrest import *
from vinyldns_python import VinylDNSClient
from test_data import TestData


@pytest.fixture(scope="module")
def rs_fixture(request, shared_zone_test_context):
    return shared_zone_test_context.list_records_context


def test_list_recordsets_no_start(rs_fixture):
    """
    Test listing all recordsets
    """
    client = rs_fixture.client
    rs_zone = rs_fixture.zone

    list_results = client.list_recordsets(rs_zone['id'], status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results, size=22, offset=0)


@pytest.mark.serial
def test_list_recordsets_with_owner_group_id_and_owner_group_name(rs_fixture):
    """
    Test that record sets with an owner group return the owner group ID and name
    """
    client = rs_fixture.client
    rs_zone = rs_fixture.zone
    shared_group = rs_fixture.group
    result_rs = None
    try:
        # create a record in the zone with an owner group ID
        new_rs = get_recordset_json(rs_zone,
                                    "test-owned-recordset", "TXT", [{'text':'should-work'}],
                                    100,
                                    shared_group['id'])

        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']

        list_results = client.list_recordsets(rs_zone['id'], status=200)
        assert_that(list_results['recordSets'], has_length(23))

        # confirm the created recordset is in the list of recordsets
        rs_from_list = (r for r in list_results['recordSets'] if r['id'] == result_rs['id']).next()
        assert_that(rs_from_list['name'], is_("test-owned-recordset"))
        assert_that(rs_from_list['ownerGroupId'], is_(shared_group['id']))
        assert_that(rs_from_list['ownerGroupName'], is_("record-ownergroup"))

    finally:
        if result_rs:
            delete_result = client.delete_recordset(rs_zone['id'], result_rs['id'], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')
            list_results = client.list_recordsets(rs_zone['id'], status=200)
            rs_fixture.check_recordsets_page_accuracy(list_results, size=22, offset=0)


def test_list_recordsets_multiple_pages(rs_fixture):
    """
    Test listing record sets in pages, using nextId from previous page for new one
    """
    client = rs_fixture.client
    rs_zone = rs_fixture.zone

    # first page of 2 items
    list_results_page = client.list_recordsets(rs_zone['id'], max_items=2, status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results_page, size=2, offset=0, nextId=True, maxItems=2)

    # second page of 5 items
    start = list_results_page['nextId']
    list_results_page = client.list_recordsets(rs_zone['id'], start_from=start, max_items=5, status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results_page, size=5, offset=2, nextId=True, startFrom=start, maxItems=5)

    # third page of 6 items
    start = list_results_page['nextId']
    list_results_page = client.list_recordsets(rs_zone['id'], start_from=start, max_items=16, status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results_page, size=15, offset=7, nextId=False, startFrom=start, maxItems=16)


def test_list_recordsets_excess_page_size(rs_fixture):
    """
    Test listing record set with page size larger than record sets count returns all records and nextId of None
    """
    client = rs_fixture.client
    rs_zone = rs_fixture.zone

    #page of 22 items
    list_results_page = client.list_recordsets(rs_zone['id'], max_items=23, status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results_page, size=22, offset=0, maxItems=23, nextId=False)


def test_list_recordsets_fails_max_items_too_large(rs_fixture):
    """
    Test listing record set with page size larger than max page size
    """
    client = rs_fixture.client
    rs_zone = rs_fixture.zone

    client.list_recordsets(rs_zone['id'], max_items=200, status=400)


def test_list_recordsets_fails_max_items_too_small(rs_fixture):
    """
    Test listing record set with page size of zero
    """
    client = rs_fixture.client
    rs_zone = rs_fixture.zone

    client.list_recordsets(rs_zone['id'], max_items=0, status=400)


def test_list_recordsets_default_size_is_100(rs_fixture):
    """
    Test default page size is 100
    """
    client = rs_fixture.client
    rs_zone = rs_fixture.zone

    list_results = client.list_recordsets(rs_zone['id'], status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results, size=22, offset=0, maxItems=100)


@pytest.mark.serial
def test_list_recordsets_duplicate_names(rs_fixture):
    """
    Test that paging keys work for records with duplicate names
    """
    client = rs_fixture.client
    rs_zone = rs_fixture.zone

    created = []

    try:
        record_data_a = [{'address': '1.1.1.1'}]
        record_data_txt = [{'text': 'some=value'}]

        record_json_a = get_recordset_json(rs_zone, '0', 'A', record_data_a, ttl=100)
        record_json_txt = get_recordset_json(rs_zone, '0', 'TXT', record_data_txt, ttl=100)

        create_response = client.create_recordset(record_json_a, status=202)
        created.append(client.wait_until_recordset_change_status(create_response, 'Complete')['recordSet']['id'])

        create_response = client.create_recordset(record_json_txt, status=202)
        created.append(client.wait_until_recordset_change_status(create_response, 'Complete')['recordSet']['id'])

        list_results = client.list_recordsets(rs_zone['id'], status=200, start_from=None, max_items=1)
        assert_that(list_results['recordSets'][0]['id'], is_(created[0]))

        list_results = client.list_recordsets(rs_zone['id'], status=200, start_from=list_results['nextId'], max_items=1)
        assert_that(list_results['recordSets'][0]['id'], is_(created[1]))

    finally:
        for id in created:
            client.delete_recordset(rs_zone['id'], id, status=202)
            client.wait_until_recordset_deleted(rs_zone['id'], id)


def test_list_recordsets_with_record_name_filter_all(rs_fixture):
    """
    Test listing all recordsets whose name contains a substring, all recordsets have substring 'list' in name
    """
    client = rs_fixture.client
    rs_zone = rs_fixture.zone

    list_results = client.list_recordsets(rs_zone['id'], record_name_filter="*list*", status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results, size=22, offset=0)


def test_list_recordsets_with_record_name_filter_and_page_size(rs_fixture):
    """
    First Listing 4 out of 5 recordsets with substring 'CNAME' in name
    Second Listing 10 out of 10 recordsets with substring 'CNAME' in name with an excess page size of 12
    """
    client = rs_fixture.client
    rs_zone = rs_fixture.zone

    # page of 4 items
    list_results = client.list_recordsets(rs_zone['id'], max_items=4, record_name_filter="*CNAME*", status=200)
    assert_that(list_results['recordSets'], has_length(4))

    list_results_records = list_results['recordSets'];
    for i in range(len(list_results_records)):
        assert_that(list_results_records[i]['name'], contains_string('CNAME'))

    # page of 5 items but excess max items
    list_results = client.list_recordsets(rs_zone['id'], max_items=12, record_name_filter="*CNAME*", status=200)
    assert_that(list_results['recordSets'], has_length(10))

    list_results_records = list_results['recordSets'];
    for i in range(len(list_results_records)):
        assert_that(list_results_records[i]['name'], contains_string('CNAME'))


def test_list_recordsets_with_record_name_filter_and_chaining_pages_with_nextId(rs_fixture):
    """
    First Listing 2 out 10 recordsets with substring 'CNAME' in name, then using next Id of
    previous page to be the start key of next page
    """
    client = rs_fixture.client
    rs_zone = rs_fixture.zone

    # page of 2 items
    list_results = client.list_recordsets(rs_zone['id'], max_items=2, record_name_filter="*CNAME*", status=200)
    assert_that(list_results['recordSets'], has_length(2))
    start_key = list_results['nextId']

    # page of 2 items
    list_results = client.list_recordsets(rs_zone['id'], start_from=start_key, max_items=2, record_name_filter="*CNAME*", status=200)
    assert_that(list_results['recordSets'], has_length(2))

    list_results_records = list_results['recordSets'];
    assert_that(list_results_records[0]['name'], contains_string('2-CNAME'))
    assert_that(list_results_records[1]['name'], contains_string('3-CNAME'))


def test_list_recordsets_with_record_name_filter_one(rs_fixture):
    """
    Test listing all recordsets whose name contains a substring, only one record set has substring '8' in name
    """
    client = rs_fixture.client
    rs_zone = rs_fixture.zone

    list_results = client.list_recordsets(rs_zone['id'], record_name_filter="*1-A*", status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results, size=1, offset=2)


def test_list_recordsets_with_record_name_filter_none(rs_fixture):
    """
    Test listing all recordsets whose name contains a substring, no record set has substring 'Dummy' in name
    """
    client = rs_fixture.client
    rs_zone = rs_fixture.zone

    list_results = client.list_recordsets(rs_zone['id'], record_name_filter="*Dummy*", status=200)
    rs_fixture.check_recordsets_page_accuracy(list_results, size=0, offset=0)


def test_list_recordsets_no_authorization(rs_fixture):
    """
    Test listing record sets without authorization
    """
    client = rs_fixture.client
    rs_zone = rs_fixture.zone
    client.list_recordsets(rs_zone['id'], sign_request=False, status=401)


@pytest.mark.serial
def test_list_recordsets_with_acl(shared_zone_test_context):
    """
    Test listing all recordsets
    """
    rs_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    new_rs = []

    try:
        acl_rule1 = generate_acl_rule('Read', groupId=shared_zone_test_context.dummy_group['id'], recordMask='test.*')
        acl_rule2 = generate_acl_rule('Write', userId='dummy', recordMask='test-list-recordsets-with-acl1')

        rec1 = seed_text_recordset(client, "test-list-recordsets-with-acl1", rs_zone)
        rec2 = seed_text_recordset(client, "test-list-recordsets-with-acl2", rs_zone)
        rec3 = seed_text_recordset(client, "BAD-test-list-recordsets-with-acl", rs_zone)

        new_rs = [rec1, rec2, rec3]

        add_ok_acl_rules(shared_zone_test_context, [acl_rule1, acl_rule2])

        result = shared_zone_test_context.dummy_vinyldns_client.list_recordsets(rs_zone['id'], status=200)
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
