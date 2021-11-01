FROM vinyldns/build:base-build-portal as builder
ARG VINYLDNS_VERSION

COPY . /vinyldns

WORKDIR /vinyldns
RUN cp /build/node_modules.tar.xz /vinyldns/modules/portal && \
    cd /vinyldns/modules/portal && tar Jxf node_modules.tar.xz && \
    cd /vinyldns

RUN sbt "set version in ThisBuild := \"${VINYLDNS_VERSION}\"; project portal; preparePortal"
RUN sbt "set version in ThisBuild := \"${VINYLDNS_VERSION}\"; project portal; universal:packageZipTarball"

FROM adoptopenjdk/openjdk11:jdk-11.0.8_10-alpine
ARG DOCKER_FILE_PATH
ARG VINYLDNS_VERSION

RUN test -n "VINYLDNS_VERSION" || (echo "VINYLDNS_VERSION  not set" && false)
COPY --from=builder /vinyldns/modules/portal/target/universal/portal.tgz /

RUN apk add --update --no-cache bash && \
    mkdir -p /opt/vinyldns/lib_extra && \
    tar -xzf /portal.tgz && \
    rm /portal.tgz && \
    mv /portal/* /opt/vinyldns && \
    echo "${VINYLDNS_VERSION}" > /opt/vinyldns/conf/version

COPY ${DOCKER_FILE_PATH}/application.conf /opt/vinyldns/conf/

VOLUME ["/opt/vinyldns/lib_extra/", "/opt/vinyldns/conf/"]

EXPOSE 9001

ENTRYPOINT ["/bin/bash","-c", "java -Dvinyldns.version=$(cat /opt/vinyldns/conf/version) \
                                -Dconfig.file=/opt/vinyldns/conf/application.conf \
                                -cp /opt/vinyldns/conf:/opt/vinyldns/lib/*:/opt/vinyldns/lib_extra/* \
                                play.core.server.ProdServerStart"]
