SHELL=bash
IMAGE_NAME=vinyldns-api-test
ROOT_DIR:=$(shell dirname $(realpath  $(lastword $(MAKEFILE_LIST))))
# macOS BSD realpath does not support `--relative-to`; this repo layout is stable.
RELATIVE_ROOT_DIR:=test/api/functional
# Allow more startup time under emulation (e.g. amd64 image on arm64 host)
API_TIMEOUT?=120s

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

.PHONY: all all-local prepare-artifact prepare-artifact-local build run run-local run-deps-bg clean-containers

all: build run

all-local: prepare-artifact-local build run

prepare-artifact:
	@set -euo pipefail; \
	cd ../../..; \
	if [[ -f artifacts/vinyldns-api.jar ]]; then \
		echo "[prepare-artifact] reusing artifacts/vinyldns-api.jar"; \
	else \
		echo "[prepare-artifact] building artifacts/vinyldns-api.jar"; \
		./build/assemble_api.sh; \
	fi

prepare-artifact-local:
	@set -euo pipefail; \
	cd ../../..; \
	echo "[prepare-artifact] removing old jar: artifacts/vinyldns-api.jar"; \
	rm -f artifacts/vinyldns-api.jar; \
	sbt clean; \
	export SBT_OPTS="-Xmx2G -Xms512M -Xss2M -XX:MaxMetaspaceSize=2G"; \
	echo "[prepare-artifact] starting sbt assembly for api project"; \
	sbt -Dbuild.scalafmtOnCompile=false -Dbuild.lintOnCompile=false ";project api;coverageOff;assembly"; \
	echo "[prepare-artifact] assembly complete: artifacts/vinyldns-api.jar"

build:
	@set -euo pipefail; \
	cd ../../..; \
	docker build -t $(IMAGE_NAME) $(DOCKER_PARAMS) --build-arg DOCKERFILE_PATH="$(RELATIVE_ROOT_DIR)" -f "$(ROOT_DIR)/Dockerfile" .

run:
	@set -euo pipefail; \
	USE_TTY=""; \
	test -t 1 && USE_TTY="-t"; \
	docker run -i $${USE_TTY} --rm $(DOCKER_PARAMS) -e API_TIMEOUT=$(API_TIMEOUT) -p 9000:9000 -p 19001-19003:19001-19003 -p 19001:19001/udp $(IMAGE_NAME) $(ARG_SEPARATOR) $(WITH_ARGS)

# Runs tests with bind-mounted functional test files for iterative development
# Allows editing tests without rebuilding the Docker image
run-local: prepare-artifact build
	@set -euo pipefail; \
	USE_TTY=""; \
	test -t 1 && USE_TTY="-t"; \
	docker run -i $${USE_TTY} --rm $(DOCKER_PARAMS) -e API_TIMEOUT=$(API_TIMEOUT) \
	   -v "$(ROOT_DIR)/../../../modules/api/src/test/functional:/functional_test:ro" \
	   -p 9000:9000 -p 19001-19003:19001-19003 \
	   -p 19001:19001/udp \
	   $(IMAGE_NAME) \
	   $(ARG_SEPARATOR) $(WITH_ARGS)

# Runs the dependencies for the functional test in the background
# This is useful when running the tests on your host machine against the API in a container
run-deps-bg:
	@set -euo pipefail
	docker stop $(IMAGE_NAME) &> /dev/null || true
	docker rm $(IMAGE_NAME) &> /dev/null || true
	USE_TTY="" && test -t 1 && USE_TTY="-t"
	docker run -d $${USE_TTY} --name $(IMAGE_NAME) $(DOCKER_PARAMS) -e API_TIMEOUT=$(API_TIMEOUT) --entrypoint "/run-deps.sh" -p 9000:9000 -p 19001-19003:19001-19003 -p 19001:19001/udp $(IMAGE_NAME)

clean-containers:
	@set -euo pipefail
	"$(ROOT_DIR)/../../../utils/clean-vinyldns-containers.sh"
