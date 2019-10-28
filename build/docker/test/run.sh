#!/usr/bin/env bash

# Assume defaults of local docker-compose if not set
if [ -z "${VINYLDNS_URL}" ]; then
  VINYLDNS_URL="http://vinyldns-api:9000"
fi
if [ -z "${DNS_IP}" ]; then
  DNS_IP=$(dig +short vinyldns-bind9)
fi

# Assume all tests if not specified
if [ -z "${TEST_PATTERN}" ]; then
  TEST_PATTERN=
else
  TEST_PATTERN="-k ${TEST_PATTERN}"
fi

echo "Waiting for API to be ready at ${VINYLDNS_URL} ..."
DATA=""
RETRY=60
SLEEP_DURATION=1
while [ "$RETRY" -gt 0 ]
do
    DATA=$(curl -I -s "${VINYLDNS_URL}/ping" -o /dev/null -w "%{http_code}")
    if [ $? -eq 0 ]
    then
        break
    else
        echo "Retrying" >&2

        let RETRY-=1
        sleep "$SLEEP_DURATION"

        if [ "$RETRY" -eq 0 ]
        then
          echo "Exceeded retries waiting for VinylDNS API to be ready, failing"
          exit 1
        fi
    fi
done

echo "Running live tests against ${VINYLDNS_URL} and DNS server ${DNS_IP}"

cd /app

# Cleanup any errant cached file copies
find . -name "*.pyc" -delete
find . -name "__pycache__" -delete

ls -al

# -m plays havoc with -k, using variables is a headache, so doing this by hand
# run parallel tests first (not serial)
set -x
./run-tests.py live_tests -n2 -v -m "not skip_production and not serial" ${TEST_PATTERN} --url=${VINYLDNS_URL} --dns-ip=${DNS_IP} --teardown=False
ret1=$?

# IMPORTANT! pytest exists status code 5 if no tests are run, force that to 0
if [ "$ret1" = 5 ]; then
  echo "No tests collected."
  ret1=0
fi

./run-tests.py live_tests -n0 -v -m "not skip_production and serial" ${TEST_PATTERN} --url=${VINYLDNS_URL} --dns-ip=${DNS_IP} --teardown=True
ret2=$?
if [ "$ret2" = 5 ]; then
  echo "No tests collected."
  ret2=0
fi

if [ $ret1 -ne 0 ] || [ $ret2 -ne 0 ]; then
  exit 1
else
  exit 0
fi

