#!/usr/bin/env bash
DIR=$( cd $(dirname $0) ; pwd -P )

cd $DIR/../

echo "Publishing docker image..."
sbt clean docker:publish
publish_result=$?
cd $DIR
exit ${publish_result}
