FROM python:2.7-buster

# Install dns utils so we can run dig
RUN apt-get update && apt-get install dnsutils -y

# The run script is what actually runs our func tests
COPY run.sh /app/run.sh
RUN chmod 777 /app/run.sh

COPY run-tests.py /app/run-tests.py
RUN chmod 777 /app/run-tests.py

# Copy over the functional test directory, this must have been copied into the build context previous to this building!
ADD functional_test /app

# Install our func test requirements
RUN pip install --index-url https://pypi.python.org/simple/ -r /app/requirements.txt

# Specifies how many CPUs to use for func tests; the more the better or specifiy "auto" for optimal results
ENV PAR_CPU=2

# set the entry point or cmd for the container to start vinyl functional_test, specify the config resource
CMD ["/bin/sh", "/app/run.sh"]
#ENTRYPOINT ["/app/run.sh"]
