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

FROM adoptopenjdk/openjdk11:jdk-11.0.8_10-alpine
ARG DOCKER_FILE_PATH
ARG VINYLDNS_VERSION

RUN test -n "VINYLDNS_VERSION" || (echo "VINYLDNS_VERSION  not set" && false) && \
    test -n "DOCKER_FILE_PATH" || (echo "DOCKER_FILE_PATH  not set" && false) && \
    apk add --update --no-cache bash && \
    mkdir -p /opt/vinyldns/lib_extra && \
    echo "${VINYLDNS_VERSION}" > /opt/vinyldns/version

COPY --from=base-build /opt/vinyldns /opt/vinyldns
COPY ${DOCKER_FILE_PATH}/application.conf /opt/vinyldns/conf
COPY ${DOCKER_FILE_PATH}/logback.xml /opt/vinyldns/conf


VOLUME ["/opt/vinyldns/lib_extra/", "/opt/vinyldns/conf/"]

EXPOSE 9001

ENV JVM_OPTS=""
ENTRYPOINT ["/bin/bash","-c", "java ${JVM_OPTS} -Dvinyldns.version=$(cat /opt/vinyldns/version) \
                               -Dlogback.configurationFile=/opt/vinyldns/conf/logback.xml \
                               -Dconfig.file=/opt/vinyldns/conf/application.conf \
                               -cp /opt/vinyldns/conf:/opt/vinyldns/lib/*:/opt/vinyldns/lib_extra/* \
                               play.core.server.ProdServerStart"]
