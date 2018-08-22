# Developer Guide

## Table of Contents
- [Developer Requirements](#developer-requirements)
- [Project Layout](#project-layout)
- [Running VinylDNS Locally](#running-vinyldns-locally)
- [Testing](#testing)
- [Validating VinylDNS](#validating-vinyldns)

## Developer Requirements
- Scala 2.12
- sbt 1+
- Java 8
- Python 2.7
- virtualenv
- Docker
- curl
- npm
- grunt

Make sure that you have the requirements installed before proceeding.

## Project Layout
[SYSTEM_DESIGN.md](SYSTEM_DESIGN.md) provides a high-level architectural overview of VinylDNS and interoperability of its components.

The main codebase is a multi-module Scala project with multiple sub-modules.  To start working with the project,
from the root directory run `sbt`.  Most of the code can be found in the `modules` directory.
The following modules are present:

* `root` - this is the parent project, if you run tasks here, it will run against all sub-modules
* [`core`](#core): core modules that are used by both the API and portal, such as cryptography implementations.
* [`api`](#api): the API is the main engine for all of VinylDNS.  This is the most active area of the codebase, as everything else typically just funnels through
the API.
* [`portal`](#portal): The portal is a user interface wrapper around the API.  Most of the business rules, logic, and processing can be found in the API.  The
_only_ features in the portal not found in the API are creation of users and user authentication.
* [`docs`](#documentation): documentation for VinylDNS.

### Core
Code that is used across multiple modules in the VinylDNS ecosystem live in `core`.

#### Code Layout
* `src/main` - the main source code
* `src/test` - unit tests

### API
The API is the RESTful API for interacting with VinylDNS.  The following technologies are used:

* [Akka HTTP](https://doc.akka.io/docs/akka-http/current/) - Used primarily for REST and HTTP calls. 
* [FS2](https://functional-streams-for-scala.github.io/fs2/) - Used for backend change processing off of message queues.
FS2 has back-pressure built in, and gives us tools like throttling and concurrency.
* [Cats Effect](https://typelevel.org/cats-effect/) - We are currently migrating away from `Future` as our primary type
and towards cats effect IO.  Hopefully, one day, all the things will be using IO.
* [Cats](https://typelevel.org/cats) - Used for functional programming.
* [PureConfig](https://pureconfig.github.io/) - For loading configuration values.  We are currently migrating to
use PureConfig everywhere.  Not all the places use it yet.

The API has the following dependencies:
* MySQL - the SQL database that houses zone data
* DynamoDB - where all of the other data is stored
* SQS - for managing concurrent updates and enabling high-availability
* Bind9 - for testing integration with a real DNS system

#### Code Layout
The API code can be found in `modules/api`.

* `functional_test` - contains the python black box / regression tests
* `src/it` - integration tests
* `src/main` - the main source code
* `src/test` - unit tests
* `src/universal` - items that are packaged in the Docker image for the VinylDNS API

The package structure for the source code follows:

* `vinyldns.api.domain` - contains the core front-end logic.  This includes things like the application services,
repository interfaces, domain model, validations, and business rules.
* `vinyldns.api.engine` - the back-end processing engine.  This is where we process commands including record changes,
zone changes, and zone syncs.
* `vinyldns.api.protobuf` - marshalling and unmarshalling to and from protobuf to types in our system
* `vinyldns.api.repository` - repository implementations live here
* `vinyldns.api.route` - HTTP endpoints

### Portal
The project is built using:
* [Play Framework](https://www.playframework.com/documentation/2.6.x/Home)
* [AngularJS](https://angularjs.org/)

The portal is _mostly_ a shim around the API.  Most actions in the user interface are translated into API calls.

The features that the Portal provides that are not in the API include:
* Authentication against LDAP
* Creation of users - when a user logs in for the first time, VinylDNS automatically creates a user and new credentials for them in the
database with their LDAP information.

#### Code Layout
The portal code can be found in `modules/portal`.

* `app` - source code for portal back-end
    * `models` - data structures that are used by the portal
    * `views` - HTML templates for each web page
    * `controllers` - logic for updating data
* `conf` - configurations and endpoint routes
* `public` - source code for portal front-end
    * `css` - stylesheets
    * `images` - images, including icons, used in the portal
    * `js` - scripts
    * `mocks` - mock JSON used in Grunt tests
    * `templates` - modal templates
* `test` - unit tests for portal back-end

### Documentation
Code used to build the microsite content for the API, operator and portal guides at https://www.vinyldns.io/. Some settings for the microsite 
are also configured in `build.sbt` of the project root.

#### Code Layout
* `src/main/resources` - Microsite resources and configurations
* `src/main/tut` - Content for microsite web pages

## Running VinylDNS Locally
VinylDNS can be started in the background by running the [quickstart instructions](README.md#quickstart) located in the README. However, VinylDNS
can also be run in the foreground.

### Starting the API Server
To start the API for integration, functional, or portal testing.  Start up sbt by running `sbt` from the root directory.
* `project api` to change the sbt project to the API
* `dockerComposeUp` to spin up the dependencies on your machine.
* `reStart` to start up the API server
* Wait until you see the message `VINYLDNS SERVER STARTED SUCCESSFULLY` before working with the server
* To stop the VinylDNS server, run `reStop` from the api project
* To stop the dependent Docker containers, run `dockerComposeStop` from the API project

See the [API Configuration Guide](https://www.vinyldns.io/operator/config-api) for information regarding API configuration.

### Starting the Portal
To run the portal locally, you _first_ have to start up the VinylDNS API Server (see instructions above).  Once
that is done, in the same `sbt` session or a different one, go to `project portal` and then execute `;preparePortal; run`.

See the [Portal Configuration Guide](https://www.vinyldns.io/operator/config-portal) for information regarding portal configuration.

## Testing
### Unit Tests
1. First, start up your Scala build tool: `sbt`.  Running *clean* immediately after starting is recommended.
1. (Optionally) Go to the project you want to work on, for example `project api` for the API; `project portal` for the portal.
1. Run _all_ unit tests by just running `test`.
1. Run an individual unit test by running `testOnly *MySpec`.
1. If you are working on a unit test and production code at the same time, use `~` (eg. `~testOnly *MySpec`) to automatically background compile for you!

### Integration Tests
Integration tests are used to test integration with _real_ dependent services.  We use Docker to spin up those
backend services for integration test development.

1. Integration tests are currently only in the `api` module.  Go to the module in sbt `project api`
1. Type `dockerComposeUp` to start up dependent background services
1. Run all integration tests by typing `it:test`.
1. Run an individual integration test by typing `it:testOnly *MyIntegrationSpec`
1. You can background compile as well if working on a single spec by using `~it:testOnly *MyIntegrationSpec`

### Functional Tests
When adding new features, you will often need to write new functional tests that black box / regression test the
API.  We have over 350 (and growing) automated regression tests.  The API functional tests are written in Python
and live under `modules/api/functional_test`.

To run functional tests, make sure that you have started the API server (directions above).  Then outside of sbt, `cd modules/api/functional_test`.

### Managing Test Zone Files
When functional tests are run, we spin up several Docker containers.  One of the Docker containers is a Bind9 DNS
server.  If you need to add or modify the test DNS zone files, you can find them in
`docker/bind9/zones`

## Validating VinylDNS
VinylDNS comes with a build script `./build.sh` that validates VinylDNS compiles, verifies that unit tests pass, and then runs functional tests.  
Note: This takes a while to run, and typically is only necessary if you want to simulate the same process that runs on the build servers.

When functional tests run, you will see a lot of output intermingled together across the various containers.  You can view only the output
of the functional tests at `target/vinyldns-functest.log`.  If you want to see the Docker log output from any one container, you can view
them after the tests complete at:

* `target/vinyldns-api.log` - the API server logs
* `target/vinyldns-bind9.log` - the Bind9 DNS server logs
* `target/vinyldns-dynamodb.log` - the DynamoDB server logs
* `target/vinyldns-elasticmq.log` - the ElasticMQ (SQS) server logs
* `target/vinyldns-functest.log` - the output of running the functional tests
* `target/vinyldns-mysql.log` - the MySQL server logs

When the func tests complete, the entire Docker setup will be automatically torn down.
