#!/usr/bin/env bash
#
# Will run all tests, and if they pass, will push new Docker images for vinyldns/api and vinyldns/portal, and push
# the core module to Maven Central
#
# Command line args:
#   skip-tests: skips functional, unit, and integration tests
#
# Necessary environment variables:
#   DOCKER_CONTENT_TRUST_REPOSITORY_PASSPHRASE: passphrase for notary delegation key
#
# sbt release will auto-bump version.sbt and make a commit on your local
#

printf "\nnote: follow the guides in MAINTAINERS.md to setup notary delegation (Docker) and get sonatype key (Maven) \n"

# If we are not in the main repository then fail fast
REMOTE_REPO=$(git config --get remote.origin.url)
echo "REMOTE REPO IS $REMOTE_REPO"
#if [[ "$REMOTE_REPO" != *-vinyldns/vinyldns.git ]]; then
#  printf "\nCannot run a release from this repository as it is not the main repository: $REMOTE_REPO \n"
#  exit 1
#fi

BRANCH=$(git rev-parse --abbrev-ref HEAD)
#if [[ "$BRANCH" != "master" ]]; then
#  printf "\nCannot run a release from this branch: $BRANCH is not master \n"
#  exit 1;
#fi

DIR=$( cd $(dirname $0) ; pwd -P )

# gpg sbt plugin fails if this is not set
export GPG_TTY=$(tty)

# force image signing
export DOCKER_CONTENT_TRUST=1

##
# Checking for uncommitted changes
##

printf "\nchecking for uncommitted changes... \n"
if ! (cd "$DIR" && git add . && git diff-index --quiet HEAD --)
then
    printf "\nerror: attempting to release with uncommitted changes\n"
    exit 1
fi

##
# Checking for environment variables
##

printf "\nchecking for notary key passphrase in env... \n"
#if [[ -z "${DOCKER_CONTENT_TRUST_REPOSITORY_PASSPHRASE}" ]]; then
#    printf "\nerror: DOCKER_CONTENT_TRUST_REPOSITORY_PASSPHRASE must be set in environment\n"
#    exit 1
#fi

##
# running tests
##

if [ "$1" != "skip-tests" ]; then
    printf "\nrunning api func tests... \n"
    "$DIR"/remove-vinyl-containers.sh
    if ! "$DIR"/func-test-api.sh
    then
        printf "\nerror: bin/func-test-api.sh failed \n"
        exit 1
    fi
    "$DIR"/remove-vinyl-containers.sh

    printf "\nrunning portal func tests... \n"
    if ! "$DIR"/func-test-portal.sh
    then
        printf "\nerror: bin/func-test-portal.sh failed \n"
        exit 1
    fi

    printf "\nrunning verify... \n"
    if ! "$DIR"/verify.sh
    then
        printf "\nerror: bin/verify.sh failed \n"
        exit 1
    fi
else
    printf "\nskipping tests... \n"
fi

##
# run release
##
# First, run the docker image release as those need to be out before running SBT as SBT bumps version
V=$(find $CURDIR/target -name "version.sbt" | head -n1 | xargs grep "[ \\t]*version in ThisBuild :=" | head -n1 | sed 's/.*"\(.*\)".*/\1/')
VERSION="$V"
if [[ "$V" == *-SNAPSHOT ]]; then
  VERSION="${V%?????????}"
fi

"$DIR"/../build/release.sh --version $VERSION --branch "master" --push --clean

printf "\nrunning sbt release... \n"
cd "$DIR"/../ && sbt "release with-defaults"

printf "\nrelease finished \n"
