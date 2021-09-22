#!/usr/bin/env bash

# Assume defaults of local docker-compose if not set
if [ -z "${VINYLDNS_URL}" ]; then
  VINYLDNS_URL="http://localhost:9000"
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

echo "Running live tests against ${VINYLDNS_URL} and DNS server ${DNS_IP}"

cd /app

# Cleanup any errant cached file copies
find . -name "*.pyc" -delete
find . -name "__pycache__" -delete

result=0
# If PROD_ENV is not true, we are in a local docker environment so do not skip anything
    echo "./run.py live_tests"
    ./run.py live_tests -v
exit $result

