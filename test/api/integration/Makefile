SHELL=bash
IMAGE_NAME=vinyldns-integraion
ROOT_DIR:=$(shell dirname $(realpath  $(lastword $(MAKEFILE_LIST))))
RELATIVE_ROOT_DIR:=$(shell realpath --relative-to=../../.. $(ROOT_DIR))
VINYLDNS_JAR_PATH?=modules/api/target/scala-2.12/vinyldns.jar

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
  RUN_ARGS ?= $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
endif

%:
	@:

.ONESHELL:

.PHONY: all build run run-local

all: build run

build:
	@set -euo pipefail
	trap 'if [ -f "$(ROOT_DIR)/vinyldns.jar" ]; then rm $(ROOT_DIR)/vinyldns.jar;	fi' EXIT
	cd ../../..
	if [ -f modules/api/target/scala-2.12/vinyldns.jar ]; then cp modules/api/target/scala-2.12/vinyldns.jar $(ROOT_DIR)/vinyldns.jar; fi
	docker build -t $(IMAGE_NAME) --build-arg DOCKERFILE_PATH="$(RELATIVE_ROOT_DIR)" -f "$(ROOT_DIR)/Dockerfile" .

run:
	@set -euo pipefail
	docker run -it --rm $(DOCKER_PARAMS) -p 9000:9000 -p 19003:19003 -p 19002:19002 -p 19001:19001/tcp -p 19001:19001/udp $(IMAGE_NAME) -- $(RUN_ARGS)

run-bg:
	@set -euo pipefail
	docker stop vinyldns-integration &> /dev/null || true
	docker rm vinyldns-integration &> /dev/null || true
	docker run -td --name vinyldns-integration --rm $(DOCKER_PARAMS) -p 9000:9000 -p 19003:19003 -p 19002:19002 -p 19001:19001/tcp -p 19001:19001/udp $(IMAGE_NAME) -- /bin/bash

run-local:
	@set -euo pipefail
	docker run -it --rm $(DOCKER_PARAMS) -p 9000:9000 -p 19003:19003 -p 19002:19002 -p 19001:19001/tcp -p 19001:19001/udp -v "$(ROOT_DIR)/../../..:/build" $(IMAGE_NAME) -- $(RUN_ARGS)
