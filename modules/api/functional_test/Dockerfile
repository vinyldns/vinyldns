# Build VinylDNS API if the JAR doesn't already exist
FROM vinyldns/build:base-api as vinyldns-api
COPY modules/api/functional_test/docker.conf modules/api/functional_test/vinyldns*.jar /opt/vinyldns/
COPY . /build/
WORKDIR /build

##  Run the build if we don't already have a vinyldns.jar
RUN if [ ! -f /opt/vinyldns/vinyldns.jar ]; then \
      env SBT_OPTS="-XX:+UseConcMarkSweepGC -Xmx4G -Xms1G" \
      sbt -Dbuild.scalafmtOnCompile=false -Dbuild.lintOnCompile=fase ";project api;coverageOff;assembly" \
      && cp modules/api/target/scala-2.12/vinyldns.jar /opt/vinyldns/; \
    fi

# Build the testing image, copying data from `vinyldns-api`
FROM vinyldns/build:base-test
SHELL ["/bin/bash","-c"]
COPY --from=vinyldns-api /opt/vinyldns /opt/vinyldns

# Local bind server files
COPY docker/bind9/etc/named.conf.local /etc/bind/
COPY docker/bind9/etc/*.conf /var/bind/config/
COPY docker/bind9/zones/ /var/bind/
RUN named-checkconf

# Copy over the functional tests
COPY modules/api/functional_test /functional_test

ENTRYPOINT ["/bin/bash", "-c", "/initialize.sh && \
                                (java -Dconfig.file=/opt/vinyldns/docker.conf -jar /opt/vinyldns/vinyldns.jar &> /opt/vinyldns/vinyldns.log &) && \
                                echo -n 'Starting VinylDNS API..' && \
                                timeout 30s grep -q 'STARTED SUCCESSFULLY' <(timeout 30s tail -f /opt/vinyldns/vinyldns.log) && \
                                echo 'done.' && \
                                /bin/bash"]