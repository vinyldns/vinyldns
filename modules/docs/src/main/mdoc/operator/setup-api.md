---
layout: docs
title:  "Setup API Server"
section: "operator_menu"
---

# Setup API Server
The API Server is the main run-time for VinylDNS.  To setup the API server, follow these steps:

1. [Pre-requisites](pre.md)
1. [Setup AWS DynamoDB](setup-dynamodb.md)
1. [Setup MySQL](setup-mysql.md)
1. [Setup AWS SQS](setup-sqs.md)
1. [Configure API Server](config-api.md)
1. [Using the API Docker Image](#using-the-api-docker-image)

## Using the API Docker Image
The API server is provided via the [VinylDNS API Image](https://hub.docker.com/r/vinyldns/api/).
The docker image allows you to mount your own config, as well as your own external dependency jars.

The API server is _stateless_, allowing you to run multiple instances in multiple data centers for high-availability
purposes.

**Note: If using VinylDNS Java Crypto and the pre-requisites defined here, no additional jars need to be loaded.**

## Environment variables
1. `MYSQL_ADDRESS` - the IP address of the mysql server; defaults to `vinyldns-mysql` assuming a docker compose setup
1. `MYSQL_PORT` - the port of the mysql server; defaults to 3306

## Volume Mounts
The API exposes volumes that allow the user to customize the runtime.  Those mounts include:

* `/opt/docker/lib_extra` - place here additional jar files that need to be loaded into the classpath when the application starts up.
This is used for "plugins" that are proprietary or not part of the standard build.  All jar files here will be placed on the class path.
* `/opt/docker/conf` - place an `application.conf` file here with your own custom settings.  Once you have your config created,
place here.

## Ports
The API only exposes port 9000 for HTTP access to all endpoints
