#!/usr/bin/env bash
#
# This script will perform the functional tests for the Portal using Docker
#
set -euo pipefail

DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

cd "$DIR/../test/portal/functional"
make
