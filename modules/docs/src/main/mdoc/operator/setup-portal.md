---
layout: docs title:  "Setup the Portal Server"
section: "operator_menu"
---

# Setup the Portal Server

The Portal Server is the web UI for VinylDNS. To setup the Portal server, follow these steps:

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

* `/opt/vinyldns/lib_extra` - place here additional jar files that need to be loaded into the classpath when the
  application starts up. This is used for "plugins" that are proprietary or not part of the standard build. All jar
  files here will be placed on the class path.
* `/opt/vinyldns/conf/application.conf` - to override default configuration settings. Follow
  the [Portal Configuration Guide](config-portal.html)

## Configuring a custom Java trustStore

To add a custom Java trustStore for LDAP certs, add the trustStore to `/opt/vinyldns/conf/trustStore.jks`. Then
add `-Djavax.net.ssl.trustStore=/opt/vinyldns/conf/trustStore.jks` to the `JVM_OPTS` environment variable for the
container.

Example:

```text
docker run -e JVM_OPTS="-Djavax.net.ssl.trustStore=/opt/vinyldns/conf/trustStore.jks" ...
```

## Additional JVM parameters

Additional JVM parameters can be added to the `JVM_OPTS` environment variable 
