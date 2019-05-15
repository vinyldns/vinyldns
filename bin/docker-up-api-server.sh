#!/bin/bash
######################################################################
# Copies the contents of `docker` into target/scala-2.12
# to start up dependent services via docker compose.  Once
# dependent services are started up, the fat jar built by sbt assembly
# is loaded into a docker container.  The api will be available
# by default on port 9000
######################################################################


DIR=$( cd $(dirname $0) ; pwd -P )

set -a # Required in order to source docker/.env
# Source customizable env files
source "$DIR"/.env
source "$DIR"/../docker/.env

WORK_DIR="$DIR"/../target/scala-2.12
mkdir -p "$WORK_DIR"

echo "Copy all Docker to the target directory so we can start up properly and the Docker context is small..."
cp -af "$DIR"/../docker "$WORK_DIR"/

echo "Copy the vinyldns.jar to the API Docker folder so it is in context..."
if [[ ! -f "$DIR"/../modules/api/target/scala-2.12/vinyldns.jar ]]; then
    echo "vinyldns.jar not found, building..."
    cd "$DIR"/../
    sbt api/clean api/assembly
    cd "$DIR"
fi
cp -f "$DIR"/../modules/api/target/scala-2.12/vinyldns.jar "$WORK_DIR"/docker/api

echo "Starting API server and all dependencies in the background..."
docker-compose -f "$WORK_DIR"/docker/docker-compose-func-test.yml --project-directory "$WORK_DIR"/docker up --build -d api

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
        echo "Retrying" >&2

        let RETRY-=1
        sleep 1

        if [ "$RETRY" -eq 0 ]
        then
          echo "Exceeded retries waiting for VinylDNS API to be ready, failing"
          exit 1
        fi
    fi
done
