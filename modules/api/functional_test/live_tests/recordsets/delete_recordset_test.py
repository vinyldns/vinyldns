import pytest
import sys
from utils import *

from hamcrest import *
from vinyldns_python import VinylDNSClient
from test_data import TestData
import time


@pytest.mark.parametrize('record_name,test_rs', TestData.FORWARD_RECORDS)
def test_delete_recordset_forward_record_types(shared_zone_test_context, record_name, test_rs):
    """
    Test deleting a recordset for forward record types
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None

    try:
        new_rs = dict(test_rs, zoneId=shared_zone_test_context.system_test_zone['id'])
        new_rs['name'] = generate_record_name() + new_rs['type']

        result = client.create_recordset(new_rs, status=202)
        assert_that(result['status'], is_('Pending'))
        print str(result)

        result_rs = result['recordSet']
        verify_recordset(result_rs, new_rs)

        records = result_rs['records']

        for record in new_rs['records']:
            assert_that(records, has_item(has_entries(record)))

        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']

        # now delete
        delete_rs = result_rs

        result = client.delete_recordset(delete_rs['zoneId'], delete_rs['id'], status=202)
        assert_that(result['status'], is_('Pending'))
        result_rs = result['recordSet']
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']

        # retry until the recordset is not found
        client.get_recordset(result_rs['zoneId'], result_rs['id'], retries=20, status=404)
        result_rs = None
    finally:
        if result_rs:
            result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=(202, 404))
            if result and 'status' in result:
                client.wait_until_recordset_change_status(result, 'Complete')


@pytest.mark.serial
@pytest.mark.parametrize('record_name,test_rs', TestData.REVERSE_RECORDS)
def test_delete_recordset_reverse_record_types(shared_zone_test_context, record_name, test_rs):
    """
    Test deleting a recordset for reverse record types
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None

    try:
        new_rs = dict(test_rs, zoneId=shared_zone_test_context.ip4_reverse_zone['id'])

        result = client.create_recordset(new_rs, status=202)
        assert_that(result['status'], is_('Pending'))
        print str(result)

        result_rs = result['recordSet']
        verify_recordset(result_rs, new_rs)

        records = result_rs['records']

        for record in new_rs['records']:
            assert_that(records, has_item(has_entries(record)))

        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']

        # now delete
        delete_rs = result_rs

        result = client.delete_recordset(delete_rs['zoneId'], delete_rs['id'], status=202)
        assert_that(result['status'], is_('Pending'))
        result_rs = result['recordSet']
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']

        # retry until the recordset is not found
        client.get_recordset(result_rs['zoneId'], result_rs['id'], retries=20, status=404)
        result_rs = None
    finally:
        if result_rs:
            result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=(202, 404))
            if result:
                client.wait_until_recordset_change_status(result, 'Complete')


