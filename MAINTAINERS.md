# Maintainers

## Table of Contents

* [Image Signing](#image-signing)
* [Release Process](#release-process)

## Image Signing

Official VinylDNS Docker images are signed with [Sigstore cosign](https://docs.sigstore.dev/cosign/signing/overview/)
using keyless signing: the release workflows sign each image with a short-lived certificate issued to the GitHub Actions
workflow through OIDC, so there are no signing keys or secrets to manage. Consumer verification steps are in the
[operator guide](modules/docs/src/main/mdoc/operator/verify-images.md).

Publishing is handled by `build/docker/publish_signed_image.sh`, called from `make publish` and `make publish-vnext`. It:

1. pushes the version tag (`<version>` or `vnext-<date>`) and resolves its digest
2. signs that digest with cosign
3. verifies the signature against the trusted release identity, and checks that an unexpected identity is rejected
4. only then points the floating tag (`latest` or `vnext`) at the same digest

Any failure fails the release job, and the floating tag is not moved. Each run records the published digest in the
job summary. Because signing needs a GitHub Actions OIDC token, images can only be published from the release workflows.

The trusted identity is defined in three places that must be kept in sync:

* `build/docker/publish_signed_image.sh` (`COSIGN_IDENTITY_REGEXP` default)
* `.github/workflows/test-image-signing.yml` (`RELEASE_IDENTITY_REGEXP`)
* `modules/docs/src/main/mdoc/operator/verify-images.md`

`.github/workflows/test-image-signing.yml` exercises the same script against a throwaway image on `ttl.sh`. It runs on
pull requests that touch the signing code, and can be run manually.

Images released before the switch to cosign were signed with Docker Content Trust, which Docker is retiring.

## Release Process

The release process is automated by GitHub Actions.

To start, create a release in GitHub with the same tag as the version found in `version.sbt`.

The release will perform the following actions:

1. Published Docker images to `hub.docker.com`
2. Attached artifacts created by the build to the GitHub release
