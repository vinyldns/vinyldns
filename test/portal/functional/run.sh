#!/usr/bin/env bash

set -eo pipefail

ROOT_DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
cd "${ROOT_DIR}"
if [ "$1" == "--interactive" ]; then
  shift
  bash
else
  # Attempt to just run grunt - this should work most of the time
  # We may need to update dependencies if our local functional tests dependencies
  # differ from those of the 'base-test-portal' docker image
  grunt unit "$@" || { echo "Attempting to recover.." && npm install -f --no-audit --no-fund && grunt unit "$@"; }
fi
