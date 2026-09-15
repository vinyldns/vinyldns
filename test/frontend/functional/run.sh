#!/usr/bin/env bash

set -eo pipefail

ROOT_DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
cd "${ROOT_DIR}"
if [ "$1" == "--interactive" ]; then
  shift
  bash
else
  npm install -f --no-audit --no-fund
  # Attempt to just run vitest - this should work most of the time
  # We may need to update dependencies if our local functional tests dependencies
  # differ from those of the 'base-test-frontend' docker image
  npm run test:coverage -- "$@" || { echo "Attempting to recover.." && npm install -f --no-audit --no-fund && npm run test:coverage -- "$@"; }
fi
