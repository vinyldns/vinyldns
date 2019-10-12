#!/bin/bash

CURDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

function usage {
    printf "usage: test.sh [OPTIONS]\n\n"
    printf "builds vinyldns artifacts locally\n\n"
    printf "options:\n"
    printf "\t-v, --version [VERSION]: the vinyldns version to test, e.g. 0.9.4-b123\n"
}

# parse args
VERSION=
while [ "$1" != "" ]; do
	case "$1" in
		-v | --version  	) VERSION="$2";  shift;;
		* ) usage; exit;;
	esac
	shift
done

set -x
docker-compose -f $CURDIR/docker/test/docker-compose.yml up -d
