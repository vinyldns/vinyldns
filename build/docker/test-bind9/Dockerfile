FROM alpine/git:1.0.7 as gitcheckout

ARG BRANCH=master

RUN git clone -b ${BRANCH} --single-branch --depth 1 https://github.com/vinyldns/vinyldns.git /vinyldns

FROM vinyldns/bind9:0.0.5

COPY --from=gitcheckout /vinyldns/docker/bind9/zones/* /var/cache/bind/zones/

COPY --from=gitcheckout /vinyldns/docker/bind9/etc/named.conf.local /var/cache/bind/config
