FROM adoptopenjdk/openjdk11:jdk-11.0.7_10-alpine

RUN apk add --update --no-cache netcat-openbsd bash

# install the jar onto the server, asserts this Dockerfile is copied to target/scala-2.12 after a build
COPY vinyldns.jar /app/vinyldns-server.jar
COPY run.sh /app/run.sh
RUN chmod a+x /app/run.sh

COPY docker.conf /app/docker.conf

EXPOSE 9000

# set the entry point for the container to start vinyl, specify the config resource
ENTRYPOINT ["/app/run.sh"]


