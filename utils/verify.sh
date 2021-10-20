#!/usr/bin/env bash
set -euo pipefail

DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
echo 'Running tests...'

cd "$DIR/../test/api/integration"
make build && make run WITH_ARGS="sbt ';validate;verify'"
verify_result=$?

if [ ${verify_result} -eq 0 ]; then
    echo 'Verify successful!'
    exit 0
else
    echo 'Verify failed!'
    exit 1
fi
