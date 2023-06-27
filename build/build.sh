#!/usr/bin/env bash
set -euo pipefail
echo "Building and Publishing artifacts to Dev For all PR's"
cp resource-vinyldns-docker/base-build/runtime/write_tty.py /usr/bin/
cp resource-vinyldns-docker/base-build/runtime/*.sh /usr/bin/
cd resource-vinyldns
apt update && apt install -y ca-certificates gnupg curl && \
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list && \
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add && \
apt update && \
SBT_VERSION=$(awk -F= '{print $2}' project/build.properties) && \
apt install -y sbt=${SBT_VERSION} git python3.9 python3-pip python3-venv python3-wheel python-is-python3 openjdk-8-jdk-headless vim gcc netcat iproute2 iputils-ping && \
rm /etc/apt/sources.list.d/sbt* && \
apt autoremove --purge  && \
apt clean && \
rm -rf /var/lib/apt/lists/*  && \
git config --global core.fileMode false && \
sbt "update" && \
mv /usr/bin/sbt /usr/bin/sbt.orig && mv /usr/bin/sbt_wrapper.sh /usr/bin/sbt && \
chmod 755 /usr/bin/write_tty.py && \
chmod 755 /usr/bin/*.sh && \
cd / && rm -rf /build && mkdir /build
env JAVA_OPTS="${JAVA_OPTS} -Xmx2G -Xms512M -Xss2M -XX:MaxMetaspaceSize=2G"
env SBT_OPTS="-Xmx2G -Xms512M -Xss2M -XX:MaxMetaspaceSize=2G" \
sbt -Dbuild.scalafmtOnCompile=false -Dbuild.lintOnCompile=fase ";project api;coverageOff;assembly" \


