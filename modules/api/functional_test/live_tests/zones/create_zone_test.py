import pytest
import uuid

from hamcrest import *
from vinyldns_python import VinylDNSClient
from vinyldns_context import VinylDNSTestContext
from utils import *

records_in_dns = [
    {'name': 'one-time.',
     'type': 'SOA',
     'records': [{u'mname': u'172.17.42.1.',
                  u'rname': u'admin.test.com.',
                  u'retry': 3600,
                  u'refresh': 10800,
                  u'minimum': 38400,
                  u'expire': 604800,
                  u'serial': 1439234395}]},
    {'name': u'one-time.',
     'type': u'NS',
     'records': [{u'nsdname': u'172.17.42.1.'}]},
    {'name': u'jenkins',
     'type': u'A',
     'records': [{u'address': u'10.1.1.1'}]},
    {'name': u'foo',
     'type': u'A',
     'records': [{u'address': u'2.2.2.2'}]},
    {'name': u'test',
     'type': u'A',
     'records': [{u'address': u'3.3.3.3'}, {u'address': u'4.4.4.4'}]},
    {'name': u'one-time.',
     'type': u'A',
     'records': [{u'address': u'5.5.5.5'}]},
    {'name': u'already-exists',
     'type': u'A',
     'records': [{u'address': u'6.6.6.6'}]}]

def test_create_zone_success(shared_zone_test_context):
    """
    Test successfully creating a zone
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        zone_name = 'one-time'

        zone = {
            'name': zone_name,
            'email': 'test@test.com',
            'adminGroupId': shared_zone_test_context.ok_group['id'],
            'connection': {
                'name': 'vinyldns.',
                'keyName': VinylDNSTestContext.dns_key_name,
                'key': VinylDNSTestContext.dns_key,
                'primaryServer': VinylDNSTestContext.dns_ip
            },
            'transferConnection': {
                'name': 'vinyldns.',
                'keyName': VinylDNSTestContext.dns_key_name,
                'key': VinylDNSTestContext.dns_key,
                'primaryServer': VinylDNSTestContext.dns_ip
            }
        }
        result = client.create_zone(zone, status=202)
        result_zone = result['zone']
        client.wait_until_zone_change_status(result, 'Synced')

        get_result = client.get_zone(result_zone['id'])

        get_zone = get_result['zone']
        assert_that(get_zone['name'], is_(zone['name']+'.'))
        assert_that(get_zone['email'], is_(zone['email']))
        assert_that(get_zone['adminGroupId'], is_(zone['adminGroupId']))
        assert_that(get_zone['latestSync'], is_not(none()))
        assert_that(get_zone['status'], is_('Active'))

        # confirm that the recordsets in DNS have been saved in vinyldns
        recordsets = client.list_recordsets(result_zone['id'])['recordSets']

        assert_that(len(recordsets), is_(7))
        for rs in recordsets:
            small_rs = dict((k, rs[k]) for k in ['name', 'type', 'records'])
            small_rs['records'] = sorted(small_rs['records'])
            assert_that(records_in_dns, has_item(small_rs))

    finally:
        if result_zone:
            client.abandon_zones([result_zone['id']], status=202)


@pytest.mark.skip_production
def test_create_zone_without_transfer_connection_leaves_it_empty(shared_zone_test_context):
    """
    Test that creating a zone with a valid connection but without a transfer connection leaves the transfer connection empty
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        zone_name = 'one-time'

        zone = {
            'name': zone_name,
            'email': 'test@test.com',
            'adminGroupId': shared_zone_test_context.ok_group['id'],
            'connection': {
                'name': 'vinyldns.',
                'keyName': VinylDNSTestContext.dns_key_name,
                'key': VinylDNSTestContext.dns_key,
                'primaryServer': VinylDNSTestContext.dns_ip
            }
        }
        result = client.create_zone(zone, status=202)
        result_zone = result['zone']
        client.wait_until_zone_exists(result)

        get_result = client.get_zone(result_zone['id'])

        get_zone = get_result['zone']
        assert_that(get_zone['name'], is_(zone['name']+'.'))
        assert_that(get_zone['email'], is_(zone['email']))
        assert_that(get_zone['adminGroupId'], is_(zone['adminGroupId']))

        assert_that(get_zone, is_not(has_key('transferConnection')))
    finally:
        if result_zone:
            client.abandon_zones([result_zone['id']], status=202)


