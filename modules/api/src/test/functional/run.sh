#!/usr/bin/env bash

set -eo pipefail

ROOT_DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
UPDATE_DEPS=""
if [ "$1" == "--update" ]; then
  UPDATE_DEPS="$1"
  shift
fi

cd "${ROOT_DIR}"
if [ "$1" == "--interactive" ]; then
  shift
  bash
else
  "./pytest.sh" "${UPDATE_DEPS}" -n4 --suppress-no-test-exit-code -v tests "$@"
fi
