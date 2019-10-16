#!/bin/bash

CURDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

function usage {
    printf "usage: start.sh [OPTIONS]\n\n"
    printf "starts a specific version of vinyldns\n\n"
    printf "options:\n"
    printf "\t-v, --version: the version to start up; required\n"
}

function wait_for_url {
	URL=$1
	DATA=""
	RETRY="60"
	echo "pinging $URL ..."
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

# Default the build to -SNAPSHOT if not set
VINYLDNS_VERSION=

while [ "$1" != "" ]; do
	case "$1" in
	  -v | --version  	) VINYLDNS_VERSION="$2";  shift 2;;
		* ) usage; exit;;
	esac
done

if [ -z "$VINYLDNS_VERSION" ]; then
  echo "VINYLDNS_VERSION not set"
  usage
  exit
else
  export VINYLDNS_VERSION=$VINYLDNS_VERSION
fi

# Actually starts up our docker images
docker-compose -f $CURDIR/docker/docker-compose.yml up --no-build -d api portal

# Waits for the URL to be available
wait_for_url "http://localhost:9001"

if [ $? -eq 0 ]; then
  echo "VinylDNS started and available at http://localhost:9001"
  exit 0
else
  echo "VinylDNS startup failed!"
  $CURDIR/stop.sh
  exit 1
fi
