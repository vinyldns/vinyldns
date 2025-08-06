FROM vinyldns/build:base-test-portal
SHELL ["/bin/bash","-c"]
ARG DOCKERFILE_PATH="test/portal/functional"

WORKDIR /functional_test
COPY modules/portal /functional_test
COPY $DOCKERFILE_PATH/run.sh /functional_test

RUN cp /build/node_modules.tar.xz /functional_test && \
    cd /functional_test &&  \
    tar Jxf node_modules.tar.xz && \
    rm -rf node_modules.tar.xz

ENTRYPOINT ["./run.sh"]

