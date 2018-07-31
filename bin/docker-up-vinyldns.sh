#!/usr/bin/env bash
######################################################################
# Copies the contents of `docker` into target/scala-2.12
# to start up dependent services via docker compose.  Once
# dependent services are started up, the fat jar built by sbt assembly
# is loaded into a docker container.  The api will be available
## by default on port 9000 and the portal will be on port 9001
######################################################################

DIR=$( cd $(dirname $0) ; pwd -P )

echo "Starting portal server and all dependencies in the background..."
docker-compose -f $DIR/../docker/docker-compose-build.yml up -d

VINYL_URL="http://localhost:9000"
echo "Waiting for API to be ready at ${VINYL_URL} ..."
DATA=""
RETRY=40
while [ $RETRY -gt 0 ]
do
    DATA=$(wget -O - -q -t 1 "${VINYL_URL}/ping")
    if [ $? -eq 0 ]
    then
        echo "Succeeded in connecting to VINYL API!"
        break
    else
        echo "Retrying Again" >&2

        let RETRY-=1
        sleep 1

        if [ $RETRY -eq 0 ]
        then
          echo "Exceeded retries waiting for VINYL API to be ready, failing"
          exit 1
        fi
    fi
done

VINYL_URL="http://localhost:9001"
echo "Waiting for PORTAL to be ready at ${VINYL_URL} ..."
DATA=""
RETRY=40
while [ $RETRY -gt 0 ]
do
    DATA=$(wget -O - -q -t 1 "${VINYL_URL}")
    if [ $? -eq 0 ]
    then
        echo "Succeeded in connecting to VINYL PORTAL!"
        break
    else
        echo "Retrying Again" >&2

        let RETRY-=1
        sleep 1

        if [ $RETRY -eq 0 ]
        then
          echo "Exceeded retries waiting for VINYL PORTAL to be ready, failing"
          exit 1
        fi
    fi
done
