from utils import *
from vinyldns_python import VinylDNSClient


class ListGroupsTestContext(object):
    def __init__(self, partition_id: str):
        self.partition_id = partition_id
        self.setup_started = False
        self.client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "listGroupAccessKey", "listGroupSecretKey")
        self.support_user_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "supportUserAccessKey", "supportUserSecretKey")
        self.group_prefix = f"test-list-my-groups{partition_id}"

    def setup(self):
        if self.setup_started:
            # Safeguard against reentrance
            return
        self.setup_started = True
        try:
            for index in range(0, 50):
                new_group = {
                    "name": "{0}-{1:0>3}".format(self.group_prefix, index),
                    "email": "test@test.com",
                    "members": [{"id": "list-group-user"}],
                    "admins": [{"id": "list-group-user"}],
                    "membershipAccessStatus": {
                        "pendingReviewMember": [],
                        "rejectedMember": [],
                        "approvedMember": []
                    }
                }
                self.client.create_group(new_group, status=200)
        except Exception:
            self.tear_down()
            traceback.print_exc()
            raise

    def tear_down(self):
        self.client.clear_zones()
        self.client.clear_groups()
        self.client.tear_down()
        self.support_user_client.clear_zones()
        self.support_user_client.clear_groups()
        self.support_user_client.tear_down()
