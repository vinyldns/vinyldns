#!/usr/bin/env bash
set -euo pipefail

DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

usage() {
  echo "USAGE: sbt.sh [options]"
  echo -e "\t-d, --debug        enable debugging"
  echo -e "\t-p, --debug-port   the debug port (default: 5021)"
}

DEBUG_SETTINGS=""
DEBUG_PORT=5021
while [[ $# -gt 0 ]]; do
  case "$1" in
  --debug | -d)
    DEBUG_SETTINGS="-e SBT_OPTS=\"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"
    shift
    ;;
  --debug-port | -p)
    DEBUG_PORT=$2
    shift
    shift
    ;;
  *)
    usage
    exit 1
    ;;
  esac
done

if [ "${DEBUG_SETTINGS}" != "" ]; then
  DEBUG_SETTINGS="${DEBUG_SETTINGS},address=${DEBUG_PORT}\" -p \"${DEBUG_PORT}:${DEBUG_PORT}\""
fi

cd "$DIR/../test/api/integration"
make build DOCKER_PARAMS="--build-arg SKIP_API_BUILD=true" && make run-local WITH_ARGS="sbt" DOCKER_PARAMS="-p \"5020-5030:5020-5030\" ${DEBUG_SETTINGS} -e RUN_SERVICES=none --env-file \"$DIR/../test/api/integration/.env.integration\""
