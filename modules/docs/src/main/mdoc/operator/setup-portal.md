---
layout: docs
title:  "Setup the Portal Server"
section: "operator_menu"
---

# Setup the Portal Server
The Portal Server is the web UI for VinylDNS.  To setup the Portal server, follow these steps:

1. [Setup API Server](setup-api.html)
1. [Setup LDAP](setup-ldap.html)
1. [Configure Portal Server](config-portal.html)
1. [Using the Portal Docker Image](#using-the-portal-docker-image)

Once you have you pre-requisites ready, review the [Portal Configuration Guide](config-portal.html) for how to build out
your configuration file.

# Using the Portal Docker Image
The Portal server is provided as a [VinylDNS Portal Image](https://hub.docker.com/r/vinyldns/portal/).

The API server is _stateless_, allowing you to run multiple instances in multiple data centers for high-availability
purposes.

## Volume mounts
* `/opt/docker/lib_extra` - place here additional jar files that need to be loaded into the classpath when the application starts up.
This is used for "plugins" that are proprietary or not part of the standard build.  All jar files here will be placed on the class path.
* `/opt/docker/conf/application.conf` - to override default configuration settings.  Follow the [Portal Configuration Guide](config-portal.html)
* `/opt/docker/conf/application.ini` - to pass additional JVM options
* `/opt/docker/conf/trustStore.jks` - to make available a custom trustStore, which has to be set in `/opt/docker/conf/application.ini` as `-Djavax.net.ssl.trustStore=/opt/docker/conf/trustStore.jks`

## Configuring a custom Java trustStore
To add a custom Java trustStore for LDAP certs, add the trustStore to `/opt/docker/conf/trustStore.jks`.
 Then add `-Djavax.net.ssl.trustStore=/opt/docker/conf/trustStore.jks` to `/opt/docker/conf/application.ini`.

## Additional JVM parameters
Additional JVM parameters can be added to `/opt/docker/conf/application.ini`
