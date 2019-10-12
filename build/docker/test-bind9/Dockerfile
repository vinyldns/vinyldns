FROM vinyldns/bind9:0.0.4

ARG branch=master

RUN git clone -b ${branch} --single-branch --depth 1 https://github.com/vinyldns/vinyldns.git /vinyldns

COPY /vinyldns/docker/bind9/zones/* /var/cache/bind/zones/

COPY /vinyldns/docker/bind9/etc/named.conf.local /var/cache/bind/config/named.conf.local

ARG BUILD_VERSION=unknown

# This will set the vinyldns version, make sure to have this in config... version = ${?VINYLDNS_VERSION}
ENV VINYLDNS_VERSION=$build_ver
