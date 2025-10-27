# Build VinylDNS API if the JAR doesn't already exist
FROM vinyldns/build:base-build as base-build
COPY . /build/
WORKDIR /build

##  Run the build if we don't already have a vinyldns-api.jar
RUN mkdir -p /opt/vinyldns/conf && \
    if [ -f artifacts/vinyldns-api.jar ]; then cp artifacts/vinyldns-api.jar /opt/vinyldns/; fi && \
    if [ ! -f /opt/vinyldns/vinyldns-api.jar ]; then \
      env SBT_OPTS="-Xmx2G -Xms512M -Xss2M -XX:MaxMetaspaceSize=2G" \
      sbt -Dbuild.scalafmtOnCompile=false -Dbuild.lintOnCompile=fase ";project api;coverageOff;assembly" \
      && cp artifacts/vinyldns-api.jar /opt/vinyldns/; \
    fi

FROM openjdk:26-slim
ARG DOCKER_FILE_PATH
ARG VINYLDNS_VERSION

RUN test -n "VINYLDNS_VERSION" || (echo "VINYLDNS_VERSION  not set" && false) && \
    test -n "DOCKER_FILE_PATH" || (echo "DOCKER_FILE_PATH  not set" && false) && \
    mkdir -p /opt/vinyldns/lib_extra && \
    echo "${VINYLDNS_VERSION}" > /opt/vinyldns/version

COPY --from=base-build /opt/vinyldns /opt/vinyldns
COPY ${DOCKER_FILE_PATH}/application.conf /opt/vinyldns/conf
COPY ${DOCKER_FILE_PATH}/log4j2.xml /opt/vinyldns/conf

# Mount the volume for config file and lib extras
VOLUME ["/opt/vinyldns/lib_extra/", "/opt/vinyldns/conf/"]

EXPOSE 9000

ENV JVM_OPTS=""
ENV INIT_SCRIPT="/bin/true"
ENTRYPOINT ["/bin/bash", "-c", "${INIT_SCRIPT} && java ${JVM_OPTS} -Dconfig.file=/opt/vinyldns/conf/application.conf \
                                -Dlog4j.configurationFile=/opt/vinyldns/conf/log4j2.xml \
                                -Dvinyldns.version=$(cat /opt/vinyldns/version) \
                                -cp /opt/vinyldns/lib_extra/*:/opt/vinyldns/vinyldns-api.jar \
                                vinyldns.api.Boot" ]
