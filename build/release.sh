#!/usr/bin/env bash
set -euo pipefail
DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

docker run -it --rm -e RUN_SERVICES=none -v "${DIR}/../:/build" vinyldns/build:base-build  /bin/bash
