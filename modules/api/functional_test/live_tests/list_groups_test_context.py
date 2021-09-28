from utils import *
from vinyldns_python import VinylDNSClient


class ListGroupsTestContext(object):
    def __init__(self, partition_id: str):
        self.partition_id = partition_id
        self.client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, access_key="listGroupAccessKey", secret_key="listGroupSecretKey")
        self.support_user_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, "supportUserAccessKey", "supportUserSecretKey")

    def build(self):
        try:
            for runner in range(0, 50):
                new_group = {
                    "name": "test-list-my-groups-{0:0>3}{0}".format(runner, self.partition_id),
                    "email": "test@test.com",
                    "members": [{"id": "list-group-user"}],
                    "admins": [{"id": "list-group-user"}]
                }
                self.client.create_group(new_group, status=200)
        except:
            self.tear_down()
            raise

    def tear_down(self):
        clear_zones(self.client)
        clear_groups(self.client)
        self.client.tear_down()
        self.support_user_client.tear_down()
