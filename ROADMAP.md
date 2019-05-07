# Roadmap
What is a Roadmap in opensource?  VinylDNS would like to communicate _direction_ in terms of the features and needs
expressed by the VinylDNS community.  In open source, demand is driven by the community through
Github issues and the [VinylDNS RFC process](https://github.com/vinyldns/rfcs).  As more members join the discussion,
we anticipate the "plan" to change.  This document will be updated regularly to reflect the changes in prioritization.

This document is organized by priority / planned release timeframes.  Reading top-down should give you a sense of the order in which new features are planned to be delivered.

## Batch Change
**Planned for: Q2 2019**
Q2 work is focused primarily on feature development for batch changes

1. Scaling batch changes - allow up to 1000 record changes in a single batch
1. Manual review of batch changes - allow a support user to manually review and approve or reject batch changes that fail under certain circumstances (such as missing zones)
1. Bulk import for batch change - allow a user to bulk import batch changes from a CSV file (e.g. Excel) to simplify the input of a large number of record changes

## Zone Management
**Planned for: Q3 2019**
Presently VinylDNS _connects to existing zones_ for management.  Zone Management will allow users
to create and manage zones in the authoritative systems themselves.  The following high-level features are planned:

1. Server Groups - allow VinylDNS admins to setup Server Groups.  A Server Group consists of the primary,
secondary, and other information for a specific DNS backend.  Server Groups are _vendor_ specific, plugins will be
be created for specific DNS vendors
1. Quotas - restrictions defined for a specific Server Group.  These include items like `maxRecordSetsPerZone`, `concurrentUpdates`and more.
1. Zone Creation - allow the creation of a sub-domain from an existing Zone.  Users choose the Server Group where
the zone will live, VinylDNS creates the delegation as well as access controls for the new zone.
1. Zone Maintenance - support the modification of zone properties, like default SOA record settings.

## Other
There are several other features that we would like to support.  We will be opening up these for RFC shortly.  These include:

1. Improved search and filter - allow users to search for records across zones
1. DNS SEC - There is no first-class support for DNS SEC.  That feature set is being defined.
1. Record meta data - VinylDNS will allow the "tagging" of DNS records with arbitrary key-value pairs
1. DNS Global Service Load Balancing (GSLB) - Support for common DNS GSLB use cases and integration with various GSLB vendors
1. A new user interface
1. Additional automation tools

