#!/usr/bin/env bash

usage () {
    echo -e "Description: Updates a user in VinylDNS to a support user, or removes the user as a support user.\n"
    echo -e "Usage: update-support-user.sh [OPTIONS] <username> <enableSupport>\n"
    echo -e "Required Parameters:"
    echo -e "username\tThe VinylDNS user for which to change the support flag"
    echo -e "enableSupport\t'true' to set the user as a support user; 'false' to remove support privileges\n"
    echo -e "OPTIONS:"
    echo -e "Must define as an environment variables the following (or pass them in on the command line)\n"
    echo -e "DB_USER (user name for accessing the VinylDNS database)"
    echo -e "DB_PASS (user password for accessing the VinylDNS database)"
    echo -e "DB_HOST (host name for the mysql server of the VinylDNS database)"
    echo -e "DB_NAME (name of the VinylDNS database, defaults to vinyldns)"
    echo -e "DB_PORT (port of the VinylDNS database, defaults to 19002)\n"
    echo -e "  -u|--user \tDatabase user name for accessing the VinylDNS database"
    echo -e "  -p|--password\tDatabase user password for accessing the VinylDNS database"
    echo -e "  -h|--host\tDatabase host name for the mysql server"
    echo -e "  -n|--name\tName of the VinylDNS database, defaults to vinyldns"
    echo -e "  -c|--port\tPort of the VinylDNS database, defaults to 19002"
}

DIR=$( cd "$(dirname "$0")" || exit ; pwd -P )
VINYL_ROOT=$DIR/..
WORK_DIR=${VINYL_ROOT}/docker

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
		-c | --port       ) DB_PORT="$2";  shift;;
		* ) break;;
	esac
	shift
done

VINYL_USER="$1"
MAKE_SUPPORT="$2"

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


if [[ -z "$VINYL_USER" ]]
then
    echo "Parameter 'username' not specified"
    ERROR="1"
fi

if [[ -z "$MAKE_SUPPORT" ]]
then
    echo "Parameter 'enableSupport' not specified"
    ERROR="1"
fi

if [[ -n "$ERROR" ]]
then
    usage
    exit 1
fi

# Copy the proto definition to the Docker context and build
cd admin
make build
make run DOCKER_PARAMS="-e \"DB_USER=$DB_USER\" -e \"DB_PASS=$DB_PASS\" -e \"DB_HOST=$DB_HOST\" -e \"DB_NAME=$DB_NAME\" -e \"DB_PORT=$DB_PORT\"" WITH_ARGS="\"$VINYL_USER\" \"$MAKE_SUPPORT\""
