SHELL=bash
IMAGE_NAME=vinyldns-api-test
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

.PHONY: all build run run-local run-deps-bg clean-containers

all: build run

build:
	@set -euo pipefail
	cd ../../..
	docker build -t $(IMAGE_NAME) $(DOCKER_PARAMS)--build-arg DOCKERFILE_PATH="$(RELATIVE_ROOT_DIR)" -f "$(ROOT_DIR)/Dockerfile" .

run:
	@set -euo pipefail
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	docker run -i $${USE_TTY} --rm $(DOCKER_PARAMS) -p 9000:9000 -p 19001-19003:19001-19003 -p 19001:19001/udp $(IMAGE_NAME) $(ARG_SEPARATOR) $(WITH_ARGS)

# Runs the dependencies for the functional test in the background
# This is useful when running the tests on your host machine against the API in a container
run-deps-bg:
	@set -euo pipefail
	docker stop $(IMAGE_NAME) &> /dev/null || true
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	docker run -d $${USE_TTY} --name $(IMAGE_NAME) --rm $(DOCKER_PARAMS) --entrypoint "/initialize.sh" -p 9000:9000 -p 19001-19003:19001-19003 -p 19001:19001/udp $(IMAGE_NAME) all tail-logs

run-local:
	@set -euo pipefail
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	docker run -i $${USE_TTY} --rm $(DOCKER_PARAMS) -p 9000:9000 -p 19001-19003:19001-19003 -p 19001:19001/udp -v "$(ROOT_DIR)/../../../modules/api/src/test/functional:/functional_test" $(IMAGE_NAME) -- $(WITH_ARGS)

clean-containers:
	@set -euo pipefail
	"$(ROOT_DIR)/../../../utils/clean-vinyldns-containers.sh"
