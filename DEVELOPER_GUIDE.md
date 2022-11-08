# Developer Guide

## Table of Contents

- [Developer Requirements (Local)](#developer-requirements-local)
- [Developer Requirements (Docker)](#developer-requirements-docker)
- [Project Layout](#project-layout)
    * [Core](#core)
    * [API](#api)
    * [Portal](#portal)
    * [Documentation](#documentation)
- [Running VinylDNS Locally](#running-vinyldns-locally)
    * [Support for M1 Macs](#support-for-m1-macs)
    * [Starting the API Server](#starting-the-api-server)
    * [Starting the Portal](#starting-the-portal)
- [Testing](#testing)
    * [Unit Tests](#unit-tests)
    * [Integration Tests](#integration-tests)
        + [Running both](#running-both)
    * [Functional Tests](#functional-tests)
        + [Running Functional Tests](#running-functional-tests)
            - [API Functional Tests](#api-functional-tests)
        + [Setup](#setup)
            - [Functional Test Context](#functional-test-context)
            - [Partitioning](#partitioning)
            - [Really Important Test Context Rules!](#really-important-test-context-rules)
            - [Managing Test Zone Files](#managing-test-zone-files)

## Developer Requirements (Local)

- Java (version: >= 8, <= 11)
- Scala 2.12
- sbt 1.4+
- curl
- docker
- docker-compose
- GNU Make 3.82+
- grunt
- Node.js/npm v12+
- Python 3.5+
- [coreutils](https://www.gnu.org/software/coreutils/)
  - Linux: `apt install coreutils` or `yum install coreutils`
  - macOS: [`brew install coreutils`](https://formulae.brew.sh/formula/coreutils)

## Developer Requirements (Docker)

Since almost everything can be run with Docker and GNU Make, if you don't want to setup a local development environment,
then you simply need:

- `Docker` v19.03+ _(earlier versions may work fine)_
- `Docker Compose` v2.0+ _(earlier versions may work fine)_
- `GNU Make` v3.82+
- `Bash` 3.2+
    - Basic utilities: `awk`, `sed`, `curl`, `grep`, etc may be needed for scripts

## Project Layout

[SYSTEM_DESIGN.md](SYSTEM_DESIGN.md) provides a high-level architectural overview of VinylDNS and interoperability of
its components.

The main codebase is a multi-module Scala project with multiple sub-modules. To start working with the project, from the
root directory run `sbt`. Most of the code can be found in the `modules` directory. The following modules are present:

- `root` - this is the parent project, if you run tasks here, it will run against all sub-modules
- [`core`](#core): core modules that are used by both the API and portal, such as cryptography implementations.
- [`api`](#api): the API is the main engine for all of VinylDNS. This is the most active area of the codebase, as
  everything else typically just funnels through the API.
- [`portal`](#portal): The portal is a user interface wrapper around the API. Most of the business rules, logic, and
  processing can be found in the API. The
  _only_ features in the portal not found in the API are creation of users and user authentication.
- [`docs`](#documentation): documentation for VinylDNS.

### Core

Code that is used across multiple modules in the VinylDNS ecosystem live in `core`.

#### Code Layout

- `src/main` - the main source code
- `src/test` - unit tests

### API

The API is the RESTful API for interacting with VinylDNS. The following technologies are used:

- [Akka HTTP](https://doc.akka.io/docs/akka-http/current/) - Used primarily for REST and HTTP calls.
- [FS2](https://functional-streams-for-scala.github.io/fs2/) - Used for backend change processing off of message queues.
  FS2 has back-pressure built in, and gives us tools like throttling and concurrency.
- [Cats Effect](https://typelevel.org/cats-effect/) - A replacement of `Future` with the `IO` monad
- [Cats](https://typelevel.org/cats) - Used for functional programming.
- [PureConfig](https://pureconfig.github.io/) - For loading configuration values.

The API has the following dependencies:

- MySQL - the SQL database that houses the data
- SQS - for managing concurrent updates and enabling high-availability
- Bind9 - for testing integration with a real DNS system

#### Code Layout

The API code can be found in `modules/api`.

- `src/it` - integration tests
- `src/main` - the main source code
- `src/test` - unit tests
- `src/universal` - items that are packaged in the Docker image for the VinylDNS API

The package structure for the source code follows:

- `vinyldns.api.domain` - contains the core front-end logic. This includes things like the application services,
  repository interfaces, domain model, validations, and business rules.
- `vinyldns.api.engine` - the back-end processing engine. This is where we process commands including record changes,
  zone changes, and zone syncs.
- `vinyldns.api.protobuf` - marshalling and unmarshalling to and from protobuf to types in our system
- `vinyldns.api.repository` - repository implementations live here
- `vinyldns.api.route` - HTTP endpoints

### Portal

The project is built using:

- [Play Framework](https://www.playframework.com/documentation/2.6.x/Home)
- [AngularJS](https://angularjs.org/)

The portal is _mostly_ a shim around the API. Most actions in the user interface are translated into API calls.

The features that the Portal provides that are not in the API include:

- Authentication against LDAP
- Creation of users - when a user logs in for the first time, VinylDNS automatically creates a user and new credentials
  for them in the database with their LDAP information.

#### Code Layout

The portal code can be found in `modules/portal`.

- `app` - source code for portal back-end
    - `models` - data structures that are used by the portal
    - `views` - HTML templates for each web page
    - `controllers` - logic for updating data
- `conf` - configurations and endpoint routes
- `public` - source code for portal front-end
    - `css` - stylesheets
    - `images` - images, including icons, used in the portal
    - `js` - scripts
    - `mocks` - mock JSON used in Grunt tests
    - `templates` - modal templates
- `test` - unit tests for portal back-end

### Documentation

Code used to build the microsite content for the API, operator and portal guides at https://www.vinyldns.io/. Some
settings for the microsite are also configured in `build.sbt` of the project root.

#### Code Layout

- `src/main/resources` - Microsite resources and configurations
- `src/main/mdoc` - Content for microsite web pages

## Running VinylDNS Locally

VinylDNS can be started in the background by running the [quickstart instructions](README.md#quickstart) located in the
README. However, VinylDNS can also be run in the foreground.

### Support for M1 Macs

If you are using a Mac running macOS with one of the new Apple M1 chips, you will need to update some dependencies to
newer versions before attempting to run VinylDNS locally. To verify whether your computer has one of these chips,
go to About This Mac in the Apple menu in the top-left corner of your screen. If next to Chip you see Apple M1,
or any later chip such as the Apple M1 Pro or Apple M1 Max, then you will need to apply these changes to the code.

#### build.sbt

Update protoc from version 2.6.1:

```shell
PB.targets in Compile := Seq(PB.gens.java("2.6.1") -> (sourceManaged in Compile).value),
PB.protocVersion := "-v261"
```

to version 3.21.7:

```shell
PB.targets in Compile := Seq(PB.gens.java("3.21.7") -> (sourceManaged in Compile).value),
PB.protocVersion := "-v3.21.7"
```

#### project/build.properties

Update `sbt.version=1.4.0` to `sbt.version=1.7.2`

#### project/Dependencies.scala

Update protobuf from version 2.6.1:

```shell
"com.google.protobuf"       %  "protobuf-java"                  % "2.6.1",
```

to version 3.21.7:

```shell
"com.google.protobuf"       %  "protobuf-java"                  % "3.21.7",
```

#### project/plugins.sbt

Update the sbt-protoc plugin from version 0.99.18:

```shell
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.18")
```

to version 1.0.6:

```shell
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")
```

### Starting the API Server

Before starting the API service, you can start the dependencies for local development:

```shell
quickstart/quickstart-vinyldns.sh --deps-only
```

This will start a container running in the background with necessary prerequisites.

Once the prerequisites are running, you can start up sbt by running `sbt` from the root directory.

- `project api` to change the sbt project to the API
- `reStart` to start up the API server
  - To enable interactive debugging, you can run `set Revolver.enableDebugging(port = 5020, suspend = true)` before running `reStart` 
- Wait until you see the message `VINYLDNS SERVER STARTED SUCCESSFULLY` before working with the server
- To stop the VinylDNS server, run `reStop` from the api project
- To stop the dependent Docker containers: `utils/clean-vinyldns-containers.sh`

See the [API Configuration Guide](https://www.vinyldns.io/operator/config-api) for information regarding API
configuration.

### Starting the Portal

To run the portal locally, you _first_ have to start up the VinylDNS API Server. This can be done by following the
instructions for [Staring the API Server](#starting-the-api-server) or by using the QuickStart:

```shell
quickstart/quickstart-vinyldns.sh --api
```

Once that is done, in the same `sbt` session or a different one, go to `project portal` and then
execute `;preparePortal; run`.

See the [Portal Configuration Guide](https://www.vinyldns.io/operator/config-portal) for information regarding portal
configuration.

## Testing

### Unit Tests

1. First, start up your Scala build tool: `build/sbt.sh` (or `sbt` if running outside of Docker).
2. (Optionally) Go to the project you want to work on, for example `project api` for the API; `project portal` for the
   portal.
3. Run _all_ unit tests by just running `test`.
4. Run a single unit test suite by running `testOnly *MySpec`.
5. Run a single unit by filtering the test name using the `-z` argument `testOnly *MySpec -- -z "some text from test"`.
    - [More information on commandline arguments](https://www.scalatest.org/user_guide/using_the_runner)
6. If you are working on a unit test and production code at the same time, use `~` (e.g., `~testOnly *MySpec`) to
   automatically background compile for you!

### Integration Tests

Integration tests are used to test integration with dependent services. We use Docker to spin up those backend services
for integration test development.

1. Type `quickstart/quickstart-vinyldns.sh --reset --deps-only` to start up dependent background services
1. Run sbt (`build/sbt.sh` or `sbt` locally)
1. Go to the target module in sbt, example: `project api`
1. Run all integration tests by typing `it:test`.
1. Run an individual integration test by typing `it:testOnly *MyIntegrationSpec`
1. You can background compile as well if working on a single spec by using `~it:testOnly *MyIntegrationSpec`
1. You must restart the dependent services (`quickstart/quickstart-vinyldns.sh --reset --deps-only`) before you rerun
   the tests.
1. For the mysql module, you may need to wait up to 30 seconds after starting the services before running the tests for
   setup to complete.

#### Running both

You can run all unit and integration tests for the api and portal by running `build/verify.sh`

### Functional Tests

When adding new features, you will often need to write new functional tests that black box / regression test the API.

- The API functional tests are written in Python and live under `modules/api/src/test/functional`.
- The Portal functional tests are written in JavaScript and live under `modules/portal/test`.

#### Running Functional Tests

To run functional tests you can simply execute the following commands:

```shell
build/func-test-api.sh
build/func-test-portal.sh
```

These command will run the API functional tests and portal functional tests respectively.

##### API Functional Tests

To run functional tests you can simply execute `build/func-test-api.sh`, but if you'd like finer-grained control, you
can work with the `Makefile` in `test/api/functional`:

```shell
# Build and then run the function test container
make -C test/api/functional build run
```

During iterative test development, you can use `make run-local` which will bind-mount the current functional tests in
the container, allowing for easier test development.

Additionally, you can pass `--interactive` to `make run` or `make run-local` to drop to a shell inside the container.
From there you can run tests with the `/functional_test/run.sh` command. This allows for finer-grained control over the
test execution process as well as easier inspection of logs.

You can run a specific test by name by running `make build` and `make run -- -k <name of test function>`. Any arguments after
`make run --` will be passed to the test runner [`test/api/functional/run.sh`](test/api/functional/run.sh).

Finally, you can execute `make run-deps-bg` to all of the dependencies for the functional test, but not run the tests.
This is useful if, for example, you want to use an interactive debugger on your local machine, but host all of the
VinylDNS API dependencies in Docker.

#### Setup

We use [pytest](https://docs.pytest.org/en/latest/) for python tests. It is helpful that you browse the documentation so
that you are familiar with pytest and how our functional tests operate.

We also use [PyHamcrest](https://pyhamcrest.readthedocs.io/en/release-1.8/) for matchers in order to write easy to read
tests. Please browse that documentation as well so that you are familiar with the different matchers for PyHamcrest.
There aren't a lot, so it should be quick.

In the `modules/api/src/test/functional` directory are a few important files for you to be familiar with:

- `vinyl_python.py` - this provides the interface to the VinylDNS API. It handles signing the request for you, as well
  as building and executing the requests, and giving you back valid responses. For all new API endpoints, there should
  be a corresponding function in the vinyl_client
- `utils.py` - provides general use functions that can be used anywhere in your tests. Feel free to contribute new
  functions here when you see repetition in the code

In the `modules/api/src/test/functional/tests` directory, we have directories / modules for different areas of the
application.

- `batch` - for managing batch updates
- `internal` - for internal endpoints (not intended for public consumption)
- `membership` - for managing groups and users
- `recordsets` - for managing record sets
- `zones` - for managing zones

##### Functional Test Context

Our functional tests use `pytest` contexts. There is a main test context that lives in `shared_zone_test_context.py`
that creates and tears down a shared test context used by many functional tests. The beauty of pytest is that it will
ensure that the test context is stood up exactly once, then all individual tests that use the context are called using
that same context.

The shared test context sets up several things that can be reused:

1. An `ok` user and group
1. A `dummy` user and group - a separate user and group helpful for testing access controls and authorization
1. An `ok.` zone accessible only by the `ok` user and `ok` group
1. A `dummy.` zone accessible only by the `dummy` user and `dummy` group
1. An IPv6 reverse zone
1. A normal IPv4 reverse zone
1. A classless IPv4 reverse zone
1. A parent zone that has child zones - used for testing NS record management and zone delegations

##### Partitioning

Each of the test zones are configured in a `partition`. By default, there are four partitions. These partitions are
effectively copies of the zones so that parallel tests can run without interfering with one another.

For instance, there are four zones for the `ok` zone: `ok1`, `ok2`, `ok3`, and `ok4`. The functional tests will handle
distributing which zone is being used by which of the parallel test runners.

As such, you should **never** hardcode the name of the zone. Always get the zone from the `shared_zone_test_context`.
For instance, to get the `ok` zone, you would write:

```python
zone = shared_zone_test_context.ok_zone
zone_name = shared_zone_test_context.ok_zone["name"]
zone_id = shared_zone_test_context.ok_zone["id"]
```

##### Really Important Test Context Rules!

1. Try to use the `shared_zone_test_context` whenever possible!  This reduces the time it takes to run functional
   tests (which is in minutes).
1. Be mindful of changes to users, groups, and zones in the shared test context, as doing so could impact downstream
   tests
1. If you do modify any entities in the shared zone context, roll those back when your function completes!

##### Managing Test Zone Files

When functional tests are run, we spin up several Docker containers. One of the Docker containers is a Bind9 DNS server.
If you need to add or modify the test DNS zone files, you can find them in
`quickstart/bind9/zones`
