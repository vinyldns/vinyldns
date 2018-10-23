---
layout: docs
title: "API Configuration Guide"
section: "operator_menu"
---

# API Configuration Guide

**Note: ALL configuration assumes a `vinyldns` namespace.  For example, sqs settings would be under `vinyldns.sqs`.**

## Configuration
- [Configuration Overview](#configuration-overview)
- [Configuration API Server](#configuring-api-server)
- [AWS SQS](#aws-sqs)
- [Database Configuration](#database-configuration)
- [Cryptography](#cryptography-settings)
- [Additional Configuration Settings](#additional-configuration-settings)
- [Full Example Config](#full-example-config)

There are a lot of configuration settings in VinylDNS.  So much so that it may seem overwhelming to configure
vinyldns to your environment.  This document describes the configuration settings, highlighting the settings
you are _most likely to change_.  All of the configuration settings are captured at the end.

It is important to note that the `api` and `portal` have _different_ configuration.  We will review the configuration
for each separately.

## Configuration Overview

### How do we config?
All configuration is done using [Typesafe Config](https://github.com/lightbend/config).  It provides a means to
specifying default configurations, and overriding the configured values in a number of ways:

1. The _default_ configuration provides "safe" default values for all configuration.  This makes it possible for you
to only change the configuration values that you _need_ to, and assume the _default_ for the rest.  This can
typically be found in a file named `reference.conf`.  The Typesafe Config library manages populating unspecified values
for you automatically.
1. You can override the `reference.conf` file by providing your own `application.conf` file when the system starts up.
We will review how to do that in the sections that follow.
1. You can override _individual_ configuration properties when the application starts up using standard jvm arguments.
For example, you can specify `-Dmy.config.value=42`, and that will override _both_ application.conf _and_ reference.conf (defaults)
1. You can further override configuration properties with _environment variables_.  The Typesafe Config provides special
syntax that allows you to use environment variables.  You can make the environment variable optional (meaning use it if it is there)
or required (fail to start up without the environment variable).  We will illustrate use of environment variables in this guide.

### Using Environment Variables
We _strongly_ recommend that you use environment variables in particular for secrets.  Laying down environment variables
in a flat file is a security vulnerability for your installation.  To demonstrate environment variable usage, here is a following
snippet...

```yaml
  sqs {
    access-key = ${AWS_ACCESS_KEY}
    secret-key = ${AWS_SECRET_ACCESS_KEY}
    signing-region = ${SQS_REGION}
    service-endpoint = ${SQS_ENDPOINT}
    queue-url = ${SQS_QUEUE_URL}
  }
```

In the example, if any of the values in `${xxx}` are not found in the environment, the application will not start up!

## Configuring API Server
The API configuration has a lot of values, the important ones reviewed here.  There are several configuration
settings that are specific to _your_ environment.

The most important configuration is around your system dependencies. Presently, these are your settings for:
* `AWS SQS`
* `AWS DynamoDB`
* `MySQL`

**We are actively working on supporting different message queues and data stores.  Look for those to become available shortly**

## AWS SQS
Be sure to follow the [AWS SQS Setup Guide](setup-sqs) first to get the values you need to configure here.

```yaml
vinyldns {

  # connection information to sqs
  sqs {
    # AWS_ACCESS_KEY, credential needed to access the SQS queue
    access-key = "XXXXXXXXX"

    # AWS_SECRET_ACCESS_KEY, for accessing the SQS queue
    secret-key = "XXXXXXXXX"

    # AWS Signing Region for the SQS queue, for example "us-east-1".  The region where the sqs queue lives
    signing-region = "us-east-1"

    # The base URL for sqs, example https://sqs.us-east-1.amazonaws.com
    service-endpoint = "https://sqs.us-east-1.amazonaws.com"

    # The full URL to the sqs queue
    queue-url = "https://sqs.us-east-1.amazonaws.com/1111111111/my-vinyldns-queue"

    # The polling interval.  You can increase this to lower the cost of SQS, possibly into the free tier.
    # Set to 5seconds for example
    polling-interval = 250millis
  }
}
```

## Database Configuration
VinylDNS supports both DynamoDB and MySQL backends. You can enable all repos in a single backend, or have a mix of the two.
For each backend, you need to configure the table(s) that should be loaded.

You must have all of the following required API repositories configured in exactly one datastore.
**At the moment, not all repositories are implemented in both datastores, but we are actively working on expanding
MySQL support to all repositories**:
- record-set (dynamodb only)
- record-change (dynamodb only)
- zone-change (mysql or dynamodb)
- user (dynamodb only)
- group (dynamodb only)
- group-change (dynamodb only)
- membership (dynamodb only)
- zone (mysql only)
- batch-change (mysql only)


If using DynamoDB, follow the [AWS DynamoDB Setup Guide](setup-dynamodb) first to get the values you need to configure here.

If using MySQL, follow the [MySQL Setup Guide](setup-mysql) first to get the values you need to configure here.


```yaml
vinyldns {

  # this list should include only the datastores being used by your instance
  data-stores = ["mysql", "dynamodb"]
  
  dynamodb {
      
    # this is the path to the DynamoDB provider. This should not be edited
    # from the default in reference.conf
    class-name = "vinyldns.dynamodb.repository.DynamoDBDataStoreProvider"
    
    settings {
      # AWS_ACCESS_KEY, credential needed to access the SQS queue
      key = "x"
    
      # AWS_SECRET_ACCESS_KEY, credential needed to access the SQS queue
      secret = "x"
    
      # DynamoDB url for the region you are running in, this example is in us-east-1
      endpoint = "https://dynamodb.us-east-1.amazonaws.com"
      
      # DynamoDB region
      region = "us-east-1"
    }
    
    repositories {
      # all repositories with config sections here will be enabled in dynamodb
      record-set {
        # Name of the table where recordsets are saved
        table-name = "recordSetTest"
        # Provisioned throughput for reads
        provisioned-reads = 30
        # Provisioned throughput for writes
        provisioned-writes = 20
      }
      record-change {
        table-name = "recordChangeTest"
        provisioned-reads = 30
        provisioned-writes = 20
      }
      zone-change {
        table-name = "zoneChangesTest"
        provisioned-reads = 30
        provisioned-writes = 20
      }
      user {
        table-name = "usersTest"
        provisioned-reads = 30
        provisioned-writes = 20
      }
      group {
        table-name = "groupsTest"
        provisioned-reads = 30
        provisioned-writes = 20
      }
      group-change {
        table-name = "groupChangesTest"
        provisioned-reads = 30
        provisioned-writes = 20
      }
      membership {
        table-name = "membershipTest"
        provisioned-reads = 30
        provisioned-writes = 20
      }
    }
  }
  
  mysql {
    
    # this is the path to the mysql provider. This should not be edited
    # from the default in reference.conf
    class-name = "vinyldns.mysql.repository.MySqlDataStoreProvider"
    
    settings {
      # the name of the database, recommend to leave this as is
      name = "vinyldns"
      
      # the jdbc driver, recommended to leave this as is
      driver = "org.mariadb.jdbc.Driver"

      # the URL used to create the schema, typically this will be without the "database" name
      migration-url = "jdbc:mariadb://localhost:19002/?user=root&password=pass"

      # the main connection URL
      url = "jdbc:mariadb://localhost:19002/vinyldns?user=root&password=pass"

      # the user to connect to MySQL
      user = "root"

      # the password to connect to MySQL
      password = "pass"

      # the maximum number of connections to scale the connection pool to
      maximum-pool-size = 20

      # the maximum number of seconds to wait for a connection from the connection pool
      connection-timeout-millis = 1000

      # The max lifetime of a connection in a pool.  Should be several seconds shorter than the database imposed connection time limit
      max-life-time = 600000
    }
    
    repositories {
      # all repositories with config sections here will be enabled in mysql
      zone {
        # no additional settings for repositories enabled in mysql
      },
      batch-change {
      }
    }
  }
}
```

## Cryptography Settings
VinylDNS uses symmetric cryptography in order to encrypt/decrypt sensitive information in the system.  This includes
TSIG keys and user secrets.  Cryptography is used in _both_ the portal as well as the api.

Cryptography is _pluggable_, meaning you can bring your own crypto with you.  All that is required is to provide an
implementation of [CryptoAlgebra](https://github.com/vinyldns/vinyldns/blob/master/modules/core/src/main/scala/vinyldns/core/crypto/CryptoAlgebra.scala)
using a crypto library of choice.  The default implementation is `NoOpCrypto`, which does not do any encryption (not recommended for production).
VinylDNS provides a cryptography implementation called `JavaCrypto` that you can use for production.  The example that follows illustrates
using the provided `JavaCrypto`.

If you create your own implementation, you have to build your jar and make it (and all dependencies) available to the VinylDNS API
and the VinylDNS portal.

The following are the configuration settings for crypto.  Notice here the _only_ thing we see is the `type`.  The `type`
is the fully qualified class name for the `CryptoAlgebra` you will be using.  If your crypto implementation requires
additional settings, they will be configured inside the `crypto` element, adjacent to the `type`.

```yaml
vinyldns {
  crypto {
    type = "vinyldns.core.crypto.JavaCrypto"
    secret = "8B06A7F3BC8A2497736F1916A123AA40E88217BE9264D8872597EF7A6E5DCE61"
  }
}
```

## Default Zone Connections
VinylDNS allows you to specify zone connection information _for each zone_.

VinylDNS has **2** connections for each zone:

1. The DDNS connection - used for making DDNS updates to the zone
1. The Transfer connection - used for making AXFR requests for zone syncing with the DNS backend

VinylDNS supports the ability to provide _default_ connections, so keys do not need to be generated for _every_ zone.  This assumes a
"default" DNS backend.

```yaml
vinyldns {
  # the DDNS connection information for the default dns backend
  defaultZoneConnection {
    # this is not really used, but must be set, usually set to the keyName itself, or a descriptive name if you are interested
    name = "vinyldns."

    # the name of the TSIG key
    keyName = "vinyldns."

    # the TSIG secret key
    key = "nzisn+4G2ldMn0q1CV3vsg=="

    # the host name or IP address, note you can add a port if not using the default by settings hostname:port
    primaryServer = "ddns1.foo.bar.com"
  }

  # the AXFR connection information for the default dns backend
  defaultTransferConnection {
    name = "vinyldns."
    keyName = "vinyldns."
    key = "nzisn+4G2ldMn0q1CV3vsg=="
    primaryServer = "vinyldns-bind9"
  }
}
```

## Additional Configuration Settings

### Approved Name Servers
When running a large DNS installation, allowing users the ability to self-manage zone delegations can lead to a lot
of problems when not done properly.  Also, allowing delegation to untrusted DNS servers can be a security risk.

To "lock down" zone delegation, you can configure name servers that you trust, so zone delegation is controlled.

The entries in the list can be host names, IP addresses, or regular expressions.

```yaml
approved-name-servers = [
  "172.17.42.1.",
  "ddns1.foo.bar.",
  ".*awsdns.*"
]
```

### Processing Disabled
The processing disabled flag can be used if doing a blue/green deployment.  When processing is disabled, the
VinylDNS engine will _not_ be actively polling the message queue for messages.

`processing-disabled = false | true`

### Color
For blue-green deployments, you can configure the color of the current node.  Not applicable to every environment.

`color = "green"`

### Version
Version of the application that is deployed.  Currently, this is a configuration value.

`version = "0.8.0"`

**Note: You can get installation information including color, version, default key name, and processing-disabled by hitting the _status_ endpoint GET /status**

### HTTP Host and Port
To specify what host and port to bind to when starting up the API server, default is 9000.

```yaml
rest {
  host = "0.0.0.0"
  port = 9000
}
```

### Sync Delay
VinylDNS uses a "sync-delay" setting that prevents users from syncing their zones too frequently.  The settings is
inspected _per zone_, and is the number of milliseconds since the _last_ sync to wait before allowing another sync for
_that_ zone.

```yaml
sync-delay = 10000
```

### Full Example Config
```yaml
# The default application.conf is not intended to be used in production.  It assumes a docker-compose
# setup for all of the services.  Provide your own application.conf on the docker mount with your
# own settings
vinyldns {

  # connection information to sqs
  sqs {
    # aws access key and secret.  Not needed for docker-compose setup
    access-key = "x"
    secret-key = "x"
    signing-region = "x"
    service-endpoint = "http://vinyldns-elasticmq:9324/"
    queue-url = "http://vinyldns-elasticmq:9324/queue/vinyldns"
  }

  # host and port the server binds to.  This should not be changed
  rest {
    host = "0.0.0.0"
    port = 9000
  }

  # the delay between zone syncs so we are not syncing too often
  sync-delay = 10000

  # crypto settings for symmetric cryptography of secrets in the system
  # Note: for production systems secrets should not live in plain text in a file
  crypto {
    type = "vinyldns.core.crypto.NoOpCrypto"
  }

  # both datastore options are in use
  data-stores = ["mysql", "dynamodb"]
  
  dynamodb {
    class-name = "vinyldns.dynamodb.repository.DynamoDBDataStoreProvider"
    
    settings {
      key = "x"
      secret = "x"
      endpoint = "http://vinyldns-dynamodb:8000"
      region = "us-east-1"
    }
    
    repositories {
      record-set {
        table-name = "recordSet"
        provisioned-reads = 30
        provisioned-writes = 20
      }
      record-change {
        table-name = "recordChange"
        provisioned-reads = 30
        provisioned-writes = 20
      }
      zone-change {
        table-name = "zoneChanges"
        provisioned-reads = 30
        provisioned-writes = 20
      }
      user {
        table-name = "users"
        provisioned-reads = 30
        provisioned-writes = 20
      }
      group {
        table-name = "groups"
        provisioned-reads = 30
        provisioned-writes = 20
      }
      group-change {
        table-name = "groupChanges"
        provisioned-reads = 30
        provisioned-writes = 20
      }
      membership {
        table-name = "membership"
        provisioned-reads = 30
        provisioned-writes = 20
      }
    }
  }
  
  mysql {
    class-name = "vinyldns.mysql.repository.MySqlDataStoreProvider"
    
    settings {
      name = "vinyldns"
      driver = "org.mariadb.jdbc.Driver"
      migration-url = "jdbc:mariadb://localhost:19002/?user=root&password=pass"
      url = "jdbc:mariadb://localhost:19002/vinyldns?user=root&password=pass"
      user = "root"
      password = "pass"
      pool-max-size = 20
      connection-timeout-millis = 1000
      max-life-time = 600000
    }
    
    repositories {
      zone {
      },
      batch-change {
      }
    }
  }

  # the DDNS connection information for the default dns backend
  defaultZoneConnection {
    name = "vinyldns."
    keyName = "vinyldns."
    key = "nzisn+4G2ldMn0q1CV3vsg=="
    primaryServer = "vinyldns-bind9"
  }

  # the AXFR connection information for the default dns backend
  defaultTransferConnection {
    name = "vinyldns."
    keyName = "vinyldns."
    key = "nzisn+4G2ldMn0q1CV3vsg=="
    primaryServer = "vinyldns-bind9"
  }

  # the max number of changes in a single batch change.  Change carefully as this has performance
  # implications
  batch-change-limit = 20
}

# Akka settings, these should not need to be modified unless you know akka http really well.
akka {
  loglevel = "INFO"
  loggers = ["akka.event.slf4j.Slf4jLogger"]
  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
  logger-startup-timeout = 30s
}

akka.http {
  server {
    # The time period within which the TCP binding process must be completed.
    # Set to `infinite` to disable.
    bind-timeout = 5s

    # Show verbose error messages back to the client
    verbose-error-messages = on
  }

  parsing {
    # akka-http doesn't like the AWS4 headers
    illegal-header-warnings = on
  }
}

```