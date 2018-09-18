#!/usr/bin/env bash

VINYLDNS_URL="http://vinyldns-api:9000"
echo "Waiting for API to be ready at ${VINYLDNS_URL} ..."
DATA=""
RETRY=60
while [ "$RETRY" -gt 0 ]
do
    DATA=$(curl -I -s "${VINYLDNS_URL}/ping" -o /dev/null -w "%{http_code}")
    if [ $? -eq 0 ]
    then
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

DNS_IP=$(dig +short vinyldns-bind9)
echo "Running live tests against ${VINYLDNS_URL} and DNS server ${DNS_IP}"

cd /app
./run-tests.py live_tests -v --url=${VINYLDNS_URL} --dns-ip=${DNS_IP}
