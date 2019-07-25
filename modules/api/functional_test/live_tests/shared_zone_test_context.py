import time
from vinyldns_python import VinylDNSClient
from vinyldns_context import VinylDNSTestContext
from hamcrest import *
from utils import *

class SharedZoneTestContext(object):
    """
    Creates multiple zones to test authorization / access to shared zones across users
    """
    def __init__(self):
        self.ok_vinyldns_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'okAccessKey', 'okSecretKey')
        self.dummy_vinyldns_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'dummyAccessKey', 'dummySecretKey')
        self.shared_zone_vinyldns_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'sharedZoneUserAccessKey', 'sharedZoneUserSecretKey')
        self.support_user_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'supportUserAccessKey', 'supportUserSecretKey')

        self.dummy_group = None
        self.ok_group = None
        self.shared_record_group = None

        self.tear_down() # ensures that the environment is clean before starting

        try:
            ok_group = {
                'name': 'ok-group',
                'email': 'test@test.com',
                'description': 'this is a description',
                'members': [ { 'id': 'ok'}, { 'id': 'support-user-id'} ],
                'admins': [ { 'id': 'ok'} ]
            }

            self.ok_group = self.ok_vinyldns_client.create_group(ok_group, status=200)
            # in theory this shouldn't be needed, but getting 'user is not in group' errors on zone creation
            self.confirm_member_in_group(self.ok_vinyldns_client, self.ok_group)

            dummy_group = {
                'name': 'dummy-group',
                'email': 'test@test.com',
                'description': 'this is a description',
                'members': [ { 'id': 'dummy'} ],
                'admins': [ { 'id': 'dummy'} ]
            }
            self.dummy_group = self.dummy_vinyldns_client.create_group(dummy_group, status=200)
            # in theory this shouldn't be needed, but getting 'user is not in group' errors on zone creation
            self.confirm_member_in_group(self.dummy_vinyldns_client, self.dummy_group)

            shared_record_group = {
                'name': 'record-ownergroup',
                'email': 'test@test.com',
                'description': 'this is a description',
                'members': [ { 'id': 'sharedZoneUser'}, { 'id': 'ok'} ],
                'admins': [ { 'id': 'sharedZoneUser'}, { 'id': 'ok'}  ]
            }
            self.shared_record_group = self.ok_vinyldns_client.create_group(shared_record_group, status=200)

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
            self.ok_vinyldns_client.wait_until_zone_active(ip4_reverse_zone_change[u'zone'][u'id'])
            self.ok_vinyldns_client.wait_until_zone_active(classless_base_zone_change[u'zone'][u'id'])
            self.ok_vinyldns_client.wait_until_zone_active(classless_zone_delegation_change[u'zone'][u'id'])
            self.ok_vinyldns_client.wait_until_zone_active(system_test_zone_change[u'zone'][u'id'])
            self.ok_vinyldns_client.wait_until_zone_active(parent_zone_change[u'zone'][u'id'])
            self.ok_vinyldns_client.wait_until_zone_active(ds_zone_change[u'zone'][u'id'])
            self.shared_zone_vinyldns_client.wait_until_zone_change_status_synced(shared_zone_change)
            shared_sync_change = self.shared_zone_vinyldns_client.sync_zone(self.shared_zone['id'])
            self.shared_zone_vinyldns_client.wait_until_zone_change_status_synced(non_test_shared_zone_change)
            non_test_shared_sync_change = self.shared_zone_vinyldns_client.sync_zone(self.non_test_shared_zone['id'])
            self.shared_zone_vinyldns_client.wait_until_zone_change_status_synced(shared_sync_change)
            self.shared_zone_vinyldns_client.wait_until_zone_change_status_synced(non_test_shared_sync_change)

            # validate all in there
            zones = self.dummy_vinyldns_client.list_zones()['zones']
            assert_that(len(zones), is_(2))
            zones = self.ok_vinyldns_client.list_zones()['zones']
            assert_that(len(zones), is_(8))
            zones = self.shared_zone_vinyldns_client.list_zones()['zones']
            assert_that(len(zones), is_(2))

        except:
            # teardown if there was any issue in setup
            try:
                self.tear_down()
            except:
                pass
            raise

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
        clear_zones(self.dummy_vinyldns_client)
        clear_zones(self.ok_vinyldns_client)
        clear_groups(self.dummy_vinyldns_client)
        clear_groups(self.ok_vinyldns_client)

    def confirm_member_in_group(self, client, group):
        retries = 2
        success = group in client.list_all_my_groups(status=200)
        while retries >= 0 and not success:
            success = group in client.list_all_my_groups(status=200)
            time.sleep(.05)
            retries -= 1
        assert_that(success, is_(True))
