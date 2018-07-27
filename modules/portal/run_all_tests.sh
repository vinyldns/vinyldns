#!/bin/bash

function check_for() {
    which $1 >/dev/null 2>&1
    EXIT_CODE=$?
    if [ ${EXIT_CODE} != 0 ]
    then
        echo "$1 is not installed"
        exit ${EXIT_CODE}
    fi
}

check_for python
check_for npm

# if the program exits before this has been captured then there must have been an error
EXIT_CODE=1

cd $(dirname $0)

# javascript code generate
bower install
grunt default

TEST_SUITES=('sbt clean coverage test'
             'grunt unit'
    )

for TEST in "${TEST_SUITES[@]}"
do
    echo "##### Running test: [$TEST]"
    $TEST
    EXIT_CODE=$?
    echo "##### Test [$TEST] ended with status [$EXIT_CODE]"
    if  [ ${EXIT_CODE} != 0 ]
    then
         exit ${EXIT_CODE}
    fi
done
