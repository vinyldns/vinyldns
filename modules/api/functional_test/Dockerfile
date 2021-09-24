FROM python:2.7-buster

# Install dns utils so we can run dig
RUN apt-get update && apt-get install dnsutils -y

# The run script is what actually runs our func tests
COPY run.sh /app/run.sh
RUN chmod u+x /app/run.sh

COPY run.py /app/run.py
RUN chmod u+x /app/run.py

# Copy over the functional test directory, this must have been copied into the build context previous to this building!
ADD . /app
RUN rm -Rf /app/functional_test/Dockerfile

# Install our func test requirements
RUN pip install --index-url https://pypi.python.org/simple/ -r /app/requirements.txt

# Specifies how many CPUs to use for func tests; the more the better or specifiy "auto" for optimal results
ENV PAR_CPU=2

# set the entry point for the container to start vinyl, specify the config resource
#ENTRYPOINT ["/app/run.sh"]
CMD ["/bin/sh", "/app/run.sh"]

