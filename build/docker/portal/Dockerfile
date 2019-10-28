FROM hseeberger/scala-sbt:8u212_1.2.8_2.12.9 as builder

ARG BRANCH=master
ARG VINYLDNS_VERSION

RUN git clone -b ${BRANCH} --single-branch --depth 1 https://github.com/vinyldns/vinyldns.git /vinyldns

# The default jvmopts are huge, meant for running everything, use a paired down version
COPY .jvmopts /vinyldns

# Needed for preparePortal
RUN apt-get update \
    && apt-get install -y \
    apt-transport-https \
    curl \
    gnupg \
    && curl -sL https://deb.nodesource.com/setup_6.x | bash - \
    && apt-get install -y nodejs \
    && npm install -g grunt-cli

RUN cd /vinyldns ; sbt "set version in ThisBuild := \"${VINYLDNS_VERSION}\"" portal/preparePortal universal:packageZipTarball

FROM openjdk:8u201-jdk-alpine3.9

RUN apk add --update --no-cache netcat-openbsd bash

COPY --from=builder /vinyldns/modules/portal/target/universal/portal.tgz /

RUN mkdir -p /opt && \
    tar -xzvf /portal.tgz && \
    mv /portal /opt/docker && \
    mkdir -p /opt/docker/lib_extra

# This will set the vinyldns version, make sure to have this in config... version = ${?VINYLDNS_VERSION}
ARG VINYLDNS_VERSION
ENV VINYLDNS_VERSION=$VINYLDNS_VERSION

# Mount the volume for config file and lib extras
# Note: These volume names are used in the build.sbt
VOLUME ["/opt/docker/lib_extra/", "/opt/docker/conf"]

EXPOSE 9000

ENTRYPOINT ["/opt/docker/bin/portal"]
