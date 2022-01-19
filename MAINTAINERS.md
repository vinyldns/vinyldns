# Maintainers

## Table of Contents

* [Docker Content Trust](#docker-content-trust)
* [Release Process](#release-process)

## Docker Content Trust

Official VinylDNS Docker images are signed when being pushed to Docker Hub. Docs for Docker Content Trust can be found
at <https://docs.docker.com/engine/security/trust/>.

Content trust is enabled through the `DOCKER_CONTENT_TRUST` environment variable, which must be set to `1`. It is
recommended that in your `~/.bashrc`, you have `export DOCKER_CONTENT_TRUST=1` by default, and if you ever want to turn
it off for a Docker command, add the `--disable-content-trust` flag to the command,
e.g. `docker pull --disable-content-trust ...`.

There are multiple Docker repositories on Docker Hub under
the [vinyldns organization](https://hub.docker.com/u/vinyldns/dashboard/). Namely:

* vinyldns/api: images for vinyldns core api engine
* vinyldns/portal: images for vinyldns web client

The offline root key and repository keys are managed by the core maintainer team. The keys managed are:

* root key: also known as the offline key, used to create the separate repository signing keys
* api key: used to sign tagged images in vinyldns/api
* portal key: used to sign tagged images in vinyldns/portal

## Release Process

The release process is automated by GitHub Actions.

To start, create a release in GitHub with the same tag as the version found in `version.sbt`.

The release will perform the following actions:

1. Published Docker images to `hub.docker.com`
2. Attached artifacts created by the build to the GitHub release
