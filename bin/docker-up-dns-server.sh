#!/bin/bash
DIR=$( cd $(dirname $0) ; pwd -P )

echo "Starting ONLY the bind9 server.  To start an api server use the api server script"
docker-compose -f $DIR/../docker/docker-compose-func-test.yml --project-directory $DIR/../docker up -d bind9
