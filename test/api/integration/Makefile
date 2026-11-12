SHELL=bash
IMAGE_NAME=vinyldns-api-integration
ROOT_DIR:=$(shell dirname $(realpath  $(lastword $(MAKEFILE_LIST))))
RELATIVE_ROOT_DIR:=$(shell realpath --relative-to=../../.. $(ROOT_DIR))

# Check that the required version of make is being used
REQ_MAKE_VER:=3.82
ifneq ($(REQ_MAKE_VER),$(firstword $(sort $(MAKE_VERSION) $(REQ_MAKE_VER))))
   $(error The version of MAKE $(REQ_MAKE_VER) or higher is required; you are running $(MAKE_VERSION))
endif

# Extract arguments for `make run`
EXTRACT_ARGS=false
ifeq (run,$(firstword $(MAKECMDGOALS)))
	EXTRACT_ARGS=true
endif
ifeq ($(EXTRACT_ARGS),true)
  # use the rest as arguments for "run"
  WITH_ARGS ?= $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
endif
ifneq ($(WITH_ARGS),)
  ARG_SEPARATOR=--
endif

%:
	@:

.ONESHELL:

.PHONY: all build init-network start-pdns stop-pdns run run-local run-bg stop-bg clean-containers

all: build init-network start-pdns run

init-network:
	@set -euo pipefail
	docker network create --driver bridge vinyldns_net &> /dev/null || true

# Marks whether this Makefile started pdns-auth itself (vs. reusing one already running, e.g.
# from quickstart/docker-compose), so stop-pdns only tears down containers it owns.
PDNS_MARKER := /tmp/.vinyldns_it_started_pdns

# Starts pdns-auth as a sibling container on vinyldns_net, reachable from the integration
# container at "vinyldns-pdns-auth:19005" (mirrors test/api/functional/Makefile's start-pdns).
# Idempotent/non-destructive: if a container with this name already exists (e.g. one started by
# quickstart/docker-compose), it's reused and just attached to vinyldns_net rather than recreated.
start-pdns:
	@set -euo pipefail
	docker network create --driver bridge vinyldns_net &> /dev/null || true
	if [ -z "$$(docker ps -aq -f name=^/vinyldns-pdns-auth$$)" ]; then \
	  docker run -d --name vinyldns-pdns-auth \
	    -p 19005:19005 -p 1053:53 -p 1053:53/udp \
	    -e PDNS_AUTH_API_KEY="vinyldns_pdns_auth_api_key" \
	    --network vinyldns_net \
	    powerdns/pdns-auth-master; \
	  docker cp "$(ROOT_DIR)/../../../quickstart/pdns/pdns.conf" vinyldns-pdns-auth:/etc/powerdns/pdns.conf; \
	  touch $(PDNS_MARKER); \
	else \
	  docker network connect vinyldns_net vinyldns-pdns-auth &> /dev/null || true; \
	  rm -f $(PDNS_MARKER); \
	fi

# Only removes pdns-auth if start-pdns created it in this invocation (see PDNS_MARKER).
stop-pdns:
	@set -euo pipefail
	if [ -f $(PDNS_MARKER) ]; then \
	  docker rm -f vinyldns-pdns-auth &> /dev/null || true; \
	  rm -f $(PDNS_MARKER); \
	fi

build:
	@set -euo pipefail
	cd ../../..
	docker build -t $(IMAGE_NAME) $(DOCKER_PARAMS) --build-arg DOCKERFILE_PATH="$(RELATIVE_ROOT_DIR)" -f "$(ROOT_DIR)/Dockerfile" .

run: init-network start-pdns
	@set -euo pipefail
	docker network create --driver bridge vinyldns_net &> /dev/null || true
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	set +e; \
	docker run -i $${USE_TTY} --rm --network vinyldns_net $(DOCKER_PARAMS) $(IMAGE_NAME) $(ARG_SEPARATOR) $(WITH_ARGS); \
	TEST_EXIT=$$?; \
	set -e; \
	$(MAKE) stop-pdns; \
	exit $$TEST_EXIT

run-bg:
	@set -euo pipefail
	docker stop $(IMAGE_NAME) &> /dev/null || true
	$(MAKE) start-pdns
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	docker run -d $${USE_TTY} --name $(IMAGE_NAME) --rm --network vinyldns_net $(DOCKER_PARAMS) -e RUN_SERVICES="deps-only tail-logs" -p 19001-19003:19001-19003 -p 19001:19001/udp $(IMAGE_NAME)

stop-bg:
	@set -euo pipefail
	docker stop $(IMAGE_NAME) &> /dev/null || true
	$(MAKE) stop-pdns

run-local:
	@set -euo pipefail
	$(MAKE) start-pdns
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	set +e; \
	docker run -i $${USE_TTY} --rm --network vinyldns_net $(DOCKER_PARAMS) -v "$(ROOT_DIR)/../../..:/build" $(IMAGE_NAME) -- $(WITH_ARGS); \
	TEST_EXIT=$$?; \
	set -e; \
	$(MAKE) stop-pdns; \
	exit $$TEST_EXIT

clean-containers:
	@set -euo pipefail
	"$(ROOT_DIR)/../../../utils/clean-vinyldns-containers.sh"
