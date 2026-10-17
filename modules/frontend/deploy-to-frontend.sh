#!/usr/bin/env bash
# deploy-to-frontend.sh
#
# Builds the React frontend directly into public/ (Play's static-assets dir).
# Vite is configured with outDir: 'public' so no copy step is needed.
# Also copies shared microsite images into public/img so Play can serve
# /img/* from /public only (no runtime dependency on docs paths).
#
# Usage:
#   cd modules/frontend
#   ./deploy-to-frontend.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Installing frontend npm packages..."
cd "${SCRIPT_DIR}"
npm install -f --no-audit --no-fund

echo "==> Building React frontend..."
npm run build

MICROSITE_IMG_DIR="${SCRIPT_DIR}/../docs/src/main/resources/microsite/img"
PUBLIC_IMG_DIR="${SCRIPT_DIR}/public/img"

if [[ -d "${MICROSITE_IMG_DIR}" ]]; then
	echo "==> Copying microsite images into public/img..."
	mkdir -p "${PUBLIC_IMG_DIR}"
	cp -R "${MICROSITE_IMG_DIR}/." "${PUBLIC_IMG_DIR}/"
else
	echo "Warning: microsite image directory not found: ${MICROSITE_IMG_DIR}"
fi

echo ""
echo "npm build success."
echo "React assets in public/:"
ls -1 "${SCRIPT_DIR}/public/index.html"
echo "${SCRIPT_DIR}/public/assets/:"
ls -1 "${SCRIPT_DIR}/public/assets/"
echo "${SCRIPT_DIR}/public/img/ (sample):"
ls -1 "${SCRIPT_DIR}/public/img/" | head -n 10
echo ""
