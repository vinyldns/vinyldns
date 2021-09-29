#!/usr/bin/env bash

set -euo pipefail

UPDATE_DEPS=""
if [ "$1" == "--update" ]; then
  UPDATE_DEPS="$1"
  shift
fi

PARAMS=("$@")
./pytest.sh "${UPDATE_DEPS}" --suppress-no-test-exit-code -v live_tests "${PARAMS[@]}"
