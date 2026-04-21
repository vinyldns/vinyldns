from utils import *
from vinyldns_python import VinylDNSClient


class ListZonesTestContext(object):
    def __init__(self, partition_id):
        self.partition_id = partition_id
        self.setup_started = False
        self.client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "listZonesAccessKey", "listZonesSecretKey")
        self.search_zone1 = None
        self.search_zone2 = None
        self.search_zone3 = None
        self.non_search_zone1 = None
        self.non_search_zone2 = None
        self.list_zones_group = None

    def setup(self):
        if self.setup_started:
            # Safeguard against reentrance
            return
        self.setup_started = True

        partition_id = self.partition_id
        group = {
            "name": f"list-zones-group{partition_id}",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "list-zones-user"}],
            "admins": [{"id": "list-zones-user"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        self.list_zones_group = self.client.create_group(group, status=200)

        search_zone_1_change = self.client.create_zone(
            {
                "name": f"list-zones-test-searched-1{partition_id}.",
                "email": "test@test.com",
                "shared": False,
                "adminGroupId": self.list_zones_group["id"],
                "isTest": True,
                "backendId": "func-test-backend"
            }, status=202)
        self.search_zone1 = search_zone_1_change["zone"]

        search_zone_2_change = self.client.create_zone(
            {
                "name": f"list-zones-test-searched-2{partition_id}.",
                "email": "test@test.com",
                "shared": False,
                "adminGroupId": self.list_zones_group["id"],
                "isTest": True,
                "backendId": "func-test-backend"
            }, status=202)
        self.search_zone2 = search_zone_2_change["zone"]

        search_zone_3_change = self.client.create_zone(
            {
                "name": f"list-zones-test-searched-3{partition_id}.",
                "email": "test@test.com",
                "shared": False,
                "adminGroupId": self.list_zones_group["id"],
                "isTest": True,
                "backendId": "func-test-backend"
            }, status=202)
        self.search_zone3 = search_zone_3_change["zone"]

        non_search_zone_1_change = self.client.create_zone(
            {
                "name": f"list-zones-test-unfiltered-1{partition_id}.",
                "email": "test@test.com",
                "shared": False,
                "adminGroupId": self.list_zones_group["id"],
                "isTest": True,
                "backendId": "func-test-backend"
            }, status=202)
        self.non_search_zone1 = non_search_zone_1_change["zone"]

        non_search_zone_2_change = self.client.create_zone(
            {
                "name": f"list-zones-test-unfiltered-2{partition_id}.",
                "email": "test@test.com",
                "shared": False,
                "adminGroupId": self.list_zones_group["id"],
                "isTest": True,
                "backendId": "func-test-backend"
            }, status=202)
        self.non_search_zone2 = non_search_zone_2_change["zone"]

        zone_changes = [search_zone_1_change, search_zone_2_change, search_zone_3_change, non_search_zone_1_change, non_search_zone_2_change]
        for change in zone_changes:
            self.client.wait_until_zone_active(change["zone"]["id"])

    def tear_down(self):
        self.client.clear_zones()
        self.client.clear_groups()
        self.client.tear_down()
