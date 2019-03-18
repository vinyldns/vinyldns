#!/usr/bin/env bash

# allow skipping with env var
if [ "$SKIP_MYSQL_WAIT" -eq "1" ]; then
    exit 0
fi

# the mysql address, default to a local docker setup
MYSQL_ADDRESS=${MYSQL_ADDRESS:-vinyldns-mysql}
MYSQL_PORT=${MYSQL_PORT:-3306}
echo "Waiting for MYSQL to be ready on ${MYSQL_ADDRESS}:${MYSQL_PORT}"
DATA=""
RETRY=30
while [ "$RETRY" -gt 0 ]
do
    DATA=$(nc -vzw1 "$MYSQL_ADDRESS" "$MYSQL_PORT")
    if [ $? -eq 0 ]
    then
        break
    else
        echo "Retrying Again" >&2

        let RETRY-=1
        sleep .5

        if [ "$RETRY" -eq 0 ]
        then
          echo "Exceeded retries waiting for MYSQL to be ready on ${MYSQL_ADDRESS}:${MYSQL_PORT}, failing"
          return 1
        fi
    fi
done
