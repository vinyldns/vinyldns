import pytest
import uuid

from utils import *
from hamcrest import *
from vinyldns_python import VinylDNSClient

def test_get_recordset_no_authorization(shared_zone_test_context):
    """
    Test getting a recordset without authorization
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.get_recordset(shared_zone_test_context.ok_zone['id'], '12345', sign_request=False, status=401)


def test_get_recordset(shared_zone_test_context):
    """
    Test getting a recordset
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None
    try:
        new_rs = {
            'zoneId': shared_zone_test_context.ok_zone['id'],
            'name': 'test_get_recordset',
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
        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']

        # Get the recordset we just made and verify
        result = client.get_recordset(result_rs['zoneId'], result_rs['id'])
        result_rs = result['recordSet']
        verify_recordset(result_rs, new_rs)

        records = [x['address'] for x in result_rs['records']]
        assert_that(records, has_length(2))
        assert_that('10.1.1.1', is_in(records))
        assert_that('10.2.2.2', is_in(records))
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')


def test_get_recordset_zone_doesnt_exist(shared_zone_test_context):
    """
    Test getting a recordset in a zone that doesn't exist should return a 404
    """
    client = shared_zone_test_context.ok_vinyldns_client
    new_rs = {
        'zoneId': shared_zone_test_context.ok_zone['id'],
        'name': 'test_get_recordset_zone_doesnt_exist',
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
    result_rs = None
    try:
        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']
        client.get_recordset('5678', result_rs['id'], status=404)
    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')


def test_get_recordset_doesnt_exist(shared_zone_test_context):
    """
    Test getting a new recordset that doesn't exist should return a 404
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.get_recordset(shared_zone_test_context.ok_zone['id'], '123', status=404)


def test_at_get_recordset(shared_zone_test_context):
    """
    Test getting a recordset with name @
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    result_rs = None
    try:
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
        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']

        # Get the recordset we just made and verify
        result = client.get_recordset(result_rs['zoneId'], result_rs['id'])
        result_rs = result['recordSet']

        expected_rs = new_rs
        expected_rs['name'] = ok_zone['name']
        verify_recordset(result_rs, expected_rs)

        records = result_rs['records']
        assert_that(records, has_length(1))
        assert_that(records[0]['text'], is_('someText'))

    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')

def test_get_recordset_from_shared_zone(shared_zone_test_context):
    """
    Test getting a recordset as the record group owner
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    retrieved_rs = None
    try:
        new_rs = get_recordset_json(shared_zone_test_context.shared_zone,
                                    "test_get_recordset", "TXT", [{'text':'should-work'}],
                                    100,
                                    shared_zone_test_context.shared_record_group['id'])

        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']

        # Get the recordset we just made and verify
        ok_client = shared_zone_test_context.ok_vinyldns_client
        retrieved = ok_client.get_recordset(result_rs['zoneId'], result_rs['id'])
        retrieved_rs = retrieved['recordSet']
        verify_recordset(retrieved_rs, new_rs)

        assert_that(retrieved_rs['ownerGroupId'], is_(shared_zone_test_context.shared_record_group['id']))
        assert_that(retrieved_rs['ownerGroupName'], is_('record-ownergroup'))

    finally:
        if retrieved_rs:
            delete_result = client.delete_recordset(retrieved_rs['zoneId'], retrieved_rs['id'], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')

def test_get_unowned_recordset_from_shared_zone(shared_zone_test_context):
    """
    Test getting an unowned recordset with no admin rights succeeds
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    result_rs = None
    try:
        new_rs = get_recordset_json(shared_zone_test_context.shared_zone,
                                    "test_get_unowned_recordset", "TXT", [{'text':'should-not-work'}])

        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']

        # Get the recordset we just made and verify
        ok_client = shared_zone_test_context.ok_vinyldns_client
        ok_client.get_recordset(result_rs['zoneId'], result_rs['id'], status=200)

    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')

def test_get_owned_recordset_from_not_shared_zone(shared_zone_test_context):
    """
    Test getting a recordset as the record group owner not in a shared zone fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_rs = None
    try:
        new_rs = get_recordset_json(shared_zone_test_context.ok_zone,
                                    "test_cant_get_owned_recordset", "TXT", [{'text':'should-work'}],
                                    100,
                                    shared_zone_test_context.shared_record_group['id'])
        result = client.create_recordset(new_rs, status=202)
        result_rs = client.wait_until_recordset_change_status(result, 'Complete')['recordSet']

        # Get the recordset we just made and verify
        shared_client = shared_zone_test_context.shared_zone_vinyldns_client
        shared_client.get_recordset(result_rs['zoneId'], result_rs['id'], status=403)

    finally:
        if result_rs:
            delete_result = client.delete_recordset(result_rs['zoneId'], result_rs['id'], status=202)
            client.wait_until_recordset_change_status(delete_result, 'Complete')
