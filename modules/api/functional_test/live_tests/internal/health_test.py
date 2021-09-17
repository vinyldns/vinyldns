import pytest

from hamcrest import *


def test_health(shared_zone_test_context):
    """
    Tests that the health check endpoint works
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.health()

