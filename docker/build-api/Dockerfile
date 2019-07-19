FROM hseeberger/scala-sbt:8u171_2.12.6_1.1.6 as builder

ARG branch=master

RUN git clone -b ${branch} --single-branch --depth 1 https://github.com/vinyldns/vinyldns.git /vinyldns

RUN cd /vinyldns ; sbt api/stage


FROM openjdk:8u201-jdk-alpine3.9

RUN apk add --update --no-cache netcat-openbsd bash

COPY --from=builder /vinyldns/modules/api/target/universal/stage /opt/docker

# Mount the volume for config file and lib extras
# Note: These volume names are used in the build.sbt
VOLUME ["/opt/docker/lib_extra/", "/opt/docker/conf"]

EXPOSE 9000

# set the entry point for the container to start vinyl, specify the config resource
ENTRYPOINT ["/opt/docker/bin/api"]
