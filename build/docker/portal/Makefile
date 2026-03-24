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

.PHONY: all artifact build run publish build-vnext publish-vnext

all: build run

artifact:
	@set -euo pipefail
	cd ../../..
	docker build $(BUILD_ARGS) --target base-build --build-arg DOCKER_FILE_PATH="$$(realpath --relative-to="." "$(ROOT_DIR)")" --build-arg VINYLDNS_VERSION="$(IMAGE_TAG)" -t "vinyldns:portal-artifact" -f "$(ROOT_DIR)/Dockerfile" .
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	docker run -i $${USE_TTY} --rm -v "$$(pwd)/:/output" vinyldns:portal-artifact /bin/bash -c "mkdir -p /output/artifacts/ && cp /build/artifacts/*.zip /output/artifacts/"

build:
	@set -euo pipefail
	cd ../../..
	docker build $(BUILD_ARGS) --build-arg DOCKER_FILE_PATH="$$(realpath --relative-to="." "$(ROOT_DIR)")" --build-arg VINYLDNS_VERSION="$(IMAGE_TAG)" -t $(IMAGE_NAME):$(IMAGE_TAG) -f "$(ROOT_DIR)/Dockerfile" .
	docker tag $(IMAGE_NAME):$(IMAGE_TAG) $(IMAGE_NAME):latest

run:
	@set -euo pipefail
	docker network create --driver bridge vinyldns_net &> /dev/null || true
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	docker run -i $${USE_TTY} --rm --network vinyldns_net --env-file "$(ROOT_DIR)/../.env" $(DOCKER_PARAMS) -v "$$(pwd)/application.conf:/opt/vinyldns/conf/application.conf" -v "$$(pwd)/log4j2.xml:/opt/vinyldns/conf/log4j2.xml" -p 9001:9001 $(IMAGE_NAME):$(IMAGE_TAG) $(ARG_SEPARATOR) $(WITH_ARGS)

publish: build
	@set -euo pipefail
	DOCKER_CONTENT_TRUST=1 docker push $(IMAGE_NAME):$(IMAGE_TAG)
	DOCKER_CONTENT_TRUST=1 docker push $(IMAGE_NAME):latest

build-vnext:
	@set -euo pipefail
	cd ../../..
	docker build $(BUILD_ARGS) --build-arg DOCKER_FILE_PATH="$$(realpath --relative-to="." "$(ROOT_DIR)")" --build-arg VINYLDNS_VERSION="vnext" -t $(IMAGE_NAME):vnext -f "$(ROOT_DIR)/Dockerfile" .
	docker tag $(IMAGE_NAME):vnext "$(IMAGE_NAME):vnext-$$(date -u  +'%Y%m%d')"

publish-vnext: build-vnext
	@set -euo pipefail
	DOCKER_CONTENT_TRUST=1 docker push $(IMAGE_NAME):vnext
	DOCKER_CONTENT_TRUST=1 docker push "$(IMAGE_NAME):vnext-$$(date -u  +'%Y%m%d')"
