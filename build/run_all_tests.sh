#!/usr/bin/env bash
set -euo pipefail
DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

source "${DIR}/../utils/includes/terminal_colors.sh"

if [ ! -d "${DIR}/../artifacts" ] || [ ! -f "${DIR}/../artifacts/vinyldns-api.jar" ]; then
  echo -e "${F_YELLOW}Warning:${F_RESET} you might want to run 'build/assemble_api.sh' first to improve performance"
fi

# ---------------------------------------------------------------------------
# Readiness helpers
# ---------------------------------------------------------------------------

wait_for_port() {
  local host=$1 port=$2 retries=${3:-60}
  echo "Waiting for $host:$port to be available..."
  until nc -z "$host" "$port" 2>/dev/null; do
    retries=$((retries - 1))
    if [ "$retries" -le 0 ]; then
      echo -e "${F_RED}Timeout waiting for $host:$port${F_RESET}"
      return 1
    fi
    sleep 1
  done
  echo "$host:$port is ready."
}

wait_for_soa() {
  local zone=$1 server=${2:-127.0.0.1} retries=${3:-60}
  echo "Waiting for SOA record for zone '$zone' from DNS server $server..."
  until dig +short +time=2 +tries=1 SOA @"$server" "$zone" 2>/dev/null | grep -q '.'; do
    retries=$((retries - 1))
    if [ "$retries" -le 0 ]; then
      echo -e "${F_RED}Timeout waiting for SOA for zone '$zone' from $server${F_RESET}"
      return 1
    fi
    sleep 1
  done
  echo "SOA for '$zone' is available."
}

echo "Running unit and integration tests..."
if ! "${DIR}/verify.sh"; then
  echo "Error running unit and integration tests."
  exit 1
fi

echo "Running API functional tests..."
if ! "${DIR}/func-test-api.sh"; then
  echo -e "${F_RED}Error running API functional tests${F_RESET}"
  echo "==== Last 200 lines of functional test output ===="
  find "${DIR}/../" -name "*.log" -newer "${DIR}/run_all_tests.sh" 2>/dev/null | \
    xargs tail -n 200 2>/dev/null || true
  exit 1
fi

echo "Running Portal functional tests..."
if ! "${DIR}/func-test-portal.sh"; then
  echo -e "${F_RED}Error running Portal functional tests${F_RESET}"
  exit 1
fi

echo "Running Frontend functional tests..."
if ! "${DIR}/func-test-frontend.sh"; then
  echo -e "${F_RED}Error running Frontend functional tests${F_RESET}"
  exit 1
fi

