#!/usr/bin/env bash
DIR=$( cd $(dirname $0) ; pwd -P )

cd $DIR

npm install -f

npm install grunt -g -f
grunt default
$DIR/../../utils/add-license-headers.sh -d=$DIR/public/lib -f=js

cd -
