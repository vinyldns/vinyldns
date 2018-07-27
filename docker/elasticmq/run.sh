#!/usr/bin/env bash

# gets the docker-ized ip address, sets it to an environment variable
export APP_HOST=`ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -f1 -d'/'`

echo "APP HOST = ${APP_HOST}"

java -Djava.net.preferIPv4Stack=true -Dconfig.file=/elasticmq/custom.conf -jar /elasticmq/server.jar
