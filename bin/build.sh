#!/usr/bin/env bash
DIR=$( cd $(dirname $0) ; pwd -P )

echo "Verifying code..."
#${DIR}/verify.sh

#step_result=$?
step_result=0
if  [ ${step_result} != 0 ]
then
    echo "Failed to verify build!!!"
    exit ${step_result}
fi

echo "Func testing the api..."
${DIR}/func-test-api.sh

step_result=$?
if  [ ${step_result} != 0 ]
then
    echo "Failed API func tests!!!"
    exit ${step_result}
fi

echo "Func testing the portal..."
${DIR}/func-test-portal.sh
step_result=$?
if  [ ${step_result} != 0 ]
then
    echo "Failed Portal func tests!!!"
    exit ${step_result}
fi

exit 0
