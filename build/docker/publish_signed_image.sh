#!/usr/bin/env bash
#
# Pushes an image tag, signs its digest with cosign (keyless), verifies the signature against the
# trusted VinylDNS release identity, and only then points any floating tags (e.g. `latest`) at that digest.
#
# Requires: docker (logged in), docker buildx, cosign, jq, and a GitHub Actions OIDC token (id-token: write).
#
set -euo pipefail

usage() {
  echo "USAGE: publish_signed_image.sh <image> <tag> [floating-tag...]"
  echo -e "\te.g. publish_signed_image.sh vinyldns/api 0.22.0 latest"
}

if [[ $# -lt 2 ]]; then
  usage
  exit 1
fi

IMAGE="$1"
TAG="$2"
shift 2
FLOATING_TAGS=("$@")

ISSUER="${COSIGN_ISSUER:-https://token.actions.githubusercontent.com}"
IDENTITY_REGEXP="${COSIGN_IDENTITY_REGEXP:-^https://github\.com/vinyldns/vinyldns/\.github/workflows/(release|release-beta|release-vnext)\.yml@refs/(heads/main|tags/v.+)$}"
UNEXPECTED_IDENTITY="https://github.com/vinyldns/vinyldns/.github/workflows/unexpected.yml@refs/heads/main"

resolve_digest() {
  docker buildx imagetools inspect "$1" --format '{{json .Manifest}}' | jq -er .digest
}

docker push "${IMAGE}:${TAG}"
DIGEST="$(resolve_digest "${IMAGE}:${TAG}")"
IMAGE_REF="${IMAGE}@${DIGEST}"
echo "Pushed ${IMAGE}:${TAG} as ${IMAGE_REF}"

cosign sign --yes "${IMAGE_REF}"

cosign verify "${IMAGE_REF}" \
  --certificate-oidc-issuer "${ISSUER}" \
  --certificate-identity-regexp "${IDENTITY_REGEXP}" > /dev/null
echo "Verified ${IMAGE_REF} against ${IDENTITY_REGEXP}"

# Guard against an over-permissive policy: an unrelated identity must not verify
if cosign verify "${IMAGE_REF}" \
  --certificate-oidc-issuer "${ISSUER}" \
  --certificate-identity "${UNEXPECTED_IDENTITY}" > /dev/null 2>&1; then
  echo "ERROR: ${IMAGE_REF} verified against unexpected identity ${UNEXPECTED_IDENTITY}" >&2
  exit 1
fi
echo "Rejected unexpected identity ${UNEXPECTED_IDENTITY}"

# Floating tags move only after verification, and must resolve to the signed digest
for FLOATING_TAG in ${FLOATING_TAGS[@]+"${FLOATING_TAGS[@]}"}; do
  docker buildx imagetools create --tag "${IMAGE}:${FLOATING_TAG}" "${IMAGE_REF}"
  FLOATING_DIGEST="$(resolve_digest "${IMAGE}:${FLOATING_TAG}")"
  if [[ "${FLOATING_DIGEST}" != "${DIGEST}" ]]; then
    echo "ERROR: ${IMAGE}:${FLOATING_TAG} resolved to ${FLOATING_DIGEST}, expected ${DIGEST}" >&2
    exit 1
  fi
  echo "Pointed ${IMAGE}:${FLOATING_TAG} at ${IMAGE_REF}"
done

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  TAG_LIST="\`${TAG}\`"
  for FLOATING_TAG in ${FLOATING_TAGS[@]+"${FLOATING_TAGS[@]}"}; do
    TAG_LIST+=", \`${FLOATING_TAG}\`"
  done
  {
    echo "### ${IMAGE}"
    echo "| | |"
    echo "|---|---|"
    echo "| Digest | \`${DIGEST}\` |"
    echo "| Tags | ${TAG_LIST} |"
    echo "| Trusted identity | verified (\`${IDENTITY_REGEXP//|/\\|}\`) |"
    echo "| Unexpected identity | rejected |"
  } >> "${GITHUB_STEP_SUMMARY}"
fi
