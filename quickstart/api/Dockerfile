# Build VinylDNS API if the JAR doesn't already exist
ARG VINYLDNS_VERSION=latest
FROM vinyldns/build:base-build as base-build
ARG DOCKER_FILE_PATH
COPY . /build/
WORKDIR /build

##  Run the build if we don't already have a vinyldns.jar
RUN mkdir -p /opt/vinyldns/conf && \
    if [ -f assembly/vinyldns.jar ]; then cp assembly/vinyldns.jar /opt/vinyldns/; fi && \
    if [ ! -f /opt/vinyldns/vinyldns.jar ]; then \
      env SBT_OPTS="-XX:+UseConcMarkSweepGC -Xmx4G -Xms1G" \
      sbt -Dbuild.scalafmtOnCompile=false -Dbuild.lintOnCompile=fase ";project api;coverageOff;assembly" \
      && cp assembly/vinyldns.jar /opt/vinyldns/; \
    fi

FROM adoptopenjdk/openjdk11:jdk-11.0.8_10-alpine

RUN apk add --update --no-cache bash

COPY --from=base-build /opt/vinyldns /opt/vinyldns

# This will set the vinyldns version, make sure to have this in config... version = ${?VINYLDNS_VERSION}
ARG VINYLDNS_VERSION="0.0.0-local-dev"
ENV VINYLDNS_VERSION=$VINYLDNS_VERSION

RUN mkdir -p /opt/vinyldns/lib_extra

# Mount the volume for config file and lib extras
# Note: These volume names are used in the build.sbt
VOLUME ["/opt/vinyldns/lib_extra/", "/opt/vinyldns/conf"]

EXPOSE 9000

ENTRYPOINT ["java", "-Dconfig.file=/opt/vinyldns/conf/vinyldns.conf", \
                    "-Dlogback.configurationFile=/opt/vinyldns/conf/logback.xml",\
                    "-Dvinyldns.base-version=${VINYLDNS_VERSION}",\
                    "-cp", "/opt/vinyldns/lib_extra/*",\
                    "-jar", "/opt/vinyldns/vinyldns.jar"]
