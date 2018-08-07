# Maintainers

## Table of Contents
- [Pushing images to Docker Hub](#pushing-images-to-docker-hub)

## Pushing images to Docker Hub

### Docker content trust
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

### Pushing a signed image
First make sure you have been given the correct permissions in the vinyldns org on Docker Hub. Then, publish the image 
you will be pushing locally first. For the API, run `sbt ;project:api;docker:publishLocal`, for the portal, 
run `sbt ;project:portal;docker:publishLocal`. The image tag will be whatever the project version is set to in 
`build.sbt` 

Then make sure `DOCKER_CONTENT_TRUST=1` is in your environment, and run `docker push vinyldns/<repo>:<tag>`. e.g. 
`docker push vinyldns/api:0.1.0`. When prompted, enter the passphrase for the root key, then the passphrase for the 
Docker repo you are pushing to. 

### Delegating image signing
The above method will work as long as a pusher has the required keys and passphrases. Optionally, the following steps can be taken
for core maintainers to push signed images via notary, without having to store the keys on their machine.

The documentation reference for this is https://docs.docker.com/engine/security/trust/trust_delegation/#generating-delegation-keys

#### Setting up notary
If you do not already have notary: 

1. Download the latest release for your machine at https://github.com/theupdateframework/notary/releases, 
for example, on a mac download the precompiled binary `notary-Darwin-amd64`
1. Rename the binary to notary, and choose a location where it will live,
e.g. `cd ~/Downloads/; mv notary-Darwin-amd64 notary; mv notary ~/Documents/notary`
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

You can test notary with `notary -s https://notary.docker.io -d ~/.docker/trust" list docker.io/vinyldns/api`, in which
you should see tagged images for the API

#### Generating a personal delegation key
1. cd to a directory where you will save your delegation keys
1. Generate your private key: `openssl genrsa -out delegation.key 2048`
1. Generate your public key: `openssl req -new -sha256 -key delegation.key -out delegation.csr`
1. Self-sign your public key (valid for one year): 
`openssl x509 -req -sha256 -days 365 -in delegation.csr -signkey delegation.key -out delegation.crt`
1. Change the `delegation.crt` to some unique name, like `my-name-vinyldns-delegation.crt`
1. Give your `my-name-vinyldns-delegation.crt` to someone that has the root keys and passphrases so 
they can add your delegation key to the repository

#### Adding a delegation key to a repository
This expects you to have the keys and passhphrases for the project that you are adding the delegation to

1. `notary delegation add docker.io/vinyldns/api targets/releases <team members delegation crt path> --all-paths`
1. `notary publish docker.io/vinyldns/api`
1. Repeat above steps for `docker.io/vinyldns/portal`, `docker.io/vinyldns/bind9`

#### Pushing trusted data as a collaborator
Run `notary key import <path to private delegation key> --role user`, after this `docker push` will sign 
images with the delegation key if your public key has been added to the repository, and you do not have the 
root keys and passphrases
