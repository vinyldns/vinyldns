#!/bin/bash

CURDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

function usage {
    printf "usage: build.sh [OPTIONS]\n\n"
    printf "builds vinyldns artifacts locally\n\n"
    printf "options:\n"
    printf "\t-b, --build: indicates a fresh build or attempt to work with existing images\n"
    printf "\t-p, --push: indicates docker will push to the repository\n"
    printf "\t-r, --repository [REPOSITORY]: the docker repository where this image will be pushed\n"
    printf "\t-t, --tag [TAG]: sets the qualifier for the semver version\n"
}

# Default the build to -SNAPSHOT if not set
BUILD_TAG="-SNAPSHOT"
REPOSITORY="docker.io"
DOCKER_PUSH=0
DO_BUILD=0

while [ "$1" != "" ]; do
	case "$1" in
    -b | --build  	) DO_BUILD=1;  shift;;
    -p | --push  	) DOCKER_PUSH=1;  shift;;
		-r | --repository  	) REPOSITORY="$2";  shift 2;;
		-t | --tag  	) BUILD_TAG="-b$2";  shift 2;;
		* ) usage; exit;;
	esac
done

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

if [ $DO_BUILD -eq 1 ]; then
  ./build.sh --version "${VINYLDNS_VERSION}"

  if [ $? -eq 0 ]; then
    set -x
    docker tag vinyldns/test-bind9:$VINYLDNS_VERSION $REPOSITORY/vinyldns/test-bind9:$VINYLDNS_VERSION
    docker tag vinyldns/test:$VINYLDNS_VERSION $REPOSITORY/vinyldns/test:$VINYLDNS_VERSION
    docker tag vinyldns/api:$VINYLDNS_VERSION $REPOSITORY/vinyldns/api:$VINYLDNS_VERSION
    docker tag vinyldns/portal:$VINYLDNS_VERSION $REPOSITORY/vinyldns/portal:$VINYLDNS_VERSION
    set +x
  fi
fi

if [ $DOCKER_PUSH -eq 1 ]; then
  set -x
  docker push $REPOSITORY/vinyldns/test-bind9:$VINYLDNS_VERSION
  docker push $REPOSITORY/vinyldns/test:$VINYLDNS_VERSION
  docker push $REPOSITORY/vinyldns/api:$VINYLDNS_VERSION
  docker push $REPOSITORY/vinyldns/portal:$VINYLDNS_VERSION
  set +x
fi
