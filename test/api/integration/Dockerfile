ARG VINYLDNS_BASE_VERSION=latest

# Build VinylDNS API if the JAR doesn't already exist
FROM vinyldns/build:base-build as base-build
ARG DOCKERFILE_PATH="test/api/integration"
COPY "${DOCKERFILE_PATH}/application.conf" /opt/vinyldns/conf/
COPY . /build/
WORKDIR /build

##  Run the build if we don't already have a vinyldns-api.jar
ARG SKIP_API_BUILD="false"
RUN if [ -f artifacts/vinyldns-api.jar ]; then cp artifacts/vinyldns-api.jar /opt/vinyldns; fi && \
    if [ ! -f /opt/vinyldns/vinyldns-api.jar ] && [ "$SKIP_API_BUILD" == "false" ]; then \
      env SBT_OPTS="-Xmx2G -Xms512M -Xss2M -XX:MaxMetaspaceSize=2G" \
      sbt -Dbuild.scalafmtOnCompile=false -Dbuild.lintOnCompile=false ";project api;coverageOff;assembly" \
      && cp artifacts/vinyldns-api.jar /opt/vinyldns/; \
    fi

# Build the testing image, copying data from `base-build`
FROM vinyldns/build:base-test-integration-${VINYLDNS_BASE_VERSION}
SHELL ["/bin/bash","-c"]
ARG DOCKERFILE_PATH
COPY --from=base-build /opt/vinyldns /opt/vinyldns

# Copy the project contents
COPY . /build/
WORKDIR /build

# Local bind server files
COPY quickstart/bind9/etc/named.conf.* /etc/bind/
COPY quickstart/bind9/zones/ /var/bind/
RUN named-checkconf

ENV RUN_SERVICES="all"
