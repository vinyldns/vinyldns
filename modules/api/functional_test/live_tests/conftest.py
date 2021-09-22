import pytest


@pytest.fixture(scope="session")
def shared_zone_test_context(request):
    from shared_zone_test_context import SharedZoneTestContext

    return SharedZoneTestContext("tmp.out")
