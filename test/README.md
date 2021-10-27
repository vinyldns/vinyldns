# Test

This folder contains test containers for running integration and functional tests.

| Path |Description |
| --- | --- |
|`api/functional` | A Docker container for running functional tests. Use the `Makefile` to build and run. <br/> You can use `WITH_ARGS` to pass [arguments to `Pytest`](https://docs.pytest.org/en/6.2.x/usage.html#specifying-tests-selecting-tests). <br/>Ex: `make run WITH_ARGS="-k test_verify_production"`<br/><br/> The tests are located in `modules/api/test/functional`|
|`api/integration` | A Docker container for running integration tests. Use the `Makefile` to build and run.<br/> You can use `WITH_ARGS` to pass the command you would like to execute in the context of this container.<br/>Ex: `make run WITH_ARGS="sbt ';validate'"`<br/><br/> This does not run any tests by default, but is used by other scripts to run the integration tests. (e.g., `build/verify.sh`)|
|`portal/functional` | A Docker container for running functional tests. Use the `Makefile` to build and run.<br/><br/> The tests are located in `modules/portal/test`|

## Execution

The functional tests can be run by using `make` as described in the table above. For other usages, check out
the `build/` directory. Specifically:

| Path |Description |
| --- | --- |
| `build/func-test-api.sh` | Runs the functional tests for the API| 
| `build/func-test-portal.sh` | Runs the functional tests for the Portal| 
| `build/verify.sh` | Runs the unit and integration tests for everything| 
