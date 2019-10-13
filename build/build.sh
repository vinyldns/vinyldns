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
		-c | --clean    	) CLEAN=1;;
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

V=$(find $BASEDIR -name "version.sbt" | head -n1 | xargs grep "[ \\t]*version in ThisBuild :=" | head -n1 | sed 's/.*"\(.*\)".*/\1/')
echo "VERSION IS ${V}..."
if [[ "$V" == *-SNAPSHOT ]]
then
  export VINYLDNS_VERSION="${V%?????????}${BUILD_TAG}"
else
  export VINYLDNS_VERSION="$V${BUILD_TAG}"
fi

echo "ENSURING DIST DIR..."
DISTDIR=$CURDIR/dist
mkdir -p $DISTDIR

echo "BUILDING $VINYLDNS_VERSION..."
API_TAG="vinyldns/api:$VINYLDNS_VERSION"
PORTAL_TAG="vinyldns/portal:$VINYLDNS_VERSION"
BIND_TAG="vinyldns/test-bind9:$VINYLDNS_VERSION"

# TODO: MAKE SURE TO USE NO-CACHE ON ALL OF THESE WHEN READY !!!
# TODO: USE DOCKER COMPOSE FOR BUILD AND BUILD IN PARALLEL !!!
set -x
#docker build -t $API_TAG $CURDIR/docker/api \
#  --build-arg BUILD_VERSION="$VINYLDNS_VERSION"

#docker build --no-cache -t $PORTAL_TAG $CURDIR/docker/portal \
#  --build-arg BUILD_VERSION="$VINYLDNS_VERSION"

docker build --no-cache -t $BIND_TAG $CURDIR/docker/test-bind9 \
  --build-arg BUILD_VERSION="$VINYLDNS_VERSION"