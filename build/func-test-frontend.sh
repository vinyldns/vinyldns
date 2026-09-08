#!/usr/bin/env bash
#
# This script will perform the functional (vitest) tests for the React frontend
#
set -euo pipefail

DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

cd "$DIR/../test/frontend/functional"
make

