SHELL=bash
ROOT_DIR:=$(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))

# Check that the required version of make is being used
REQ_MAKE_VER:=3.82
ifneq ($(REQ_MAKE_VER),$(firstword $(sort $(MAKE_VERSION) $(REQ_MAKE_VER))))
   $(error The version of MAKE $(REQ_MAKE_VER) or higher is required; you are running $(MAKE_VERSION))
endif

.ONESHELL:

.PHONY: all build run

all: build run

build:
	@set -euo pipefail
	trap 'if [ -f modules/api/functional_test/vinyldns.jar ]; then rm modules/api/functional_test/vinyldns.jar;	fi' EXIT
	cd ../../..
	if [ -f modules/api/target/scala-2.12/vinyldns.jar ]; then cp modules/api/target/scala-2.12/vinyldns.jar modules/api/functional_test/vinyldns.jar; fi
	docker build -t vinyldns-test -f modules/api/functional_test/Dockerfile .

run:
	@set -euo pipefail
	docker run -it --rm -p 9000:9000 -p 19001:53/tcp -p 19001:53/udp vinyldns-test