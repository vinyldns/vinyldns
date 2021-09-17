#!/bin/bash -e

if [ ! -d "./.virtualenv" ]; then
    echo "Creating virtualenv..."
    virtualenv --clear --python="$(which python3.9)" ./.virtualenv
fi

if ! diff ./requirements.txt ./.virtualenv/requirements.txt &> /dev/null; then
     echo "Installing dependencies..."
     .virtualenv/bin/python ./.virtualenv/bin/pip install --index-url https://pypi.python.org/simple/ -r ./requirements.txt
     cp ./requirements.txt ./.virtualenv/
fi
