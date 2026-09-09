FROM vinyldns/build:base-test-portal
SHELL ["/bin/bash","-c"]
ARG DOCKERFILE_PATH="test/frontend/functional"
# base-test-portal ships an old Node (built for the legacy grunt/portal stack),
# too old for Vite/Vitest; install a current Node LTS to run the React frontend tests.
ARG NODE_MAJOR=22
RUN curl -fsSL https://deb.nodesource.com/setup_${NODE_MAJOR}.x | bash - && \
    apt-get install -y nodejs

WORKDIR /functional_test
COPY modules/frontend /functional_test
COPY $DOCKERFILE_PATH/run.sh /functional_test
# vite.config.ts resolves ../../version.sbt relative to modules/frontend
COPY version.sbt /version.sbt

ENTRYPOINT ["./run.sh"]

