from utils import *
from vinyldns_python import VinylDNSClient


class ListZonesTestContext(object):
    def __init__(self, partition_id):
        self.partition_id = partition_id
        self.client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "listZonesAccessKey", "listZonesSecretKey")

    def build(self):
        partition_id = self.partition_id
        group = {
            "name": f"list-zones-group{partition_id}",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "list-zones-user"}],
            "admins": [{"id": "list-zones-user"}]
        }
        list_zones_group = self.client.create_group(group, status=200)
        search_zone_1_change = self.client.create_zone(
            {
                "name": f"list-zones-test-searched-1{partition_id}.",
                "email": "test@test.com",
                "shared": False,
                "adminGroupId": list_zones_group["id"],
                "isTest": True,
                "backendId": "func-test-backend"
            }, status=202)

        search_zone_2_change = self.client.create_zone(
            {
                "name": f"list-zones-test-searched-2{partition_id}.",
                "email": "test@test.com",
                "shared": False,
                "adminGroupId": list_zones_group["id"],
                "isTest": True,
                "backendId": "func-test-backend"
            }, status=202)

        search_zone_3_change = self.client.create_zone(
            {
                "name": f"list-zones-test-searched-3{partition_id}.",
                "email": "test@test.com",
                "shared": False,
                "adminGroupId": list_zones_group["id"],
                "isTest": True,
                "backendId": "func-test-backend"
            }, status=202)

        non_search_zone_1_change = self.client.create_zone(
            {
                "name": f"list-zones-test-unfiltered-1{partition_id}.",
                "email": "test@test.com",
                "shared": False,
                "adminGroupId": list_zones_group["id"],
                "isTest": True,
                "backendId": "func-test-backend"
            }, status=202)

        non_search_zone_2_change = self.client.create_zone(
            {
                "name": f"list-zones-test-unfiltered-2{partition_id}.",
                "email": "test@test.com",
                "shared": False,
                "adminGroupId": list_zones_group["id"],
                "isTest": True,
                "backendId": "func-test-backend"
            }, status=202)

        zone_changes = [search_zone_1_change, search_zone_2_change, search_zone_3_change, non_search_zone_1_change, non_search_zone_2_change]
        for change in zone_changes:
            self.client.wait_until_zone_active(change["zone"]["id"])

    def tear_down(self):
        clear_zones(self.client)
        clear_groups(self.client)
        self.client.tear_down()
