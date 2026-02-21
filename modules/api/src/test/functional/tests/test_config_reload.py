"""
Functional tests for the /config/reload endpoint.

Access control:
  - Authenticated non-super users    → 200 OK
  - Authenticated super users        → 403 Not Authorized
  - Unauthenticated / bad credentials → 401 Unauthorized
  - GET /config/reload               → 405 Method Not Allowed

Run against a live VinylDNS instance (e.g. via the quickstart Docker stack).
"""
import pytest
from hamcrest import *

from vinyldns_context import VinylDNSTestContext
from vinyldns_python import VinylDNSClient


# ---------------------------------------------------------------------------
# Shared client fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def ok_client():
    """Regular (non-super) authenticated user."""
    client = VinylDNSClient(
        VinylDNSTestContext.vinyldns_url,
        "okAccessKey",
        "okSecretKey",
    )
    yield client
    client.tear_down()


@pytest.fixture(scope="module")
def super_client():
    """Super-user client (should be forbidden from reloading config)."""
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


# ---------------------------------------------------------------------------
# Helper: raw GET against /config/reload (method-not-allowed check)
# ---------------------------------------------------------------------------

def _get_config_reload(client: VinylDNSClient):
    """Issue a raw GET to /config/reload and return (status_code, body)."""
    from urllib.parse import urljoin
    url = urljoin(client.index_url, "/config/reload")
    return client.make_request(url, method="GET", headers=client.headers)


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestConfigReloadEndpoint:

    # --- Happy-path ---

    def test_reload_config_succeeds_for_regular_user(self, ok_client):
        """A regular authenticated user can trigger a config reload (200 OK)."""
        status_code, data = ok_client.reload_config(status=200)
        assert_that(status_code, is_(200))
        assert_that(data, contains_string("reloaded successfully"))

    # --- Authorization ---

    def test_reload_config_forbidden_for_super_user(self, super_client):
        """Super users are explicitly denied the reload action (403 Forbidden)."""
        status_code, data = super_client.reload_config(status=403)
        assert_that(status_code, is_(403))

    def test_reload_config_unauthorized_without_valid_credentials(self, bad_client):
        """Requests signed with unknown credentials are rejected (401 Unauthorized)."""
        status_code, _ = bad_client.reload_config(status=401)
        assert_that(status_code, is_(401))

    # --- HTTP method ---

    def test_reload_config_method_not_allowed_for_get(self, ok_client):
        """GET /config/reload must be rejected (405 Method Not Allowed)."""
        status_code, _ = _get_config_reload(ok_client)
        assert_that(status_code, is_(405))

    # --- Idempotency ---

    def test_reload_config_idempotent_on_repeated_calls(self, ok_client):
        """Calling reload multiple times in a row must all succeed (200 OK)."""
        for _ in range(3):
            status_code, data = ok_client.reload_config(status=200)
            assert_that(status_code, is_(200))
            assert_that(data, contains_string("reloaded successfully"))

    # --- Side-effect: other endpoints still respond after reload ---

    def test_color_endpoint_still_responds_after_reload(self, ok_client):
        """GET /color must continue to return a valid color string after reload."""
        ok_client.reload_config(status=200)
        color = ok_client.color()
        assert_that(color, any_of(contains_string("blue"), contains_string("green")))

    def test_ping_endpoint_still_responds_after_reload(self, ok_client):
        """GET /ping must still return 200 after a config reload."""
        ok_client.reload_config(status=200)
        from urllib.parse import urljoin
        url = urljoin(ok_client.index_url, "/ping")
        status_code, _ = ok_client.make_request(url, method="GET", headers=ok_client.headers)
        assert_that(status_code, is_(200))

    def test_status_endpoint_still_responds_after_reload(self, ok_client):
        """GET /status must still return a response after a config reload."""
        ok_client.reload_config(status=200)
        from urllib.parse import urljoin
        url = urljoin(ok_client.index_url, "/status")
        status_code, _ = ok_client.make_request(url, method="GET", headers=ok_client.headers)
        assert_that(status_code, is_(200))
