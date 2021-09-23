IMAGE_NAME = vinyldns-functest

#Build docker image for functional_tests.
.PHONY: build_docker_functest
build_docker_functest:
	docker build -t $(IMAGE_NAME) modules/api/functional_test

#run docker image for functional_tests.
.PHONY: run_docker_functest
run_docker_functest:
	docker run --network docker_default $(IMAGE_NAME)
