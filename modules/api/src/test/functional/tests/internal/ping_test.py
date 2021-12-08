from hamcrest import *


def test_ping(shared_zone_test_context):
    """
    Tests that the ping endpoint works appropriately
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = client.ping()

    assert_that(result, is_("PONG"))
