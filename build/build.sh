#!/bin/bash

CURDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

function usage {
    printf "usage: build.sh [OPTIONS]\n\n"
    printf "builds vinyldns artifacts locally\n\n"
    printf "options:\n"
    printf "\t-b, --branch [BRANCH]: the git branch or tag to build\n"
    printf "\t-v, --version [VERSION]: the version given to this build\n"
}

# parse args
VINYLDNS_VERSION=
BRANCH="master"
while [ "$1" != "" ]; do
	case "$1" in
		-b | --branch  	) BRANCH="$2";  shift;;
		-v | --version  	) VINYLDNS_VERSION="$2";  shift;;
		* ) usage; exit 1;;
	esac
	shift
done

export VINYLDNS_VERSION=$VINYLDNS_VERSION
echo "BUILDING $VINYLDNS_VERSION..."

# Builds the images
docker-compose -f $CURDIR/docker/docker-compose.yml build \
  --no-cache \
  --parallel \
  --build-arg VINYLDNS_VERSION="$VINYLDNS_VERSION" \
  --build-arg BRANCH="${BRANCH}"

if [ ? -eq 0 ]; then
  # Runs smoke tests to make sure the new images are sound
  docker-compose -f $CURDIR/docker/docker-compose.yml --log-level ERROR up --exit-code-from functest
fi


