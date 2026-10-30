---
layout: docs
title:  "Verify Docker Images"
section: "operator_menu"
---

# Verify Docker Images
Official VinylDNS images (`vinyldns/api` and `vinyldns/portal`) are signed with [Sigstore cosign](https://docs.sigstore.dev/cosign/verifying/verify/)
when they are published. Signing is "keyless": each image is signed by the VinylDNS GitHub Actions release workflow that
built it, using a short-lived certificate tied to that workflow. There is no public key to download; you verify the
signature against the workflow identity and the GitHub OIDC issuer instead.

## Trusted Identities
A signature is trusted only when its certificate was issued by GitHub Actions to one of the VinylDNS release workflows,
running from a release tag or `main`.

| Field | Value |
|---|---|
| OIDC issuer | `https://token.actions.githubusercontent.com` |
| Stable releases | `https://github.com/vinyldns/vinyldns/.github/workflows/release.yml@refs/tags/v<version>` |
| Beta releases | `https://github.com/vinyldns/vinyldns/.github/workflows/release-beta.yml@refs/tags/v<version>` or `...@refs/heads/main` |
| `vnext` builds | `https://github.com/vinyldns/vinyldns/.github/workflows/release-vnext.yml@refs/heads/main` |

All of these are matched by the following identity expression, which the release workflows also use to check every
image they publish:

```
^https://github\.com/vinyldns/vinyldns/\.github/workflows/(release|release-beta|release-vnext)\.yml@refs/(heads/main|tags/v.+)$
```

## Verify an Image
Install [cosign](https://docs.sigstore.dev/cosign/system_config/installation/), then:

```shell
cosign verify vinyldns/api:<version> \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  --certificate-identity-regexp '^https://github\.com/vinyldns/vinyldns/\.github/workflows/(release|release-beta|release-vnext)\.yml@refs/(heads/main|tags/v.+)$'
```

A successful verification lists the checks performed and exits `0`. An unsigned image, or one signed by any other
identity, fails with `no matching signatures` and a non-zero exit code.

To pin a single release exactly, use `--certificate-identity` instead of `--certificate-identity-regexp`, for example
`--certificate-identity 'https://github.com/vinyldns/vinyldns/.github/workflows/release.yml@refs/tags/v0.22.0'`.

## Verify and Deploy by Digest
A tag such as `latest` or `0.22.0` can be moved to point at a different image between the time you verify it and the
time you deploy it. To make sure the image you run is the image you verified, resolve the tag to its digest once, then
verify and deploy that digest:

{% raw %}
```shell
DIGEST=$(docker buildx imagetools inspect vinyldns/api:<version> --format '{{json .Manifest}}' | jq -r .digest)

cosign verify "vinyldns/api@${DIGEST}" \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  --certificate-identity-regexp '^https://github\.com/vinyldns/vinyldns/\.github/workflows/(release|release-beta|release-vnext)\.yml@refs/(heads/main|tags/v.+)$'

docker pull "vinyldns/api@${DIGEST}"
```
{% endraw %}

Use the same `vinyldns/api@sha256:...` reference in your deployment manifests. The version tag and its floating tag
(`latest`, or `vnext` for `vnext-<date>`) always point at the same signed digest.

In Kubernetes, admission controllers such as [Kyverno](https://kyverno.io/docs/writing-policies/verify-images/sigstore/)
or the [Sigstore policy-controller](https://docs.sigstore.dev/policy-controller/overview/) can enforce this check for every
`vinyldns/*` image using the issuer and identity above.

## Migrating from Docker Content Trust
Images published before cosign signing was introduced (`v0.22.0-beta.3` and earlier) were signed only with Docker
Content Trust (DCT), which Docker is retiring. Newer images have no DCT signature, so:

* Remove `DOCKER_CONTENT_TRUST=1` from any environment that pulls new VinylDNS images; with it set, pulls of new tags fail.
* Replace any `docker trust inspect` or Notary checks with the `cosign verify` command above.
* `cosign verify` reports `no matching signatures` for older, DCT-only images. Upgrade to a cosign-signed release to verify them.
