#!/bin/bash
######################################################################
# Runs e2e tests against the portal
######################################################################

DIR=$( cd $(dirname $0) ; pwd -P )
WORK_DIR=$DIR/../modules/portal

function check_for() {
    which $1 >/dev/null 2>&1
    EXIT_CODE=$?
    if [ ${EXIT_CODE} != 0 ]
    then
        echo "$1 is not installed"
        exit ${EXIT_CODE}
    fi
}

cd $WORK_DIR
check_for python
check_for npm

# if the program exits before this has been captured then there must have been an error
EXIT_CODE=1

# javascript code generate
npm install
grunt default

TEST_SUITES=('grunt unit')

for TEST in "${TEST_SUITES[@]}"
do
    echo "##### Running test: [$TEST]"
    $TEST
    EXIT_CODE=$?
    echo "##### Test [$TEST] ended with status [$EXIT_CODE]"
    if  [ ${EXIT_CODE} != 0 ]
    then
        cd -
        exit ${EXIT_CODE}
    fi
done

cd -
exit 0
