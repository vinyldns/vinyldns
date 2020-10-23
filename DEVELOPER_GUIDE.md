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
- Java 8 (at least u162)
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
* `dockerComposeUp` to spin up the dependencies on your machine from the root project.
* `project api` to change the sbt project to the API
* `reStart` to start up the API server
* Wait until you see the message `VINYLDNS SERVER STARTED SUCCESSFULLY` before working with the server
* To stop the VinylDNS server, run `reStop` from the api project
* To stop the dependent Docker containers, change to the root project `project root`, then run `dockerComposeStop` from the API project

See the [API Configuration Guide](https://www.vinyldns.io/operator/config-api) for information regarding API configuration.

### Starting the Portal
To run the portal locally, you _first_ have to start up the VinylDNS API Server (see instructions above).  Once
that is done, in the same `sbt` session or a different one, go to `project portal` and then execute `;preparePortal; run`.

See the [Portal Configuration Guide](https://www.vinyldns.io/operator/config-portal) for information regarding portal configuration.

### Loading test data
Normally the portal can be used for all VinylDNS requests. Test users are locked down to only have access to test zones,
which the portal connection modal has not been updated to incorporate. To connect to a zone with testuser, you will need to use an alternative
client and set `isTest=true` on the zone being connected to.

Use the vinyldns-js client (Note, you need Node installed):

```
git clone https://github.com/vinyldns/vinyldns-js.git
cd vinyldns-js
npm install
export VINYLDNS_API_SERVER=http://localhost:9000
export VINYLDNS_ACCESS_KEY_ID=testUserAccessKey
export VINYLDNS_SECRET_ACCESS_KEY=testUserSecretKey
npm run repl
> var groupId;
> vinyl.createGroup({"name": "test-group", "email":"test@test.com", members: [{id: "testuser"}], admins: [{id: "testuser"}]}).then(res => {groupId = res.id}).catch(err => {console.log(err)});
> vinyl.createZone ({name: "ok.", isTest: true, adminGroupId: groupId, email: "test@test.com"}).then(res => { console.log(res) }).catch(err => { console.log(err) })

You should now be able to see the zone in the portal at localhost:9001 when logged in as username=testuser password=testpassword
```

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

1. Type `dockerComposeUp` to start up dependent background services
1. Go to the target module in sbt, example: `project api`
1. Run all integration tests by typing `it:test`.
1. Run an individual integration test by typing `it:testOnly *MyIntegrationSpec`
1. You can background compile as well if working on a single spec by using `~it:testOnly *MyIntegrationSpec`
1. You must stop (`dockerComposeStop`) and start (`dockerComposeUp`) the dependent services from the root project (`project root`) before you rerun the tests.
1. For the mysql module, you may need to wait up to 30 seconds after starting the services before running the tests for setup to complete.

#### Running both

You can run all unit and integration tests for the api and portal by running `sbt verify`

### Functional Tests
When adding new features, you will often need to write new functional tests that black box / regression test the
API.  We have over 350 (and growing) automated regression tests.  The API functional tests are written in Python
and live under `modules/api/functional_test`.

#### Running functional tests
To run functional tests, make sure that you have started the API server (directions above).
Then in another terminal session:

1. `cd modules/api/functional_test`
1. `./run.py live_tests -v`

You can run a specific test by name by running `./run.py live_tests -v -k <name of test function>`

You run specific tests for a portion of the project, say recordsets, by running `./run.py live_tests/recordsets -v`

#### Our Setup
We use [pytest](https://docs.pytest.org/en/latest/) for python tests.  It is helpful that you browse the documentation
so that you are familiar with pytest and how our functional tests operate.

We also use [PyHamcrest](https://pyhamcrest.readthedocs.io/en/release-1.8/) for matchers in order to write easy
to read tests.  Please browse that documentation as well so that you are familiar with the different matchers
for PyHamcrest.  There aren't a lot, so it should be quick.


In the `modules/api/functional_test` directory are a few important files for you to be familiar with:

* vinyl_client.py - this provides the interface to the VinylDNS API.  It handles signing the request for you, as well
as building and executing the requests, and giving you back valid responses.  For all new API endpoints, there should
be a corresponding function in the vinyl_client
* utils.py - provides general use functions that can be used anywhere in your tests.  Feel free to contribute new
functions here when you see repetition in the code

Functional tests run on every build, and are designed to work _in every environment_.  That means locally, in Docker,
and in production environments.

In the `modules/api/functional_test/live_tests` directory, we have directories / modules for different areas of the application.

* membership - for managing groups and users
* recordsets - for managing record sets
* zones - for managing zones
* internal - for internal endpoints (not intended for public consumption)
* batch - for managing batch updates

##### Functional Test Context
Our func tests use pytest contexts.  There is a main test context that lives in `shared_zone_test_context.py`
that creates and tears down a shared test context used by many functional tests.  The
beauty of pytest is that it will ensure that the test context is stood up exactly once, then all individual tests
that use the context are called using that same context.

The shared test context sets up several things that can be reused:

1. An `ok` user and group
1. A `dummy` user and group - a separate user and group helpful for testing access controls and authorization
1. An `ok.` zone accessible only by the `ok` user and `ok` group
1. A `dummy.` zone accessible only by the `dummy` user and `dummy` group
1. An IPv6 reverse zone
1. A normal IPv4 reverse zone
1. A classless IPv4 reverse zone
1. A parent zone that has child zones - used for testing NS record management and zone delegations

##### Really Important Test Context Rules!

1. Try to use the `shared_zone_test_context` whenever possible!  This reduces the time
it takes to run functional tests (which is in minutes).
1. Limit changes to users, groups, and zones in the shared test context, as doing so could impact downstream tests
1. If you do modify any entities in the shared zone context, roll those back when your function completes!

##### Managing Test Zone Files
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
* `target/vinyldns-elasticmq.log` - the ElasticMQ (SQS) server logs
* `target/vinyldns-functest.log` - the output of running the functional tests
* `target/vinyldns-mysql.log` - the MySQL server logs

When the func tests complete, the entire Docker setup will be automatically torn down.
