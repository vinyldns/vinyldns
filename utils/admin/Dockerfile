FROM znly/protoc:0.4.0 as pbcompile
WORKDIR /vinyldns

# Needs to protoc compile modules/core/src/main/protobuf/VinylDNSProto.proto
COPY modules/core/src/main/protobuf/VinylDNSProto.proto /vinyldns

# Create a compiled protobuf in /vinyldns/target
RUN mkdir -p /vinyldns/python && \
    protoc --proto_path=/vinyldns --python_out=/vinyldns/python /vinyldns/VinylDNSProto.proto


FROM vinyldns/build:base-build
ARG DOCKERFILE_PATH
WORKDIR /app
RUN pip install mysql-connector-python==8.0.27 protobuf==3.20.3

ENV DB_USER="root" DB_PASS="pass" DB_HOST="vinyldns-integration" DB_NAME="vinyldns" DB_PORT="19002"

COPY --from=pbcompile /vinyldns/python .
COPY ${DOCKERFILE_PATH}/update-user.py .
RUN chmod 755 update-user.py

ENTRYPOINT ["./update-user.py"]
