#!/usr/bin/env bash

printf "\nnote: follow the guides in MAINTAINERS.md to setup notary delegation (Docker) and get sonatype key (Maven) \n"

DIR=$( cd $(dirname $0) ; pwd -P )

# gpg sbt plugin fails if this is not set
export GPG_TTY=$(tty)

##
# running tests
##
if [ "$1" != "skip-tests" ]; then
  # Checking for uncommitted changes
  printf "\nchecking for uncommitted changes... \n"
  if ! (cd "$DIR" && git add . && git diff-index --quiet HEAD --)
  then
      printf "\nerror: attempting to release with uncommitted changes\n"
      exit 1
  fi
  # If we are not in the main repository then fail fast
  REMOTE_REPO=$(git config --get remote.origin.url)
  echo "REMOTE REPO IS $REMOTE_REPO"
  if [[ "$REMOTE_REPO" != *-vinyldns/vinyldns.git ]]; then
    printf "\nCannot run a release from this repository as it is not the main repository: $REMOTE_REPO \n"
    exit 1
  fi

  # If we are not on the master branch,then fail fast
  BRANCH=$(git rev-parse --abbrev-ref HEAD)
  if [[ "$BRANCH" != "master" ]]; then
    printf "\nCannot run a release from this branch: $BRANCH is not master \n"
    exit 1;
  fi

  printf "\nrunning api func tests... \n"
  if ! "$DIR"/func-test-api.sh
  then
      printf "\nerror: bin/func-test-api.sh failed \n"
      exit 1
  fi

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
fi

##
# run release
##
cd "$DIR"/../ && sbt release && cd $DIR

printf "\nrelease finished \n"
