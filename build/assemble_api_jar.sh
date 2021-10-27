#!/usr/bin/env bash
#
# This script will build the vinyldns.jar file using Docker. The file will
# be placed in the configured location (currently `assembly/` off of the root)
#
set -euo pipefail

DIR=$(
  cd "$(dirname "$0")"
  pwd -P
)

usage() {
  echo "USAGE: assemble_jar.sh [options]"
  echo -e "\t-n, --no-clean         do no perform a clean before assembling the jar"
  echo -e "\t-u, --update           update the underlying docker image"
}

SKIP_CLEAN=0
UPDATE_DOCKER=0
while [[ $# -gt 0 ]]; do
  case "$1" in
  --no-clean | -n)
    SKIP_CLEAN=1
    shift
    ;;
  --update | -u)
    UPDATE_DOCKER=1
    shift
    ;;
  *)
    usage
    exit 1
    ;;
  esac
done

if ! [[ $SKIP_CLEAN -eq 1 ]]; then
  "${DIR}/deep_clean.sh"
fi

if [[ $UPDATE_DOCKER -eq 1 ]]; then
    echo "Pulling latest version of 'vinyldns/build:base-test-integration'"
    docker pull vinyldns/build:base-test-integration
fi

echo "Building VinylDNS API jar file"
docker run -it --rm -e RUN_SERVICES=none -v "${DIR}/..:/build" vinyldns/build:base-test-integration -- sbt 'api/assembly'
