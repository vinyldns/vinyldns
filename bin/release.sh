#!/usr/bin/env bash
#
# Will run all tests, and if they pass, will push new Docker images for vinyldns/api and vinyldns/portal, and push
# the core module to Maven Central
#
# Necessary environment variables:
#   DOCKER_CONTENT_TRUST_REPOSITORY_PASSPHRASE - passphrase for notary delegation key
#   VINYLDNS_RELEASE_TYPE - type of release, (comcast, public-release, or public-snapshot), defaults to public-snapshot
#       comcast: will push docker images and core module to comcast internal artifactory
#       public-release: will push docker images to Docker Hub, and core module to Maven Central
#       public-snapshot (default): will push docker images to Docker Hub, and core module to sonatype staging repo
#
# sbt release will auto-bump version.sbt and make a commit. Make a pr with that commit after the release
#

printf "\n note: follow the guides in MAINTAINERS.md to setup notary delegation (Docker) and get sonatype key (Maven) \n"

##
# Checking for passphrases to signing keys in environment
##

printf "\n checking for notary key passphrase in env... \n"
if [[ -z "${DOCKER_CONTENT_TRUST_REPOSITORY_PASSPHRASE}" ]]; then
    printf "\n error: DOCKER_CONTENT_TRUST_REPOSITORY_PASSPHRASE must be set in environment \n"
    exit 1
fi

printf "\n checking for release type in env (comcast, public-release, or public-snapshot)... \n"
if [[ -z "${VINYLDNS_RELEASE_TYPE}" ]]; then
    printf "\n note: VINYLDNS_RELEASE_TYPE not found, default to public-snapshot \n"
fi

DIR=$( cd $(dirname $0) ; pwd -P )

##
# functional tests
##

printf "\n running api func tests... \n"
sh "$DIR"/remove-vinyl-containers.sh
cd "$DIR" && sh "$DIR"/func-test-api.sh
if  [ $? != 0 ]; then
    printf "\n error: bin/func-test-api.sh failed \n"
    exit 1
fi
sh "$DIR"/remove-vinyl-containers.sh

printf "\n running portal func tests... \n"
cd "$DIR" && sh "$DIR"/func-test-portal.sh
if  [ $? != 0 ]; then
    printf "\n error: bin/func-test-portal.sh failed \n"
    exit 1
fi

##
# run release
##

printf "\n running sbt release... \n"
cd "$DIR"/../ && sbt release

printf "\n release finished \n"
