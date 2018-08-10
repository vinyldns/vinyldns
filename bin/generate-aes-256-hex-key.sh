#!/bin/bash

# Generate 256-bit AES key.
#
# Usage:
#  $ ./generate-aes-256-hex-key.sh [passphrase]
# * passphrase: Optional passphrase used to generate secret key. A pseudo-random passphrase will be used if
#               one is not provided.

if [[ ! -z "$1" ]]
then
    echo "Using user-provided passphrase."
fi

PASSPHRASE=${1:-$(openssl rand 32)}

KEY=$(openssl enc -aes-256-cbc -k "$PASSPHRASE" -P -md sha1 | awk -F'=' 'NR == 2 {print $2}')
echo "Your 256-bit AES hex key: $KEY"
