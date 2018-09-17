from utils import *
from hamcrest import *
from vinyldns_python import VinylDNSClient
from dns.resolver import *
from vinyldns_context import VinylDNSTestContext


def test_request_fails_when_user_account_is_locked():
    """
    Test request fails with Forbidden (403) when user account is locked
    """
    client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'lockedAccessKey', 'lockedSecretKey')
    client.list_batch_change_summaries(status=403)

def test_request_fails_when_user_is_not_found():
    """
    Test request fails with Unauthorized (401) when user account is not found
    """
    client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'unknownAccessKey', 'anyAccessSecretKey')

    client.list_batch_change_summaries(status=401)

def test_request_succeeds_when_user_is_found_and_not_locked():
    """
    Test request success with Success (200) when user account is found and not locked
    """
    client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'okAccessKey', 'okSecretKey')

    client.list_batch_change_summaries(status=200)
