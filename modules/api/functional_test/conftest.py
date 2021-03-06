import os

from vinyldns_context import VinylDNSTestContext


def pytest_addoption(parser):
    """
    Adds additional options that we can parse when we run the tests, stores them in the parser / py.test context
    """
    parser.addoption("--url", dest="url", action="store", default="http://localhost:9000",
                     help="URL for application to root")
    parser.addoption("--dns-ip", dest="dns_ip", action="store", default="127.0.0.1:19001",
                     help="The ip address for the dns server to use for the tests")
    parser.addoption("--dns-zone", dest="dns_zone", action="store", default="vinyldns.",
                     help="The zone name that will be used for testing")
    parser.addoption("--dns-key-name", dest="dns_key_name", action="store", default="vinyldns.",
                     help="The name of the key used to sign updates for the zone")
    parser.addoption("--dns-key", dest="dns_key", action="store", default="nzisn+4G2ldMn0q1CV3vsg==",
                     help="The tsig key")

    # optional
    parser.addoption("--basic-auth", dest="basic_auth_creds",
                     help="Basic auth credentials in 'user:pass' format")
    parser.addoption("--basic-auth-realm", dest="basic_auth_realm",
                     help="Basic auth realm to use with credentials supplied by \"-b\"")
    parser.addoption("--iauth-creds", dest="iauth_creds",
                     help="Intermediary auth (codebig style) in 'key:secret' format")
    parser.addoption("--oauth-creds", dest="oauth_creds",
                     help="OAuth credentials in consumer:secret format")
    parser.addoption("--environment", dest="cim_env", action="store", default="test",
                     help="CIM_ENV that we are testing against.")
    parser.addoption("--teardown", dest="teardown", action="store", default="True",
                     help="True | False - Whether to teardown the test fixture, or leave it for another run")


def pytest_configure(config):
    """
    Loads the test context since we are no longer using run.py
    """

    # Monkey patch ssl so we do not verify ssl certs
    import ssl
    try:
        _create_unverified_https_context = ssl._create_unverified_context
    except AttributeError:
        # Legacy Python that doesn't verify HTTPS certificates by default
        pass
    else:
        # Handle target environment that doesn't support HTTPS verification
        ssl._create_default_https_context = _create_unverified_https_context

    url = config.getoption("url", default="http://localhost:9000/")
    if not url.endswith('/'):
        url += '/'

    import sys
    sys.dont_write_bytecode = True

    VinylDNSTestContext.configure(config.getoption("dns_ip"),
                                  config.getoption("dns_zone"),
                                  config.getoption("dns_key_name"),
                                  config.getoption("dns_key"),
                                  config.getoption("url"),
                                  config.getoption("teardown"))

    from shared_zone_test_context import SharedZoneTestContext
    if not hasattr(config, 'workerinput'):
        print 'Master, standing up the test fixture...'
        # use the fixture file if it exists
        if os.path.isfile('tmp.out'):
            print 'Fixture file found, assuming the fixture file'
            SharedZoneTestContext('tmp.out')
        else:
            print 'No fixture file found, loading a new test fixture'
            ctx = SharedZoneTestContext()
            ctx.out_fixture_file("tmp.out")
    else:
        print 'This is a worker'


def pytest_unconfigure(config):
    # this attribute is only set on workers
    print 'Master exiting...'
    if not hasattr(config, 'workerinput') and VinylDNSTestContext.teardown:
        print 'Master cleaning up...'
        from shared_zone_test_context import SharedZoneTestContext
        ctx = SharedZoneTestContext('tmp.out')
        ctx.tear_down()
        os.remove('tmp.out')
    else:
        print 'Worker exiting...'


def pytest_report_header(config):
    """
    Overrides the test result header like we do in pyfunc test
    """
    header = "Testing against CIM_ENV " + config.getoption("cim_env")
    header += "\r\nURL: " + config.getoption("url")
    header += "\r\nRunning from directory " + os.getcwd()
    header += '\r\nTest shim directory ' + os.path.dirname(__file__)
    header += "\r\nDNS IP: " + config.getoption("dns_ip")
    return header