def test_create_zone_fails_no_authorization(shared_zone_test_context):
    """
    Test creating a new zone without authorization
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone = {
        'name': str(uuid.uuid4()),
        'email': 'test@test.com',
    }
    client.create_zone(zone, sign_request=False, status=401)


def test_create_missing_zone_data(shared_zone_test_context):
    """
    Test that creating a zone without providing necessary data (name and email) returns errors
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone = {
        'random_key': 'some_value',
        'another_key': 'meaningless_data'
    }

    errors = client.create_zone(zone, status=400)['errors']
    assert_that(errors, contains_inanyorder('Missing Zone.name', 'Missing Zone.email'))


def test_create_invalid_zone_data(shared_zone_test_context):
    """
    Test that creating a zone with invalid data returns errors
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = 'test.zone.invalid.'

    zone = {
        'name': zone_name,
        'email': 'test@test.com',
        'status': 'invalid_status'
    }

    errors = client.create_zone(zone, status=400)['errors']
    assert_that(errors, contains_inanyorder('Invalid ZoneStatus'))


def test_create_zone_with_connection_failure(shared_zone_test_context):
    """
    Test creating a new zone with a an invalid key and connection info fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = 'one-time.'
    zone = {
        'name': zone_name,
        'email': 'test@test.com',
        'connection': {
            'name': zone_name,
            'keyName': zone_name,
            'key': VinylDNSTestContext.dns_key,
            'primaryServer': VinylDNSTestContext.dns_ip
        }
    }
    client.create_zone(zone, status=400)


def test_create_zone_returns_409_if_already_exists(shared_zone_test_context):
    """
    Test creating a zone returns a 409 Conflict if the zone name already exists
    """
    create_conflict = dict(shared_zone_test_context.ok_zone)
    create_conflict['connection']['key'] = VinylDNSTestContext.dns_key # necessary because we encrypt the key
    create_conflict['transferConnection']['key'] = VinylDNSTestContext.dns_key

    shared_zone_test_context.ok_vinyldns_client.create_zone(create_conflict, status=409)


