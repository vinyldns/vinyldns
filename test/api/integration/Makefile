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

.PHONY: all build run run-local run-bg stop-bg clean-containers

all: build run

build:
	@set -euo pipefail
	cd ../../..
	docker build -t $(IMAGE_NAME) $(DOCKER_PARAMS) --build-arg DOCKERFILE_PATH="$(RELATIVE_ROOT_DIR)" -f "$(ROOT_DIR)/Dockerfile" .

run:
	@set -euo pipefail
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	docker run -i $${USE_TTY} --rm $(DOCKER_PARAMS) $(IMAGE_NAME) $(ARG_SEPARATOR) $(WITH_ARGS)

run-bg:
	@set -euo pipefail
	docker stop $(IMAGE_NAME) &> /dev/null || true
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	docker run -d $${USE_TTY} --name $(IMAGE_NAME) --rm $(DOCKER_PARAMS) -e RUN_SERVICES="deps-only tail-logs" -p 19001-19003:19001-19003 -p 19001:19001/udp $(IMAGE_NAME)

stop-bg:
	@set -euo pipefail
	docker stop $(IMAGE_NAME) &> /dev/null || true

run-local:
	@set -euo pipefail
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	docker run -i $${USE_TTY} --rm $(DOCKER_PARAMS) -v "$(ROOT_DIR)/../../..:/build" $(IMAGE_NAME) -- $(WITH_ARGS)

clean-containers:
	@set -euo pipefail
	"$(ROOT_DIR)/../../../utils/clean-vinyldns-containers.sh"
