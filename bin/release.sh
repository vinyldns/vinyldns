#!/usr/bin/env bash
#
# Will run all tests, and if they pass, will push new Docker images for vinyldns/api and vinyldns/portal, and push
# the core module to Maven Central
#
# Necessary environment variables:
#   DOCKER_CONTENT_TRUST_REPOSITORY_PASSPHRASE - passphrase for notary delegation key
#
# sbt release will auto-bump version.sbt and make a commit on your local
#

printf "\nnote: follow the guides in MAINTAINERS.md to setup notary delegation (Docker) and get sonatype key (Maven) \n"

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
if [[ -z "${DOCKER_CONTENT_TRUST_REPOSITORY_PASSPHRASE}" ]]; then
    printf "\nerror: DOCKER_CONTENT_TRUST_REPOSITORY_PASSPHRASE must be set in environment\n"
    exit 1
fi

##
# functional tests
##

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

##
# run release
##

printf "\nrunning sbt release... \n"
cd "$DIR"/../ && sbt release

printf "\nrelease finished \n"
