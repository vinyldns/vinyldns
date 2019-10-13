FROM alpine/git:1.0.7 as gitcheckout

ARG branch=master

RUN git clone -b ${branch} --single-branch --depth 1 https://github.com/vinyldns/vinyldns.git /vinyldns

FROM python:2.7.16-alpine3.9

RUN apk add --update --no-cache bind-tools netcat-openbsd bash curl

# The run script is what actually runs our func tests
COPY run.sh /app/run.sh
COPY run-tests.py /app/run-tests.py

RUN chmod a+x /app/run.sh && chmod a+x /app/run-tests.py

# Copy over the functional test directory, this must have been copied into the build context previous to this building!
COPY --from=gitcheckout /vinyldns/modules/api/functional_test/* /app/

# Install our func test requirements
RUN pip install --index-url https://pypi.python.org/simple/ -r /app/requirements.txt

ENV VINYLDNS_URL=""
ENV DNS_IP=""
ENV TEST_PATTERN="verify_production"

# set the entry point for the container to start vinyl, specify the config resource
ENTRYPOINT ["/app/run.sh"]
