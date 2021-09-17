from __future__ import print_function
import time
#from utils import *
from hamcrest import assert_that, is_

from functional_test.live_tests.list_batch_summaries_test_context import ListBatchChangeSummariesTestContext
from functional_test.live_tests.list_groups_test_context import ListGroupsTestContext
from functional_test.live_tests.list_recordsets_test_context import ListRecordSetsTestContext
from functional_test.live_tests.list_zones_test_context import ListZonesTestContext
from functional_test.vinyldns_context import VinylDNSTestContext
from functional_test.vinyldns_python import VinylDNSClient

from functional_test.utils import clear_zones, clear_groups


class SharedZoneTestContext(object):
    """
    Creates multiple zones to test authorization / access to shared zones across users
    """

    def __init__(self, fixture_file=None):
        self.ok_vinyldns_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'okAccessKey', 'okSecretKey')
        self.dummy_vinyldns_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'dummyAccessKey',
                                                    'dummySecretKey')
        self.shared_zone_vinyldns_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'sharedZoneUserAccessKey',
                                                          'sharedZoneUserSecretKey')
        self.support_user_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'supportUserAccessKey',
                                                  'supportUserSecretKey')
        self.unassociated_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'listGroupAccessKey',
                                                  'listGroupSecretKey')
        self.test_user_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'testUserAccessKey',
                                               'testUserSecretKey')
        self.history_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'history-key', 'history-secret')
        self.list_zones = ListZonesTestContext()
        self.list_zones_client = self.list_zones.client
        self.list_records_context = ListRecordSetsTestContext()
        self.list_groups_context = ListGroupsTestContext()
        self.list_batch_summaries_context = None

        self.dummy_group = None
        self.ok_group = None
        self.shared_record_group = None
        self.history_group = None
        self.group_activity_created = None
        self.group_activity_updated = None

        # if we are using an existing fixture, load the fixture file and pull all of our data from there
        if fixture_file:
            print ("\r\n!!! FIXTURE FILE IS SET !!!")
            self.load_fixture_file(fixture_file)
        else:
            print ("\r\n!!! FIXTURE FILE NOT SET, BUILDING FIXTURE !!!")
            # No fixture file, so we have to build everything ourselves
            self.tear_down()  # ensures that the environment is clean before starting
            try:
                ok_group = {
                    'name': 'ok-group',
                    'email': 'test@test.com',
                    'description': 'this is a descriptionn',
                    'members': [{'id': 'ok'}, {'id': 'support-user-id'}],
                    'admins': [{'id': 'ok'}]
                }

                self.ok_group = self.ok_vinyldns_client.create_group(ok_group, status=200)
                # in theory this shouldn't be needed, but getting 'user is not in group' errors on zone creation
                self.confirm_member_in_group(self.ok_vinyldns_client, self.ok_group)

                dummy_group = {
                    'name': 'dummy-group',
                    'email': 'test@test.com',
                    'description': 'this is a description',
                    'members': [{'id': 'dummy'}],
                    'admins': [{'id': 'dummy'}]
                }
                self.dummy_group = self.dummy_vinyldns_client.create_group(dummy_group, status=200)
                # in theory this shouldn't be needed, but getting 'user is not in group' errors on zone creation
                self.confirm_member_in_group(self.dummy_vinyldns_client, self.dummy_group)

                shared_record_group = {
                    'name': 'record-ownergroup',
                    'email': 'test@test.com',
                    'description': 'this is a description',
                    'members': [{'id': 'sharedZoneUser'}, {'id': 'ok'}],
                    'admins': [{'id': 'sharedZoneUser'}, {'id': 'ok'}]
                }
                self.shared_record_group = self.ok_vinyldns_client.create_group(shared_record_group, status=200)

                history_group = {
                    'name': 'history-group',
                    'email': 'test@test.com',
                    'description': 'this is a description',
                    'members': [{'id': 'history-id'}],
                    'admins': [{'id': 'history-id'}]
                }
                self.history_group = self.history_client.create_group(history_group, status=200)
                self.confirm_member_in_group(self.history_client, self.history_group)

                history_zone_change = self.history_client.create_zone(
                    {
                        'name': 'system-test-history.',
                        'email': 'i.changed.this.1.times@history-test.com',
                        'shared': False,
                        'adminGroupId': self.history_group['id'],
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
                self.history_zone = history_zone_change['zone']

                ok_zone_change = self.ok_vinyldns_client.create_zone(
                    {
                        'name': 'ok.',
                        'email': 'test@test.com',
                        'shared': False,
                        'adminGroupId': self.ok_group['id'],
                        'isTest': True,
                        'connection': {
                            'name': 'ok.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        },
                        'transferConnection': {
                            'name': 'ok.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        }
                    }, status=202)
                self.ok_zone = ok_zone_change['zone']

                dummy_zone_change = self.dummy_vinyldns_client.create_zone(
                    {
                        'name': 'dummy.',
                        'email': 'test@test.com',
                        'shared': False,
                        'adminGroupId': self.dummy_group['id'],
                        'isTest': True,
                        'connection': {
                            'name': 'dummy.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        },
                        'transferConnection': {
                            'name': 'dummy.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        }
                    }, status=202)
                self.dummy_zone = dummy_zone_change['zone']

                ip6_reverse_zone_change = self.ok_vinyldns_client.create_zone(
                    {
                        'name': '1.9.e.f.c.c.7.2.9.6.d.f.ip6.arpa.',
                        'email': 'test@test.com',
                        'shared': False,
                        'adminGroupId': self.ok_group['id'],
                        'isTest': True,
                        'connection': {
                            'name': 'ip6.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        },
                        'transferConnection': {
                            'name': 'ip6.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        }
                    }, status=202
                )
                self.ip6_reverse_zone = ip6_reverse_zone_change['zone']

                ip6_16_nibble_zone_change = self.ok_vinyldns_client.create_zone(
                    {
                        'name': '0.0.0.1.1.9.e.f.c.c.7.2.9.6.d.f.ip6.arpa.',
                        'email': 'test@test.com',
                        'shared': False,
                        'adminGroupId': self.ok_group['id'],
                        'isTest': True,
                        'backendId': 'func-test-backend'
                    }, status=202
                )
                self.ip6_16_nibble_zone = ip6_16_nibble_zone_change['zone']

                ip4_reverse_zone_change = self.ok_vinyldns_client.create_zone(
                    {
                        'name': '10.10.in-addr.arpa.',
                        'email': 'test@test.com',
                        'shared': False,
                        'adminGroupId': self.ok_group['id'],
                        'isTest': True,
                        'connection': {
                            'name': 'ip4.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        },
                        'transferConnection': {
                            'name': 'ip4.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        }
                    }, status=202
                )
                self.ip4_reverse_zone = ip4_reverse_zone_change['zone']

                self.classless_base_zone_json = {
                    'name': '2.0.192.in-addr.arpa.',
                    'email': 'test@test.com',
                    'shared': False,
                    'adminGroupId': self.ok_group['id'],
                    'isTest': True,
                    'connection': {
                        'name': 'classless-base.',
                        'keyName': VinylDNSTestContext.dns_key_name,
                        'key': VinylDNSTestContext.dns_key,
                        'primaryServer': VinylDNSTestContext.dns_ip
                    },
                    'transferConnection': {
                        'name': 'classless-base.',
                        'keyName': VinylDNSTestContext.dns_key_name,
                        'key': VinylDNSTestContext.dns_key,
                        'primaryServer': VinylDNSTestContext.dns_ip
                    }
                }

                classless_base_zone_change = self.ok_vinyldns_client.create_zone(
                    self.classless_base_zone_json, status=202
                )
                self.classless_base_zone = classless_base_zone_change['zone']

                classless_zone_delegation_change = self.ok_vinyldns_client.create_zone(
                    {
                        'name': '192/30.2.0.192.in-addr.arpa.',
                        'email': 'test@test.com',
                        'shared': False,
                        'adminGroupId': self.ok_group['id'],
                        'isTest': True,
                        'connection': {
                            'name': 'classless.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        },
                        'transferConnection': {
                            'name': 'classless.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        }
                    }, status=202
                )
                self.classless_zone_delegation_zone = classless_zone_delegation_change['zone']

                system_test_zone_change = self.ok_vinyldns_client.create_zone(
                    {
                        'name': 'system-test.',
                        'email': 'test@test.com',
                        'shared': False,
                        'adminGroupId': self.ok_group['id'],
                        'isTest': True,
                        'connection': {
                            'name': 'system-test.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        },
                        'transferConnection': {
                            'name': 'system-test.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        }
                    }, status=202
                )
                self.system_test_zone = system_test_zone_change['zone']

                # parent zone gives access to the dummy user, dummy user cannot manage ns records
                parent_zone_change = self.ok_vinyldns_client.create_zone(
                    {
                        'name': 'parent.com.',
                        'email': 'test@test.com',
                        'shared': False,
                        'adminGroupId': self.ok_group['id'],
                        'isTest': True,
                        'acl': {
                            'rules': [
                                {
                                    'accessLevel': 'Delete',
                                    'description': 'some_test_rule',
                                    'userId': 'dummy'
                                }
                            ]
                        },
                        'connection': {
                            'name': 'parent.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        },
                        'transferConnection': {
                            'name': 'parent.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        }
                    }, status=202)
                self.parent_zone = parent_zone_change['zone']

                # mimicking the spec example
                ds_zone_change = self.ok_vinyldns_client.create_zone(
                    {
                        'name': 'example.com.',
                        'email': 'test@test.com',
                        'shared': False,
                        'adminGroupId': self.ok_group['id'],
                        'isTest': True,
                        'connection': {
                            'name': 'example.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        },
                        'transferConnection': {
                            'name': 'example.',
                            'keyName': VinylDNSTestContext.dns_key_name,
                            'key': VinylDNSTestContext.dns_key,
                            'primaryServer': VinylDNSTestContext.dns_ip
                        }
                    }, status=202)
                self.ds_zone = ds_zone_change['zone']

                # zone with name configured for manual review
                requires_review_zone_change = self.ok_vinyldns_client.create_zone(
                    {
                        'name': 'zone.requires.review.',
                        'email': 'test@test.com',
                        'shared': False,
                        'adminGroupId': self.ok_group['id'],
                        'isTest': True,
                        'backendId': 'func-test-backend'
                    }, status=202)
                self.requires_review_zone = requires_review_zone_change['zone']

                get_shared_zones = self.shared_zone_vinyldns_client.list_zones(status=200)['zones']
                shared_zone = [zone for zone in get_shared_zones if zone['name'] == "shared."]
                non_test_shared_zone = [zone for zone in get_shared_zones if zone['name'] == "non.test.shared."]

                shared_zone_change = self.set_up_shared_zone(shared_zone[0]['id'])
                self.shared_zone = shared_zone_change['zone']

                non_test_shared_zone_change = self.set_up_shared_zone(non_test_shared_zone[0]['id'])
                self.non_test_shared_zone = non_test_shared_zone_change['zone']

                # wait until our zones are created
                self.ok_vinyldns_client.wait_until_zone_active(system_test_zone_change[u'zone'][u'id'])
                self.ok_vinyldns_client.wait_until_zone_active(ok_zone_change[u'zone'][u'id'])
                self.dummy_vinyldns_client.wait_until_zone_active(dummy_zone_change[u'zone'][u'id'])
                self.ok_vinyldns_client.wait_until_zone_active(ip6_reverse_zone_change[u'zone'][u'id'])
                self.ok_vinyldns_client.wait_until_zone_active(ip6_16_nibble_zone_change[u'zone'][u'id'])
                self.ok_vinyldns_client.wait_until_zone_active(ip4_reverse_zone_change[u'zone'][u'id'])
                self.ok_vinyldns_client.wait_until_zone_active(classless_base_zone_change[u'zone'][u'id'])
                self.ok_vinyldns_client.wait_until_zone_active(classless_zone_delegation_change[u'zone'][u'id'])
                self.ok_vinyldns_client.wait_until_zone_active(system_test_zone_change[u'zone'][u'id'])
                self.ok_vinyldns_client.wait_until_zone_active(parent_zone_change[u'zone'][u'id'])
                self.ok_vinyldns_client.wait_until_zone_active(ds_zone_change[u'zone'][u'id'])
                self.ok_vinyldns_client.wait_until_zone_active(requires_review_zone_change[u'zone'][u'id'])
                self.history_client.wait_until_zone_active(history_zone_change[u'zone'][u'id'])
                self.shared_zone_vinyldns_client.wait_until_zone_change_status_synced(shared_zone_change)

                shared_sync_change = self.shared_zone_vinyldns_client.sync_zone(self.shared_zone['id'])
                self.shared_zone_vinyldns_client.wait_until_zone_change_status_synced(non_test_shared_zone_change)
                non_test_shared_sync_change = self.shared_zone_vinyldns_client.sync_zone(
                    self.non_test_shared_zone['id'])

                self.shared_zone_vinyldns_client.wait_until_zone_change_status_synced(shared_sync_change)
                self.shared_zone_vinyldns_client.wait_until_zone_change_status_synced(non_test_shared_sync_change)

                # validate all in there
                zones = self.dummy_vinyldns_client.list_zones()['zones']
                assert_that(len(zones), is_(2))
                zones = self.ok_vinyldns_client.list_zones()['zones']
                assert_that(len(zones), is_(10))
                zones = self.shared_zone_vinyldns_client.list_zones()['zones']
                assert_that(len(zones), is_(2))

                # initialize history
                self.init_history()

                # initalize group activity
                self.init_group_activity()

                # initialize list zones, only do this when constructing the whole!
                self.list_zones.build()

                # note: there are no state to load, the tests only need the client
                self.list_zones_client = self.list_zones.client

                # build the list of records; note: we do need to save the test records
                self.list_records_context.build()

                # build the list of groups
                self.list_groups_context.build()

            except:
                # teardown if there was any issue in setup
                try:
                    self.tear_down()
                except:
                    pass
                raise

        # We need to load somethings AFTER we are all initialized, do that here
        self.list_batch_summaries_context = ListBatchChangeSummariesTestContext(self)

    def init_history(self):
        from test_data import TestData
        import copy
        # Initialize the zone history
        # change the zone nine times to we have update events in zone change history,
        # ten total changes including creation
        for i in range(2, 11):
            zone_update = copy.deepcopy(self.history_zone)
            zone_update['connection']['key'] = VinylDNSTestContext.dns_key
            zone_update['transferConnection']['key'] = VinylDNSTestContext.dns_key
            zone_update['email'] = 'i.changed.this.{0}.times@history-test.com'.format(i)
            zone_update = self.history_client.update_zone(zone_update, status=202)['zone']

        # create some record sets
        test_a = TestData.A.copy()
        test_a['zoneId'] = self.history_zone['id']
        test_aaaa = TestData.AAAA.copy()
        test_aaaa['zoneId'] = self.history_zone['id']
        test_cname = TestData.CNAME.copy()
        test_cname['zoneId'] = self.history_zone['id']

        a_record = self.history_client.create_recordset(test_a, status=202)['recordSet']
        aaaa_record = self.history_client.create_recordset(test_aaaa, status=202)['recordSet']
        cname_record = self.history_client.create_recordset(test_cname, status=202)['recordSet']

        # wait here for all the record sets to be created
        self.history_client.wait_until_recordset_exists(a_record['zoneId'], a_record['id'])
        self.history_client.wait_until_recordset_exists(aaaa_record['zoneId'], aaaa_record['id'])
        self.history_client.wait_until_recordset_exists(cname_record['zoneId'], cname_record['id'])

        # update the record sets
        a_record_update = copy.deepcopy(a_record)
        a_record_update['ttl'] += 100
        a_record_update['records'][0]['address'] = '9.9.9.9'
        a_change = self.history_client.update_recordset(a_record_update, status=202)

        aaaa_record_update = copy.deepcopy(aaaa_record)
        aaaa_record_update['ttl'] += 100
        aaaa_record_update['records'][0]['address'] = '2003:db8:0:0:0:0:0:4'
        aaaa_change = self.history_client.update_recordset(aaaa_record_update, status=202)

        cname_record_update = copy.deepcopy(cname_record)
        cname_record_update['ttl'] += 100
        cname_record_update['records'][0]['cname'] = 'changed-cname.'
        cname_change = self.history_client.update_recordset(cname_record_update, status=202)

        self.history_client.wait_until_recordset_change_status(a_change, 'Complete')
        self.history_client.wait_until_recordset_change_status(aaaa_change, 'Complete')
        self.history_client.wait_until_recordset_change_status(cname_change, 'Complete')

        # delete the recordsets
        self.history_client.delete_recordset(a_record['zoneId'], a_record['id'])
        self.history_client.delete_recordset(aaaa_record['zoneId'], aaaa_record['id'])
        self.history_client.delete_recordset(cname_record['zoneId'], cname_record['id'])

        self.history_client.wait_until_recordset_deleted(a_record['zoneId'], a_record['id'])
        self.history_client.wait_until_recordset_deleted(aaaa_record['zoneId'], aaaa_record['id'])
        self.history_client.wait_until_recordset_deleted(cname_record['zoneId'], cname_record['id'])

    def init_group_activity(self):
        client = self.ok_vinyldns_client
        created_group = None

        group_name = 'test-list-group-activity-max-item-success'

        # cleanup existing group if it's already in there
        groups = client.list_all_my_groups()
        existing = [grp for grp in groups if grp['name'] == group_name]
        for grp in existing:
            client.delete_group(grp['id'], status=200)

        members = [{'id': 'ok'}]
        new_group = {
            'name': group_name,
            'email': 'test@test.com',
            'members': members,
            'admins': [{'id': 'ok'}]
        }
        created_group = client.create_group(new_group, status=200)

        update_groups = []
        updated_groups = []
        # each update changes the member
        for runner in range(0, 10):
            id = "dummy{0:0>3}".format(runner)
            members = [{'id': id}]
            update_groups.append({
                'id': created_group['id'],
                'name': group_name,
                'email': 'test@test.com',
                'members': members,
                'admins': [{'id': 'ok'}]
            })
            updated_groups.append(client.update_group(update_groups[runner]['id'], update_groups[runner], status=200))

        self.group_activity_created = created_group
        self.group_activity_updated = updated_groups

    def load_fixture_file(self, fixture_file):
        # The fixture file contains all of the groups and zones,
        # The format is simply json where groups = [] and zones = []
        import json
        with open(fixture_file) as json_file:
            data = json.load(json_file)
            self.ok_group = data['ok_group']
            self.ok_zone = data['ok_zone']
            self.dummy_group = data['dummy_group']
            self.shared_record_group = data['shared_record_group']
            self.dummy_zone = data['dummy_zone']
            self.ip6_reverse_zone = data['ip6_reverse_zone']
            self.ip6_16_nibble_zone = data['ip6_16_nibble_zone']
            self.ip4_reverse_zone = data['ip4_reverse_zone']
            self.classless_base_zone = data['classless_base_zone']
            self.classless_zone_delegation_zone = data['classless_zone_delegation_zone']
            self.system_test_zone = data['system_test_zone']
            self.parent_zone = data['parent_zone']
            self.ds_zone = data['ds_zone']
            self.requires_review_zone = data['requires_review_zone']
            self.shared_zone = data['shared_zone']
            self.non_test_shared_zone = data['non_test_shared_zone']
            self.history_zone = data['history_zone']
            self.history_group = data['history_group']
            self.group_activity_created = data['group_activity_created']
            self.group_activity_updated = data['group_activity_updated']

    def out_fixture_file(self, fixture_file):
        print ("\r\n!!! PRINTING OUT FIXTURE FILE !!!")
        import json
        # output the fixture file, be sure to be in sync with the load_fixture_file
        data = {'ok_group': self.ok_group, 'ok_zone': self.ok_zone, 'dummy_group': self.dummy_group,
                'shared_record_group': self.shared_record_group, 'dummy_zone': self.dummy_zone,
                'ip6_reverse_zone': self.ip6_reverse_zone, 'ip6_16_nibble_zone': self.ip6_16_nibble_zone,
                'ip4_reverse_zone': self.ip4_reverse_zone, 'classless_base_zone': self.classless_base_zone,
                'classless_zone_delegation_zone': self.classless_zone_delegation_zone,
                'system_test_zone': self.system_test_zone, 'parent_zone': self.parent_zone, 'ds_zone': self.ds_zone,
                'requires_review_zone': self.requires_review_zone, 'shared_zone': self.shared_zone,
                'non_test_shared_zone': self.non_test_shared_zone, 'history_zone': self.history_zone,
                'history_group': self.history_group, 'group_activity_created': self.group_activity_created,
                'group_activity_updated': self.group_activity_updated}
        with open(fixture_file, 'w') as out_file:
            json.dump(data, out_file)

    def set_up_shared_zone(self, zone_id):
        # shared zones are created through test data loader, but needs connection info added here to use
        get_shared_zone = self.shared_zone_vinyldns_client.get_zone(zone_id)
        shared_zone = get_shared_zone['zone']

        connection_info = {
            'name': 'shared.',
            'keyName': VinylDNSTestContext.dns_key_name,
            'key': VinylDNSTestContext.dns_key,
            'primaryServer': VinylDNSTestContext.dns_ip
        }

        shared_zone['connection'] = connection_info
        shared_zone['transferConnection'] = connection_info

        return self.shared_zone_vinyldns_client.update_zone(shared_zone, status=202)

    def tear_down(self):
        """
        The ok_vinyldns_client is a zone admin on _all_ the zones.

        We shouldn't have to do any checks now, as zone admins have full rights to all zones, including
        deleting all records (even in the old shared model)
        """
        self.list_zones.tear_down()
        self.list_records_context.tear_down()

        if self.list_batch_summaries_context:
            self.list_batch_summaries_context.tear_down(self)

        if self.list_groups_context:
            self.list_groups_context.tear_down()

        clear_zones(self.dummy_vinyldns_client)
        clear_zones(self.ok_vinyldns_client)
        clear_zones(self.history_client)
        clear_groups(self.dummy_vinyldns_client, "global-acl-group-id")
        clear_groups(self.ok_vinyldns_client, "global-acl-group-id")
        clear_groups(self.history_client)

    @staticmethod
    def confirm_member_in_group(client, group):
        retries = 2
        success = group in client.list_all_my_groups(status=200)
        while retries >= 0 and not success:
            success = group in client.list_all_my_groups(status=200)
            time.sleep(.05)
            retries -= 1
        assert_that(success, is_(True))
