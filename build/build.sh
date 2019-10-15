#!/bin/bash

function usage {
    printf "usage: build.sh [OPTIONS]\n\n"
    printf "builds vinyldns artifacts locally\n\n"
    printf "options:\n"
    printf "\t-t, --tag [TAG]: sets the qualifier for the semver version\n"
}

# parse args
TAG=
while [ "$1" != "" ]; do
	case "$1" in
		-t | --tag  	) TAG="$2";  shift;;
		* ) usage; exit;;
	esac
	shift
done

if [ -z "$TAG" ]; then
  # Default to snapshot
  BUILD_TAG="-SNAPSHOT"
else
  BUILD_TAG="-b$TAG"
fi

CURDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BASEDIR=$CURDIR/../

# Calculate the version by using version.sbut, this will pull out something like 0.9.4
V=$(find $BASEDIR -name "version.sbt" | head -n1 | xargs grep "[ \\t]*version in ThisBuild :=" | head -n1 | sed 's/.*"\(.*\)".*/\1/')
echo "VERSION IS ${V}..."
if [[ "$V" == *-SNAPSHOT ]]
then
  export VINYLDNS_VERSION="${V%?????????}${BUILD_TAG}"
else
  export VINYLDNS_VERSION="$V${BUILD_TAG}"
fi

echo "BUILDING $VINYLDNS_VERSION..."
set -x

# Builds the images
docker-compose -f $CURDIR/docker/docker-compose.yml build \
  --no-cache \
  --parallel \
  --build-arg VINYLDNS_VERSION="$VINYLDNS_VERSION" \
  --build-arg BRANCH="master"
