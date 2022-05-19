#!/usr/bin/env bash
set -euo pipefail

DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
source "${DIR}/../utils/includes/terminal_colors.sh"

if [ ! -d "${DIR}/../artifacts" ] || [ ! -f "${DIR}/../artifacts/vinyldns-api.jar" ]; then
  echo -e "${F_YELLOW}Warning:${F_RESET} you might want to run 'build/assemble_api.sh' first to improve performance"
fi

cd "${DIR}/../test/api/integration"
make build && make run DOCKER_PARAMS="-v \"$(pwd)/../../../target:/build/target\"" WITH_ARGS="bash -c \"sbt ';validate' && sbt ';verify'\""
