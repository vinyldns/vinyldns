#!/bin/bash
######################################################################
# Copies the contents of `docker` into target/scala-2.12
# to start up dependent services via docker compose.  Once
# dependent services are started up, the fat jar built by sbt assembly
# is loaded into a docker container.  Finally, the func tests run inside
# another docker container
# At the end, we grab all the logs and place them in the target
# directory
######################################################################

DIR=$( cd $(dirname $0) ; pwd -P )
WORK_DIR=$DIR/../target/scala-2.12
mkdir -p $WORK_DIR

echo "Cleaning up unused networks..."
docker network prune -f

echo "Copy all docker to the target directory so we can start up properly and the docker context is small..."
cp -af $DIR/../docker $WORK_DIR/

echo "Copy over the functional tests as well as those that are run in a container..."
mkdir -p $WORK_DIR/functest
rsync -av --exclude='.virtualenv' $DIR/../modules/api/functional_test $WORK_DIR/docker/functest

echo "Copy the vinyldns.jar to the api docker folder so it is in context..."
if [[ ! -f $DIR/../modules/api/target/scala-2.12/vinyldns.jar ]]; then
    echo "vinyldns jar not found, building..."
    cd $DIR/../
    sbt api/clean api/assembly
    cd $DIR
fi
cp -f $DIR/../modules/api/target/scala-2.12/vinyldns.jar $WORK_DIR/docker/api

echo "Starting docker environment and running func tests..."

# If PAR_CPU is unset; default to auto
if [ -z "${PAR_CPU}" ]; then
  export PAR_CPU=auto
fi

docker-compose -f $WORK_DIR/docker/docker-compose-func-test.yml --project-directory $WORK_DIR/docker --log-level ERROR up --build --exit-code-from functest
test_result=$?

echo "Grabbing the logs..."

docker logs vinyldns-api > $DIR/../target/vinyldns-api.log 2>/dev/null
docker logs vinyldns-bind9 > $DIR/../target/vinyldns-bind9.log 2>/dev/null
docker logs vinyldns-mysql > $DIR/../target/vinyldns-mysql.log 2>/dev/null
docker logs vinyldns-elasticmq > $DIR/../target/vinyldns-elasticmq.log 2>/dev/null
docker logs vinyldns-functest > $DIR/../target/vinyldns-functest.log 2>/dev/null

echo "Cleaning up docker containers..."
$DIR/./remove-vinyl-containers.sh

echo "Func tests returned result: ${test_result}"
exit ${test_result}
