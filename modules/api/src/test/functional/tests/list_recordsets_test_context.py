from utils import *
from vinyldns_python import VinylDNSClient


class ListRecordSetsTestContext(object):
    def __init__(self, partition_id: str):
        self.partition_id = partition_id
        self.setup_started = False
        self.client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "listRecordsAccessKey", "listRecordsSecretKey")
        self.zone = None
        self.all_records = []
        self.group = None

        get_zone = self.client.get_zone_by_name(f"list-records{partition_id}.", status=(200, 404))
        if get_zone and "zone" in get_zone:
            self.zone = get_zone["zone"]
            self.all_records = self.client.list_recordsets_by_zone(self.zone["id"])["recordSets"]
            my_groups = self.client.list_my_groups(group_name_filter="list-records-group")
            if my_groups and "groups" in my_groups and len(my_groups["groups"]) > 0:
                self.group = my_groups["groups"][0]

    def setup(self):
        if self.setup_started:
            # Safeguard against reentrance
            return
        self.setup_started = True

        partition_id = self.partition_id
        group = {
            "name": f"list-records-group{partition_id}",
            "email": "test@test.com",
            "description": "this is a description",
            "members": [{"id": "list-records-user"}],
            "admins": [{"id": "list-records-user"}],
            "membershipAccessStatus": {
                "pendingReviewMember": [],
                "rejectedMember": [],
                "approvedMember": []
            }
        }
        self.group = self.client.create_group(group, status=200)
        zone_change = self.client.create_zone(
            {
                "name": f"list-records{partition_id}.",
                "email": "test@test.com",
                "shared": False,
                "adminGroupId": self.group["id"],
                "isTest": True,
                "backendId": "func-test-backend"
            }, status=202)
        self.client.wait_until_zone_active(zone_change["zone"]["id"])
        self.zone = zone_change["zone"]
        self.all_records = self.client.list_recordsets_by_zone(self.zone["id"])["recordSets"]

    def tear_down(self):
        self.client.clear_zones()
        self.client.clear_groups()
        self.client.tear_down()

    def check_recordsets_page_accuracy(self, list_results_page, size, offset, next_id=False, start_from=False, max_items=100, record_type_filter=False, name_sort="ASC"):
        # validate fields
        if next_id:
            assert_that(list_results_page, has_key("nextId"))
        else:
            assert_that(list_results_page, is_not(has_key("nextId")))
        if start_from:
            assert_that(list_results_page["startFrom"], is_(start_from))
        else:
            assert_that(list_results_page, is_not(has_key("startFrom")))
        if record_type_filter:
            assert_that(list_results_page, has_key("recordTypeFilter"))
        else:
            assert_that(list_results_page, is_not(has_key("recordTypeFilter")))
        assert_that(list_results_page["maxItems"], is_(max_items))
        assert_that(list_results_page["nameSort"], is_(name_sort))

        # validate actual page
        list_results_recordsets_page = list_results_page["recordSets"]
        assert_that(list_results_recordsets_page, has_length(size))
        for i in range(len(list_results_recordsets_page)):
            assert_that(list_results_recordsets_page[i]["name"], is_(self.all_records[i + offset]["name"]))
            verify_recordset(list_results_recordsets_page[i], self.all_records[i + offset])
            assert_that(list_results_recordsets_page[i]["accessLevel"], is_("Delete"))

    def check_recordsets_parameters(self, list_results_page, next_id=False, start_from=False, max_items=100, record_type_filter=False, name_sort="ASC"):
        # validate fields
        if next_id:
            assert_that(list_results_page, has_key("nextId"))
        else:
            assert_that(list_results_page, is_not(has_key("nextId")))
        if start_from:
            assert_that(list_results_page["startFrom"], is_(start_from))
        else:
            assert_that(list_results_page, is_not(has_key("startFrom")))
        if record_type_filter:
            assert_that(list_results_page, has_key("recordTypeFilter"))
        else:
            assert_that(list_results_page, is_not(has_key("recordTypeFilter")))
        assert_that(list_results_page["maxItems"], is_(max_items))
        assert_that(list_results_page["nameSort"], is_(name_sort))
