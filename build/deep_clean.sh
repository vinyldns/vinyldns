#!/usr/bin/env bash
#
# This script will delete all target/ directories and the assembly/ directory
#
set -euo pipefail
DIR=$(
  cd "$(dirname "$0")"
  pwd -P
)

echo "Performing deep clean"
find "${DIR}/.." -type d -name target -o -name assembly | while read -r p; do if [ -d "$p" ]; then
  echo -n "Removing $p.."
  rm -r "$p" || (echo -e "\e[93mError deleting $p, you may need to be root\e[0m"; exit 1)
  echo "done."
fi; done
