#!/usr/bin/env python3
"""
Verify the quickstart generate-zone DNS provider configuration.

Verifies that the "bind" and "powerdns" providers configured in
build/docker/api/application.conf actually loaded (i.e. the provider map isn't
empty) and that their configured endpoints are reachable from the running
VinylDNS API container by exercising a full create/delete round trip against
each provider.

Requires: pip install requests
Usage:    python3 quickstart/verify-dns-providers.py [--api-url http://localhost:9000]
"""
import argparse
import json
from pathlib import Path
import sys
import time
from datetime import datetime, timezone
from urllib.parse import urlparse, urljoin

import requests

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "modules/api/src/test/functional"))
from aws_request_signer import AwsSigV4RequestSigner

DEFAULT_API_URL = "http://localhost:9000"
DEFAULT_ACCESS_KEY = "okAccessKey"
DEFAULT_SECRET_KEY = "okSecretKey"
MAX_RETRIES = 30
RETRY_WAIT_SECONDS = 1


class SmokeTestFailure(Exception):
    pass


class Client:
    def __init__(self, api_url, access_key, secret_key):
        self.api_url = api_url
        self.signer = AwsSigV4RequestSigner(api_url, access_key, secret_key)
        self.session = requests.Session()

    def call(self, method, path, body=None, sign=True, expected_statuses=None):
        url = urljoin(self.api_url, path)
        body_string = json.dumps(body) if body is not None else ""
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if sign:
            headers["X-Amz-Date"] = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
            headers.update(
                self.signer.sign_request_headers(
                    method, urlparse(url).path, headers, body_string, None
                )
            )
        response = self.session.request(method, url, headers=headers, data=body_string, timeout=10)
        if expected_statuses and response.status_code not in expected_statuses:
            raise SmokeTestFailure(
                f"{method} {path} returned {response.status_code}, expected one of {expected_statuses}: {response.text}"
            )
        try:
            return response.status_code, response.json()
        except ValueError:
            return response.status_code, response.text


def wait_for_health(client):
    print(f"Waiting for VinylDNS API at {client.api_url} ..", end="", flush=True)
    for _ in range(MAX_RETRIES):
        try:
            status, _ = client.call("GET", "/health", sign=False)
            if status == 200:
                print(" OK")
                return
        except requests.RequestException:
            pass
        print(".", end="", flush=True)
        time.sleep(RETRY_WAIT_SECONDS)
    raise SmokeTestFailure(f"VinylDNS API never became healthy at {client.api_url}")


def check_allowed_providers(client):
    print("Checking that bind/powerdns providers loaded from config ..", end=" ")
    _, providers = client.call("GET", "/zones/generate/allowedDNSProviders", expected_statuses=[200])
    missing = [p for p in ("bind", "powerdns") if p not in providers]
    if missing:
        raise SmokeTestFailure(
            f"allowedDNSProviders returned {providers}, missing {missing}. "
            "The dns-provider-api.providers config is likely empty/misconfigured."
        )
    print(f"OK ({providers})")


def create_group(client):
    print("Creating a temporary admin group ..", end=" ")
    group = {
        "name": f"smoke-test-group-{int(time.time())}",
        "email": "test@test.com",
        "description": "vinyldns quickstart DNS provider verification",
        "members": [{"id": "ok"}],
        "admins": [{"id": "ok"}],
    }
    _, data = client.call("POST", "/groups", body=group, expected_statuses=[200])
    print(f"OK ({data['id']})")
    return data["id"]


def delete_group(client, group_id):
    client.call("DELETE", f"/groups/{group_id}", expected_statuses=[200, 404])


def wait_for_generate_zone_active(client, zone_id):
    for _ in range(MAX_RETRIES):
        status, data = client.call("GET", f"/zones/generate/id/{zone_id}", expected_statuses=[200])
        if data.get("status") == "Active":
            return data
        time.sleep(RETRY_WAIT_SECONDS)
    raise SmokeTestFailure(f"Generated zone {zone_id} never became Active")


def smoke_test_provider(client, group_id, provider, zone_name, provider_params):
    print(f"Exercising provider '{provider}' (zone {zone_name}) ..", end=" ")
    generated = None
    try:
        request = {
            "groupId": group_id,
            "email": "test@test.com",
            "provider": provider,
            "zoneName": zone_name,
            "providerParams": provider_params,
        }
        status, generated = client.call("POST", "/zones/generate", body=request, expected_statuses=[202])
        generated = wait_for_generate_zone_active(client, generated["id"])
        response_code = (generated.get("response") or {}).get("responseCode")
        if response_code is None or response_code >= 300:
            raise SmokeTestFailure(f"Unexpected provider response: {generated.get('response')}")
        print("OK")
    finally:
        if generated:
            client.call("DELETE", f"/zones/generate/{generated['id']}", expected_statuses=[202, 404])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-url", default=DEFAULT_API_URL)
    parser.add_argument("--access-key", default=DEFAULT_ACCESS_KEY)
    parser.add_argument("--secret-key", default=DEFAULT_SECRET_KEY)
    args = parser.parse_args()

    client = Client(args.api_url, args.access_key, args.secret_key)
    suffix = int(time.time())
    group_id = None
    try:
        wait_for_health(client)
        check_allowed_providers(client)
        group_id = create_group(client)

        smoke_test_provider(
            client,
            group_id,
            "bind",
            f"smoke-test-bind-{suffix}.",
            {
                "nameservers": ["172.17.42.1.", "ns1.example.com."],
                "admin_email": "admin@test.com",
            },
        )
        smoke_test_provider(
            client,
            group_id,
            "powerdns",
            f"smoke-test-pdns-{suffix}.",
            {
                "kind": "Native",
                "nameservers": ["ns1.example.com."],
            },
        )

        print("\nProvider verification passed: bind and powerdns are configured and reachable.")
        return 0
    except SmokeTestFailure as e:
        print(f"\nProvider verification FAILED: {e}", file=sys.stderr)
        return 1
    finally:
        if group_id:
            delete_group(client, group_id)


if __name__ == "__main__":
    sys.exit(main())