def test_delete_recordset_with_verify(shared_zone_test_context):
    """
    Test deleting a new record set removes it from the backend
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None
    try:
        new_rs = {
            'zoneId': shared_zone_test_context.ok_zone['id'],
            'name': 'test_delete_recordset_with_verify',
            'type': 'A',
            'ttl': 100,
            'records': [
                {
                    'address': '10.1.1.1'
                },
                {
                    'address': '10.2.2.2'
                }
            ]
        }
        print "\r\nCreating recordset in zone " + str(shared_zone_test_context.ok_zone) + "\r\n"
        result = client.create_recordset(new_rs, status=202)
        print str(result)

        assert_that(result['changeType'], is_('Create'))
        assert_that(result['status'], is_('Pending'))
        assert_that(result['created'], is_not(none()))
        assert_that(result['userId'], is_not(none()))

        result_rs = result['recordSet']
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']
        print "\r\n\r\n!!!recordset is active!  Verifying..."

        verify_recordset(result_rs, new_rs)
        print "\r\n\r\n!!!recordset verified..."

        records = [x['address'] for x in result_rs['records']]
        assert_that(records, has_length(2))
        assert_that('10.1.1.1', is_in(records))
        assert_that('10.2.2.2', is_in(records))

        print "\r\n\r\n!!!verifying recordset in dns backend"
        # verify that the record exists in the backend dns server
        answers = dns_resolve(shared_zone_test_context.ok_zone, result_rs['name'], result_rs['type'])
        rdata_strings = rdata(answers)
        assert_that(rdata_strings, has_length(2))
        assert_that('10.1.1.1', is_in(rdata_strings))
        assert_that('10.2.2.2', is_in(rdata_strings))

        # Delete the record set and verify that it is removed
        delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
        client.wait_until_recordset_change_status(delete_result, 'Complete')

        answers = dns_resolve(shared_zone_test_context.ok_zone, result_rs['name'], result_rs['type'])
        not_found = len(answers) == 0

        assert_that(not_found, is_(True))

        result_rs = None
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')


def test_user_can_delete_record_in_owned_zone(shared_zone_test_context):
    """
    Test user can delete a record that in a zone that it is owns
    """

    client = shared_zone_test_context.ok_vinyldns_client
    rs = None
    try:
        rs = client.create_recordset(
            {
                'zoneId': shared_zone_test_context.ok_zone['id'],
                'name': 'test_user_can_delete_record_in_owned_zone',
                'type': 'A',
                'ttl': 100,
                'records': [
                    {
                        'address': '10.10.10.10'
                    }
                ]
            }, status=202)['recordSet']
        client.wait_until_recordset_exists(rs['zoneId'], rs['id'])

        client.delete_recordset(rs['zoneId'], rs['id'], status=202)
        client.wait_until_recordset_deleted(rs['zoneId'], rs['id'])
        rs = None
    finally:
        if rs:
            try:
                client.delete_recordset(rs['zoneId'], rs['id'], status=(202, 404))
                client.wait_until_recordset_deleted(rs['zoneId'], rs['id'])
            finally:
                pass


def test_user_cannot_delete_record_in_unowned_zone(shared_zone_test_context):
    """
    Test user cannot delete a record that in an unowned zone
    """

    client = shared_zone_test_context.dummy_vinyldns_client
    unauthorized_client = shared_zone_test_context.ok_vinyldns_client
    rs = None
    try:
        rs = client.create_recordset(
            {
                'zoneId': shared_zone_test_context.dummy_zone['id'],
                'name': 'test-user-cannot-delete-record-in-unowned-zone',
                'type': 'A',
                'ttl': 100,
                'records': [
                    {
                        'address': '10.10.10.10'
                    }
                ]
            }, status=202)['recordSet']

        client.wait_until_recordset_exists(rs['zoneId'], rs['id'])
        unauthorized_client.delete_recordset(rs['zoneId'], rs['id'], status=403)
    finally:
        if rs:
            try:
                client.delete_recordset(rs['zoneId'], rs['id'], status=(202, 404))
                client.wait_until_recordset_deleted(rs['zoneId'], rs['id'])
            finally:
                pass


def test_delete_recordset_no_authorization(shared_zone_test_context):
    """
    Test delete a recordset without authorization
    """
    client = shared_zone_test_context.dummy_vinyldns_client
    client.delete_recordset(shared_zone_test_context.ok_zone['id'], '1234', sign_request=False, status=401)


@pytest.mark.serial
def test_delete_ipv4_ptr_recordset(shared_zone_test_context):
    """
    Test deleting an IPv4 PTR recordset deletes the record
    """
    client = shared_zone_test_context.ok_vinyldns_client
    reverse4_zone = shared_zone_test_context.ip4_reverse_zone
    result_rs = None

    try:
        orig_rs = {
            'zoneId': reverse4_zone['id'],
            'name': '30.0',
            'type': 'PTR',
            'ttl': 100,
            'records': [
                {
                    'ptrdname': 'ftp.vinyldns.'
                }
            ]
        }
        result = client.create_recordset(orig_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']
        print "\r\n\r\n!!!recordset is active!  Deleting..."

        delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
        client.wait_until_recordset_change_status(delete_result, 'Complete')
        result_rs = None
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=(202, 404))
            client.wait_until_recordset_change_status(delete_result, 'Complete')


def test_delete_ipv4_ptr_recordset_does_not_exist_fails(shared_zone_test_context):
    """
    Test deleting a nonexistent IPv4 PTR recordset returns not found
    """
    client =shared_zone_test_context.ok_vinyldns_client
    client.delete_recordset(shared_zone_test_context.ip4_reverse_zone['id'], '4444', status=404)


def test_delete_ipv6_ptr_recordset(shared_zone_test_context):
    """
    Test deleting an IPv6 PTR recordset deletes the record
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None
    try:
        orig_rs = {
            'zoneId': shared_zone_test_context.ip6_reverse_zone['id'],
            'name': '0.7.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0',
            'type': 'PTR',
            'ttl': 100,
            'records': [
                {
                    'ptrdname': 'ftp.vinyldns.'
                }
            ]
        }
        result = client.create_recordset(orig_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']
        print "\r\n\r\n!!!recordset is active!  Deleting..."

        delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
        client.wait_until_recordset_change_status(delete_result, 'Complete')
        result_rs = None
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=(202, 404))
            client.wait_until_recordset_change_status(delete_result, 'Complete')



