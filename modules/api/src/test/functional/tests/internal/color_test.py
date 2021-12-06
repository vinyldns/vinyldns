from hamcrest import *


def test_color(shared_zone_test_context):
    """
    Tests that the color endpoint works appropriately
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result = client.color()

    assert_that(["green", "blue"], has_item(result))
