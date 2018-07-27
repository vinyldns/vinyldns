FROM alpine:3.2
FROM anapsix/alpine-java:8_server-jre

EXPOSE 9324

COPY run.sh /elasticmq/run.sh
COPY custom.conf /elasticmq/custom.conf
COPY elasticmq-server-0.13.2.jar /elasticmq/server.jar

ENTRYPOINT ["/elasticmq/run.sh"]
