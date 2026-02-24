"""
tests for the /config/reload endpoint.

Access control:
  - Authenticated super users        → 200 OK
  - Authenticated non-super users    → 403 Not Authorized
  - Unauthenticated / bad credentials → 401 Unauthorized
  - GET /config/reload               → 405 Method Not Allowed
"""
import pytest
from hamcrest import *

from vinyldns_context import VinylDNSTestContext
from vinyldns_python import VinylDNSClient


# Shared client fixtures
@pytest.fixture(scope="module")
def ok_client():
    """Regular (non-super) authenticated user — forbidden from reloading config."""
    client = VinylDNSClient(
        VinylDNSTestContext.vinyldns_url,
        "okAccessKey",
        "okSecretKey",
    )
    yield client
    client.tear_down()


@pytest.fixture(scope="module")
def super_client():
    """Super-user client — authorised to reload config."""
    client = VinylDNSClient(
        VinylDNSTestContext.vinyldns_url,
        "superUserAccessKey",
        "superUserSecretKey",
    )
    yield client
    client.tear_down()


@pytest.fixture(scope="module")
def bad_client():
    """Client with invalid credentials (should be rejected as unauthorized)."""
    client = VinylDNSClient(
        VinylDNSTestContext.vinyldns_url,
        "invalidAccessKey",
        "invalidSecretKey",
    )
    yield client
    client.tear_down()

def _get_config_reload(client: VinylDNSClient):
    """Issue a raw GET to /config/reload and return (status_code, body)."""
    from urllib.parse import urljoin
    url = urljoin(client.index_url, "/config/reload")
    return client.make_request(url, method="GET", headers=client.headers)

class TestConfigReloadEndpoint:

    def test_reload_config_succeeds_for_super_user(self, super_client):
        """A super user can trigger a config reload (200 OK)."""
        status_code, data = super_client.reload_config(status=200)
        assert_that(status_code, is_(200))
        assert_that(data, contains_string("reloaded successfully"))

    def test_reload_config_forbidden_for_regular_user(self, ok_client):
        """Regular (non-super) users are not authorized to reload config (403 Forbidden)."""
        status_code, data = ok_client.reload_config(status=403)
        assert_that(status_code, is_(403))

    def test_reload_config_unauthorized_without_valid_credentials(self, bad_client):
        """Requests signed with unknown credentials are rejected (401 Unauthorized)."""
        status_code, _ = bad_client.reload_config(status=401)
        assert_that(status_code, is_(401))

    def test_reload_config_idempotent_on_repeated_calls(self, super_client):
        """Calling reload multiple times in a row must all succeed (200 OK)."""
        for _ in range(3):
            status_code, data = super_client.reload_config(status=200)
            assert_that(status_code, is_(200))
            assert_that(data, contains_string("reloaded successfully"))

    def test_color_endpoint_still_responds_after_reload(self, super_client):
        """GET /color must continue to return a valid color string after reload."""
        super_client.reload_config(status=200)
        color = super_client.color()
        assert_that(color, any_of(contains_string("blue"), contains_string("green")))

    def test_ping_endpoint_still_responds_after_reload(self, super_client):
        """GET /ping must still return 200 after a config reload."""
        super_client.reload_config(status=200)
        from urllib.parse import urljoin
        url = urljoin(super_client.index_url, "/ping")
        status_code, _ = super_client.make_request(url, method="GET", headers=super_client.headers)
        assert_that(status_code, is_(200))

    def test_status_endpoint_still_responds_after_reload(self, super_client):
        """GET /status must still return a response after a config reload."""
        super_client.reload_config(status=200)
        from urllib.parse import urljoin
        url = urljoin(super_client.index_url, "/status")
        status_code, _ = super_client.make_request(url, method="GET", headers=super_client.headers)
        assert_that(status_code, is_(200))

    # --- HTTP method ---
    def test_reload_config_method_not_allowed_for_get(self, super_client):
        """GET /config/reload must be rejected (405 Method Not Allowed)."""
        status_code, _ = _get_config_reload(super_client)
        assert_that(status_code, is_(405))
