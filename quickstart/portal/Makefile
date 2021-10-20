SHELL=bash
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

%:
	@:

.ONESHELL:

.PHONY: all build run

all: build run

build:
	@set -euo pipefail
	cd ../..
	docker build -t $(IMAGE_NAME) --build-arg DOCKERFILE_PATH="$(RELATIVE_ROOT_DIR)" -f "$(ROOT_DIR)/Dockerfile" .

run:
	@set -euo pipefail
	docker run -it --rm $(DOCKER_PARAMS) -p 9001:9001 $(IMAGE_NAME) -- $(WITH_ARGS)

run-bg:
	@set -euo pipefail
	docker stop $(IMAGE_NAME) &> /dev/null || true
	docker rm $(IMAGE_NAME) &> /dev/null || true
	docker run -td --name $(IMAGE_NAME) --rm $(DOCKER_PARAMS) -p 9001:9001 $(IMAGE_NAME) -- /bin/bash
