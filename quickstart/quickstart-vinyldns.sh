#!/usr/bin/env bash
#####################################################################################################
# Starts up the api, portal, and dependent services via
# docker-compose. The api will be available on localhost:9000 and the
# portal will be on localhost:9001
#
# Relevant overrides can be found in .env
#####################################################################################################
set -eo pipefail

DIR=$(
  cd "$(dirname "$0")" || exit
  pwd -P
)
source "${DIR}/../utils/includes/terminal_colors.sh"

function usage() {
  echo -e "usage: quickstart-vinyldns.sh [OPTIONS]"
  echo -e "Starts up a local VinylDNS installation using Docker Compose"
  echo
  echo -e "options:"
  echo -e "\t-a, --api          start the API, but not the Portal and its dependencies (e.g., LDAP)"
  echo -e "\t-b, --build        force a rebuild of the Docker images with the local code"
  echo -e "\t-c, --clean        stops all VinylDNS containers and exits"
  echo -e "\t-d, --deps         start up the dependencies, but not the API or Portal"
  echo -e "\t-sh, --shell       loads the .env file into a new BASH shell. The .env file can be overridden with -e"
  echo -e "\t-e, --env-file     specifies the suffix of the .env file (relative to the docker-compose file) to load (e.g., 'dev' - to load '.env.dev')"
  echo -e "\t-r, --reset        stops any the running containers before starting new containers"
  echo -e "\t-s, --service      specify the service to run"
  echo -e "\t-t, --timeout      the time to wait (in seconds) for the Portal and API to start (default: 60)"
  echo -e "\t-u, --update       remove the local quickstart images to force a rebuild"
  echo -e "\t-v, --version-tag  specify Docker image tag version (default: latest)"
  echo
  echo -e "\t-h, --help         show this help"
}

function wait_for_url() {
  echo -n "Waiting for ${F_BLUE}$1${F_RESET} at ${URL} .."
  RETRY="$TIMEOUT"
  while [ "$RETRY" -ge 0 ]; do
    echo -n "."
    if curl -I -s "${URL}" -o /dev/null -w "%{http_code}" &>/dev/null || false; then
      echo "${F_GREEN}OK${F_RESET}"
      break
    else
      ((RETRY -= 1))
      sleep 1
      if [[ $RETRY -eq 1 ]]; then
        echo "${F_RED}FAILED${F_RESET}"
        echo "${F_RED}Timeout waiting for ${F_BLUE}$1${F_RED} to be ready${F_RESET}"
        exit 1
      fi
    fi
  done
}

function is_running() {
  if (docker ps --format "{{.Image}}" | grep -q "$1"); then
    return 0
  fi
  return 1
}

function wait_for_api() {
  if is_running "vinyldns/api"; then
    URL="$VINYLDNS_API_URL"
    wait_for_url "VinylDNS API"
  fi
}

function wait_for_portal() {
  if is_running "vinyldns/portal"; then
    URL="$VINYLDNS_PORTAL_URL"
    wait_for_url "VinylDNS Portal"
  fi
}

# Defaults
TIMEOUT=60
DOCKER_COMPOSE_CONFIG="${DIR}/docker-compose.yml"
SERVICE=""
BUILD=""
RESET_DOCKER=0
UPDATE=0
CLEAN=0
ENV_FILE="${DIR}/.env"
SHELL_REQUESTED=0
while [[ $# -gt 0 ]]; do
  case "$1" in
  -t | --timeout)
    TIMEOUT="$2"
    shift
    shift
    ;;
  -d | --deps | --deps-only)
    SERVICE="$SERVICE integration ldap"
    shift
    ;;
  -a | --api | --api-only)
    SERVICE="$SERVICE api"
    shift
    ;;
  -p | --portal | --portal-only)
    SERVICE="$SERVICE ldap portal"
    shift
    ;;
  -c | --clean)
    CLEAN=1
    shift
    ;;
  -s | --service)
    SERVICE="$SERVICE $2"
    shift
    shift
    ;;
  -u | --update)
    UPDATE=1
    shift
    ;;
  -b | --build)
    BUILD="--build"
    shift
    ;;
  -r | --reset)
    RESET_DOCKER=1
    shift
    ;;
  -sh | --shell)
    SHELL_REQUESTED=1
    shift
    ;;
  -e | --env-file)
    if [ ! -f "${DIR}/.env.$2" ]; then
      echo "Cannot load ${DIR}/.env.$2"
      exit 1
    fi
    export ENV_FILE="${DIR}/.env.$2"
    shift
    shift
    ;;
  -v | --version-tag)
    export VINYLDNS_VERSION=$2
    export VINYLDNS_BASE_VERSION=${VINYLDNS_VERSION}
    export VINYLDNS_IMAGE_VERSION=${VINYLDNS_VERSION}
    shift
    shift
    ;;
  *)
    usage
    exit
    ;;
  esac
