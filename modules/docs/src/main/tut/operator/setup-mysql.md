---
layout: docs
title:  "Setup MySQL"
section: "operator_menu"
---

# Setup MySQL
VinylDNS stores some tables in MySQL.  The motivation to split databases was due to the Query limitations avaiable
in AWS DynamoDB.  Currently, the following tables are present in MySQL

* `zone` - holds zones
* `zone_access` - holds user or group identifiers that have access to zones
* `batch_change` - holds batch changes (multiple changes across zones in a single batch)
* `single_change` - holds individual changes within a batch_change

## Setting up the database
VinylDNS uses [Flyway](https://flywaydb.org/) to manage SQL migrations.  This means that any database changes, including
creating the database, adding tables, etc. are all _automatically applied_ when VinylDNS starts up.  You do not need
to do anything other than giving access to VinylDNS API from your MySQL server instance.  You can view the database
schema and migrations in the api module [db/migration folder](https://github.com/vinyldns/vinyldns/tree/master/modules/api/src/main/resources/db/migration)

VinylDNS uses [HikariCP](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby) for a high-speed connection
pool.

## Configuring MySQL
Before you can configure MySQL, make note of the host, username, and password that you will be using.
Follow the [API Database Configuration](config-api#database-configuration) to complete the setup.