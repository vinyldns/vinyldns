#!/usr/bin/env bash
#
# This script will perform the functional tests for the API using Docker
#
set -euo pipefail

DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

# Allow NoOpCrypto for testing to enable JAR-based functional tests
export VINYLDNS_ALLOW_NOOP_CRYPTO_FOR_TESTING=true

cd "$DIR/../test/api/functional"
make
