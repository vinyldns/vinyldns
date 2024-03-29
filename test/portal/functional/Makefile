SHELL=bash
IMAGE_NAME=vinyldns-portal-test
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

%:
	@:

.ONESHELL:

.PHONY: all build run run-local clean-containers

all: build run

build:
	@set -euo pipefail
	cd ../../..
	docker build -t $(IMAGE_NAME) --build-arg DOCKERFILE_PATH="$(RELATIVE_ROOT_DIR)" -f "$(ROOT_DIR)/Dockerfile" .

run:
	@set -euo pipefail
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	docker run -i $${USE_TTY} --rm $(IMAGE_NAME) $(WITH_ARGS)

run-local:
	@set -euo pipefail
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	docker run -i $${USE_TTY} --rm -v "$$(pwd)/../../../modules/portal:/functional_test" -v "$$(pwd)/run.sh:/functional_test/run.sh" $(IMAGE_NAME) $(WITH_ARGS)

clean-containers:
	@set -euo pipefail
	"$(ROOT_DIR)/../../../utils/clean-vinyldns-containers.sh"
