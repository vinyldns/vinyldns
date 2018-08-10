---
layout: docs
title: "Configuration Guide"
section: "operator"
---

**Note: ALL configuration assumes a `vinyldns` namespace.  For example, sqs settings would be under `vinyldns.sqs`.**

# Configuration Guide
There are a lot of configuration settings in VinylDNS.  So much so that it may seem overwhelming to configure
vinyldns to your environment.  This document describes the configuration settings, high-lighting the settings
you are _most likely to change_.  All of the configuration settings are captured at the end.

It is important to note that the `api` and `portal` have _different_ configuration.  We will review the configuration
for each separately.

## How do we config?
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

## API Configuration
The API configuration has a lot of values, the important ones reviewed here.  There are several configuration
settings that are specific to _your_ environment.

The most important configuration is around your system dependencies. Presently, these are your settings for:
* `AWS SQS`
* `AWS DynamoDB`
* `MySQL`

**We are actively working on supporting different message queues and data stores.  Look for those to become available shortly**

### AWS SQS
SQS is used to provide high-availability and failover in the event that a node crashes mid-stream while processing a message.
The backend processing for VinylDNS is built to be idempotent so changes can be fully re-applied.  The SQS queue
also provides a mechanism to _throttle_ updates, in the event that an out-of-control client submits thousands or
millions of concurrent requests, they will all be throttled through SQS.

You must setup an SQS queue before you can start working with VinylDNS.  An [AWS SQS Getting Started Guide](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-getting-started.html)
provides the information you need to setup your queue.

#### Setting up AWS SQS
The traffic with AWS SQS is rather low.  Presently, Comcast operates multiple SQS queues across multiple environments (dev, staging, prod),
and incur a cost of less than $10 USD per month.  SQS allows up to 1MM requests per month in the _free_ tier.  It is possible
to operate VinylDNS entirely in the "free" tier.  You can "tune down" your usage by increasing your polling interval.

The following SQS Queue Attributes are recommended (these are in AWS when you create an SQS Queue):

* `Queue Type` - Standard
* `Delivery Delay` - 0 seconds
* `Default Visibility Timeout` - 1 minute (how long it takes a record change to complete, usually a second)
* `Message Retention Period` - 4 days
* `Maximum Message Size` - 256KB
* `Receive Message Wait Time` - 0 seconds
* `Maximum Receives` - 100 (how many times a message will be retried before failing.  Note: if any messages retry more than 100 times, there is likely a problem requiring immediate attention)
* `Dead Letter Queue` - use a dead letter queue for all queues

The following configuration elements are provided...
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

### AWS DYNAMODB
[AWS DynamoDB](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Introduction.html) is the default database
for _most_ of the data that is stored in VinylDNS.  The following tables are used in VinylDNS:

* `RecordSet` - holds record data
* `RecordSetChange` - audit history of all changes made to records
* `User` - holds user information, including access keys and secrets
* `Group` - group information, including name, email and description
* `Membership` - connects users to groups
* `GroupChange` - holds audit history for groups
* `UserChange` - holds audit history for all users (only used in the portal currently)
* `ZoneChange` - audit history for changes to zones (not record related)

AWS DYNAMODB connection information is configured one time, and the same connection is used across all tables.  Therefore,
you must ensure that all tables live inside the _same_ AWS region accessible by the _same_ credentials.

#### Setting up DYNAMODB
**If the tables do not yet exist, starting up the application will _automatically_ create the tables for you.  Starting
up the application for the first time is often the best way to setup the tables, as they do require keys and indexes to be setup.**

[Provisioned Throughput](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ProvisionedThroughput.html)
is a mechanism that controls how many reads and writes can happen concurrently against your tables and indexes.  You can
configure *Auto Scaling* for your tables, so you do not have to worry about these settings.

The most _important_ thing to remember for Provisioned Throughput is that you pay more for _writes_ than _reads_.  To manage,
costs, it is important to use Auto-Scaling, or turn down your provisioned throughput settings to be really low.

If your installation does not have large zones (100,000s of records), and takes relatively low throughput, you can turn
the throughput very low and operate in the "almost" free-tier.  The following guides help you tune your settings:

