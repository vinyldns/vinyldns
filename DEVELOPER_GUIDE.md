# Getting Started

## Table of Contents
- [Project Structure](#project-structure)
- [Developer Requirements](#developer-requirements)
- [Docker](#docker)
- [Configuration](#configuration)
- [Starting the API Server Locally](#starting-the-api-server-locally)
- [Starting the Portal Locally](#starting-the-portal-locally)
- [Testing](#testing)
- [Handy Scripts](#handy-scripts)

## Project Structure
Make sure that you have the requirements installed before proceeding.

The main codebase is a multi-module Scala project with multiple sub-modules.  To start working with the project,
from the root directory run `sbt`.  Most of the code can be found in the `modules` directory.
The following modules are present:

* `root` - this is the parent project, if you run tasks here, it will run against all sub-modules
* `api` - the engine behind VinylDNS.  Has the REST API that all things interact with.
* `core` - contains code applicable across modules
* `portal` - the web user interface for VinylDNS
* `docs` - the API Documentation for VinylDNS

### VinylDNS API
The API is the RESTful API for interacting with VinylDNS.  The code is found in `modules/api`.  The following technologies are used:

* [Akka HTTP](https://doc.akka.io/docs/akka-http/current/) - Used primarily for REST and HTTP calls.  We migrated
code from Spray.io, so Akka HTTP was a rather seamless upgrade
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

#### The API Code
The API code can be found in `modules/api`

* `functional_test` - contains the python black box / regression tests
* `src/it` - integration tests
* `src/main` - the main source code
* `src/test` - unit tests
* `src/universal` - items that are packaged in the docker image for the VinylDNS API

The package structure for the source code follows:

* `vinyldns.api.domain` - contains the core front-end logic.  This includes things like the application services,
repository interfaces, domain model, validations, and business rules.
* `vinyldns.api.engine` - the back-end processing engine.  This is where we process commands including record changes,
zone changes, and zone syncs.
* `vinyldns.api.protobuf` - marshalling and unmarshalling to and from protobuf to types in our system
* `vinyldns.api.repository` - repository implementations live here
* `vinyldns.api.route` - http endpoints

### VinylDNS Portal
The Portal project (found in `modules/portal`) is the user interface for VinylDNS.  The project is built
using:
* [Play Framework](https://www.playframework.com/documentation/2.6.x/Home)
* [AngularJS](https://angularjs.org/)

Tne portal is _mostly_ a shim around the API.  Most actions in the user interface and translated into API calls.

The features that the Portal provides that are not in the API include:
* Authentication against LDAP
* Creation of users - when a user logs in for the first time, VinylDNS automatically creates a user for them in the
database with their LDAP information.

## Developer Requirements
- sbt
- Java 8
- Python 2.7
- virtualenv
- docker

## Docker
Be sure to install the latest version of [docker](https://docs.docker.com/).  You must have docker running in order to work with VinylDNS on your machine.
Be sure to start it up if it is not running before moving further.

#### Starting a vinyldns installation locally in docker
Running `./bin/docker-up-vinyldns.sh` will spin up the production docker images of the vinyldns-api and vinyldns-portal.
This will startup all the dependencies as well as the api and portal servers.  
It will then ping the api on `http://localhost:9000` and the portal on `http://localhost:9001` and notify you if either failed to start.
The portal can be viewed in a browser at `http://localhost:9001`

Alternatively, you can manually run docker-compose with this config `docker/docker-compose-build.yml`.
From the root directory run `docker-compose -f ./docker/docker-compose-build.yml up -d`

To stop the local setup, run `./bin/remove-vinyl-containers.sh` from the project root.

### Configuration for the vinyldns-api image
VinylDNS depends on several dependencies including mysql, sqs, dynamodb and a DNS server. These can be passed in as
environment variables, or you can override the config file with your own settings.

By default, the api image is configured to run in a docker compose environment locally. To run in a production environment,
you would have to configure the portal appropriately. 

#### Environment variables
1. `MYSQL_ADDRESS` - the IP address of the mysql server; defaults to `vinyldns-mysql` assuming a docker compose setup
1. `MYSQL_PORT` - the port of the mysql server; defaults to 3306

#### Volume Mounts
vinyldns exposes volumes that allow the user to customize the runtime.  Those mounts include:

* `/opt/docker/lib_extra` - place here additional jar files that need to be loaded into the classpath when the application starts up.
This is used for "plugins" that are proprietary or not part of the standard build.  All jar files here will be placed on the class path.
* `/opt/docker/conf` - place an `application.conf` file here with your own custom settings.  This can be easier than passing in environment
variables.

#### Ports
vinyldns only exposes port 9000 for HTTP access to all endpoints

### Configuration for the vinyldns-portal image
Like the api image, the portal image is configured to run in a docker compose environment locally. To run in a production environment,
you would have to configure the portal appropriately using these settings. 

#### Volume mounts
* `/opt/docker/lib_extra` - place here additional jar files that need to be loaded into the classpath when the application starts up.
This is used for "plugins" that are proprietary or not part of the standard build.  All jar files here will be placed on the class path.
* `/opt/docker/conf/application.conf` - to override default configuration settings
* `/opt/docker/conf/application.ini` - to pass additional JVM options 
* `/opt/docker/conf/trustStore.jks` - to make available a custom trustStore, which has to be set in `/opt/docker/conf/application.ini` as `-Djavax.net.ssl.trustStore=/opt/docker/conf/trustStore.jks`

#### Custom LDAP config
In `docker/portal/application.conf` there is a switch for `portal.test_login = true`. This is set by default so 
developers can login to the portal with username=testuser and password=testpassword. Custom LDAP settings will have to 
be set in `docker/portal/application.conf`

#### Configuring a custom Java trustStore
To add a custom Java trustStore, say for LDAP certs, add the trustStore to `docker/portal/trustStore.jks`. Then
add `-Djavax.net.ssl.trustStore=/opt/docker/conf/trustStore.jks` to `docker/portal/application.ini`.  

#### Additional JVM parameters
Additional JVM parameters can be added to `docker/portal/application.ini`

### Validating everything
VinylDNS comes with a build script `./build.sh` that validates, verifies, and runs functional tests.  Note: This
takes a while to run, and typically is only necessary if you want to simulate the same process that runs on the build
servers

When functional tests run, you will see a lot of output intermingled together across the various containers.  You can view only the output
of the functional tests at `target/vinyldns-functest.log`.  If you want to see the docker log output from any one
container, you can view them after the tests complete at:

* `target/vinyldns-api.log` - the api server logs
* `target/vinyldns-bind9.log` - the bind9 DNS server logs
* `target/vinyldns-dynamodb.log` - the DynamoDB server logs
* `target/vinyldns-elasticmq.log` - the ElasticMQ (SQS) server logs
* `target/vinyldns-functest.log` - the output of running the functional tests
* `target/vinyldns-mysql.log` - the MySQL server logs

When the func tests complete, the entire docker setup will be automatically torn down.

## Starting the API server locally
To start the API for integration, functional, or portal testing.  Start up sbt by running `sbt` from the root directory.
* `project api` to change the sbt project to the api
* `dockerComposeUp` to spin up the dependencies on your machine.
* `reStart` to start up the API server
* Wait until you see the message `VINYLDNS SERVER STARTED SUCCESSFULLY` before working with the server
* To stop the VinylDNS server, run `reStop` from the api project
* To stop the dependent docker containers, run `dockerComposeStop` from the api project

## Starting the Portal locally
To run the portal locally, you _first_ have to start up the VinylDNS API Server (see instructions above).  Once
that is done, in the same `sbt` session or a different one, go to `project portal` and then execute `;preparePortal; run`.

### Testing the portal against your own LDAP directory
Often, it is valuable to test locally hitting your own LDAP directory.  This is possible to do, just take care when
following these steps as to not accidentally check in secrets or your own environment information in future PRs.

1. Create a file `modules/portal/conf/local.conf`.  This file is added to `.gitignore` so it should not be committed
1. Configure your own LDAP settings in local.conf. See the LDAP section of `modules/portal/conf/application.conf` for the
expected format. Be sure to set `portal.test_login = false` in that file to override the test setting
1. If you need SSL certs, you will need to create a java keystore that holds your SSL certificates.  The portal only
_reads_ from the trust store, so you do not need to pass in the password to the app.
1. Put the trust store in `modules/portal/private` directory.  It is also added to .gitignore to prevent you from
accidentally committing it.
1. Start `sbt` in a separate terminal by running `sbt -Djavax.net.ssl.trustStore="modules/portal/private/trustStore.jks"`
1. Go to `project portal` and type `;preparePortal;run` to start up the portal
1. You can now login using your own LDAP repository going to http://localhost:9001/login

## Configuration
Configuration of the application is done using [Typesafe Config](https://github.com/typesafehub/config).

* `reference.conf` contains the _default_ configuration values.
* `application.conf` contains environment specific overrides of the defaults

## Testing
### Unit Tests
1. First, start up your scala build tool: `sbt`.  I usually do a *clean* immediately after starting.
1. (Optionally) Go to the project you want to work on, for example `project api` for the api; `project portal` for the portal.
1. Run _all_ unit tests by just running `test`
1. Run an individual unit test by running `testOnly *MySpec`
1. If you are working on a unit test and production code at the same time, use `~` that automatically background compiles for you!
`~testOnly *MySpec`

### Integration Tests
Integration tests are used to test integration with _real_ dependent services.  We use docker to spin up those
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

To run functional tests, make sure that you have started the api server (directions above).  Then outside of sbt, `cd modules/api/functional_test`.

### Managing Test Zone Files
When functional tests are run, we spin up several docker containers.  One of the docker containers is a Bind9 DNS
server.  If you need to add or modify the test DNS zone files, you can find them in
`docker/bind9/zones`

## Handy Scripts
### Start up complete local API and Portal servers
`bin/docker-up-vinyldns.sh` - this will start up the `vinyldns/api:latest` and `vinyldns/portal:latest` images from docker hub

> Note: to start up images with local changes, run `sbt ;project api; docker:publishLocal; project portal; docker:publishLocal`

The following ports and services are available:

- mysql - 3306
- dynamodb - 19000
- bind9 - 19001
- sqs - 9324
- api server (main vinyl backend app) - 9000

To kill the environment, run `bin/remove-vinyl-containers.sh`

### Start up a DNS server
`bin/docker-up-dns-server.sh` - fires up a DNS server.  Sometimes, especially when developing func tests, you want
to quickly see how new test zones / records behave without having to fire up an entire environment.  This script
fires up _only_ the dns server with our test zones.  The DNS server is accessible locally on port 19001.

### Publish the API docker image
`bin/docker-publish-api.sh` - publishes the API docker image.  You must be logged into the repo you are publishing to
using `docker login`, or create a file in `~/.ivy/.dockerCredentials` that has your credentials in it following the format defined in https://www.scala-sbt.org/1.x/docs/Publishing.html
