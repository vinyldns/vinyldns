SHELL=bash
IMAGE_NAME=vinyldns-update-user
ROOT_DIR:=$(shell dirname $(realpath  $(lastword $(MAKEFILE_LIST))))
RELATIVE_ROOT_DIR:=$(shell realpath --relative-to=../.. $(ROOT_DIR))

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

.PHONY: all build run proto

all: build run

build:
	@set -euo pipefail
	cd ../..
	docker build -t $(IMAGE_NAME) --build-arg DOCKERFILE_PATH="$(RELATIVE_ROOT_DIR)" -f "$(ROOT_DIR)/Dockerfile" .

run:
	@set -euo pipefail
	docker network create --driver bridge vinyldns_net &> /dev/null || true
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	docker run -i $${USE_TTY} --network vinyldns_net --rm $(DOCKER_PARAMS) $(IMAGE_NAME) $(WITH_ARGS)

proto:
	@set -euo pipefail
	cp ../../modules/core/src/main/protobuf/VinylDNSProto.proto .
	protoc VinylDNSProto.proto --python_out=.
	rm VinylDNSProto.proto
