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
  echo -n "Removing $(realpath --relative-to="$DIR" "$p").." && \
  { { rm -rf "$p" &> /dev/null  && echo "done."; } || { echo -e "\e[93mERROR\e[0m: you may need to be root"; exit 1; } }
fi; done
