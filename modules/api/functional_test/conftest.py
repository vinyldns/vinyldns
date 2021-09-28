import ipaddress
import logging
import os
import ssl
import sys

import _pytest.config
import pytest

from vinyldns_context import VinylDNSTestContext

logger = logging.getLogger(__name__)
logging.basicConfig(
    level=os.environ.get("VINYL_LOG_LEVEL") or logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.StreamHandler(stream=sys.stderr)
    ]
)
config_context = {}


def pytest_addoption(parser: _pytest.config.argparsing.Parser) -> None:
    """
    Adds additional options that we can parse when we run the tests, stores them in the parser / py.test context
    """
    parser.addoption("--url", dest="url", action="store", default="http://localhost:9000", help="URL for application to root")
    parser.addoption("--dns-ip", dest="dns_ip", action="store", default="127.0.0.1:19001", help="The ip address for the dns name server to update")
    parser.addoption("--resolver-ip", dest="resolver_ip", action="store", help="The ip address for the dns server to use for the tests during resolution. This is usually the same as `--dns-ip`")
    parser.addoption("--dns-zone", dest="dns_zone", action="store", default="vinyldns.", help="The zone name that will be used for testing")
    parser.addoption("--dns-key-name", dest="dns_key_name", action="store", default="vinyldns.", help="The name of the key used to sign updates for the zone")
    parser.addoption("--dns-key", dest="dns_key", action="store", default="nzisn+4G2ldMn0q1CV3vsg==", help="The TSIG key")
    parser.addoption("--dns-key-algo", dest="dns_key_algo", action="store", default="HMAC-MD5", help="The TSIG key algorithm")

    # optional
    parser.addoption("--basic-auth", dest="basic_auth_creds", help="Basic auth credentials in `user:pass` format")
    parser.addoption("--basic-auth-realm", dest="basic_auth_realm", help="Basic auth realm to use with credentials supplied by `-b`")
    parser.addoption("--iauth-creds", dest="iauth_creds", help="Intermediary auth in `key:secret` format")
    parser.addoption("--oauth-creds", dest="oauth_creds", help="OAuth credentials in `consumer:secret` format")
    parser.addoption("--environment", dest="environment", action="store", default="test", help="Environment that we are testing against")
    parser.addoption("--teardown", dest="teardown", action="store", default="True", help="True to teardown the test fixture; false to leave it for another run")
    parser.addoption("--enable-safety_check", dest="enable_safety_check", action="store_true",
                     help="If provided, enable object mutation safety checks; otherwise safety checks are disable. "
                          "This is a handy development tool to catch rogue tests mutating data which can affect other tests.")


def pytest_configure(config: _pytest.config.Config) -> None:
    """
    Loads the test context since we are no longer using run.py
    """
    logger.info("Starting configuration")

    # Monkey patch ssl so we do not verify ssl certs
    _create_unverified_https_context = ssl._create_unverified_context

    # Handle target environment that doesn't support HTTPS verification
    ssl._create_default_https_context = _create_unverified_https_context

    url = config.getoption("url")
    if not url.endswith("/"):
        url += "/"

    # Define markers
    config.addinivalue_line("markers", "serial")
    config.addinivalue_line("markers", "skip_production")
    config.addinivalue_line("markers", "manual_batch_review")

    name_server_ip = retrieve_resolver(config.getoption("dns_ip"))
    VinylDNSTestContext.configure(name_server_ip=name_server_ip,
                                  resolver_ip=retrieve_resolver(config.getoption("resolver_ip", name_server_ip) or name_server_ip),
                                  zone=config.getoption("dns_zone"),
                                  key_name=config.getoption("dns_key_name"),
                                  key=config.getoption("dns_key"),
                                  url=url,
                                  teardown=config.getoption("teardown").lower() == "true",
                                  key_algo=config.getoption("dns_key_algo"),
                                  enable_safety_check=config.getoption("enable_safety_check"))


def pytest_report_header(config: _pytest.config.Config) -> str:
    """
    Overrides the test result header like we do in pyfunc test
    """
    logger.debug("testing!")
    header = "Testing against environment " + config.getoption("environment")
    header += "\nURL: " + config.getoption("url")
    header += "\nRunning from directory " + os.getcwd()
    header += "\nTest shim directory " + os.path.dirname(__file__)
    header += "\nDNS IP: " + config.getoption("dns_ip")
    return header


def retrieve_resolver(resolver_name: str) -> str:
    """
    Retrieves the ip address of the DNS resolver when given a hostname
    :param resolver_name: The name/ip of the resolver
    :return: The IP address, and optionally port, of the resolver
    """
    parts = resolver_name.split(":")
    resolver_address = parts[0]
    try:
        ipaddress.ip_address(parts[0])
        return resolver_name
    except ValueError:
        logger.warning("`--dns_ip` is set to `%s`, which isn't a valid ip/port combination (hostname?)", resolver_name)
        try:
            import socket
            resolver_address = socket.gethostbyname(parts[0])
            resolver_address = [resolver_address] + parts[1:]
            resolver_address = ":".join(resolver_address)
            logger.warning("Translating `%s` resolver to `%s`", resolver_name, resolver_address)
        except:
            logger.error("Cannot translate `%s` into a usable resolver address", resolver_name)
            pytest.exit(1)

    return resolver_address
