#!/usr/bin/env bash
#
# This script with kill and remove containers associated
# with VinylDNS
#
# Note: this will not remove the actual images from your
# machine, just the running containers

ALL_IDS=$(docker ps -a | grep -e 'vinyldns' -e 'flaviovs/mock-smtp'  | awk '{print $1}')
if [ "${ALL_IDS}" == "" ]; then
  echo "Nothing to remove"
  exit 0
fi

RUNNING_IDS=$(docker ps | grep -e 'vinyldns' -e 'flaviovs/mock-smtp' | awk '{print $1}')
if [ "${RUNNING_IDS}" != "" ]; then
  echo "Killing running containers..."
  echo "${RUNNING_IDS}" | xargs docker kill
fi

ALL_IDS=$(docker ps -a | grep -e 'vinyldns' -e 'flaviovs/mock-smtp'  | awk '{print $1}')
if [ "${ALL_IDS}" != "" ]; then
  echo "Removing containers..."
  echo "${ALL_IDS}" | xargs docker rm -v
fi

docker network prune -f