def test_delete_ipv6_ptr_recordset_does_not_exist_fails(shared_zone_test_context):
    """
    Test deleting a nonexistent IPv6 PTR recordset returns not found
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.delete_recordset(shared_zone_test_context.ip6_reverse_zone['id'], '6666', status=404)


def test_delete_recordset_zone_not_found(shared_zone_test_context):
    """
    Test deleting a recordset in a zone that doesn't exist should return a 404
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.delete_recordset('1234', '4567', status=404)


def test_delete_recordset_not_found(shared_zone_test_context):
    """
    Test deleting a recordset that doesn't exist should return a 404
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.delete_recordset(shared_zone_test_context.ok_zone['id'], '1234', status=404)


@pytest.mark.serial
def test_at_delete_recordset(shared_zone_test_context):
    """
    Test deleting a recordset with name @ in an existing zone
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    result_rs = None
    new_rs = {
        'zoneId': ok_zone['id'],
        'name': '@',
        'type': 'TXT',
        'ttl': 100,
        'records': [
            {
                'text': 'someText'
            }
        ]
    }
    print "\r\nCreating recordset in zone " + str(ok_zone) + "\r\n"
    result = client.create_recordset(new_rs, status=202)

    print json.dumps(result, indent=3)

    assert_that(result['changeType'], is_('Create'))
    assert_that(result['status'], is_('Pending'))
    assert_that(result['created'], is_not(none()))
    assert_that(result['userId'], is_not(none()))

    result_rs = result['recordSet']
    result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']
    print "\r\n\r\n!!!recordset is active!  Verifying..."

    expected_rs = new_rs
    expected_rs['name'] = ok_zone['name']
    verify_recordset(result_rs, expected_rs)

    print "\r\n\r\n!!!recordset verified..."

    records = result_rs['records']
    assert_that(records, has_length(1))
    assert_that(records[0]['text'], is_('someText'))

    print "\r\n\r\n!!!deleting recordset in dns backend"
    delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
    client.wait_until_recordset_change_status(delete_result, 'Complete')

    # verify that the record does not exist in the backend dns server
    answers = dns_resolve(ok_zone, ok_zone['name'], result_rs['type'])
    not_found = len(answers) == 0
    assert_that(not_found)


def test_delete_recordset_with_different_dns_data(shared_zone_test_context):
    """
    Test deleting a recordset with out-of-sync rdata in dns (ex. if the record was modified manually)
    """

    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    result_rs = None

    try:
        new_rs = {
            'zoneId': ok_zone['id'],
            'name': 'test_delete_recordset_with_different_dns_data',
            'type': 'A',
            'ttl': 100,
            'records': [
                {
                    'address': '10.1.1.1'
                }
            ]
        }
        print "\r\nCreating recordset in zone " + str(ok_zone) + "\r\n"
        result = client.create_recordset(new_rs, status=202)
        print str(result)

        result_rs = result['recordSet']
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']
        print "\r\n\r\n!!!recordset is active!  Verifying..."

        verify_recordset(result_rs, new_rs)
        print "\r\n\r\n!!!recordset verified..."

        result_rs['records'][0]['address'] = "10.8.8.8"
        result = client.update_recordset(result_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']

        print "\r\n\r\n!!!verifying recordset in dns backend"
        answers = dns_resolve(ok_zone, result_rs['name'], result_rs['type'])
        assert_that(answers, has_length(1))

        response = dns_update(ok_zone, result_rs['name'], 300, result_rs['type'], '10.9.9.9')
        print "\nSuccessfully updated the record, record is now out of sync\n"
        print str(response)

        # check you can delete
        delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
        client.wait_until_recordset_change_status(delete_result, 'Complete')
        result_rs = None

    finally:
        if result_rs:
            try:
                delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=(202, 404))
                if delete_result:
                    client.wait_until_recordset_change_status(delete_result, 'Complete')
            except:
                pass


