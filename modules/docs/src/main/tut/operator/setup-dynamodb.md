---
layout: docs
title:  "AWS DynamoDB Setup Guide"
section: "operator_menu"
position: 3
---

# AWS DynamoDB Setup Guide
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

AWS DynamoDB connection information is configured one time, and the same connection is used across all tables.  Therefore,
you must ensure that all tables live inside the _same_ AWS region accessible by the _same_ credentials.

## Setting up DynamoDB
**If the tables do not yet exist, starting up the application will _automatically_ create the tables for you.  Starting
up the application for the first time is often the best way to setup the tables, as they do require attributes and indexes to be setup.**

[Provisioned Throughput](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ProvisionedThroughput.html)
is a mechanism that controls how many reads and writes can happen concurrently against your tables and indexes.  You can
configure *Auto Scaling* for your tables, so you do not have to worry about these settings.

The most _important_ thing to remember for Provisioned Throughput is that you pay more for _writes_ than _reads_.  To manage,
costs, it is important to use Auto-Scaling, or turn down your provisioned throughput settings to be really low.

If your installation does not have large zones (100,000s of records), and takes relatively low throughput, you can turn
the throughput very low and operate in the "almost" free-tier.  The following guides help you tune your settings...

### RecordSet Table
This table (and recordSetChange) require the highest throughput.  If you have large zones, the first time
you load a zone, all records will be loaded into the `recordSet` table.  If the settings are too low, it can take a long time
for the records to be loaded, and worst case scenario the operation will fail.

**Attributes**
* `zone_id` - String(UUID) - the id of the zone the record set belongs to
* `record_set_id` - String(UUID) - the unique id for this record set
* `record_set_name` - String - the record set name
* `record_set_type` - String - the RRType of record set, for example A, AAAA, CNAME
* `record_set_sort` - String - the case in-sensitive name for the record set, used for sort purposes
* `record_set_blob` - Binary - hold a binary array representing the record set.  Currently protocol buffer byte array

**Table Keys**
* HASH = `record_set_id`
* SORT = <none>

**Indexes**
* `zone_id_record_set_name_index` - Global Secondary Index
    * HASH = `zone_id`
    * SORT = `record_set_name`
    * Projection Type = `ALL`
* `zone_id_record_set_sort_index` - Global Secondary Index
    * HASH = `zone_id`
    * SORT = `record_set_sort`
    * Projection Type = `ALL`

### RecordSetChange Table
Every time any record is updated, the audit trail is inserted into the `recordSetChange` table.  This
also should have higher settings, as usage, especially on writes, can be rather high.

### User Table
Very low writes, very small data, high read rate (every API call looks up the user info)

### Group Table
Very low writes, very small data, high read rate (every API call looks up the user groups)

### Membership Table
Very low writes, very small data, high read rate (every API call looks up the user membership)

### GroupChange Table
Very low writes, very small data, very low read

### UserChange Table
Very low writes, very small data, very low read

### ZoneChange Table
Very low writes, medium amount of data, very low read
