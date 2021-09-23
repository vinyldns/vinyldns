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

if [ -z "${PAR_CPU}" ]; then
  export PAR_CPU=2
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
find . -name ".pytest_cache" -delete

result=0
# -m plays havoc with -k, using variables is a headache, so doing this by hand
echo "./run-tests.py live_tests -n${PAR_CPU} -v --url=${VINYLDNS_URL} --dns-ip=${DNS_IP} ${TEST_PATTERN} --teardown=False"
./run-tests.py live_tests -n${PAR_CPU} -v --url=${VINYLDNS_URL} --dns-ip=${DNS_IP} ${TEST_PATTERN} --teardown=False
result=$?
exit $result
