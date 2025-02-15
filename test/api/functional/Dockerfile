# Build VinylDNS API if the JAR doesn't already exist
FROM vinyldns/build:base-build as base-build
ARG DOCKERFILE_PATH="test/api/functional"
COPY "${DOCKERFILE_PATH}/application.conf" /opt/vinyldns/conf/
COPY . /build/
WORKDIR /build

##  Run the build if we don't already have a vinyldns-api.jar
RUN if [ -f artifacts/vinyldns-api.jar ]; then cp artifacts/vinyldns-api.jar /opt/vinyldns; fi && \
    if [ ! -f /opt/vinyldns/vinyldns-api.jar ]; then \
      env SBT_OPTS="-Xmx2G -Xms512M -Xss2M -XX:MaxMetaspaceSize=2G" \
      sbt -Dbuild.scalafmtOnCompile=false -Dbuild.lintOnCompile=fase ";project api;coverageOff;assembly" \
      && cp artifacts/vinyldns-api.jar /opt/vinyldns/; \
    fi

# Build the testing image, copying data from `vinyldns-api`
FROM vinyldns/build:base-test
SHELL ["/bin/bash","-c"]
ARG DOCKERFILE_PATH
COPY --from=base-build /opt/vinyldns /opt/vinyldns

# Start create zones api
RUN pip install --no-cache-dir fastapi uvicorn pydantic[email]
RUN sed -i '/if \[\[ \$START_BIND -eq 1 \]\]; then/a\
         \  echo -n "Starting create zones API.." & python3 /build/utils/generate_zones_bind_api.py &' /initialize.sh

# Local bind server files
COPY quickstart/bind9/etc/named.conf.* /etc/bind/
COPY quickstart/bind9/zones/ /var/bind/
RUN named-checkconf

# Copy over the functional tests
COPY  modules/api/src/test/functional /functional_test

ENTRYPOINT ["/bin/bash", "-c", "/initialize.sh bind localstack vinyldns-api mysql && { /functional_test/run.sh \"$@\" || { cat /opt/vinyldns/vinyldns.log && exit 1; }; }"]
