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
Each row in the RecordSet table is a `RRSet`, which means it comprises one or more "Records" inside of it.

**Usage**
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
* SORT = `<none>`

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
Each record set change could potentially live inside a `ChangeSet`.  A `ChangeSet` being one or more individual
`RecordSetChange` instances.  Each _record_ in the `RecordSetChange` table corresponds to an individual change
such as "Create a new record set".

**Usage**
Every time any record is updated, the audit trail is inserted into the `recordSetChange` table.  This
also should have higher settings, as usage, especially on writes, can be rather high.

**Attributes**
* `change_set_id` - String(UUID) - id for the change set this change belongs to
* `record_set_change_id` - String(UUID) - the id for the record set change
* `zone_id` - String(UUID) the zone this record change was made for
* `status` - Number - a number representing the status of the change (Pending = 0 | Processing = 1 | Complete = 2 | Applied = 100)
* `created_timestamp` - String - a string representing the GMT formatted date/time when the change set was created
* `record_set_change_created_timestamp` - Number - a number in EPOCH millis when the change was created
* `processing_timestamp` - String - a string representing the GMT formatted date/time when the record set change was processed
* `record_set_change_blob` - Binary - the protobuf serialized bytes that represent the entire record set change

**Table Keys**
* HASH = `record_set_change_id`
* SORT = `<none>`

**Indexes**
* `zone_id_change_status_index` - Global Secondary Index
    * HASH = `zone_id`
    * SORT = `change_set_status`
    * Projection Type = `ALL`
* `zone_id_record_set_change_id_index` - Global Secondary Index
    * HASH = `zone_id`
    * SORT = `record_set_change_id`
    * Projection Type = `ALL`
* `change_status_index` - Global Secondary Index
    * HASH = `change_set_status`
    * SORT = `zone_id`
    * Projection Type = `ALL`
* `zone_id_created_index` - Global Secondary Index
    * HASH = `zone_id`
    * SORT = `record_set_change_created_timestamp`
    * Projection Type = `ALL`

### User Table
The User Table holds user specific information.  Each row in the table is a separate distinct user.
To enable encryption at rest, the user table should be encrypted.
**Encryption can only be enabled when the table is first created.**

**Usage**
Very low writes, very small data, high read rate (every API call looks up the user info)

**Attributes**
* `userid` - String(UUID) - a unique identifier for this user
* `username` - String - LDAP user name for this user
* `firstname` - String - user first name
* `lastname` - String - user last name
* `email` - String - user's email address
* `created` - Number - EPOCH time in millis when the user was created in VinylDNS
* `accesskey` - String - The access key (public) for the user to interact with the VinylDNS API
* `secretkey` - String - the secret key (private) for the user to interact with the VinylDNS API.  This secret is
encryted by default using the configured `Crypto` implementation.  It is also encrypted at rest.
* `super` - Boolean - an indicator that the user is a VinylDNS Admin user (can access all data and operations)

**Note: there is no way to programatically set the super flag, as it has a tremendous amount of power.  We are looking
for ideas and ways that we can provide super type access with some additional checks.  To set this flag, you would need
to hand-roll your own script at this point and set this attribute.**

**Table Keys**
* HASH = `userid`
* SORT = `<none>`

**Indexes**
* `username_index` - Global Secondary Index
    * HASH = `username`
    * SORT = `<none>`
    * Projection Type = `ALL`
* `access_key_index` - Global Secondary Index
    * HASH = `accesskey`
    * SORT = `<none>`
    * Projection Type = `ALL`

### Group Table
The Group table holds group information, including group name, email, and ids of members.

**Usage**
Very low writes, very small data, high read rate (every API call looks up the user groups)

**Attributes**
* `group_id` - String(UUID) - unique identifier for the group
* `name` - String - the name of the group
* `email` - String - the email (usually distribution list) of the gruop
* `desc` - String - the description of the group
* `status` - String - the group status (Active | Deleted)
* `member_ids` - String Set - the ids of all members (users) of the group
* `admin_ids` - String Set - the ids of all members who are group managers

**Table Keys**
* HASH = `group_id`
* SORT = `<none>`

**Indexes**
* `group_name_index` - Global Secondary Index
    * HASH = `name`
    * SORT = `<none>`
    * Projection Type = `ALL`

### Membership Table
The Membership table is a "join" table linking users and groups.  It supports fast look ups for all groups that
a user is a member of.

**Usage**
Very low writes, very small data, high read rate (every API call looks up the user groups)

**Attributes**
* `user_id` - String(UUID)
* `group_id` - String(UUID)

**Table Keys**
* HASH = `user_id`
* SORT = `group_id`

**Indexes**
*none*

### GroupChange Table
Group changes are required anytime groups are created, modified, or deleted.  This includes changes in group ownership
and group membership.

**Usage**
Very low writes, very small data, very low read

**Attributes**
* `group_change_id` - String(UUID) - the unique identifier for the group change
* `group_id` - String(UUID) - the unique identifier for the group
* `created` - Number - the date / time in EPOCH millis
* `group_change_blob` - Binary - protobuf of the group change

**Table Keys**
* HASH = `group_id`
* SORT = `created`

**Indexes**
* `GROUP_ID_AND_CREATED_INDEX` - Global Secondary Index
    * HASH = `group_id`
    * SORT = `created`
    * Projection Type = `ALL`

### UserChange Table
UserChange holds information of when new users are created in VinylDNS.  It is different as it does not serialize
the change data as protobuf.

**Usage**
Very low writes, very small data, very low read

**Attributes**
* `timestamp` - String - the datetime the change was made
* `userId` - String(UUID) - the unique identifier for the user being changed
* `username` - String - the username for the user being changed
* `changeType` - String - created | updated | deleted
* `updateUser` - Map - a map of the attributes being updated
* `previousUser` - Map - a map of the new attributes

### ZoneChange Table
Anytime an update is made to a zone, the event is stored here.  This includes changes to the admin group or ACL rules.

**Usage**
Very low writes, small data, high read

**Attributes**
* `zone_id` - String(UUID) - unique identifier for the zone
* `change_id` - String(UUID) - the unique identifier for this zone change
* `status` - String - the status of the zone change (Active, Deleted, PendingUpdate, PendingDelete, Syncing)
* `blob` - Binary - the protobuf serialized bytes for this zone change
* `created` - Number - the date/time in EPOCH milliseconds

**Table Keys**
* HASH = `zone_id`
* SORT = `change_id`

**Indexes**
* `zone_id_status_index` - Global Secondary Index
    * HASH = `zone_id`
    * SORT = `status`
    * Projection Type = `ALL`
* `status_zone_id_index` - Global Secondary Index
    * HASH = `status`
    * SORT = `zone_id`
    * Projection Type = `ALL`
* `zone_id_created_index` - Global Secondary Index
    * HASH = `zone_id`
    * SORT = `created`
    * Projection Type = `ALL`
