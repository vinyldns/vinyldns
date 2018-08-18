---
layout: docs
title:  "Pre-requsites"
section: "operator_menu"
---

# VinylDNS Pre-requisites
VinylDNS has the following external requirements that need to be setup so that VinylDNS can operate.  Those include:

1. [Database](database) - the database houses all of VinylDNS information including history, records, zones, and users
1. [Message Queue](message-queue) - the message queue supports high-availability and throttling of commands to DNS backend servers
1. [LDAP](ldap) - ldap supports both authentication as well as the source of truth for users that are managed inside the VinylDNS database

## Database
[database]: #database

The VinylDNS database has a `NoSQL` / non-relational design to it.  Instead of having a heavily normalized set of SQL tables
that surface in the system, instead VinylDNS relies on `Repositories`, where each `Repository` is independent of each one another.
This allows implementers to best map each `Repository` into the data-store of choice.

As `Repositories` are independent, there are no "transactions" that take _across_ repositories.  Each `Repository` implementation
can choose to use transactions if it maps to multiple tables within itself.

There are **links** across repositories, for example the `RecordSet.id` would be referenced in a `RecordSetChangeRepository`.

The following are the repositories presently used by VinylDNS...

* `RecordSetRepository` - Instead of individual DNS records, VinylDNS works at the `RRSet`.  The unique key for RecordSet is
the `record name` + `record type`.
* `RecordChangeRepository` - The history of all changes to all records in VinylDNS.  In general, some kind of pruning strategy
should be implemented otherwise this could get quite large
* `ZoneRepository` - Holds DNS Zones
* `ZoneChangeRepository` - The history of all changes made to _zones_ in VinylDNS.  Zone changes can including syncs,
updating ACL rules, changing zone ownership, etc.
* `GroupRepository` - Holds VinylDNS Groups.
* `UserRepository` - Holds VinylDNS Users.  These users are typically created the first time the user logs into the portal.
The user information will be pulled from LDAP, and inserted into the VinylDNS UserRepository.
* `MembershipRepository` - Holds a link from users to groups.
* `GroupChangeRepository` - Holds changes to groups and membership
* `BatchChangeRepository` - VinylDNS allows users to submit multiple record changes _across_ DNS zones at the same time within a `Batch`.
The `BatchChangeRepository` holds the batch itself and all individual changes that executed in the batch.
* `UserChangeRepository` - Holds changes to users

**Note: The UserChangeRepository is currently only used in the portal, and lives outside of the api.  This will be moved
alongside the other repositories in the near future**

## Database Types
### AWS DynamoDB
VinylDNS has gone through several architecture evolutions.  Along the way, DynamoDB was chosen as the data store due to
the volume of data at Comcast.  It is an excellent key-value store with extremely high performance characteristics.

VinylDNS uses DynamoDB presently for the following repositories:

1. RecordSetRepository
1. RecordChangeRepository
1. ZoneChangeRepository
1. GroupRepository
1. UserRepository
1. MembershipRepository
1. GroupChangeRepository
1. UserChangeRepository

Review the [Setup AWS DynamoDB Guide](setup-dynamodb) for more information.

### MySQL
VinylDNS currently uses MySQL only for the following repositories.

1. ZoneRepository
1. BatchChangeRepository

Originally, the `ZoneRepository` lived in DynamoDB.  However, the access controls in VinylDNS made it very difficult
to use DynamoDB as the query interface is limited.  A SQL interface with `JOIN`s was required.

Review the [Setup MySQL Guide](setup-mysql) for more information.

## Message Queues
Most operations that take place in VinylDNS use a message queue.  These operations require high-availability, fault-tolerance
with retry, and throttling.  The message queue supports these characteristics in VinylDNS.

Some operations do not use the message queue, these include user and group changes as they do not carry the same
fault-tolerance and throttling requirements.

## Message Queue Types
### AWS SQS
VinylDNS uses AWS SQS as its message queue.  SQS has the following characteristics:

1. High-Availability
1. Retry - in the event that a message cannot be processed, or if a node fails midstream processing, it will be automatically
made available for another node to process
1. Back-pressure - SQS is a _pull based_ system, meaning that if VinylDNS is currently busy, new messages will not be pulled for processing.
As soon as a node becomes available, the message will be pulled.  This is much preferable to a _push_ based system, where
bottlenecks in processing could cause an increase in heap pressure in the API nodes themselves.
1. Price - SQS is very reasonably priced.  Comcast operates multiple message queues for different environments (dev, staging, prod, etc).
The price to use SQS is in the single digit dollars per month.  VinylDNS can be tuned to run exclusively in the _free tier_.

Review the [Setup AWS SQS Guide](setup-sqs) for more information.

## LDAP
VinylDNS uses LDAP in order to authenticate users in the **Portal**.  LDAP is **not** used in the API, instead the API uses
its own user and group database for authentication.

When a user first logs into VinylDNS, their user information (first name, last name, user name, email) will be pulled from
LDAP, and stored in the `UserRepository`.  Credentials will also be generated for the user and stored encrypted in the `UserRepository`.

Review the [Setup LDAP Guide](setup-ldap) for more information