done

# Load environment variables
export $(echo $(cat "${ENV_FILE}" | sed 's/#.*//g'| xargs))

if [[ $SHELL_REQUESTED -eq 1 ]]; then
  echo "Please wait.. creating a new shell with the environment variables set."
  echo "To return, simply exit the new shell with 'exit' or ^D."
  bash
  exit
fi

# The version of VinylDNS docker image to run
export VINYLDNS_VERSION=latest
# The base/starting version of VinylDNS docker build image to use (vinyldns/build:<version>)
export VINYLDNS_BASE_VERSION=latest
# The version of the images to build
export VINYLDNS_IMAGE_VERSION=${VINYLDNS_VERSION}

# Make the list of services unique
SERVICE=$(echo "$SERVICE" | uniq)

if [[ $RESET_DOCKER -eq 1 ]] || [[ $CLEAN -eq 1 ]]; then
  "${DIR}/../utils/clean-vinyldns-containers.sh"
  if [[ $CLEAN -eq 1 ]]; then
    echo "${F_GREEN}Clean up completed!${F_RESET}"
    exit 0
  fi
fi

if [ -n "${BUILD}" ] || [ -n "$(docker images vinyldns/portal:local-dev --format '{{.Repository}}:{{.Tag}}')" ]; then
  VINYLDNS_IMAGE_VERSION="local-dev"
  export VINYLDNS_VERSION=${VINYLDNS_IMAGE_VERSION}
fi

# Update images if requested
if [[ $UPDATE -eq 1 ]]; then
  echo "${F_YELLOW}Removing any running VinylDNS docker containers tagged ${F_RESET}'${VINYLDNS_IMAGE_VERSION}'${F_YELLOW}...${F_RESET}"
  "${DIR}/../utils/clean-vinyldns-containers.sh"  &> /dev/null || true

  echo "${F_YELLOW}Removing any local VinylDNS Docker images tagged ${F_RESET}'${VINYLDNS_IMAGE_VERSION}'${F_YELLOW}...${F_RESET}"
  docker images -a |grep vinyldns | grep "${VINYLDNS_IMAGE_VERSION}" | awk '{print $3}' | xargs docker rmi -f &> /dev/null || true
  echo "${F_GREEN}Successfully removed all local VinylDNS Docker images and running containers tagged ${F_RESET}'${VINYLDNS_IMAGE_VERSION}'${F_YELLOW}...${F_RESET}"
  if [ -z "${BUILD}" ]; then
    echo "${F_LRED}You may need to re-run with the '--build' flag...${F_RESET}"
  fi
fi

if [ -n "${BUILD}" ]; then
  echo "Building containers and starting VinylDNS (${VINYLDNS_IMAGE_VERSION}) in the background..."
else
  echo "Starting VinylDNS (${VINYLDNS_IMAGE_VERSION}) the background..."
fi

# shellcheck disable=SC2086
docker compose -f "${DOCKER_COMPOSE_CONFIG}" --env-file "${ENV_FILE}" up ${BUILD} -d ${SERVICE} || (
  echo -e "${F_RED}Sorry, there was an error starting VinylDNS :-(\nTry resetting any existing containers with:\n\t${F_RESET}'$0 --reset'"; \
  exit 1; \
)

if is_running "vinyldns/portal" || is_running "vinyldns/api"; then
  echo
  wait_for_api
  wait_for_portal
  echo
fi

if is_running "vinyldns/portal"; then
  echo "${F_GREEN}VinylDNS started! You can connect to the portal via ${F_RESET}${VINYLDNS_PORTAL_URL}"
elif is_running "vinyldns/api"; then
  echo "${F_GREEN}VinylDNS API started! You can connect to the API via ${F_RESET}${VINYLDNS_API_URL}"
else
  echo "${F_GREEN}VinylDNS dependencies started!${F_RESET}"
fi
echo "${F_GREEN}To clean up the running containers:${F_RESET}"
echo "    $0 --clean"