* `recordSet` - this table (and recordSetChange) require the highest throughput.  If you have large zones, the first time
you load a zone, all records will be loaded into the `recordSet` table.  If the settings are too low, it can take a long time
for the records to be loaded, and worst case scenario the operation will fail.
* `recordSetChange` - every time any record is updated, the audit trail is inserted into the `recordSetChange` table.  This
also should have higher settings, as usage, especially on writes, can be rather high.
* `user` - very low writes, very small data, high read rate (every API call looks up the user info)
* `group` - very low writes, very small data, high read rate (every API call looks up the user groups)
* `membership` - very low writes, very small data, high read rate (every API call looks up the user membership)
* `groupChange` - very low writes, very small data, very low read
* `userChange` - very low writes, very small data, very low read
* `zoneChange` - very low writes, medium amount of data, very low read

The following represents the configuration for dynamodb...

```yaml
vinyldns {

  dynamo {

    # AWS_ACCESS_KEY, credential needed to access the SQS queue
    key = "x"

    # AWS_SECRET_ACCESS_KEY, credential needed to access the SQS queue
    secret = "x"

    # DynamoDB url for the region you are running in, this example is in us-east-1
    endpoint = "https://dynamodb.us-east-1.amazonaws.com"
  }

  # These are settings for each table
  zoneChanges {
    dynamo {
      # Name of the table where zone changes are saved
      tableName = "zoneChange"

      # Provisioned throughput for reads
      provisionedReads = 30

      # Provisioned throughput for writes
      provisionedWrites = 30
    }
  }

  recordSet {
    dynamo {
      tableName = "recordSet"
      provisionedReads = 30
      provisionedWrites = 30
    }
  }

  recordChange {
    dynamo {
      tableName = "recordChange"
      provisionedReads = 30
      provisionedWrites = 30
    }
  }

  users {
    dynamo {
      tableName = "users"
      provisionedReads = 30
      provisionedWrites = 30
    }
  }

  groups {
    dynamo {
      tableName = "groups"
      provisionedReads = 30
      provisionedWrites = 30
    }
  }

  groupChanges {
    dynamo {
      tableName = "groupChanges"
      provisionedReads = 30
      provisionedWrites = 30
    }
  }

  membership {
    dynamo {
      tableName = "membership"
      provisionedReads = 30
      provisionedWrites = 30
    }
  }
}
```

### MYSQL Configuration
VinylDNS uses MySQL to house zone and batch change data.  The decision to use MySQL was due to query patterns that
were just not possible in DynamoDB (or easily possible).  The following data is housed in MySQL:

* `Zone` - zone level metadata and access control rules
* `Zone Access` - determines users and groups that have access to zones
* `Batch Change` - batch change meta data
* `Single Change` - individual changes with a batch change

VinylDNS uses [HikariCP](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby) for a high-speed connection
pool.  **Note: not all settings are available in VinylDNS config.  Additional settings beyond those mentioned will require a small code change.**

#### Setting up MYSQL
Any MySQL server will work with VinylDNS.  You can use AWS RDS, but you do not have to.

**NOTE: This will be updated shortly.  Running flyway migrations out-of-band is not ideal, and cumbersome**

