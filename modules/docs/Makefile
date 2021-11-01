SHELL=bash
IMAGE_NAME=vinyldns-build-docs
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
  WITH_ARGS := $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
  # ...and turn them into do-nothing targets
  $(eval $(WITH_ARGS):;@:)
endif


.ONESHELL:

.PHONY: all run

all: run

run:
	@set -euo pipefail
	cd ../..
	test -t 1 && USE_TTY="-t"
	docker run -i $${USE_TTY} --rm -p "4000:4000" -v "$$(pwd):/build" vinyldns/build:base-build-docs /bin/bash
