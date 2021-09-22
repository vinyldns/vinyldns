from urllib.parse import urljoin

from utils import *
from hamcrest import *
from dns.resolver import *

from vinyldns_context import VinylDNSTestContext
from fvinyldns_python import VinylDNSClient


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


def test_request_fails_when_accessing_non_existent_route():
    """
    Test request fails with NotFound (404) when route cannot be resolved, regardless of authentication
    """
    client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'unknownAccessKey', 'anyAccessSecretKey')
    url = urljoin(VinylDNSTestContext.vinyldns_url, u'/no-existo')
    _, data = client.make_request(url, u'GET', client.headers, status=404)

    assert_that(data, is_("The requested path [/no-existo] does not exist."))

def test_request_fails_with_unsupported_http_method_for_route():
    """
    Test request fails with MethodNotAllowed (405) when HTTP Method is not supported for specified route
    """
    client = VinylDNSClient(VinylDNSTestContext.vinyldns_url, 'unknownAccessKey', 'anyAccessSecretKey')
    url = urljoin(VinylDNSTestContext.vinyldns_url, u'/zones')
    _, data = client.make_request(url, u'PUT', client.headers, status=405)

    assert_that(data, is_("HTTP method not allowed, supported methods: GET, POST"))
