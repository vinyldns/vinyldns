# Build VinylDNS API if the JAR doesn't already exist
FROM vinyldns/build:base-build as base-build
ARG DOCKERFILE_PATH="test/api/integration"
COPY "${DOCKERFILE_PATH}/vinyldns.*" /opt/vinyldns/
COPY . /build/
WORKDIR /build

##  Run the build if we don't already have a vinyldns.jar
RUN if [ -f assembly/vinyldns.jar ]; then cp assembly/vinyldns.jar /opt/vinyldns; fi && \
    if [ ! -f /opt/vinyldns/vinyldns.jar ]; then \
      env SBT_OPTS="-XX:+UseConcMarkSweepGC -Xmx4G -Xms1G" \
      sbt -Dbuild.scalafmtOnCompile=false -Dbuild.lintOnCompile=fase ";project api;coverageOff;assembly" \
      && cp modules/api/target/scala-2.12/vinyldns.jar /opt/vinyldns/; \
    fi

# Build the testing image, copying data from `vinyldns-api`
FROM vinyldns/build:base-test-integration
SHELL ["/bin/bash","-c"]
ARG DOCKERFILE_PATH
COPY --from=base-build /opt/vinyldns /opt/vinyldns

# Copy the project contents
COPY . /build/
WORKDIR /build

# Local bind server files
COPY docker/bind9/etc/named.conf.* /etc/bind/
COPY docker/bind9/zones/ /var/bind/
RUN named-checkconf

ENV RUN_SERVICES="all"
