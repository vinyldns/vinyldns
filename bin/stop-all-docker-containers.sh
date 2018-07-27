#!/bin/bash
echo "Shutting down docker"

docker kill $(docker ps -a -q) || echo "No docker containers to kill"
docker rm -v $(docker ps -a -q) || echo "No docker volumes to remove"
docker network prune -f
