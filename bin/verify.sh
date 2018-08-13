#!/bin/bash
echo 'Running tests...'

echo 'Stopping any docker containers...'
./bin/remove-vinyl-containers.sh

echo 'Starting up docker for integration testing and running unit and integration tests on all modules...'
sbt ";validate;verify"
verify_result=$?

echo 'Stopping any docker containers...'
./bin/remove-vinyl-containers.sh

if [ ${verify_result} -eq 0 ]
then
    echo 'Verify successful!'
    exit 0
else
    echo 'Verify failed!'
    exit 1
fi
