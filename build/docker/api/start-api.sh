#!/bin/bash
set -euo pipefail

if [ "${WAIT_FOR_LOCALSTACK:-false}" = "true" ]; then
  echo "Waiting for LocalStack (SQS) on vinyldns-integration:9003..."
  for i in $(seq 1 60); do
    if curl -sk https://vinyldns-integration:9003 >/dev/null 2>&1; then
      echo "LocalStack is up after $i checks."
      break
    fi
    echo "LocalStack not ready ($i), sleeping 2s..."
    sleep 2
  done
fi

exec java $JVM_OPTS -Dconfig.file=/opt/vinyldns/conf/application.conf \
  -Dlog4j.configurationFile=/opt/vinyldns/conf/log4j2.xml \
  -Dvinyldns.version=$(cat /opt/vinyldns/version) \
  -cp /opt/vinyldns/lib_extra/*:/opt/vinyldns/vinyldns-api.jar \
  vinyldns.api.Boot


