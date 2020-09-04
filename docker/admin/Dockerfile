FROM znly/protoc:0.4.0 as pbcompile

# Needs to protoc compile modules/core/src/main/protobuf/VinylDNSProto.proto
COPY VinylDNSProto.proto /vinyldns/target/

# Create a compiled protobuf in /vinyldns/target
RUN protoc --version && \
    protoc --proto_path=/vinyldns/target --python_out=/vinyldns/target /vinyldns/target/VinylDNSProto.proto


FROM python:3.7-alpine

RUN pip install mysql-connector-python

COPY --from=pbcompile /vinyldns/target /app/
COPY update-support-user.py /app/update-support-user.py

WORKDIR /app
