---
layout: docs
title:  "Setup MySQL"
section: "operator_menu"
---

# Setup MySQL
Our instance of VinylDNS currently stores some tables in MySQL, though all tables and a queue implementation are available in MySQL. Note
that the `batch_change` and `zone` tables are _only_ available in MySQL. 

The motivation to split databases was due to the query limitations available in AWS DynamoDB.  Currently, the following tables are present in
our instance:

* `zone` - holds zones
* `zone_access` - holds user or group identifiers that have access to zones
* `batch_change` - holds batch changes (multiple changes across zones in a single batch)
* `single_change` - holds individual changes within a `batch_change`
* `user` - holds user information, including access keys and secrets
*  `record-set` - holds record data

## Setting up the database
VinylDNS uses [Flyway](https://flywaydb.org/) to manage SQL migrations.  This means that any database changes, including
creating the database, adding tables, etc. are all _automatically applied_ when VinylDNS starts up.  You do not need
to do anything other than giving access to VinylDNS API from your MySQL server instance.  You can view the database
schema and migrations in the `mysql` module [db/migration folder](https://github.com/vinyldns/vinyldns/tree/master/modules/mysql/src/main/resources/db/migration)

VinylDNS uses [HikariCP](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby) for a high-speed connection
pool.

## Configuring MySQL
Before you can configure MySQL, make note of the host, username, and password that you will be using.
Follow the [API Database Configuration](config-api.html#database-configuration) to complete the setup.
