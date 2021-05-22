from hamcrest import *
from vinyldns_client import VinylDNSClient
from vinyldns_context import VinylDNSTestContext
import time

def test_sync_zone_success():
    """
    Test syncing a zone
    """
    zone_name = 'small'
    client = VinylDNSClient()

    zones = client.list_zones()['zones']
    zone = [z for z in zones if z['name'] == zone_name + "."]

    lastLatestSync = []
    new = True
    if zone:
        zone = zone[0]
        lastLatestSync = zone['latestSync']
        new = False

    else:
        # create zone if it doesn't exist
        zone = {
            'name': zone_name,
            'email': 'test@test.com',
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
        zone_change = client.create_zone(zone, status=202)
        zone = zone_change['zone']
        client.wait_until_zone_active(zone_change[u'zone'][u'id'])

    zone_id = zone['id']

    # run sync
    change = client.sync_zone(zone_id, status=202)

    # brief wait for zone status change. Can't use getZoneHistory here to check on the changeset itself,
    # the action times out (presumably also querying the same record change table that the sync itself
    # is interacting with)
    time.sleep(0.5)
    client.wait_until_zone_status(zone_id, 'Active')

    # confirm zone has been updated
    get_result = client.get_zone(zone_id)
    synced_zone = get_result['zone']
    latestSync = synced_zone['latestSync']
    assert_that(synced_zone['updated'], is_not(none()))
    assert_that(latestSync, is_not(none()))
    if not new:
        assert_that(latestSync, is_not(lastLatestSync))
