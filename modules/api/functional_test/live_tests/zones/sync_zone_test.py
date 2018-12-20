from hamcrest import *
from vinyldns_python import VinylDNSClient
from vinyldns_context import VinylDNSTestContext
from utils import *
import time

MAX_RETRIES = 30
RETRY_WAIT = 0.05

records_in_dns = [
    {'name': 'sync-test.',
     'type': 'SOA',
     'records': [{u'mname': u'172.17.42.1.',
                  u'rname': u'admin.test.com.',
                  u'retry': 3600,
                  u'refresh': 10800,
                  u'minimum': 38400,
                  u'expire': 604800,
                  u'serial': 1439234395}]},
    {'name': u'sync-test.',
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
    {'name': u'sync-test.',
     'type': u'A',
     'records': [{u'address': u'5.5.5.5'}]},
    {'name': u'already-exists',
     'type': u'A',
     'records': [{u'address': u'6.6.6.6'}]},
    {'name': u'fqdn',
     'type': u'A',
     'records': [{u'address': u'7.7.7.7'}]},
    {'name': u'_sip._tcp',
     'type': u'SRV',
     'records': [{u'priority': 10, u'weight': 60, u'port': 5060, u'target': u'foo.sync-test.'}]},
    {'name': u'existing.dotted',
     'type': u'A',
     'records': [{u'address': u'9.9.9.9'}]}]

records_post_update = [
    {'name': 'sync-test.',
     'type': 'SOA',
     'records': [{u'mname': u'172.17.42.1.',
                  u'rname': u'admin.test.com.',
                  u'retry': 3600,
                  u'refresh': 10800,
                  u'minimum': 38400,
                  u'expire': 604800,
                  u'serial': 0}]},
    {'name': u'sync-test.',
     'type': u'NS',
     'records': [{u'nsdname': u'172.17.42.1.'}]},
    {'name': u'foo',
     'type': u'A',
     'records': [{u'address': u'1.2.3.4'}]},
    {'name': u'test',
     'type': u'A',
     'records': [{u'address': u'3.3.3.3'}, {u'address': u'4.4.4.4'}]},
    {'name': u'sync-test.',
     'type': u'A',
     'records': [{u'address': u'5.5.5.5'}]},
    {'name': u'already-exists',
     'type': u'A',
     'records': [{u'address': u'6.6.6.6'}]},
    {'name': u'newrs',
     'type': u'A',
     'records': [{u'address': u'2.3.4.5'}]},
    {'name': u'fqdn',
     'type': u'A',
     'records': [{u'address': u'7.7.7.7'}]},
    {'name': u'_sip._tcp',
     'type': u'SRV',
     'records': [{u'priority': 10, u'weight': 60, u'port': 5060, u'target': u'foo.sync-test.'}]},
    {'name': u'existing.dotted',
     'type': u'A',
     'records': [{u'address': u'9.9.9.9'}]},
    {'name': u'dott.ed',
     'type': u'A',
     'records': [{u'address': u'6.7.8.9'}]}]


@pytest.mark.skip_production
def test_sync_zone_success(shared_zone_test_context):
    """
    Test syncing a zone
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone_name = 'sync-test'

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
    try:
        zone_change = client.create_zone(zone, status=202)
        zone = zone_change['zone']
        client.wait_until_zone_active(zone['id'])

        time.sleep(.5)

        # confirm zone has been synced
        get_result = client.get_zone(zone['id'])
        synced_zone = get_result['zone']
        latest_sync = synced_zone['latestSync']
        assert_that(latest_sync, is_not(none()))

        # confirm that the recordsets in DNS have been saved in vinyldns
        recordsets = client.list_recordsets(zone['id'])['recordSets']

        assert_that(len(recordsets), is_(10))
        for rs in recordsets:
            small_rs = dict((k, rs[k]) for k in ['name', 'type', 'records'])
            small_rs['records'] = sorted(small_rs['records'])
            if small_rs['type'] == 'SOA':
                assert_that(small_rs['name'], is_('sync-test.'))
            else:
                assert_that(records_in_dns, has_item(small_rs))

        # make changes to the dns backend
        dns_update(zone, 'foo', 38400, 'A', '1.2.3.4')
        dns_add(zone, 'newrs', 38400, 'A', '2.3.4.5')
        dns_delete(zone, 'jenkins', 'A')

        # add unknown this should not be synced
        dns_add(zone, 'dnametest', 38400, 'DNAME', 'test.com.')

        # add a dotted host, this should be synced, so we will have 10 records ( +1 )
        dns_add(zone, 'dott.ed', 38400, 'A', '6.7.8.9')

        # wait for next sync
        time.sleep(10)

        # sync again
        change = client.sync_zone(zone['id'], status=202)
        client.wait_until_zone_change_status_synced(change)

        # confirm cannot again sync without waiting
        client.sync_zone(zone['id'], status=403)

        # validate zone
        get_result = client.get_zone(zone['id'])
        synced_zone = get_result['zone']
        assert_that(synced_zone['latestSync'], is_not(latest_sync))
        assert_that(synced_zone['status'], is_('Active'))
        assert_that(synced_zone['updated'], is_not(none()))

        # confirm that the updated recordsets in DNS have been saved in vinyldns
        recordsets = client.list_recordsets(zone['id'])['recordSets']
        assert_that(len(recordsets), is_(11))
        for rs in recordsets:
            small_rs = dict((k, rs[k]) for k in ['name', 'type', 'records'])
            small_rs['records'] = sorted(small_rs['records'])
            if small_rs['type'] == 'SOA':
                small_rs['records'][0]['serial'] = 0
            # records_post_update does not contain dnametest
            assert_that(records_post_update, has_item(small_rs))

        changes = client.list_recordset_changes(zone['id'])
        for c in changes['recordSetChanges']:
            assert_that(c['systemMessage'], is_('Change applied via zone sync'))

        for rs in recordsets:
            # confirm that we cannot update the dotted host if the name is the same
            if rs['name'] == 'dott.ed':
                attempt_update = rs
                attempt_update['ttl'] = attempt_update['ttl'] + 100
                errors = client.update_recordset(attempt_update, status=422)
                assert_that(errors, is_("Record with name " + rs['name'] + " is a dotted host which is illegal "
                                        "in this zone " + zone_name + "."))

                # we should be able to delete the record
                client.delete_recordset(rs['zoneId'], rs['id'], status=202)
                client.wait_until_recordset_deleted(rs['zoneId'], rs['id'])
            if rs['name'] == "example.dotted":
                # confirm that we can modify the example dotted
                good_update = rs
                good_update['name'] = "example-dotted"
                change = client.update_recordset(good_update, status=202)
                client.wait_until_recordset_change_status(change, 'Complete')

    finally:
        if 'id' in zone:
            dns_update(zone, 'foo', 38400, 'A', '2.2.2.2')
            dns_delete(zone, 'newrs', 'A')
            dns_add(zone, 'jenkins', 38400, 'A', '10.1.1.1')
            dns_delete(zone, 'example-dotted', 'A')
            client.abandon_zones([zone['id']], status=202)
