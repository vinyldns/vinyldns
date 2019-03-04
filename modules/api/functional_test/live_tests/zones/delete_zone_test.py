import pytest
import uuid

from hamcrest import *
from vinyldns_python import VinylDNSClient
from vinyldns_context import VinylDNSTestContext
from utils import *


def test_delete_zone_success(shared_zone_test_context):
    """
    Test deleting a zone
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
        client.wait_until_zone_active(result_zone['id'])

        client.delete_zone(result_zone['id'], status=202)
        client.wait_until_zone_deleted(result_zone['id'])

        client.get_zone(result_zone['id'], status=404)
        result_zone = None

    finally:
        if result_zone:
            client.abandon_zones([result_zone['id']], status=202)


def test_delete_zone_twice(shared_zone_test_context):
    """
    Test deleting a zone with deleted status returns 404
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
        client.wait_until_zone_active(result_zone['id'])

        client.delete_zone(result_zone['id'], status=202)
        client.wait_until_zone_deleted(result_zone['id'])

        client.delete_zone(result_zone['id'], status=404)
        result_zone = None

    finally:
        if result_zone:
            client.abandon_zones([result_zone['id']], status=202)


def test_delete_zone_returns_404_if_zone_not_found(shared_zone_test_context):
    """
    Test deleting a zone returns a 404 if the zone was not found
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.delete_zone('nothere', status=404)


def test_delete_zone_no_authorization(shared_zone_test_context):
    """
    Test deleting a zone without authorization
    """
    client = shared_zone_test_context.ok_vinyldns_client

    client.delete_zone('1234', sign_request=False, status=401)
