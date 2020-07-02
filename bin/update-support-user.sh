#!/usr/bin/env bash

usage () {
    echo -e "Description: Updates a user in VinylDNS to a support user, or removes the user as a support user.\n"
    echo -e "Usage: update-support-user.sh [OPTIONS] [VINYLDNS USERNAME] [SUPPORT - True | False]\n"
    echo -e "Must define as an environment variables the following (or pass them in on the command line)\n"
    echo -e "DB_USER (user name for accessing the vinyldns database)"
    echo -e "DB_PASS (user password for accessing the vinyldns database)"
    echo -e "DB_HOST (host name for the mysql server of the vinyldns database)"
    echo -e "DB_NAME (name of the vinyldns database, defaults to vinyldns)\n"
    echo -e "DB_PORT (port of the vinyldns database, defaults to 3306)\n"
    echo -e "\t-u|--user \tuser name for accessing the vinyldns database"
    echo -e "\t-p|--password  \tuser password for accessing the vinyldns database"
    echo -e "\t-h|--host  \thost name for the mysql server of the vinyldns database"
    echo -e "\t-n|--name    \tname of the vinyldns database, defaults to vinyldns"
    echo -e "\t-n|--port    \port of the vinyldns database, defaults to 3306"
}

DIR=$( cd $(dirname $0) ; pwd -P )
WORK_DIR=$DIR/../docker

MAKE_SUPPORT="${@: -1}"
VINYL_USER="${@:(-2):1}"

echo "$VINYL_USER - $MAKE_SUPPORT"

DB_USER=$DB_USER
DB_PASS=$DB_PASS
DB_HOST=$DB_HOST
DB_NAME=${DB_NAME:-vinyldns}
DB_PORT=${DB_PORT:-19002}

while [ "$1" != "" ]; do
	case "$1" in
		-u | --user  	    ) DB_USER="$2";  shift;;
        -p | --password  	) DB_PASS="$2";  shift;;
		-h | --host     	) DB_HOST="$2";  shift;;
		-n | --name     	) DB_NAME="$2";  shift;;
		-c | --port         ) DB_PORT="$2";  shift;;
		* ) break;;
	esac
	shift
done

ERROR=
if [[ -z "$DB_USER" ]]
then
    echo "No DB_USER environment variable found"
    ERROR="1"
fi

if [[ -z "$DB_PASS" ]]
then
    echo "No DB_PASS environment variable found"
    ERROR="1"
fi

if [[ -z "$DB_HOST" ]]
then
    echo "No DB_HOST environment variable found"
    ERROR="1"
fi

if [[ -z "$DB_NAME" ]]
then
    echo "No DB_NAME environment variable found"
    ERROR="1"
fi

if [[ -n "$ERROR" ]]
then
    usage
    exit 1
fi

docker build -t vinyldns/admin $WORK_DIR/admin

docker run \
    -e "DB_USER=$DB_USER" \
    -e "DB_PASS=$DB_PASS" \
    -e "DB_HOST=$DB_HOST" \
    -e "DB_NAME=$DB_NAME" \
    -e "DB_PORT=$DB_PORT" \
    vinyldns/admin:latest update-support-user.py $VINYL_USER $MAKE_SUPPORT