def test_create_zone_returns_400_for_invalid_data(shared_zone_test_context):
    """
    Test creating a zone returns a 400 if the request body is invalid
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone = {
        'jim': 'bob',
        'hey': 'you'
    }
    client.create_zone(zone, status=400)


@pytest.mark.skip_production
def test_create_zone_no_connection_uses_defaults(shared_zone_test_context):

    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = 'one-time'

    zone = {
        'name': zone_name,
        'email': 'test@test.com',
        'adminGroupId': shared_zone_test_context.ok_group['id']
    }

    try:
        zone_change = client.create_zone(zone, status=202)
        zone = zone_change['zone']
        client.wait_until_zone_exists(zone_change)

        # Check response from create
        assert_that(zone['name'], is_(zone_name+'.'))
        print "'connection' not in zone = " + 'connection' not in zone

        assert_that('connection' not in zone)#KeyError: 'connection'
        assert_that('transferConnection' not in zone)

        # Check that it was internally stored correctly using GET
        zone_get = client.get_zone(zone['id'])['zone']
        assert_that(zone_get['name'], is_(zone_name+'.'))
        assert_that('connection' not in zone_get)
        assert_that('transferConnection' not in zone_get)

    finally:
        if 'id' in zone:
            client.abandon_zones([zone['id']], status=202)


def test_zone_connection_only(shared_zone_test_context):

    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = 'one-time'

    zone = {
        'name': zone_name,
        'email': 'test@test.com',
        'adminGroupId': shared_zone_test_context.ok_group['id'],
        'connection': {
            'name': 'vinyldns.',
            'keyName': VinylDNSTestContext.dns_key_name,
            'key': VinylDNSTestContext.dns_key,
            'primaryServer': VinylDNSTestContext.dns_ip
        },
        'transferConnection': {
            'name': 'vinyldns.',
            'keyName': VinylDNSTestContext.dns_key_name,
            'key': VinylDNSTestContext.dns_key,
            'primaryServer': VinylDNSTestContext.dns_ip
        }
    }

    expected_connection = {
        'name': 'vinyldns.',
        'keyName': VinylDNSTestContext.dns_key_name,
        'key': VinylDNSTestContext.dns_key,
        'primaryServer': VinylDNSTestContext.dns_ip
    }

    try:
        zone_change = client.create_zone(zone, status=202)
        zone = zone_change['zone']
        client.wait_until_zone_exists(zone_change)

        # Check response from create
        assert_that(zone['name'], is_(zone_name+'.'))
        assert_that(zone['connection']['name'], is_(expected_connection['name']))
        assert_that(zone['connection']['keyName'], is_(expected_connection['keyName']))
        assert_that(zone['connection']['primaryServer'], is_(expected_connection['primaryServer']))
        assert_that(zone['transferConnection']['name'], is_(expected_connection['name']))
        assert_that(zone['transferConnection']['keyName'], is_(expected_connection['keyName']))
        assert_that(zone['transferConnection']['primaryServer'], is_(expected_connection['primaryServer']))

        # Check that it was internally stored correctly using GET
        zone_get = client.get_zone(zone['id'])['zone']
        assert_that(zone_get['name'], is_(zone_name+'.'))
        assert_that(zone['connection']['name'], is_(expected_connection['name']))
        assert_that(zone['connection']['keyName'], is_(expected_connection['keyName']))
        assert_that(zone['connection']['primaryServer'], is_(expected_connection['primaryServer']))
        assert_that(zone['transferConnection']['name'], is_(expected_connection['name']))
        assert_that(zone['transferConnection']['keyName'], is_(expected_connection['keyName']))
        assert_that(zone['transferConnection']['primaryServer'], is_(expected_connection['primaryServer']))

    finally:
        if 'id' in zone:
            client.abandon_zones([zone['id']], status=202)


def test_zone_bad_connection(shared_zone_test_context):

    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = 'one-time'

    zone = {
        'name': zone_name,
        'email': 'test@test.com',
        'connection': {
            'name': zone_name,
            'keyName': VinylDNSTestContext.dns_key_name,
            'key': 'somebadkey',
            'primaryServer': VinylDNSTestContext.dns_ip
        }
    }

    client.create_zone(zone, status=400)


def test_zone_bad_transfer_connection(shared_zone_test_context):

    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = 'one-time'

    zone = {
        'name': zone_name,
        'email': 'test@test.com',
        'connection': {
            'name': zone_name,
            'keyName': VinylDNSTestContext.dns_key_name,
            'key': VinylDNSTestContext.dns_key,
            'primaryServer': VinylDNSTestContext.dns_ip
        },
        'transferConnection': {
            'name': zone_name,
            'keyName': VinylDNSTestContext.dns_key_name,
            'key': "bad",
            'primaryServer': VinylDNSTestContext.dns_ip
        }
    }

    client.create_zone(zone, status=400)


def test_zone_transfer_connection(shared_zone_test_context):

    client = shared_zone_test_context.ok_vinyldns_client

    zone_name = 'one-time'

    zone = {
        'name': zone_name,
        'email': 'test@test.com',
        'adminGroupId': shared_zone_test_context.ok_group['id'],
        'connection': {
            'name': zone_name,
            'keyName': VinylDNSTestContext.dns_key_name,
            'key': VinylDNSTestContext.dns_key,
            'primaryServer': VinylDNSTestContext.dns_ip
        },
        'transferConnection': {
            'name': zone_name,
            'keyName': VinylDNSTestContext.dns_key_name,
            'key': VinylDNSTestContext.dns_key,
            'primaryServer': VinylDNSTestContext.dns_ip
        }
    }

    expected_connection = {
        'name': zone_name,
        'keyName': VinylDNSTestContext.dns_key_name,
        'key': VinylDNSTestContext.dns_key,
        'primaryServer': VinylDNSTestContext.dns_ip
    }

    try:
        zone_change = client.create_zone(zone, status=202)
        zone = zone_change['zone']
        client.wait_until_zone_exists(zone_change)

        # Check response from create
        assert_that(zone['name'], is_(zone_name+'.'))
        assert_that(zone['connection']['name'], is_(expected_connection['name']))
        assert_that(zone['connection']['keyName'], is_(expected_connection['keyName']))
        assert_that(zone['connection']['primaryServer'], is_(expected_connection['primaryServer']))
        assert_that(zone['transferConnection']['name'], is_(expected_connection['name']))
        assert_that(zone['transferConnection']['keyName'], is_(expected_connection['keyName']))
        assert_that(zone['transferConnection']['primaryServer'], is_(expected_connection['primaryServer']))

        # Check that it was internally stored correctly using GET
        zone_get = client.get_zone(zone['id'])['zone']
        assert_that(zone_get['name'], is_(zone_name+'.'))
        assert_that(zone['connection']['name'], is_(expected_connection['name']))
        assert_that(zone['connection']['keyName'], is_(expected_connection['keyName']))
        assert_that(zone['connection']['primaryServer'], is_(expected_connection['primaryServer']))
        assert_that(zone['transferConnection']['name'], is_(expected_connection['name']))
        assert_that(zone['transferConnection']['keyName'], is_(expected_connection['keyName']))
        assert_that(zone['transferConnection']['primaryServer'], is_(expected_connection['primaryServer']))

    finally:
        if 'id' in zone:
            client.abandon_zones([zone['id']], status=202)


def test_user_cannot_create_zone_with_nonmember_admin_group(shared_zone_test_context):
    """
    Test user cannot create a zone with an admin group they are not a member of
    """
    zone = {
        'name': 'one-time.',
        'email': 'test@test.com',
        'adminGroupId': shared_zone_test_context.dummy_group['id'],
        'connection': {
            'name': 'vinyldns.',
            'keyName': VinylDNSTestContext.dns_key_name,
            'key': VinylDNSTestContext.dns_key,
            'primaryServer': VinylDNSTestContext.dns_ip
        },
        'transferConnection': {
            'name': 'vinyldns.',
            'keyName': VinylDNSTestContext.dns_key_name,
            'key': VinylDNSTestContext.dns_key,
            'primaryServer': VinylDNSTestContext.dns_ip
        }
    }

    shared_zone_test_context.ok_vinyldns_client.create_zone(zone, status=400)


def test_user_cannot_create_zone_with_failed_validations(shared_zone_test_context):
    """
    Test that a user cannot create a zone that has invalid zone data
    """
    zone = {
        'name': 'invalid-zone.',
        'email': 'test@test.com',
        'adminGroupId': shared_zone_test_context.ok_group['id'],
        'connection': {
            'name': 'vinyldns.',
            'keyName': VinylDNSTestContext.dns_key_name,
            'key': VinylDNSTestContext.dns_key,
            'primaryServer': VinylDNSTestContext.dns_ip
        },
        'transferConnection': {
            'name': 'vinyldns.',
            'keyName': VinylDNSTestContext.dns_key_name,
            'key': VinylDNSTestContext.dns_key,
            'primaryServer': VinylDNSTestContext.dns_ip
        }
    }

    result = shared_zone_test_context.ok_vinyldns_client.create_zone(zone, status=400)
    import json
    print json.dumps(result, indent=4)
    assert_that(result['errors'], contains_inanyorder(
        contains_string("not-approved.thing.com. is not an approved name server")
    ))
