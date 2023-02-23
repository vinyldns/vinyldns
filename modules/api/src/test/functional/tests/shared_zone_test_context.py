import copy
import inspect
import logging
from typing import MutableMapping, Mapping

from tests.list_batch_summaries_test_context import ListBatchChangeSummariesTestContext
from tests.list_groups_test_context import ListGroupsTestContext
from tests.list_recordsets_test_context import ListRecordSetsTestContext
from tests.list_zones_test_context import ListZonesTestContext
from tests.test_data import TestData
from utils import *
from vinyldns_python import VinylDNSClient

logger = logging.getLogger(__name__)


class SharedZoneTestContext(object):
    """
    Creates multiple zones to test authorization / access to shared zones across users
    """
    _data_cache: MutableMapping[str, MutableMapping[str, Mapping]] = {}


    def __init__(self, partition_id: str):
        self.partition_id = partition_id
        self.setup_started = False
        self.ok_vinyldns_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "okAccessKey", "okSecretKey")
        self.dummy_vinyldns_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "dummyAccessKey", "dummySecretKey")
        self.shared_zone_vinyldns_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "sharedZoneUserAccessKey", "sharedZoneUserSecretKey")
        self.support_user_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "supportUserAccessKey", "supportUserSecretKey")
        self.super_user_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "superUserAccessKey", "superUserSecretKey")
        self.unassociated_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "listGroupAccessKey", "listGroupSecretKey")
        self.test_user_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "testUserAccessKey", "testUserSecretKey")
        self.history_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "history-key", "history-secret")
        self.non_user_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "not-exist-key", "not-exist-secret")
        self.clients = [self.ok_vinyldns_client, self.dummy_vinyldns_client, self.shared_zone_vinyldns_client,
                        self.support_user_client, self.super_user_client, self.unassociated_client,
                        self.test_user_client, self.history_client, self.non_user_client]
        self.list_zones = ListZonesTestContext(partition_id)
        self.list_zones_client = self.list_zones.client
        self.list_records_context = ListRecordSetsTestContext(partition_id)
        self.list_groups_context = ListGroupsTestContext(partition_id)
        self.list_batch_summaries_context = ListBatchChangeSummariesTestContext(partition_id)

        self.dummy_group = None
        self.ok_group = None
        self.shared_record_group = None
        self.history_group = None
        self.group_activity_created = None
        self.group_activity_updated = None

        self.history_zone = None
        self.ok_zone = None
        self.dummy_zone = None
        self.ip6_reverse_zone = None
        self.ip6_16_nibble_zone = None
        self.ip4_reverse_zone = None
        self.classless_base_zone = None
        self.classless_zone_delegation_zone = None
        self.system_test_zone = None
        self.parent_zone = None
        self.ds_zone = None
        self.requires_review_zone = None
        self.shared_zone = None

        self.ip4_10_prefix = None
        self.ip4_classless_prefix = None
        self.ip6_prefix = None


    def setup(self):
        if self.setup_started:
            # Safeguard against reentrance
            return
        self.setup_started = True

        partition_id = self.partition_id
        try:
            ok_group = {
                "name": f"ok-group{partition_id}",
                "email": "test@test.com",
                "description": "this is a description",
                "members": [{"id": "ok"}, {"id": "support-user-id"}],
                "admins": [{"id": "ok"}]
            }

            self.ok_group = self.ok_vinyldns_client.create_group(ok_group, status=200)
            # in theory this shouldn"t be needed, but getting "user is not in group' errors on zone creation
            self.confirm_member_in_group(self.ok_vinyldns_client, self.ok_group)

            dummy_group = {
                "name": f"dummy-group{partition_id}",
                "email": "test@test.com",
                "description": "this is a description",
                "members": [{"id": "dummy"}],
                "admins": [{"id": "dummy"}]
            }
            self.dummy_group = self.dummy_vinyldns_client.create_group(dummy_group, status=200)
            # in theory this shouldn"t be needed, but getting "user is not in group' errors on zone creation
            self.confirm_member_in_group(self.dummy_vinyldns_client, self.dummy_group)

            shared_record_group = {
                "name": f"record-ownergroup{partition_id}",
                "email": "test@test.com",
                "description": "this is a description",
                "members": [{"id": "sharedZoneUser"}, {"id": "ok"}, {"id": "support-user-id"}],
                "admins": [{"id": "sharedZoneUser"}, {"id": "ok"}]
            }
            self.shared_record_group = self.ok_vinyldns_client.create_group(shared_record_group, status=200)

            history_group = {
                "name": f"history-group{partition_id}",
                "email": "test@test.com",
                "description": "this is a description",
                "members": [{"id": "history-id"}],
                "admins": [{"id": "history-id"}]
            }
            self.history_group = self.history_client.create_group(history_group, status=200)
            self.confirm_member_in_group(self.history_client, self.history_group)

            history_zone_change = self.history_client.create_zone(
                {
                    "name": f"system-test-history{partition_id}.",
                    "email": "i.changed.this.1.times@history-test.com",
                    "shared": False,
                    "adminGroupId": self.history_group["id"],
                    "isTest": True,
                    "connection": {
                        "name": "vinyldns.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    },
                    "transferConnection": {
                        "name": "vinyldns.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    }
                }, status=202)
            self.history_zone = history_zone_change["zone"]

            # initialize history
            self.history_client.wait_until_zone_active(history_zone_change["zone"]["id"])
            self.init_history()

            ok_zone_change = self.ok_vinyldns_client.create_zone(
                {
                    "name": f"ok{partition_id}.",
                    "email": "test@test.com",
                    "shared": False,
                    "adminGroupId": self.ok_group["id"],
                    "isTest": True,
                    "connection": {
                        "name": "ok.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    },
                    "transferConnection": {
                        "name": "ok.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    }
                }, status=202)
            self.ok_zone = ok_zone_change["zone"]

            dummy_zone_change = self.dummy_vinyldns_client.create_zone(
                {
                    "name": f"dummy{partition_id}.",
                    "email": "test@test.com",
                    "shared": False,
                    "adminGroupId": self.dummy_group["id"],
                    "isTest": True,
                    "acl": {
                        "rules": [
                            {
                                "accessLevel": "Delete",
                                "description": "some_test_rule",
                                "userId": "history-id"
                            }
                        ]
                    },
                    "connection": {
                        "name": "dummy.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    },
                    "transferConnection": {
                        "name": "dummy.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    }
                }, status=202)
            self.dummy_zone = dummy_zone_change["zone"]

            self.ip6_prefix = f"fd69:27cc:fe9{partition_id}"
            ip6_reverse_zone_change = self.ok_vinyldns_client.create_zone(
                {
                    "name": f"{partition_id}.9.e.f.c.c.7.2.9.6.d.f.ip6.arpa.",
                    "email": "test@test.com",
                    "shared": False,
                    "adminGroupId": self.ok_group["id"],
                    "isTest": True,
                    "connection": {
                        "name": "ip6.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    },
                    "transferConnection": {
                        "name": "ip6.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    }
                }, status=202
            )
            self.ip6_reverse_zone = ip6_reverse_zone_change["zone"]

            ip6_16_nibble_zone_change = self.ok_vinyldns_client.create_zone(
                {
                    "name": f"0.0.0.1.{partition_id}.9.e.f.c.c.7.2.9.6.d.f.ip6.arpa.",
                    "email": "test@test.com",
                    "shared": False,
                    "adminGroupId": self.ok_group["id"],
                    "isTest": True,
                    "backendId": "func-test-backend"
                }, status=202
            )
            self.ip6_16_nibble_zone = ip6_16_nibble_zone_change["zone"]

            self.ip4_10_prefix = f"10.{partition_id}"
            ip4_reverse_zone_change = self.ok_vinyldns_client.create_zone(
                {
                    "name": f"{partition_id}.10.in-addr.arpa.",
                    "email": "test@test.com",
                    "shared": False,
                    "adminGroupId": self.ok_group["id"],
                    "isTest": True,
                    "connection": {
                        "name": "ip4.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    },
                    "transferConnection": {
                        "name": "ip4.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    }
                }, status=202
            )
            self.ip4_reverse_zone = ip4_reverse_zone_change["zone"]

            self.ip4_classless_prefix = f"192.0.{partition_id}"
            classless_base_zone_change = self.ok_vinyldns_client.create_zone(
                {
                    "name": f"{partition_id}.0.192.in-addr.arpa.",
                    "email": "test@test.com",
                    "shared": False,
                    "adminGroupId": self.ok_group["id"],
                    "isTest": True,
                    "connection": {
                        "name": "classless-base.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    },
                    "transferConnection": {
                        "name": "classless-base.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    }
                }, status=202
            )
            self.classless_base_zone = classless_base_zone_change["zone"]

            classless_zone_delegation_change = self.ok_vinyldns_client.create_zone(
                {
                    "name": f"192/30.{partition_id}.0.192.in-addr.arpa.",
                    "email": "test@test.com",
                    "shared": False,
                    "adminGroupId": self.ok_group["id"],
                    "isTest": True,
                    "connection": {
                        "name": "classless.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    },
                    "transferConnection": {
                        "name": "classless.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    }
                }, status=202
            )
            self.classless_zone_delegation_zone = classless_zone_delegation_change["zone"]

            system_test_zone_change = self.ok_vinyldns_client.create_zone(
                {
                    "name": f"system-test{partition_id}.",
                    "email": "test@test.com",
                    "shared": False,
                    "adminGroupId": self.ok_group["id"],
                    "isTest": True,
                    "connection": {
                        "name": "system-test.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    },
                    "transferConnection": {
                        "name": "system-test.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    }
                }, status=202
            )
            self.system_test_zone = system_test_zone_change["zone"]

            # parent zone gives access to the dummy user, dummy user cannot manage ns records
            parent_zone_change = self.ok_vinyldns_client.create_zone(
                {
                    "name": f"parent.com{partition_id}.",
                    "email": "test@test.com",
                    "shared": False,
                    "adminGroupId": self.ok_group["id"],
                    "isTest": True,
                    "acl": {
                        "rules": [
                            {
                                "accessLevel": "Delete",
                                "description": "some_test_rule",
                                "userId": "dummy"
                            }
                        ]
                    },
                    "connection": {
                        "name": "parent.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    },
                    "transferConnection": {
                        "name": "parent.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    }
                }, status=202)
            self.parent_zone = parent_zone_change["zone"]

            # mimicking the spec example
            ds_zone_change = self.ok_vinyldns_client.create_zone(
                {
                    "name": f"example.com{partition_id}.",
                    "email": "test@test.com",
                    "shared": False,
                    "adminGroupId": self.ok_group["id"],
                    "isTest": True,
                    "connection": {
                        "name": "example.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    },
                    "transferConnection": {
                        "name": "example.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    }
                }, status=202)
            self.ds_zone = ds_zone_change["zone"]

            # zone with name configured for manual review
            requires_review_zone_change = self.ok_vinyldns_client.create_zone(
                {
                    "name": f"zone.requires.review{partition_id}.",
                    "email": "test@test.com",
                    "shared": False,
                    "adminGroupId": self.ok_group["id"],
                    "isTest": True,
                    "backendId": "func-test-backend"
                }, status=202)
            self.requires_review_zone = requires_review_zone_change["zone"]

            # Shared zone
            shared_zone_change = self.support_user_client.create_zone(
                {
                    "name": f"shared{partition_id}.",
                    "email": "test@test.com",
                    "shared": True,
                    "adminGroupId": self.shared_record_group["id"],
                    "isTest": True,
                    "connection": {
                        "name": "shared.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    },
                    "transferConnection": {
                        "name": "shared.",
                        "keyName": VinylDNSTestContext.dns_key_name,
                        "key": VinylDNSTestContext.dns_key,
                        "algorithm": VinylDNSTestContext.dns_key_algo,
                        "primaryServer": VinylDNSTestContext.name_server_ip
                    }
                }, status=202)
            self.shared_zone = shared_zone_change["zone"]

            # wait until our zones are created
            self.ok_vinyldns_client.wait_until_zone_active(system_test_zone_change["zone"]["id"])
            self.ok_vinyldns_client.wait_until_zone_active(ok_zone_change["zone"]["id"])
            self.dummy_vinyldns_client.wait_until_zone_active(dummy_zone_change["zone"]["id"])
            self.ok_vinyldns_client.wait_until_zone_active(ip6_reverse_zone_change["zone"]["id"])
            self.ok_vinyldns_client.wait_until_zone_active(ip6_16_nibble_zone_change["zone"]["id"])
            self.ok_vinyldns_client.wait_until_zone_active(ip4_reverse_zone_change["zone"]["id"])
            self.ok_vinyldns_client.wait_until_zone_active(classless_base_zone_change["zone"]["id"])
            self.ok_vinyldns_client.wait_until_zone_active(classless_zone_delegation_change["zone"]["id"])
            self.ok_vinyldns_client.wait_until_zone_active(system_test_zone_change["zone"]["id"])
            self.ok_vinyldns_client.wait_until_zone_active(parent_zone_change["zone"]["id"])
            self.ok_vinyldns_client.wait_until_zone_active(ds_zone_change["zone"]["id"])
            self.ok_vinyldns_client.wait_until_zone_active(requires_review_zone_change["zone"]["id"])
            self.shared_zone_vinyldns_client.wait_until_zone_active(shared_zone_change["zone"]["id"])

            # initialize group activity
            self.init_group_activity()

            # initialize list zones, only do this when constructing the whole!
            self.list_zones.setup()

            # note: there are no state to load, the tests only need the client
            self.list_zones_client = self.list_zones.client

            # build the list of records; note: we do need to save the test records
            self.list_records_context.setup()

            # build the list of groups
            self.list_groups_context.setup()
        except Exception:
            # Cleanup if setup fails
            self.tear_down()
            traceback.print_exc()
            raise

    def init_history(self):
        # Initialize the zone history
        # change the zone nine times to we have update events in zone change history,
        # ten total changes including creation
        for i in range(2, 11):
            zone_update = copy.deepcopy(self.history_zone)
            zone_update["connection"]["key"] = VinylDNSTestContext.dns_key
            zone_update["transferConnection"]["key"] = VinylDNSTestContext.dns_key
            zone_update["email"] = "i.changed.this.{0}.times@history-test.com".format(i)
            self.history_client.update_zone(zone_update, status=202)

        # create some record sets
        test_a = TestData.A.copy()
        test_a["zoneId"] = self.history_zone["id"]
        test_aaaa = TestData.AAAA.copy()
        test_aaaa["zoneId"] = self.history_zone["id"]
        test_cname = TestData.CNAME.copy()
        test_cname["zoneId"] = self.history_zone["id"]

        a_record = self.history_client.create_recordset(test_a, status=202)["recordSet"]
        aaaa_record = self.history_client.create_recordset(test_aaaa, status=202)["recordSet"]
        cname_record = self.history_client.create_recordset(test_cname, status=202)["recordSet"]

        # wait here for all the record sets to be created
        self.history_client.wait_until_recordset_exists(a_record["zoneId"], a_record["id"])
        self.history_client.wait_until_recordset_exists(aaaa_record["zoneId"], aaaa_record["id"])
        self.history_client.wait_until_recordset_exists(cname_record["zoneId"], cname_record["id"])

        # update the record sets
        a_record_update = copy.deepcopy(a_record)
        a_record_update["ttl"] += 100
        a_record_update["records"][0]["address"] = "9.9.9.9"
        a_change = self.history_client.update_recordset(a_record_update, status=202)

        aaaa_record_update = copy.deepcopy(aaaa_record)
        aaaa_record_update["ttl"] += 100
        aaaa_record_update["records"][0]["address"] = "2003:db8:0:0:0:0:0:4"
        aaaa_change = self.history_client.update_recordset(aaaa_record_update, status=202)

        cname_record_update = copy.deepcopy(cname_record)
        cname_record_update["ttl"] += 100
        cname_record_update["records"][0]["cname"] = "changed-cname."
        cname_change = self.history_client.update_recordset(cname_record_update, status=202)

        self.history_client.wait_until_recordset_change_status(a_change, "Complete")
        self.history_client.wait_until_recordset_change_status(aaaa_change, "Complete")
        self.history_client.wait_until_recordset_change_status(cname_change, "Complete")

        # delete the recordsets
        self.history_client.delete_recordset(a_record["zoneId"], a_record["id"])
        self.history_client.delete_recordset(aaaa_record["zoneId"], aaaa_record["id"])
        self.history_client.delete_recordset(cname_record["zoneId"], cname_record["id"])

        self.history_client.wait_until_recordset_deleted(a_record["zoneId"], a_record["id"])
        self.history_client.wait_until_recordset_deleted(aaaa_record["zoneId"], aaaa_record["id"])
        self.history_client.wait_until_recordset_deleted(cname_record["zoneId"], cname_record["id"])

    def init_group_activity(self):
        client = self.ok_vinyldns_client

        group_name = f"test-list-group-activity-max-item-success{self.partition_id}"

        members = [{"id": "ok"}]
        new_group = {
            "name": group_name,
            "email": "test@test.com",
            "members": members,
            "admins": [{"id": "ok"}]
        }
        created_group = client.create_group(new_group, status=200)

        update_groups = []
        updated_groups = []
        # each update changes the member
        for runner in range(0, 10):
            members = [{"id": "dummy{0:0>3}".format(runner)}]
            update_groups.append({
                "id": created_group["id"],
                "name": group_name,
                "email": "test@test.com",
                "members": members,
                "admins": [{"id": "ok"}]
            })
            updated_groups.append(client.update_group(update_groups[runner]["id"], update_groups[runner], status=200))

        self.group_activity_created = created_group
        self.group_activity_updated = updated_groups

    def tear_down(self):
        """
        The ok_vinyldns_client is a zone admin on _all_ the zones.

        We shouldn't have to do any checks now, as zone admins have full rights to all zones, including
        deleting all records (even in the old shared model)
        """
        try:
            self.list_zones.tear_down()
            self.list_records_context.tear_down()

            if self.list_batch_summaries_context:
                self.list_batch_summaries_context.tear_down(self)

            if self.list_groups_context:
                self.list_groups_context.tear_down()

            for client in self.clients:
                client.clear_zones()

            for client in self.clients:
                client.clear_groups()

            # Close all clients
            for client in self.clients:
                client.tear_down()

        except Exception:
            traceback.print_exc()
            raise

    @staticmethod
    def confirm_member_in_group(client, group):
        retries = 2
        success = group in client.list_all_my_groups(status=200)
        while retries >= 0 and not success:
            success = group in client.list_all_my_groups(status=200)
            time.sleep(.05)
            retries -= 1
        assert_that(success, is_(True))
