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

# If PROD_ENV is not true, we are in a local docker environment so do not skip anything
if [ "${PROD_ENV}" = "true" ]; then
    # -m plays havoc with -k, using variables is a headache, so doing this by hand
    # run parallel tests first (not serial)
    echo "./run-tests.py live_tests -n0 -v -m \"not skip_production and not serial\" -v --url=${VINYLDNS_URL} --dns-ip=${DNS_IP} ${TEST_PATTERN}"
    ./run-tests.py live_tests -n0 -v -m "not skip_production and not serial" --url=${VINYLDNS_URL} --dns-ip=${DNS_IP} ${TEST_PATTERN}
    if [ $? -eq 0 ]; then
      # run serial tests second (serial marker)
      echo "./run-tests.py live_tests -n0 -v -m \"not skip_production and serial\" -v --url=${VINYLDNS_URL} --dns-ip=${DNS_IP} ${TEST_PATTERN}"
      ./run-tests.py live_tests -n0 -v -m "not skip_production and serial" --url=${VINYLDNS_URL} --dns-ip=${DNS_IP} ${TEST_PATTERN}
    fi
else
    # run parallel tests first (not serial)
    echo "./run-tests.py live_tests -n0 -v -m \"not serial\" --url=${VINYLDNS_URL} --dns-ip=${DNS_IP} ${TEST_PATTERN}"
    ./run-tests.py live_tests -n0 -v -m "not serial" --url=${VINYLDNS_URL} --dns-ip=${DNS_IP} ${TEST_PATTERN}

    if [ $? -eq 0 ]; then
      # run serial tests second (serial marker)
      echo "./run-tests.py live_tests -n0 -v -m \"serial\" --url=${VINYLDNS_URL} --dns-ip=${DNS_IP} ${TEST_PATTERN}"
      ./run-tests.py live_tests -n0 -v -m "serial" --url=${VINYLDNS_URL} --dns-ip=${DNS_IP} ${TEST_PATTERN}
    fi
fi
