from utils import *
from vinyldns_python import VinylDNSClient


class ListGeneratedZonesTestContext(object):
    def __init__(self, partition_id):
        self.partition_id = partition_id
        self.setup_started = False
        self.client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "listZonesAccessKey", "listZonesSecretKey")
        self.search_generate_zone1 = None
        self.search_generate_zone2 = None
        self.search_generate_zone3 = None
        self.non_search_generate_zone1 = None
        self.non_search_generate_zone2 = None
        self.list_generate_zones_group = None

    def setup(self):
        if self.setup_started:
            # Safeguard against reentrance
            return
        self.setup_started = True

        partition_id = self.partition_id
        group = {
            "name": f"list-generated-zones-group{partition_id}",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "list-zones-user"}],
            "admins": [{"id": "list-zones-user"}]
        }
        self.list_generate_zones_group = self.client.create_group(group, status=200)

        self.search_generate_zone1 = self.client.generate_zone(
            {
                "groupId": self.list_generate_zones_group["id"],
                "email": "test@test.com",
                "provider": "powerdns",
                "zoneName": f"list-zones-test-searched-1{partition_id}.",
                "providerParams": {
                    "kind": "Native",
                    "nameservers": [
                        "172.17.42.1.",
                        "ns1.parent.com."
                    ]
                }
            }, status=202)

        self.search_generate_zone2 = self.client.generate_zone(
            {
                "groupId": self.list_generate_zones_group["id"],
                "email": "test@test.com",
                "provider": "powerdns",
                "zoneName": f"list-zones-test-searched-2{partition_id}.",
                "providerParams": {
                    "kind": "Native",
                    "nameservers": [
                        "172.17.42.1.",
                        "ns1.parent.com."
                    ]
                }
            }, status=202)

        self.search_generate_zone3 = self.client.generate_zone(
            {
                "groupId": self.list_generate_zones_group["id"],
                "email": "test@test.com",
                "provider": "powerdns",
                "zoneName": f"list-zones-test-searched-3{partition_id}.",
                "providerParams": {
                    "kind": "Native",
                    "nameservers": [
                        "172.17.42.1.",
                        "ns1.parent.com."
                    ]
                }
            }, status=202)

        self.non_search_generate_zone1 = self.client.generate_zone(
            {
                "groupId": self.list_generate_zones_group["id"],
                "email": "test@test.com",
                "provider": "powerdns",
                "zoneName": f"list-zones-test-unfiltered-1{partition_id}.",
                "providerParams": {
                    "kind": "Native",
                    "nameservers": [
                        "172.17.42.1.",
                        "ns1.parent.com."
                    ]
                }
            }, status=202)

        self.non_search_generate_zone2 = self.client.generate_zone(
            {
                "groupId": self.list_generate_zones_group["id"],
                "email": "test@test.com",
                "provider": "powerdns",
                "zoneName": f"list-zones-test-unfiltered-2{partition_id}.",
                "providerParams": {
                    "kind": "Native",
                    "nameservers": [
                        "172.17.42.1.",
                        "ns1.parent.com."
                    ]
                }
            }, status=202)

        generate_zones = [self.search_generate_zone1, self.search_generate_zone2, self.search_generate_zone3, self.non_search_generate_zone1, self.non_search_generate_zone2]
        for zone in generate_zones:
            self.client.wait_until_generate_zone_active(zone["id"])

    def tear_down(self):
        self.client.clear_generate_zones()
        self.client.clear_groups()
        self.client.tear_down()