VinylDNS uses [Flyway](https://flywaydb.org/) for database migrations.
Flyway migrations are located in `modules/api/src/main/resources/db/migration`.  To run migrations:

1. Download the Flyway client https://flywaydb.org/documentation/commandline/
2. Configure flyway to point to _your_ MySQL database
3. Configure flyway to point to the VinylDNS migration scripts
4. Run `flyway migrate` to update the schema to current

The following configuration settings are used for MySQL...

```yaml
  db {
    # the name of the database, recommend to leave this as is
    name = "vinyldns"

    # should be false in production
    local-mode = false

    default {
      # the jdbc driver, recommended to leave this as is
      driver = "org.mariadb.jdbc.Driver"

      # the URL used to create the schema, typically this will be without the "database" name
      migrationUrl = "jdbc:mariadb://localhost:19002/?user=root&password=pass"

      # the main connection URL
      url = "jdbc:mariadb://localhost:19002/vinyldns?user=root&password=pass"

      # the user to connect to MySQL
      user = "root"

      # the password to connect to MySQL
      password = "pass"

      # the number of connections that will be acquired in the connection pool for this vinyldns instance
      poolInitialSize = 10

      # the maximum number of connections to scale the connection pool to
      poolMaxSize = 20

      # the maximum number of seconds to wait for a connection from the connection pool
      connectionTimeoutMillis = 1000

      # The max lifetime of a connection in a pool.  Should be several seconds shorter than the database imposed connection time limit
      maxLifeTime = 600000
    }
  }
```

### Cryptography Settings
VinylDNS uses symmetric cryptography in order to encrypt/decrypt sensitive information in the system.  This includes
TSIG keys and user secrets.  Cryptography is used in _both_ the portal as well as the api.

Cryptography is _pluggable_, meaning you can bring your own crypto with you.  All that is required is to provide an
implementation of [CryptoAlgebra](https://github.com/vinyldns/vinyldns/blob/master/modules/core/src/main/scala/vinyldns/core/crypto/CryptoAlgebra.scala)
using a crypto library of choice.  The default implementation is `NoOpCrypto`, which does not do any encryption (not recommended for production).

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

**API Docker Image**
You can create your own docker image, pulling from the officially signed docker images.  Or, you can use the official
VinylDNS docker image, and add your jars to the mount.

* /opt/docker/lib_extra - place here additional jar files that need to be loaded into the classpath when the application starts up.
* /opt/docker/conf - place an application.conf file here with your own custom settings. This can be easier than passing in environment variables.

For example...

```yaml
  vinyldns-api:
    image: "vinyldns/api:8.0.0"
    container_name: "vinyldns-api"
    volumes:
      - ./my/lib:/opt/docker/lib_extra
      - ./my/conf:/opt/docker/conf
```

**Portal Docker Image**
The portal docker image also allows you to bring your own jars.

* /opt/docker/lib_extra - place here additional jar files that need to be loaded into the classpath when the application starts up.
* /opt/docker/conf/application.conf - to override default configuration settings
* /opt/docker/conf/application.ini - to pass additional JVM options
* /opt/docker/conf/trustStore.jks - to make available a custom trustStore for your own SSL, which has to be set in /opt/docker/conf/application.ini as -Djavax.net.ssl.trustStore=/opt/docker/conf/trustStore.jks

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

The entries in the list can be host names, IP addresses, or regular expressions...

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

**Note: You can get installation information including color, version, and processing-disabled by hitting the _status_ endpoint GET /status**

### HTTP Host and Port
To specify what host and port to bind to when starting up the API server, default is 9000...

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

  # default settings point to the setup from docker compose
  db {
    name = "vinyldns"
    # set this to true if we run migrations to initialize the database schema on start
    local-mode = true
    default {
      driver = "org.mariadb.jdbc.Driver"
      migrationUrl = "jdbc:mariadb://vinyldns-mysql:3306/?user=root&password=pass"
      url = "jdbc:mariadb://vinyldns-mysql:3306/vinyldns?user=root&password=pass"
      user = "root"
      password = "pass"
      poolInitialSize = 10
      poolMaxSize = 20
      connectionTimeoutMillis = 1000
      maxLifeTime = 600000
    }
  }

  # dynamodb settings, for local docker compose the secrets are not needed
  dynamo {
    key = "x"
    secret = "x"
    endpoint = "http://vinyldns-dynamodb:8000"
  }

  # dynamodb table settings follow
  zoneChanges {
    dynamo {
      tableName = "zoneChange"
      provisionedReads = 30
      provisionedWrites = 30
    }
  }

  recordSet {
    dynamo {
      tableName = "recordSet"
      provisionedReads = 30
      provisionedWrites = 30
    }
  }

  recordChange {
    dynamo {
      tableName = "recordChange"
      provisionedReads = 30
      provisionedWrites = 30
    }
  }

  users {
    dynamo {
      tableName = "users"
      provisionedReads = 30
      provisionedWrites = 30
    }
  }

  groups {
    dynamo {
      tableName = "groups"
      provisionedReads = 30
      provisionedWrites = 30
    }
  }

  groupChanges {
    dynamo {
      tableName = "groupChanges"
      provisionedReads = 30
      provisionedWrites = 30
    }
  }

  membership {
    dynamo {
      tableName = "membership"
      provisionedReads = 30
      provisionedWrites = 30
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