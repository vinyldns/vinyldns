#!/usr/bin/env bash
#
# Will run all tests, and if they pass, will push new Docker images for vinyldns/api and vinyldns/portal, and push
# the core module to Maven Central
#
# Necessary environment variables:
#   DOCKER_CONTENT_TRUST_REPOSITORY_PASSPHRASE - passphrase for notary delegation key
#   VINYLDNS_RELEASE - whether the release is a full release or a snapshot: true or false
#
# sbt release will auto-bump version.sbt and make a commit on your local
#

printf "\nnote: follow the guides in MAINTAINERS.md to setup notary delegation (Docker) and get sonatype key (Maven) \n"

DIR=$( cd $(dirname $0) ; pwd -P )
export GPG_TTY=$(tty)

##
# Checking for uncommitted changes (sbt release will fail if so)
##

printf "\nchecking for uncommitted changes... \n"
cd "$DIR" && git add . && git diff-index --quiet HEAD --
if  [ $? != 0 ]; then
    printf "\nerror: attempting to release with uncommitted changes\n"
    exit 1
fi

##
# Checking for environment variables
##

printf "\nchecking for VINYLDNS_RELEASE (true or false)... \n"
if [[ -z "${VINYLDNS_RELEASE}" ]]; then
    printf "\nnote: VINYLDNS_RELEASE not found, release will assume it is a snapshot and not a full release...\n"
fi

printf "\nchecking for notary key passphrase in env... \n"
if [[ -z "${DOCKER_CONTENT_TRUST_REPOSITORY_PASSPHRASE}" ]]; then
    printf "\nerror: DOCKER_CONTENT_TRUST_REPOSITORY_PASSPHRASE must be set in environment\n"
    exit 1
fi

##
# functional tests
##

printf "\nrunning api func tests... \n"
sh "$DIR"/remove-vinyl-containers.sh
cd "$DIR" && sh "$DIR"/func-test-api.sh
if  [ $? != 0 ]; then
    printf "\nerror: bin/func-test-api.sh failed \n"
    exit 1
fi
sh "$DIR"/remove-vinyl-containers.sh

printf "\nrunning portal func tests... \n"
cd "$DIR" && sh "$DIR"/func-test-portal.sh
if  [ $? != 0 ]; then
    printf "\nerror: bin/func-test-portal.sh failed \n"
    exit 1
fi

##
# run release
##

printf "\nrunning sbt release... \n"
cd "$DIR"/../ && sbt release

printf "\nrelease finished \n"
