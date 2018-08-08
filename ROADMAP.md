# Roadmap
What is a Roadmap in opensource?  VinylDNS would like to communicate _direction_ in terms of the features and needs
expressed by the VinylDNS community.  In open source, demand is driven by the community through
Github issues and the [VinylDNS RFC process](https://github.com/vinyldns/rfcs).  As more members join the discussion,
we anticipate the "plan" to change.  This document will be updated regularly to reflect the changes in prioritization.

## Pluggable Dependencies
**Planned for: Q3 2018**
Presently, VinylDNS requires operators to run DynamoDB, MySQL, and SQS.  These system dependencies were chosen to
support the massive DNS footprint of Comcast.  However, they are viewed as a hurdle to adoption for VinylDNS.  We would like to make the external system dependencies "pluggable".  This will allow VinylDNS users to provide their own implementations which can be configured and loaded dynamically at run-time.

The following high-level features will be developed:
1. Pluggable Repositories - support multiple datastores other than the ones we run. 
1. Pluggable Queues - support different message queues other than SQS, for example RabbitMQ.

## Batch Change
**Planned for: Q4 2018**
A major new feature introduced recently is the ability to make multiple changes across zones.  The changes are applied
by consulting the _current_ access control model.  The access control model follows:

1. If the user is an admin (VinylDNS or Zone admin), they can make any changes to DNS records
1. If the user is granted access to make the change by an ACL rule, the change is allowed
1. Otherwise, the change is rejected with a `Not Authorized`

The final phase of work will add several new features to Batch Changes.  Those include:

1. [Shared Zones](https://github.com/vinyldns/rfcs/pull/6) - IP space and large common zones are cumbersome to manage using fine-grained ACL rules.  Shared zones
enable self-service management of records via a record ownership model for access controls.  Record ownership assigns
a group as the owner of the record to restrict who can modify that record.
1. Profanity filter - reject changes that use profane language in record labels or text.
1. Blacklisting - allow rejections on any DNS records that are in a blacklist.  This will prevent high-value domains
from being modified via VinylDNS.

## Zone Management
Presently VinylDNS _connects to existing zones_ for management.  Zone Management will allow users
to create and manage zones in the authoritative systems themselves.  The following high-level features are planned:

1. Server Groups - allow VinylDNS admins to setup Server Groups.  A Server Group consists of the primary,
secondary, and other information for a specific DNS backend.  Server Groups are _vendor_ specific, plugins will be
be created for specific DNS vendors
1. Quotas - restrictions defined for a specific Server Group.  These include items like `maxRecordSetsPerZone`, `concurrentUpdates`and more.
1. Zone Creation - allow the creation of a sub-domain from an existing Zone.  Users choose the Server Group where
the zone will live, VinylDNS creates the delegation as well as access controls for the new zone.
1. Zone Maintenance - support the modification of zone properties, like default SOA record settings.

**Planned for: Q2 2019**

## Other
There are several other features that we would like to support.  We will be opening up these for RFC, and their
implementation can hopefully be contributed by members in the open source community, or perhaps the demand shuffles
our priorities.  These include:

1. DNS SEC - There is no first-class support for DNS SEC.  That feature set is being defined.
1. Record meta data - VinylDNS will allow the "tagging" of DNS records with arbitrary key-value pairs
1. DNS GSLB - Integration with various GSLB vendors for common DNS GSLB configurations
1. A new user interface
1. Additional automation tools

