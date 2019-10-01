#!/usr/bin/env bash
#####################################################################################################
# Starts up the api, portal, and dependent services via
# docker-compose. The api will be available on localhost:9000 and the
# portal will be on localhost:9001
#
# Relevant overrides can be found at ./.env and ../docker/.env
#
# Options:
#	-t, --timeout seconds: overwrite ping timeout, default of 60
#	-a, --api-only: only starts up vinyldns-api and its dependencies, excludes vinyldns-portal
#	-c, --clean: re-pull vinyldns/api and vinyldns/portal images from docker hub
#	-v, --version tag: overwrite vinyldns/api and vinyldns/portal docker tags
#####################################################################################################

function wait_for_url {
	echo "pinging ${URL} ..."
	DATA=""
	RETRY="$TIMEOUT"
	while [ "$RETRY" -gt 0 ]
	do
		DATA=$(curl -I -s "${URL}" -o /dev/null -w "%{http_code}")
		if [ $? -eq 0 ]
		then
			echo "Succeeded in connecting to ${URL}!"
			break
		else
			echo "Retrying" >&2

			let RETRY-=1
			sleep 1

			if [ "$RETRY" -eq 0 ]
			then
			  echo "Exceeded retries waiting for ${URL} to be ready, failing"
			  exit 1
			fi
		fi
	done
}

function usage {
    printf "usage: docker-up-vinyldns.sh [OPTIONS]\n\n"
    printf "starts up a local VinylDNS installation using docker compose\n\n"
    printf "options:\n"
    printf "\t-t, --timeout seconds: overwrite ping timeout of 60\n"
    printf "\t-a, --api-only: do not start up vinyldns-portal\n"
    printf "\t-c, --clean: re-pull vinyldns/api and vinyldns/portal images from docker hub\n"
    printf "\t-v, --version tag: overwrite vinyldns/api and vinyldns/portal docker tags\n"
}

function clean_images {
	if (( $CLEAN == 1 )); then
		echo "cleaning docker images..."
		docker rmi vinyldns/api:$VINYLDNS_VERSION
		docker rmi vinyldns/portal:$VINYLDNS_VERSION
	fi
}

function wait_for_api {
	echo "Waiting for api..."
	URL="$VINYLDNS_API_URL"
	wait_for_url
}

function wait_for_portal {
	# check if portal was skipped
	if [ "$SERVICE" != "api" ]; then
		echo "Waiting for portal..."
		URL="$VINYLDNS_PORTAL_URL"
		wait_for_url
	fi
}

# initial var setup
DIR=$( cd $(dirname $0) ; pwd -P )
TIMEOUT=60
DOCKER_COMPOSE_CONFIG="${DIR}/../docker/docker-compose-build.yml"
# empty service starts up all docker services in compose file
SERVICE=""
# when CLEAN is set to 1, existing docker images are deleted so they are re-pulled
CLEAN=0

# source env before parsing args so vars can be overwritten
set -a # Required in order to source docker/.env
# Source customizable env files
source "$DIR"/.env
source "$DIR"/../docker/.env

# parse args
while [ "$1" != "" ]; do
	case "$1" in
		-t | --timeout  	) TIMEOUT="$2";  shift;;
		-a | --api-only 	) SERVICE="api";;
		-c | --clean    	) CLEAN=1;;
		-v | --version		) export VINYLDNS_VERSION=$2; shift;;
		* ) usage; exit;;
	esac
	shift
done

clean_images

echo "timeout is set to ${TIMEOUT}"
echo "vinyldns version is set to '${VINYLDNS_VERSION}'"

echo "Starting vinyldns and all dependencies in the background..."
docker-compose -f "$DOCKER_COMPOSE_CONFIG" up -d ${SERVICE}

wait_for_api
wait_for_portal
