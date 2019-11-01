# Maintainers

## Table of Contents
* [Docker Content Trust](#docker-content-trust)
    * [Docker Hub Account](#docker-hub-account)
    * [Delegating Image Signing](#delegating-image-signing)
        * [Setting up Notary](#setting-up-notary)
        * [Generating a Personal Delegation Key](#generating-a-personal-delegation-key)
        * [Adding a Delegation Key To a Repository](#adding-a-delegation-key-to-a-repository)
        * [Pushing a Signed Image with your Delegation Key](#pushing-a-signed-image-with-your-delegation-key)
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
* vinyldns/test-bind9: contains the setup to run functional tests 
* vinyldns/test: has the actual functional tests pinned to a version of VinylDNS 

The offline root key and repository keys are managed by the core maintainer team. The keys managed are:

* root key: also known as the offline key, used to create the separate repository signing keys
* api key: used to sign tagged images in vinyldns/api
* portal key: used to sign tagged images in vinyldns/portal
* bind9 key: used to sign tagged images in the vinyldns/bind9
* test-bind9 key: used to sign tagged images in the vinyldns/test-bind9
* test key: used to sign tagged images in the vinyldns/test

These keys are named in a <hash>.key format, e.g. 5526ecd15bd413e08718e66c440d17a28968d5cd2922b59a17510da802ca6572.key,
do not change the names of the keys. 

Docker expects these keys to be saved in `~/.docker/trust/private`. Each key is encrypted with a passphrase, that you 
must have available when pushing an image.

### Docker Hub Account

If you don't have one already, make an account on Docker Hub. Get added as a Collaborator to vinyldns/api, vinyldns/portal,
and vinyldns/bind9

### Delegating Image Signing
Someone with our keys can sign images when pushing, but instead of sharing those keys we can utilize 
notary to delegate image signing permissions in a safer way. Notary will have you make a public-private key pair and 
upload your public key. This way you only need your private key, and a developer's permissions can easily be revoked. 

#### Setting up Notary
If you do not already have notary: 

1. Download the latest release for your machine at https://github.com/theupdateframework/notary/releases, 
for example, on a mac download the precompiled binary `notary-Darwin-amd64`
1. Rename the binary to notary, and choose a location where it will live,
e.g. `cd ~/Downloads/; mv notary-Darwin-amd64 notary; mv notary ~/Documents/notary; cd ~/Documents`
1. Make it executable, e.g. `chmod +x notary`
1. Add notary to your path, e.g. `vim ~/.bashrc`, add `export PATH="$PATH":<path to notary>`
1. Create a `~/.notary/config.json` with
 
``` 
{
   "trust_dir" : "~/.docker/trust",
   "remote_server": {
     "url": "https://notary.docker.io"
   }
 }
```

You can test notary with `notary -s https://notary.docker.io -d ~/.docker/trust list docker.io/vinyldns/api`, in which
you should see tagged images for the VinylDNS API

> Note: you'll pretty much always use the `-s https://notary.docker.io -d ~/.docker/trust` args when running notary,
it will be easier for you to alias a command like `notarydefault` to `notary -s https://notary.docker.io -d ~/.docker/trust`
in your `.bashrc` 

#### Generating a Personal Delegation Key
1. `cd` to a directory where you will save your delegation keys and certs
1. Generate your private key: `openssl genrsa -out delegation.key 2048`
1. Generate your public key: `openssl req -new -sha256 -key delegation.key -out delegation.csr`, all fields are optional,
but when it gets to your email it makes sense to add that
1. Self-sign your public key (valid for one year): 
`openssl x509 -req -sha256 -days 365 -in delegation.csr -signkey delegation.key -out delegation.crt`
1. Change the `delegation.crt` to some unique name, like `my-name-vinyldns-delegation.crt`
1. Give your `my-name-vinyldns-delegation.crt` to someone that has the root keys and passphrases so 
they can upload your delegation key to the repository

#### Adding a Delegation Key to a Repository
This expects you to have the root keys and passphrases for the Docker repositories

1. List current keys: `notary -s https://notary.docker.io -d ~/.docker/trust delegation list docker.io/vinyldns/api`
1. Add team member's public key: `notary delegation add docker.io/vinyldns/api targets/releases <team members delegation crt path> --all-paths`
1. Push key: `notary publish docker.io/vinyldns/api`
1. Repeat above steps for `docker.io/vinyldns/portal`

Add their key ID to the table below, it can be viewed with `notary -s https://notary.docker.io -d ~/.docker/trust delegation list docker.io/vinyldns/api`.
It will be the one that didn't show up when you ran step one of this section

| Name | Key ID |
|----------------|------------------------------------------------------------------
| Nima Eskandary | 66027c822d68133da859f6639983d6d3d9643226b3f7259fc6420964993b499a, cdca33de91c54f801d89240d18b5037e274461ba1c88c10451070c97e9f665b4 |
| Rebecca Star | 04285e24d3b9a8b614b34da229669de1f75c9faa471057e8b4a7d60aac0d5bf5 |
| Michael Ly |dd3a5938fc927de087ad4b59d6ac8f62b6502d05b2cc9b0623276cbac7dbf05b |

#### Pushing a Signed Image with your Delegation Key
1. Run `notary key import <path to private delegation key> --role user`
1. You will have to create a passphrase for this key that encrypts it at rest. Use a password generator to make a 
strong password and save it somewhere safe, like apple keychain or some other password manager
1. From now on `docker push` will be try to sign images with the delegation key if it was configured for that Docker 
repository

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
staging repo.
1. When it comes to the sonatype stage, you will need the passphrase handy for the signing key, [Sonatype Credentials](#sonatype-credentials)
1. Assuming things were successful, make a pr since sbt release auto-bumped `version.sbt` and made a commit for you
1. Run `./build/docker-release.sh --branch [TAG CREATED FROM PREVIOUS STEP, e.g. v0.9.3] --clean --push`
1. You will need to have your keys ready so you can sign each image as it is published.
