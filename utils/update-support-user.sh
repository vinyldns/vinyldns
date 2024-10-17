#!/usr/bin/env bash

usage () {
    echo -e "Usage: update-support-user.sh [OPTIONS] <username> <enableSupport>\n"
    echo -e "Description: Updates a user in VinylDNS to a support user, or removes the user as a support user.\n"
    echo -e "Required Parameters:"
    echo -e "username\tThe VinylDNS user for which to change the support flag"
    echo -e "enableSupport\t'true' to set the user as a support user; 'false' to remove support privileges\n"
    echo -e "OPTIONS:"
    echo -e "  -u|--user \tDatabase user name for accessing the VinylDNS database (DB_USER - default=root)"
    echo -e "  -p|--password\tDatabase user password for accessing the VinylDNS database (DB_PASS - default=pass)"
    echo -e "  -h|--host\tDatabase host name for the mysql server (DB_HOST - default=vinyldns-integration)"
    echo -e "  -n|--name\tName of the VinylDNS database, (DB_NAME - default=vinyldns)"
    echo -e "  -c|--port\tPort of the VinylDNS database, (DB_PORT - default=19002)"
}

DIR=$( cd "$(dirname "$0")" || exit ; pwd -P )
VINYL_ROOT=$DIR/..
WORK_DIR=${VINYL_ROOT}/docker

DB_USER=${DB_USER:-root}
DB_PASS=${DB_PASS:-pass}
DB_HOST=${DB_HOST:-vinyldns-integration}
DB_NAME=${DB_NAME:-vinyldns}
DB_PORT=${DB_PORT:-19002}

while [ "$1" != "" ]; do
	case "$1" in
		-u | --user  	    ) DB_USER="$2";  shift;;
    -p | --password  	) DB_PASS="$2";  shift;;
		-h | --host     	) DB_HOST="$2";  shift;;
		-n | --name     	) DB_NAME="$2";  shift;;
		-c | --port       ) DB_PORT="$2";  shift;;
		* ) break;;
	esac
	shift
done

VINYL_USER="$1"
MAKE_SUPPORT=""

if [ $2 = "True" ]; then
    MAKE_SUPPORT="--support=yes"
elif [ $2 = "False" ]; then
	MAKE_SUPPORT="--support=no"
fi

ERROR=
if [ -z "$DB_USER" ]; then
    ERROR="1"
fi

if [ -z "$DB_PASS" ]; then
    ERROR="1"
fi

if [ -z "$DB_HOST" ]; then
    ERROR="1"
fi

if [ -z "$DB_NAME" ]; then
    ERROR="1"
fi

if [ -z "$VINYL_USER" ]; then
    ERROR="1"
fi

if [ -z "$MAKE_SUPPORT" ]; then
    ERROR="1"
fi

if [ -n "$ERROR" ]; then
    usage
    exit 1
fi

# Build and run the Docker container
cd admin
make build
make run DOCKER_PARAMS="-e \"DB_USER=$DB_USER\" -e \"DB_PASS=$DB_PASS\" -e \"DB_HOST=$DB_HOST\" -e \"DB_NAME=$DB_NAME\" -e \"DB_PORT=$DB_PORT\"" WITH_ARGS="\"$VINYL_USER\" \"$MAKE_SUPPORT\""
