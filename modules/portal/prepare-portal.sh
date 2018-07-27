#!/usr/bin/env bash
DIR=$( cd $(dirname $0) ; pwd -P )

cd $DIR

npm install

npm install grunt -g
grunt default
$DIR/../../bin/add-license-headers.sh -d=$DIR/public/lib -f=js

cd -
