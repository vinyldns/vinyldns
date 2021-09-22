from hamcrest import *
from utils import *
from vinyldns_context import VinylDNSTestContext
from vinyldns_python import VinylDNSClient

from utils import clear_zones, clear_groups


class ListGroupsTestContext(object):
    def __init__(self):
        self.client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, access_key='listGroupAccessKey',
                                     secret_key='listGroupSecretKey')
        self.support_user_client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'supportUserAccessKey',
                                                  'supportUserSecretKey')

    def build(self):
        try:
            for runner in range(0, 50):
                new_group = {
                    'name': "test-list-my-groups-{0:0>3}".format(runner),
                    'email': 'test@test.com',
                    'members': [{'id': 'list-group-user'}],
                    'admins': [{'id': 'list-group-user'}]
                }
                self.client.create_group(new_group, status=200)

        except:
            # teardown if there was any issue in setup
            try:
                self.tear_down()
            except:
                pass
            raise

    def tear_down(self):
        clear_zones(self.client)
        clear_groups(self.client)
