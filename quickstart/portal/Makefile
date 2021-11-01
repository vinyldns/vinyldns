SHELL=bash
IMAGE_TAG=$(shell awk -F'"' '{print $$2}' ../../../version.sbt)
IMAGE_NAME=vinyldns/portal
ROOT_DIR:=$(shell dirname $(realpath  $(lastword $(MAKEFILE_LIST))))

# Check that the required version of make is being used
REQ_MAKE_VER:=3.82
ifneq ($(REQ_MAKE_VER),$(firstword $(sort $(MAKE_VERSION) $(REQ_MAKE_VER))))
   $(error The version of MAKE $(REQ_MAKE_VER) or higher is required; you are running $(MAKE_VERSION))
endif

# Extract arguments for `make run`
EXTRACT_ARGS=true
ifeq (run,$(firstword $(MAKECMDGOALS)))
	EXTRACT_ARGS=true
endif
ifeq ($(EXTRACT_ARGS),true)
  # use the rest as arguments for "run"
  WITH_ARGS ?= $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
endif
ifdef $(WITH_ARGS)
	ARG_SEPARATOR=--
endif

%:
	@:

.ONESHELL:

.PHONY: all build run

all: build run

build:
	@set -euo pipefail
	cd ../..
	docker build -t $(IMAGE_NAME) -f "$(ROOT_DIR)/Dockerfile" .

run:
	@set -euo pipefail
	docker network create --driver bridge vinyldns_net &> /dev/null || true
	test -t 1 && USE_TTY="-t"
	docker run -i $${USE_TTY} --rm $(DOCKER_PARAMS) --network vinyldns_net -p 9001:9001 $(IMAGE_NAME) $(ARG_SEPARATOR) $(WITH_ARGS)

run-bg:
	@set -euo pipefail
	docker stop $(IMAGE_NAME) &> /dev/null || true
	docker rm $(IMAGE_NAME) &> /dev/null || true
	docker network create --driver bridge vinyldns_net &> /dev/null || true
	test -t 1 && USE_TTY="-t"
	docker run -d $${USE_TTY} --name $(IMAGE_NAME) --rm $(DOCKER_PARAMS) --network vinyldns_net -p 9001:9001 $(IMAGE_NAME) -- /bin/bash
