import pytest

from hamcrest import *
from vinyldns_python import VinylDNSClient


def test_health(shared_zone_test_context):
    """
    Tests that the health check endpoint works
    """
    client = shared_zone_test_context.ok_vinyldns_client
    client.health()

