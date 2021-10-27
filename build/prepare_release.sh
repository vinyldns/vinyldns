#!/usr/bin/env bash
set -euo pipefail
DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

source "${DIR}/../utils/includes/terminal_colors.sh"

if [ ! -d "${DIR}/../assembly" ] || [ ! -f "${DIR}/../assembly/vinyldns.jar" ]; then
  echo -e "${F_YELLOW}Warning:${F_RESET} you might want to run 'build/assemble_api_jar.sh' first to improve performance"
fi

echo "Running unit and integration tests..."
if ! "${DIR}/verify.sh"; then
  echo "Error running unit and integration tests."
  exit 1
fi

echo "Running API functional tests..."
if ! "${DIR}/func-test-api.sh"; then
  echo "Error running API functional tests"
  exit 1
fi

echo "Running Portal functional tests..."
if ! "${DIR}/func-test-portal.sh"; then
  echo "Error running Portal functional tests"
  exit 1
fi
