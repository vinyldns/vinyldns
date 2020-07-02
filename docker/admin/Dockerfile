FROM znly/protoc:0.4.0 as pbcompile

RUN apk update && apk upgrade && \
    apk add --no-cache bash git openssh

# needs to protoc compile modules/core/src/main/protobuf/VinylDNSProto.proto
RUN git clone -b master --single-branch --depth 1 https://github.com/vinyldns/vinyldns.git /vinyldns

# create a compiled protobuf in /vinyldns/target
RUN mkdir -p /vinyldns/target && \
    cp vinyldns/modules/core/src/main/protobuf/VinylDNSProto.proto /vinyldns/target && \
    protoc --version && \
    protoc --proto_path=/vinyldns/target --python_out=/vinyldns/target /vinyldns/target/VinylDNSProto.proto


FROM python:3.7-alpine

RUN apk add --update --no-cache bind-tools netcat-openbsd bash curl
RUN pip install mysql-connector-python

COPY --from=pbcompile /vinyldns/target /app/
COPY update-support-user.py /app/update-support-user.py

WORKDIR /app
ENTRYPOINT ["python"]
