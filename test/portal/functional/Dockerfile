FROM vinyldns/build:base-test-portal
SHELL ["/bin/bash","-c"]
ARG DOCKERFILE_PATH="test/portal/functional"

# Ensure Node 22 and latest npm for portal build compatibility
# MUST remove old packages first, clear caches, then install fresh
ARG NODE_MAJOR=22
RUN apt-get update && \
    apt-get purge -y nodejs npm && \
    apt-get autoremove -y && \
    curl -fsSL https://deb.nodesource.com/setup_${NODE_MAJOR}.x | bash - && \
    apt-get install -y nodejs && \
    npm install -g npm@latest && \
    rm -rf /root/.npm && \
    npm cache clean --force && \
    node --version && npm --version && \
    npm -v && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /functional_test
COPY modules/portal /functional_test
COPY $DOCKERFILE_PATH/run.sh /functional_test

RUN cp /build/node_modules.tar.xz /functional_test && \
    cd /functional_test &&  \
    tar Jxf node_modules.tar.xz && \
    rm -rf node_modules.tar.xz

ENTRYPOINT ["./run.sh"]

