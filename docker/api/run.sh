#!/usr/bin/env bash

# gets the docker-ized ip address, sets it to an environment variable
export APP_HOST=`ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -f1 -d'/'`

export DYNAMO_ADDRESS="vinyldns-dynamodb"
export DYNAMO_PORT=8000
export JOURNAL_HOST="vinyldns-dynamodb"
export JOURNAL_PORT=8000
export MYSQL_ADDRESS="vinyldns-mysql"
export MYSQL_PORT=3306
export JDBC_USER=root
export JDBC_PASSWORD=pass
export DNS_ADDRESS="vinyldns-bind9"
export DYNAMO_KEY="local"
export DYNAMO_SECRET="local"
export DYNAMO_TABLE_PREFIX=""
export ELASTICMQ_ADDRESS="vinyldns-elasticmq"
export DYNAMO_ENDPOINT="http://${DYNAMO_ADDRESS}:${DYNAMO_PORT}"
export JDBC_URL="jdbc:mariadb://${MYSQL_ADDRESS}:${MYSQL_PORT}/vinyldns?user=${JDBC_USER}&password=${JDBC_PASSWORD}"
export JDBC_MIGRATION_URL="jdbc:mariadb://${MYSQL_ADDRESS}:${MYSQL_PORT}/?user=${JDBC_USER}&password=${JDBC_PASSWORD}"

# wait until mysql is ready...
echo 'Waiting for MYSQL to be ready...'
DATA=""
RETRY=40
SLEEP_DURATION=1
while [ "$RETRY" -gt 0 ]
do
    DATA=$(nc -vzw1 vinyldns-mysql 3306)
    if [ $? -eq 0 ]
    then
        break
    else
        echo "Retrying" >&2

        let RETRY-=1
        sleep "$SLEEP_DURATION"

        if [ "$RETRY" -eq 0 ]
        then
          echo "Exceeded retries waiting for MYSQL to be ready, failing"
          return 1
        fi
    fi
done

echo "Starting up Vinyl..."
sleep 2
java -Djava.net.preferIPv4Stack=true -Dconfig.file=/app/docker.conf -Dakka.loglevel=INFO -Dlogback.configurationFile=test/logback.xml -jar /app/vinyldns-server.jar vinyldns.api.Boot

