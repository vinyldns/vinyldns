#!/usr/bin/env bash
set -euo pipefail
DIR=$( cd "$(dirname "$0")" ; pwd -P )

cd "${DIR}"

npm install -f --no-audit --no-fund --no-package-lock
npm install grunt -g -f

grunt default
