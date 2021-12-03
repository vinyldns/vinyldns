#!/usr/bin/env bash
set -euo pipefail

DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

cd "$DIR/../test/api/integration"
make build DOCKER_PARAMS="--build-arg SKIP_API_BUILD=true" && make run-local WITH_ARGS="sbt" DOCKER_PARAMS="-e RUN_SERVICES=none"
