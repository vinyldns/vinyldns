import sys
import json

from vinyldns_python import VinylDNSClient
from vinyldns_context import VinylDNSTestContext
from hamcrest import *
from itertools import *
from hamcrest import *
from utils import *
from test_data import TestData


class ZoneHistoryContext(object):
    """
    Creates a zone with multiple zone changes and record set changes
    """

    def __init__(self):
        self.client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'history-key', 'history-secret')
        self.tear_down()
        self.group = None

        group = {
            'name': 'history-group',
            'email': 'test@test.com',
            'description': 'this is a description',
            'members': [ { 'id': 'history-id'} ],
            'admins': [ { 'id': 'history-id'} ]
        }

        self.group = self.client.create_group(group, status=200)
        # in theory this shouldn't be needed, but getting 'user is not in group' errors on zone creation
        self.confirm_member_in_group(self.client, self.group)

        zone_change = self.client.create_zone(
            {
                'name': 'system-test-history.',
                'email': 'i.changed.this.1.times@history-test.com',
                'shared': False,
                'adminGroupId': self.group['id'],
                'isTest': True,
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
            }, status=202)
        self.zone = zone_change['zone']

        self.client.wait_until_zone_exists(zone_change)

        # change the zone nine times to we have update events in zone change history, ten total changes including creation
        for i in range(2,11):
            zone_update = dict(self.zone)
            zone_update['connection']['key'] = VinylDNSTestContext.dns_key
            zone_update['transferConnection']['key'] = VinylDNSTestContext.dns_key
            zone_update['email'] = 'i.changed.this.{0}.times@history-test.com'.format(i)
            zone_update = self.client.update_zone(zone_update, status=202)['zone']

        # create some record sets
        (achange, a_record) = self.create_recordset(TestData.A)
        (aaaachange, aaaa_record) = self.create_recordset(TestData.AAAA)
        (cnamechange, cname_record) = self.create_recordset(TestData.CNAME)

        # wait here for all the record sets to be created
        self.client.wait_until_recordset_exists(a_record['zoneId'], a_record['id'])
        self.client.wait_until_recordset_exists(aaaa_record['zoneId'], aaaa_record['id'])
        self.client.wait_until_recordset_exists(cname_record['zoneId'], cname_record['id'])

        # update the record sets
        a_record_update = dict(a_record)
        a_record_update['ttl'] += 100
        a_record_update['records'][0]['address'] = '9.9.9.9'
        (achange, a_record_update) = self.update_recordset(a_record_update)

        aaaa_record_update = dict(aaaa_record)
        aaaa_record_update['ttl'] += 100
        aaaa_record_update['records'][0]['address'] = '2003:db8:0:0:0:0:0:4'
        (aaaachange, aaaa_record_update) = self.update_recordset(aaaa_record_update)

        cname_record_update = dict(cname_record)
        cname_record_update['ttl'] += 100
        cname_record_update['records'][0]['cname'] = 'changed-cname.'
        (cnamechange, cname_record_update) = self.update_recordset(cname_record_update)

        self.client.wait_until_recordset_change_status(achange, 'Complete')
        self.client.wait_until_recordset_change_status(aaaachange, 'Complete')
        self.client.wait_until_recordset_change_status(cnamechange, 'Complete')


        # delete the recordsets
        self.delete_recordset(a_record)
        self.delete_recordset(aaaa_record)
        self.delete_recordset(cname_record)

        self.client.wait_until_recordset_deleted(a_record['zoneId'], a_record['id'])
        self.client.wait_until_recordset_deleted(aaaa_record['zoneId'], aaaa_record['id'])
        self.client.wait_until_recordset_deleted(cname_record['zoneId'], cname_record['id'])


        # the resulting context should contain all of the parts so it makes it simple to test
        self.results = {
            'zone': self.zone,
            'zoneUpdate': zone_update,
            'creates': [a_record, aaaa_record, cname_record],
            'updates': [a_record_update, aaaa_record_update, cname_record_update]
        }

    # finalizer called by py.test when the simulation is torn down
    def tear_down(self):
        self.clear_zones()
        self.clear_group()


    def clear_group(self):
        groups = self.client.list_all_my_groups()
        group_ids = map(lambda x: x['id'], groups)

        for group_id in group_ids:
            self.client.delete_group(group_id, status=200)


    def clear_zones(self):
        # Get the groups for the ok user
        groups = self.client.list_all_my_groups()
        group_ids = map(lambda x: x['id'], groups)

        zones = self.client.list_zones()['zones']

        # we only want to delete zones that the ok user "owns"
        zones_to_delete = filter(lambda x: (x['adminGroupId'] in group_ids) or (x['account'] in group_ids), zones)
        zone_names_to_delete = map(lambda x: x['name'], zones_to_delete)

        zoneids_to_delete = map(lambda x: x['id'], zones_to_delete)

        self.client.abandon_zones(zoneids_to_delete)


    def create_recordset(self, rs):
        rs['zoneId'] = self.zone['id']
        result = self.client.create_recordset(rs, status=202)
        return result, result['recordSet']


    def update_recordset(self, rs):
        rs['zoneId'] = self.zone['id']
        result = self.client.update_recordset(rs, status=202)
        return result, result['recordSet']


    def delete_recordset(self, rs):
        result =  self.client.delete_recordset(self.zone['id'], rs['id'], status=202)
        return result, result['recordSet']

    def confirm_member_in_group(self, client, group):
        retries = 2
        success = group in client.list_all_my_groups(status=200)
        while retries >= 0 and not success:
            success = group in client.list_all_my_groups(status=200)
            time.sleep(.05)
            retries -= 1
        assert_that(success, is_(True))
