---
layout: docs
title:  "Understand Connections"
section: "portal_menu"
---
## Understand Connections <a id="understandConnections"></a>
VinylDNS provides the ability to specify two different connections to the backend DNS servers.

- The primary connection is used for issuing DNS updates
- The transfer connection is used for syncing DNS data with VinylDNS

If you do not have *any* keys, then you can leave this information empty.  VinylDNS will
assume a set of **default** keys that should provide the access VinylDNS needs to manage
your zone.

If you have an existing TSIG key that you are using for issuing DDNS updates to DNS,
and you wish to continue to use it, you *must* request that your zone be setup to
allow transfers from VinylDNS. **Note**: If you make any changes *outside* of VinylDNS they will not be reflected in VinylDNS unless you manually [sync the zone](manage-records.md#sync-zones).

If you have an existing TSIG key that you are using for issuing DDNS updates,
and you no longer need the key, please contact your VinylDNS administrators to ensure that the key is *revoked* and the zone is setup with the default VinylDNS TSIG key. Once
your key is revoked, you can leave the connections empty in which case VinylDNS
will assume the default keys and they should work.
