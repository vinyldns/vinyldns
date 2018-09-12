#!/usr/bin/env bash
######################################################################
# Starts up the api, portal, and dependent services via
# docker-compose. The api will be available on localhost:9000 and the
# portal will be on localhost:9001
######################################################################

DIR=$( cd $(dirname $0) ; pwd -P )

set -a # Required in order to source docker/.env
# Source customizable env files
source "$DIR"/.env
source "$DIR"/../docker/.env

echo "Starting portal server and all dependencies in the background..."
docker-compose -f "$DIR"/../docker/docker-compose-build.yml up -d

echo "Waiting for API to be ready at ${VINYLDNS_API_URL} ..."
DATA=""
RETRY=40
while [ "$RETRY" -gt 0 ]
do
    DATA=$(curl -I -s "${VINYLDNS_API_URL}/ping" -o /dev/null -w "%{http_code}")
    if [ $? -eq 0 ]
    then
        echo "Succeeded in connecting to VinylDNS API!"
        break
    else
        echo "Retrying Again" >&2

        let RETRY-=1
        sleep 1

        if [ "$RETRY" -eq 0 ]
        then
          echo "Exceeded retries waiting for VinylDNS API to be ready, failing"
          exit 1
        fi
    fi
done

echo "Waiting for portal to be ready at ${VINYLDNS_PORTAL_URL} ..."
DATA=""
RETRY=40
while [ "$RETRY" -gt 0 ]
do
    DATA=$(curl -I -s "${VINYLDNS_PORTAL_URL}" -o /dev/null -w "%{http_code}")
    if [ $? -eq 0 ]
    then
        echo "Succeeded in connecting to VinylDNS portal!"
        break
    else
        echo "Retrying Again" >&2

        let RETRY-=1
        sleep 1

        if [ "$RETRY" -eq 0 ]
        then
          echo "Exceeded retries waiting for VinylDNS portal to be ready, failing"
          exit 1
        fi
    fi
done
