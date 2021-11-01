FROM vinyldns/build:base-build-portal as builder
ARG VINYLDNS_VERSION="0.0.0-local-dev"

COPY . /vinyldns

WORKDIR /vinyldns
RUN cp /build/node_modules.tar.xz /vinyldns/modules/portal && \
    cd /vinyldns/modules/portal && tar Jxf node_modules.tar.xz && \
    cd /vinyldns

RUN sbt "set version in ThisBuild := \"${VINYLDNS_VERSION}\"; project portal; preparePortal"
RUN sbt "set version in ThisBuild := \"${VINYLDNS_VERSION}\"; project portal; universal:packageZipTarball"

FROM adoptopenjdk/openjdk11:jdk-11.0.8_10-alpine

RUN apk add --update --no-cache bash

COPY --from=builder /vinyldns/modules/portal/target/universal/portal.tgz /

RUN mkdir -p /opt && \
    tar -xzf /portal.tgz && \
    mv /portal /opt/vinyldns && \
    mkdir -p /opt/vinyldns/lib_extra

# This will set the vinyldns version, make sure to have this in config... version = ${?VINYLDNS_VERSION}
ENV VINYLDNS_VERSION=$VINYLDNS_VERSION

# Mount the volume for config file and lib extras
# Note: These volume names are used in the build.sbt
VOLUME ["/opt/vinyldns/lib_extra/", "/opt/vinyldns/conf"]

EXPOSE 9001

ENTRYPOINT ["/opt/vinyldns/bin/portal"]
