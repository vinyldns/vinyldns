#!/usr/bin/env bash
#
# This script will build the vinyldns-api.jar file using Docker. The file will
# be placed in the configured location (currently `artifacts/` off of the root)
#
set -euo pipefail

DIR=$(
  cd "$(dirname "$0")"
  pwd -P
)

usage() {
  echo "USAGE: assemble_api.sh [options]"
  echo -e "\t-n, --no-cache         do not use cache when building the artifact"
  echo -e "\t-u, --update           update the underlying docker image"
}

NO_CACHE=0
UPDATE_DOCKER=0
while [[ $# -gt 0 ]]; do
  case "$1" in
  --no-cache | -n)
    NO_CACHE=1
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

if [[ $NO_CACHE -eq 1 ]]; then
  rm -rf "${DIR}/../artifacts/vinyldns-api.jar" &> /dev/null || true
  docker rmi vinyldns:api-artifact &> /dev/null || true
fi

if [[ $UPDATE_DOCKER -eq 1 ]]; then
    echo "Pulling latest version of 'vinyldns/build:base-build'"
    docker pull vinyldns/build:base-build
fi

echo "Building VinylDNS API artifact"
make -C "${DIR}/docker/api" artifact
