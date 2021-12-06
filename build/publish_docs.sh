#!/usr/bin/env bash
set -euo pipefail
DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

docker run -i --rm -e RUN_SERVICES=none -v "${DIR}/../:/build" vinyldns/build:base-build-docs /bin/bash -c "sbt ';project docs; publishMicrosite'"
