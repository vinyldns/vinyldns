#!/usr/bin/env bash
#
# This script will build the API and Portal artifacts from the current
# workspace code
#
set -euo pipefail

DIR=$(
  cd "$(dirname "$0")"
  pwd -P
)

usage() {
  echo "USAGE: assemble_artifacts.sh [options]"
  echo -e "\t-c, --clean         removes all files from the ./artifacts directory"
}

clean(){
  echo "Cleaning artifacts"
  if [ -d "${DIR}/../artifacts/" ] && [ -f "${DIR}/../artifacts/vinyldns-api.jar" ]; then
    rm "${DIR}/../artifacts/vinyldns-api.jar"
  fi
  if [ -d "${DIR}/../artifacts/" ] && [ -f "${DIR}/../artifacts/vinyldns-portal.zip" ]; then
    rm "${DIR}/../artifacts/vinyldns-portal.zip"
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
  --clean | -c)
    clean
    exit 0
    ;;
  *)
    usage
    exit 1
    ;;
  esac
done

clean
"${DIR}/assemble_api.sh" && "${DIR}/assemble_portal.sh"
