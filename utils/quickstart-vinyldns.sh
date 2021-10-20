#!/usr/bin/env bash
#####################################################################################################
# Starts up the api, portal, and dependent services via
# docker-compose. The api will be available on localhost:9000 and the
# portal will be on localhost:9001
#
# Relevant overrides can be found in quickstart/.env
#
# Options:
# -t, --timeout seconds: overwrite ping timeout of 60
# -a, --api-only: do not start up vinyldns-portal
# -s, --service: specify the service to run
# -c, --clean: re-pull vinyldns/api and vinyldns/portal images from docker hub
# -b, --build: rebuild images when applicable
# -v, --version tag: overwrite vinyldns/api and vinyldns/portal docker tags
#####################################################################################################
set -eo pipefail

function wait_for_url() {
  echo -n "Checking ${URL}..."
  RETRY="$TIMEOUT"
  while [ "$RETRY" -gt 0 ]; do
    if curl -I -s "${URL}" -o /dev/null -w "%{http_code}" &>/dev/null || false; then
      echo "Succeeded in connecting to ${URL}!"
      break
    else
      echo -n "."

      ((RETRY -= 1))
      sleep 1

      if [ "$RETRY" -eq 0 ]; then
        echo "Exceeded retries waiting for ${URL} to be ready, failing"
        exit 1
      fi
    fi
  done
}

function usage() {
  printf "usage: quickstart-vinyldns.sh [OPTIONS]\n\n"
  printf "Starts up a local VinylDNS installation using docker compose\n\n"
  printf "options:\n"
  printf "\t-t, --timeout seconds: overwrite ping timeout of 60\n"
  printf "\t-a, --api-only: do not start up vinyldns-portal\n"
  printf "\t-s, --service: specify the service to run\n"
  printf "\t-c, --clean: re-pull vinyldns/api and vinyldns/portal images from docker hub\n"
  printf "\t-b, --build: rebuild images when applicable\n"
  printf "\t-v, --version tag: overwrite vinyldns/api and vinyldns/portal docker tags\n"
}

function clean_images() {
  if [[ $CLEAN -eq 1 ]]; then
    echo "cleaning docker images..."
    docker rmi "vinyldns/api:${VINYLDNS_VERSION}"
    docker rmi "vinyldns/portal:${VINYLDNS_VERSION}"
  fi
}

function wait_for_api() {
  echo "Waiting for api..."
  URL="$VINYLDNS_API_URL"
  wait_for_url
}

function wait_for_portal() {
  # check if portal was skipped
  if [ "$SERVICE" != "integration" ]; then
    echo "Waiting for portal..."
    URL="$VINYLDNS_PORTAL_URL"
    wait_for_url
  fi
}

# initial var setup
DIR=$(
  cd "$(dirname "$0")"
  pwd -P
)
TIMEOUT=60
DOCKER_COMPOSE_CONFIG="${DIR}/../quickstart/docker-compose.yml"
# empty service starts up all docker services in compose file
SERVICE=""
# when CLEAN is set to 1, existing docker images are deleted so they are re-pulled
CLEAN=0
# default to latest for docker versions
export VINYLDNS_VERSION=latest

# source env before parsing args so vars can be overwritten
set -a # Required in order to source docker/.env
# Source customizable env files
source "$DIR"/../quickstart/.env

# parse args
BUILD=""
while [[ $# -gt 0 ]]; do
  case "$1" in
  -t | --timeout)
    TIMEOUT="$2"
    shift
    shift
    ;;
  -a | --api-only)
    SERVICE="integration"
    shift
    ;;
  -s | --service)
    SERVICE="$2"
    shift
    shift
    ;;
  -c | --clean)
    CLEAN=1
    shift
    ;;
  -b | --build)
    BUILD="--build"
    shift
    ;;
  -v | --version)
    export VINYLDNS_VERSION=$2
    shift
    shift
    ;;
  *)
    usage
    exit
    ;;
  esac
done

clean_images

echo "timeout is set to ${TIMEOUT}"
echo "vinyldns version is set to '${VINYLDNS_VERSION}'"

echo "Starting vinyldns and all dependencies in the background..."
docker-compose -f "$DOCKER_COMPOSE_CONFIG" up ${BUILD} -d "${SERVICE}"

wait_for_api
wait_for_portal
