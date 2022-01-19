#!/usr/bin/env bash
set -euo pipefail

clean_up() {
  echo "Cleaning up.."
  if [ -d "./.virtualenv" ]; then
    rm -rf ./.virtualenv
  fi
  exit 1
}

if [ ! -d "./.virtualenv" ]; then
  # If we're interrupted during this process, make sure we cleanup
  trap clean_up INT TERM
  echo -n "Creating virtualenv..."
  python3 -m venv --clear ./.virtualenv
  echo "done"
  source ./.virtualenv/bin/activate
  pip3 install -r requirements.txt
else
  # Try to activate; on failure, clean up
  source ./.virtualenv/bin/activate || clean_up

  # We can pass --update as the first parameter to rerun the pip install
  if [ "$1" == "--update" ]; then
    echo "Updating dependencies..."
    pip3 install -r requirements.txt
    shift
  fi
fi

PYTHONPATH=. pytest "$@"
