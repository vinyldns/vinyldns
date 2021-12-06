#!/usr/bin/env bash
set -euo pipefail
DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

USE_TTY="" && test -t 1 && USE_TTY="-t"
docker run -i ${USE_TTY} --rm -e RUN_SERVICES=none -e SBT_MICROSITES_PUBLISH_TOKEN="${SBT_MICROSITES_PUBLISH_TOKEN}" -v "${DIR}/../:/build" vinyldns/build:base-build-docs /bin/bash -c "sbt ';project docs; publishMicrosite'"
