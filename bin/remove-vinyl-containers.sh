#!/usr/bin/env bash
#
# The local vinyldns setup used for testing relies on the
# following docker images:
#   mysql:5.7
#   cnadiminti/dynamodb-local:2017-02-16
#   s12v/elasticmq:0.13.8
#   vinyldns/bind9
#   vinyldns/api
#   vinyldns/portal
#
# This script with kill and remove containers associated
# with these names and/or tags
#
# Note: this will not remove the actual images from your
# machine, just the running containers

IDS=$(docker ps -a | grep -e 'mysql:5.7' -e 'cnadiminti/dynamodb-local:2017-02-16' -e 's12v/elasticmq:0.13.8' -e 'vinyldns' -e 'flaviovs/mock-smtp' | awk '{print $1}')

echo "killing..."
echo $(echo "$IDS" | xargs -I {} docker kill {})
echo

echo "removing..."
echo $(echo "$IDS" | xargs -I {} docker rm -v {})
echo

echo "pruning network..."
docker network prune -f