@pytest.mark.serial
def test_user_can_delete_record_via_user_acl_rule(shared_zone_test_context):
    """
    Test user DELETE ACL rule - delete
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule = generate_acl_rule('Delete', userId='dummy')

        result_rs = seed_text_recordset(client, "test_user_can_delete_record_via_user_acl_rule", ok_zone)

        #Dummy user cannot delete record in zone
        shared_zone_test_context.dummy_vinyldns_client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=403, retries=3)

        #add rule
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])

        #Dummy user can delete record
        shared_zone_test_context.dummy_vinyldns_client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
        shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_deleted(result_rs['zoneId'], result_rs['id'])
        result_rs = None
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')


@pytest.mark.serial
def test_user_cannot_delete_record_with_write_txt_read_all(shared_zone_test_context):
    """
    Test user WRITE TXT READ all ACL rule
    """
    client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    created_rs = None
    try:
        acl_rule1 = generate_acl_rule('Read', userId='dummy', recordMask='www-*')
        acl_rule2 = generate_acl_rule('Write', userId='dummy', recordMask='www-user-cant-delete', recordTypes=['TXT'])

        add_ok_acl_rules(shared_zone_test_context, [acl_rule1, acl_rule2])

        # verify dummy can see ok_zone
        dummy_view = dummy_client.list_zones()['zones']
        zone_ids = [zone['id'] for zone in dummy_view]
        assert_that(zone_ids, has_item(ok_zone['id']))

        # dummy should be able to add the RS
        new_rs = get_recordset_json(ok_zone, "www-user-cant-delete", "TXT", [{'text':'should-work'}])
        rs_change = dummy_client.create_recordset(new_rs, status=202)
        created_rs = dummy_client.wait_until_recordset_change_status(rs_change, 'Complete')['recordSet']
        verify_recordset(created_rs, new_rs)

        #dummy cannot delete the RS
        dummy_client.delete_recordset(ok_zone['id'], created_rs['id'], status=403)

    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if created_rs:
            delete_result = client.delete_recordset(created_rs['zoneId'], created_rs['id'], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')


@pytest.mark.serial
def test_user_can_delete_record_via_group_acl_rule(shared_zone_test_context):
    """
    Test group DELETE ACL rule - delete
    """
    result_rs = None
    ok_zone = shared_zone_test_context.ok_zone
    client = shared_zone_test_context.ok_vinyldns_client
    try:
        acl_rule = generate_acl_rule('Delete', groupId=shared_zone_test_context.dummy_group['id'])

        result_rs = seed_text_recordset(client, "test_user_can_delete_record_via_group_acl_rule", ok_zone)

        #Dummy user cannot delete record in zone
        shared_zone_test_context.dummy_vinyldns_client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=403)

        #add rule
        add_ok_acl_rules(shared_zone_test_context, [acl_rule])

        #Dummy user can delete record
        shared_zone_test_context.dummy_vinyldns_client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
        shared_zone_test_context.ok_vinyldns_client.wait_until_recordset_deleted(result_rs['zoneId'], result_rs['id'])
        result_rs = None
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        if result_rs:
            delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')


def test_ns_delete_for_admin_group_passes(shared_zone_test_context):
    """
    Tests that an ns delete passes
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.parent_zone
    ns_rs = None

    try:
        new_rs = {
            'zoneId': zone['id'],
            'name': generate_record_name(),
            'type': 'NS',
            'ttl': 38400,
            'records': [
                {
                    'nsdname': 'ns1.parent.com.'
                }
            ]
        }
        result = client.create_recordset(new_rs, status=202)
        ns_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']

        delete_result = client.delete_recordset(ns_rs['zoneId'], ns_rs['id'], status=202)
        client.wait_until_recordset_change_status(delete_result, 'Complete')

        ns_rs = None

    finally:
        if ns_rs:
            client.delete_recordset(ns_rs['zoneId'], ns_rs['id'], status=(202,404))
            client.wait_until_recordset_deleted(ns_rs['zoneId'], ns_rs['id'])


