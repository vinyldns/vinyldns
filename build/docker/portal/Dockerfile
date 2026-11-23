FROM vinyldns/build:base-build-portal as base-build
ARG VINYLDNS_VERSION
COPY . /build
WORKDIR /build

RUN mkdir -p /opt/vinyldns/conf && \
    if [ -f artifacts/vinyldns-portal.zip ]; then \
      unzip artifacts/vinyldns-portal.zip -d /opt/vinyldns && \
      mv /opt/vinyldns/vinyldns-portal/{lib,share,conf} /opt/vinyldns && \
      rm -rf /opt/vinyldns/vinyldns-portal*; \
     fi && \
     if [ ! -f /opt/vinyldns/lib/vinyldns.portal*.jar ]; then \
       cp /build/node_modules.tar.xz /build/modules/portal && \
       cd /build/modules/portal && tar Jxf node_modules.tar.xz && \
       cd /build && \
       modules/portal/prepare-portal.sh && \
       sbt "set version in ThisBuild := \"${VINYLDNS_VERSION}\"; project portal; dist" && \
       unzip artifacts/vinyldns-portal.zip -d /opt/vinyldns && \
       mv /opt/vinyldns/vinyldns-portal/{lib,share,conf} /opt/vinyldns && \
       rm -rf /opt/vinyldns/vinyldns-portal*; \
    fi

# Temurin 25.0.5.1+2 includes the fix for CVE-2026-114938.
FROM eclipse-temurin:25@sha256:ce2ef9d2d67a68de5e196d4de9d743327c44a10b57fe02ec39f572c652cacfd3
ARG DOCKER_FILE_PATH
ARG VINYLDNS_VERSION

RUN test -n "VINYLDNS_VERSION" || (echo "VINYLDNS_VERSION  not set" && false) && \
    test -n "DOCKER_FILE_PATH" || (echo "DOCKER_FILE_PATH  not set" && false) && \
    mkdir -p /opt/vinyldns/lib_extra && \
    echo "${VINYLDNS_VERSION}" > /opt/vinyldns/version && \
    apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates && \
    rm -rf /var/lib/apt/lists/*

COPY --from=base-build /opt/vinyldns /opt/vinyldns
COPY ${DOCKER_FILE_PATH}/application.conf /opt/vinyldns/conf
COPY ${DOCKER_FILE_PATH}/log4j2.xml /opt/vinyldns/conf


VOLUME ["/opt/vinyldns/lib_extra/", "/opt/vinyldns/conf/"]

EXPOSE 9001

ENV JVM_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
ENV INIT_SCRIPT="/bin/true"
ENTRYPOINT ["/bin/bash","-c", "${INIT_SCRIPT} && java ${JVM_OPTS} -Dvinyldns.version=$(cat /opt/vinyldns/version) \
                               -Dlog4j.configurationFile=/opt/vinyldns/conf/log4j2.xml \
                               -Dconfig.file=/opt/vinyldns/conf/application.conf \
                               -cp /opt/vinyldns/conf:/opt/vinyldns/lib/*:/opt/vinyldns/lib_extra/* \
                               play.core.server.ProdServerStart"]
