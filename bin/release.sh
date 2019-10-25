#!/usr/bin/env bash

function usage() {
  printf "usage: release.sh [OPTIONS]\n\n"
  printf "builds and releases vinyldns artifacts\n\n"
  printf "options:\n"
  printf "\t-b, --bump [BUMP]: what to bump: major | minor | patch; default is patch\n"
}

BUMP="patch"
while [ "$1" != "" ]; do
  case "$1" in
  -b | --bump)
    BUMP="$2"
    shift 2
    ;;
  *)
    usage
    exit
    ;;
  esac
done

if [[ "$BUMP" == "major" ]]; then
  BUMP="sbtrelease.Version.Bump.Major"
elif [[ "$BUMP" == "minor" ]]; then
  BUMP="sbtrelease.Version.Bump.Minor"
else
  BUMP="sbtrelease.Version.Bump.Bugfix"
fi

printf "\nnote: follow the guides in MAINTAINERS.md to setup notary delegation (Docker) and get sonatype key (Maven) \n"

# If we are not in the main repository then fail fast
REMOTE_REPO=$(git config --get remote.origin.url)
#echo "REMOTE REPO IS $REMOTE_REPO"
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
# running tests
##
#printf "\nrunning api func tests... \n"
#"$DIR"/remove-vinyl-containers.sh
#if ! "$DIR"/func-test-api.sh
#then
#    printf "\nerror: bin/func-test-api.sh failed \n"
#    exit 1
#fi
#"$DIR"/remove-vinyl-containers.sh
#
#printf "\nrunning portal func tests... \n"
#if ! "$DIR"/func-test-portal.sh
#then
#    printf "\nerror: bin/func-test-portal.sh failed \n"
#    exit 1
#fi
#
#printf "\nrunning verify... \n"
#if ! "$DIR"/verify.sh
#then
#    printf "\nerror: bin/verify.sh failed \n"
#    exit 1
#fi

##
# run release
##
# First, run the docker image release as those need to be out before running SBT as SBT bumps version
V=$(find $CURDIR/target -name "version.sbt" | head -n1 | xargs grep "[ \\t]*version in ThisBuild :=" | head -n1 | sed 's/.*"\(.*\)".*/\1/')
VERSION="$V"
if [[ "$V" == *-SNAPSHOT ]]; then
  VERSION="${V%?????????}"
fi

printf "\nrunning sbt release bumping $BUMP... \n"
cd "$DIR"/../ && sbt "set releaseVersionBump := sbtrelease.Version.Bump.Major" "release with-defaults"

printf "\nrelease finished \n"