def test_ns_delete_existing_ns_origin_fails(shared_zone_test_context):
    """
    Tests that an ns delete for existing ns origin fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.parent_zone

    list_results_page = client.list_recordsets_by_zone(zone['id'], status=200)['recordSets']

    apex_ns = [item for item in list_results_page if item['type'] == 'NS' and item['name'] in zone['name']][0]

    client.delete_recordset(apex_ns['zoneId'], apex_ns['id'], status=422)


def test_delete_dotted_a_record_apex_succeeds(shared_zone_test_context):
    """
    Test that deleting an apex A record set containing dots succeeds.
    """

    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.parent_zone

    apex_a_record = {
        'zoneId': zone['id'],
        'name': zone['name'].rstrip('.'),
        'type': 'A',
        'ttl': 500,
        'records': [{'address': '127.0.0.1'}]
    }
    try:
        apex_a_response = client.create_recordset(apex_a_record, status=202)
        apex_a_rs = client.wait_until_recordset_change_status(apex_a_response, 'Complete')['recordSet']
        assert_that(apex_a_rs['name'],is_(apex_a_record['name'] + '.'))

    finally:
        delete_result = client.delete_recordset(apex_a_rs['zoneId'], apex_a_rs['id'], status=202)
        client.wait_until_recordset_change_status(delete_result, 'Complete')


def test_delete_high_value_domain_fails(shared_zone_test_context):
    """
    Test that deleting a high value domain fails
    """

    client = shared_zone_test_context.ok_vinyldns_client
    zone_system = shared_zone_test_context.system_test_zone
    list_results_page_system = client.list_recordsets_by_zone(zone_system['id'], status=200)['recordSets']
    record_system = [item for item in list_results_page_system if item['name'] == 'high-value-domain'][0]

    errors_system = client.delete_recordset(record_system['zoneId'], record_system['id'], status=422)
    assert_that(errors_system, is_('Record name "high-value-domain.system-test." is configured as a High Value Domain, so it cannot be modified.'))


def test_delete_high_value_domain_fails_ip4_ptr(shared_zone_test_context):
    """
    Test that deleting a high value domain fails for ip4 ptr
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone_ip4 = shared_zone_test_context.classless_base_zone
    list_results_page_ip4 = client.list_recordsets_by_zone(zone_ip4['id'], status=200)['recordSets']
    record_ip4 = [item for item in list_results_page_ip4 if item['name'] == '253'][0]

    errors_ip4 = client.delete_recordset(record_ip4['zoneId'], record_ip4['id'], status=422)
    assert_that(errors_ip4, is_('Record name "192.0.2.253" is configured as a High Value Domain, so it cannot be modified.'))


def test_delete_high_value_domain_fails_ip6_ptr(shared_zone_test_context):
    """
    Test that deleting a high value domain fails for ip6 ptr
    """

    client = shared_zone_test_context.ok_vinyldns_client
    zone_ip6 = shared_zone_test_context.ip6_reverse_zone
    list_results_page_ip6 = client.list_recordsets_by_zone(zone_ip6['id'], status=200)['recordSets']
    record_ip6 = [item for item in list_results_page_ip6 if item['name'] == '0.0.0.0.f.f.f.f.0.0.0.0.0.0.0.0.0.0.0.0'][0]

    errors_ip6 = client.delete_recordset(record_ip6['zoneId'], record_ip6['id'], status=422)
    assert_that(errors_ip6, is_('Record name "fd69:27cc:fe91:0000:0000:0000:ffff:0000" is configured as a High Value Domain, so it cannot be modified.'))


def test_no_delete_access_non_test_zone(shared_zone_test_context):
    """
    Test that a test user cannot delete a record in a non-test zone (even if admin)
    """

    client = shared_zone_test_context.shared_zone_vinyldns_client
    zone_id = shared_zone_test_context.non_test_shared_zone['id']

    list_results = client.list_recordsets_by_zone(zone_id, status=200)['recordSets']
    record_delete = [item for item in list_results if item['name'] == 'delete-test'][0]

    client.delete_recordset(zone_id, record_delete['id'], status=403)

