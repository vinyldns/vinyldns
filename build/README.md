## Building VinylDNS

This folder contains scripts and everything you need to build and test VinylDNS from your own machine.

## Pre-requisites

- `docker` - you will need docker and docker-compose installed locally

## Local Build and Test

1. `./docker-release.sh --clean`
1. Open up `version.sbt` in the root to know the directory (or capture in the script output)
1. Once complete, run a test `./start.sh --version 0.9.4-SNAPSHOT` (replace 0.9.4 with the value in version.sbt).
1. Login to the portal at http://localhost:9001 to verify everything looks good
1. Run `./stop.sh` to bring everything down

### Release Process

1. If you are using image signing / docker notary, be sure you set the environment variable `export DOCKER_CONTENT_TRUST=1`.  
Whether you sign or not is up to your organization.  You need to have notary setup to be able to sign properly.
1. Be sure to login to your docker registry, typically done by `docker login` in the terminal you will release from.
1. The actual version number is pulled from the local `version.sbt` based on the branch specified (defaults to master)
1. Run `./docker-release.sh --push --clean --tag [your tag here] --branch [your branch here]`
    1. typically the `tag` is a build number that you maintain, for example a build number in Jenkins.  Using this field is recommended.  This value will be appended to the generated version as `-b[TAG]`; for example `0.9.4-b123` if using `123` for the tag.
    1. the `branch` defaults to `master` if not specified, you can choose any branch or tag from https://github.com/vinyldns/vinyldns
1. The version generated will be whatever the version is in the `version.sbt` on the `branch` specified (defaults to master)
1. Each of the images are built using the branch specified and the correct version
1. The func tests are run with only smoke tests against the API image to verify it is working
1. If everything passes, and the user specifies `--push`, the images are tagged and released to the docker repository (defaults to docker hub)

### Release Script
Does a clean build off of remote master and tags it with 
`./docker-release.sh --clean --push --tag 123`

The release script is used for doing a release.  It takes the following parameters:

- `-b | --branch [BRANCH]` - what branch to pull from, can be any PR branch or a tag like `v0.9.3`, defaults to `master`
- `-c | --clean` - a flag that indicates to perform a build.  If omitted, the release script will look for a 
pre-built image locally
- `-p | --push` - a flag that indicates to push to the remote docker registry.  The default docker registry 
is `docker.io`
- `-r | --repository [REPOSITORY]` - a URL to your docker registry, defaults to `docker.io`
- `-t | --tag [TAG]` - a build qualifier for this build.  For example, pass in the build number for your 
continuous integration tool
- `-v | --version [VERSION]` - overrides the version calculation and forces the version passed in.  Used primarily for official releases

## Docker Images

The build will generate several VinylDNS docker images that are used to deploy into any environment VinylDNS

- `vinyldns/api` - this is the heart of the VinylDNS system, the backend API
- `vinyldns/portal` - the VinylDNS web UI
- `vinyldns/test-bind9` - a DNS server that is configured to support running the functional tests
- `vinyldns/test` - a container that will execute functional tests, and exit success or failure when the tests are complete

### vinyldns/api

The default build for vinyldns api assumes an **ALL MYSQL** installation. 

**Environment Variables**
- `VINYLDNS_VERSION` - this is the version of VinylDNS the API is running, typically you will not set this as 
it is set as part of the container build

**Volumes**
- `/opt/docker/conf/` - if you need to have your own application config file.  This is **MANDATORY** for
any production environments.  Typically, you will add your own `application.conf` file in here with your settings.
- `/opt/docker/lib_extra/` - if you need to have additional jar files available to your VinylDNS instance.
Rarely used, but if you want to bring your own message queue or database you can put the `jar` files there

### vinyldns/portal

The default build for vinyldns portal assumes an **ALL MYSQL** installation.

**Environment Variables**
- `VINYLDNS_VERSION` - this is the version of VinylDNS the API is running, typically you will not set this as 
it is set as part of the container build

**Volumes**
- `/opt/docker/conf/` - if you need to have your own application config file.  This is **MANDATORY** for
any production environments.  Typically, you will add your own `application.conf` file in here with your settings.
- `/opt/docker/lib_extra/` - if you need to have additional jar files available to your VinylDNS instance.
Rarely used, but if you want to bring your own message queue or database you can put the `jar` files there

### vinyldns/test-bind9

This pulls correct DNS configuration to run func tests.  You can largely disregard what is in here

### vinyldns/test

This is used to run functional tests against a vinyldns instance.  **This is very useful for verifying 
your environment as part of doing an upgrade.**  By default, it will run against a local docker-compose setup.

**Environment Variables**
- `VINYLDNS_URL` - the url to the vinyldns you will test against
- `DNS_IP` - the IP address to the `vinyldns/test-bind9` container that you will use for test purposes
- `TEST_PATTERN` - the actual functional test you want to run.  *Important, set to empty string to run 
ALL test; otherwise, omit the environment variable when you run to just run smoke tests*.

**Example**

This example will run all functional tests on the given VinylDNS url and DNS IP address
`docker run -e VINYLDNS_URL="https://my.vinyldns.example.com" -e DNS_IP="1.2.3.4" -e TEST_PATTERN=""`


