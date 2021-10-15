#!/usr/bin/env bash

set -eo pipefail

ROOT_DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

cd "${ROOT_DIR}"
if [ "$1" == "--interactive" ]; then
  shift
  bash
else
  grunt unit "$@"
fi