def test_delete_for_user_in_record_owner_group_in_shared_zone_succeeds(shared_zone_test_context):
    """
    Test that a user in record owner group can delete a record in a shared zone
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone
    shared_group = shared_zone_test_context.shared_record_group

    record_json = get_recordset_json(shared_zone, 'test_shared_del_og', 'A', [{'address': '1.1.1.1'}], ownergroup_id = shared_group['id'])

    create_rs = shared_client.create_recordset(record_json, status=202)
    result_rs = shared_client.wait_until_recordset_change_status(create_rs, 'Complete')['recordSet']

    delete_rs = ok_client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
    ok_client.wait_until_recordset_change_status(delete_rs, 'Complete')

def test_delete_for_zone_admin_in_shared_zone_succeeds(shared_zone_test_context):
    """
    Test that a zone admin not in record owner group can delete a record in a shared zone
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone

    record_json = get_recordset_json(shared_zone, 'test_shared_del_admin', 'A', [{'address': '1.1.1.1'}], ownergroup_id = shared_zone_test_context.shared_record_group['id'])

    create_rs = shared_client.create_recordset(record_json, status=202)
    result_rs = shared_client.wait_until_recordset_change_status(create_rs, 'Complete')['recordSet']

    delete_rs = shared_client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
    shared_client.wait_until_recordset_change_status(delete_rs, 'Complete')

def test_delete_for_unowned_record_with_approved_record_type_in_shared_zone_succeeds(shared_zone_test_context):
    """
    Test that a user not associated with a unowned record can delete it in a shared zone
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone
    ok_client = shared_zone_test_context.ok_vinyldns_client

    record_json = get_recordset_json(shared_zone, 'test_shared_approved_record_type', 'A', [{'address': '1.1.1.1'}])

    create_rs = shared_client.create_recordset(record_json, status=202)
    result_rs = shared_client.wait_until_recordset_change_status(create_rs, 'Complete')['recordSet']

    delete_rs = ok_client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
    ok_client.wait_until_recordset_change_status(delete_rs, 'Complete')

def test_delete_for_user_not_in_record_owner_group_in_shared_zone_fails(shared_zone_test_context):
    """
    Test that a user cannot delete a record in a shared zone if not part of record owner group
    """

    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone
    result_rs = None

    record_json = get_recordset_json(shared_zone, 'test_shared_del_nonog', 'A', [{'address': '1.1.1.1'}], ownergroup_id = shared_zone_test_context.shared_record_group['id'])

    try:
        create_rs = shared_client.create_recordset(record_json, status=202)
        result_rs = shared_client.wait_until_recordset_change_status(create_rs, 'Complete')['recordSet']

        error = dummy_client.delete_recordset(shared_zone['id'], result_rs['id'], status=403)
        assert_that(error, is_('User dummy does not have access to delete test-shared-del-nonog.shared.'))

    finally:
        if result_rs:
            delete_rs = shared_client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
            shared_client.wait_until_recordset_change_status(delete_rs, 'Complete')

def test_delete_for_user_not_in_unowned_record_in_shared_zone_fails_if_record_type_is_not_approved(shared_zone_test_context):
    """
    Test that a user cannot delete a record in a shared zone if the record is unowned and the record type is not approved
    """

    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone
    result_rs = None

    record_json = get_recordset_json(shared_zone, 'test_shared_del_not_approved_record_type', 'MX', [{'preference': 3, 'exchange': 'mx'}])

    try:
        create_rs = shared_client.create_recordset(record_json, status=202)
        result_rs = shared_client.wait_until_recordset_change_status(create_rs, 'Complete')['recordSet']

        error = dummy_client.delete_recordset(shared_zone['id'], result_rs['id'], status=403)
        assert_that(error, is_('User dummy does not have access to delete test-shared-del-not-approved-record-type.shared.'))

    finally:
        if result_rs:
            delete_rs = shared_client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
            shared_client.wait_until_recordset_change_status(delete_rs, 'Complete')

def test_delete_for_user_in_record_owner_group_in_non_shared_zone_fails(shared_zone_test_context):
    """
    Test that a user in record owner group cannot delete a record in a non-shared zone
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    result_rs = None

    record_json = get_recordset_json(ok_zone, 'test_non_shared_del_og', 'A', [{'address': '1.1.1.1'}], ownergroup_id = shared_zone_test_context.shared_record_group['id'])

    try:
        create_rs = ok_client.create_recordset(record_json, status=202)
        result_rs = ok_client.wait_until_recordset_change_status(create_rs, 'Complete')['recordSet']

        error = shared_client.delete_recordset(ok_zone['id'], result_rs['id'], status=403)
        assert_that(error, is_('User sharedZoneUser does not have access to delete test-non-shared-del-og.ok.'))

    finally:
        if result_rs:
            delete_rs = ok_client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
            ok_client.wait_until_recordset_change_status(delete_rs, 'Complete')
