#!/bin/bash

CURDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker-compose -f $CURDIR/docker/docker-compose.yml down