import pytest


@pytest.fixture(scope="session")
def shared_zone_test_context(request):
    print "\r\n\r\n!!! SETTING UP TEST FIXTURE FOR TMP OUT !!!"
    from shared_zone_test_context import SharedZoneTestContext
    ctx = SharedZoneTestContext("tmp.out")

    # def fin():
    #     # ctx.tear_down()
    #
    # # request.addfinalizer(fin)

    return ctx
