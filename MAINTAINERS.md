# Maintainers

## Table of Contents
* [Docker Content Trust](#docker-content-trust)
* [Sonatype Credentials](#sonatype-credentials)
* [Release Process](#release-process)

## Docker Content Trust

Official VinylDNS Docker images are signed when being pushed to Docker Hub. Docs for Docker Content Trust can be found 
at https://docs.docker.com/engine/security/trust/content_trust/.

Content trust is enabled through the `DOCKER_CONTENT_TRUST` environment variable, which must be set to 1. It is recommended that 
in your `~/.bashrc`, you have `export DOCKER_CONTENT_TRUST=1` by default, and if you ever want to turn it off for a 
Docker command, add the `--disable-content-trust` flag to the command, e.g. `docker pull --disable-content-trust ...`.

There are multiple Docker repositories on Docker Hub under 
the [vinyldns organization](https://hub.docker.com/u/vinyldns/dashboard/). Namely: 

* vinyldns/api: images for vinyldns core api engine 
* vinyldns/portal: images for vinyldns web client
* vinyldns/bind9: images for local DNS server used for testing 

The offline root key and repository keys are managed by the core maintainer team. The keys managed are:

* root key: also known as the offline key, used to create the separate repository signing keys
* api key: used to sign tagged images in vinyldns/api
* portal key: used to sign tagged images in vinyldns/portal
* bind9 key: used to sign tagged images in the vinyldns/bind9

These keys are named in a <hash>.key format, e.g. 5526ecd15bd413e08718e66c440d17a28968d5cd2922b59a17510da802ca6572.key,
do not change the names of the keys. 

Docker expects these keys to be saved in `~/.docker/trust/private`. Each key is encrypted with a passphrase, that you 
must have available when pushing an image.


## Sonatype Credentials

The core module is pushed to oss.sonatype.org under io.vinyldns

To be able to push to sonatype you will need the pgp key used to sign the module. We use a [blackbox](https://github.com/StackExchange/blackbox/)
repo to share this key and its corresponding passphrase. Follow these steps to set it up properly on your local

1. Ensure you have a gpg key setup on your machine by running `gpg -K`, if you do not then run `gpg --gen-key` to create one,
note you will have to generate a strong passphrase and save it in some password manager
1. Make sure you have blackbox, on mac this would be `brew install blackbox`
1. Clone our blackbox repo, get the git url from another maintainer
1. Run `blackbox_addadmin <the email associated with your gpg key>`
1. Commit your changes to the blackbox repo and push to master
1. Have an existing admin pull the repo and run `gpg --keyring keyrings/live/pubring.kbx --export | gpg --import`, and `blackbox_update_all_files`
1. Have the existing admin commit and push those changes to master
1. Back to you - pull the changes, and now you should be able to read those files
1. Run `blackbox_edit_start vinyldns-sonatype-key.asc.gpg` to temporarily decrypt the sonatype signing key
1. Run `gpg --import vinyldns-sonatype-key.asc` to import the sonatype signing key to your keyring
1. Run `blackbox_edit_end vinyldns-sonatype-key.asc.gpg` to re-encrypt the sonatype signing key
1. Run `blackbox_cat vinyldns-sonatype.txt.gpg` to view the passphrase for that key - you will need this passphrase handy when releasing
1. Create a file `~/.sbt/1.0/vinyldns-gpg-credentials` with the content

    ```
    realm=GnuPG Key ID
    host=gpg
    user=vinyldns@gmail.com
    password=ignored-must-use-pinentry
    ```

1. Add credenial configuration to global sbt setting in `~/.sbt/1.0/credential.sbt` with the content

    ```
    credentials += Credentials(Path.userHome / ".sbt" / "1.0" / "vinyldns-gpg-credentials")
    ```

## Release Process

We are using sbt-release to run our release steps and auto-bump the version in `version.sbt`. The `bin/release.sh`
script will first run functional tests, then kick off `sbt release`, which also runs unit and integration tests before
running the release

1. Follow [Docker Content Trust](#docker-content-trust) to setup a notary delegation for yourself
1. Follow [Sonatype Credentials](#sonatype-credentials) to setup the sonatype pgp signing key on your local
1. Make sure you're logged in to Docker with `docker login`
1. Run `bin/release.sh` _Note: the arg "skip-tests" will skip unit, integration and functional testing before a release_
1. You will be asked to confirm the version which originally comes from `version.sbt`. _NOTE: if the version ends with 
`SNAPSHOT`, then the docker latest tag won't be applied and the core module will only be published to the sonatype
staging repo._
1. When it comes to the sonatype stage, you will need the passphrase handy for the signing key, [Sonatype Credentials](#sonatype-credentials)
1. Assuming things were successful, make a pr since sbt release auto-bumped `version.sbt` and made a commit for you
1. Run `./build/docker-release.sh --branch [TAG CREATED FROM PREVIOUS STEP, e.g. v0.9.3] --clean --push`
1. You will need to have your keys ready so you can sign each image as it is published.
