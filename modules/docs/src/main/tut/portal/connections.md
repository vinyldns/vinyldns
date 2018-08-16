---
layout: docs
title:  "Understanding Connections"
section: "portal_menu"
---
## Understanding Connections <a id="understandingConnections"></a>
VinylDNS provides the ability to specify two different connections to the backend DNS servers.

- The primary connection is used for issuing DNS updates
- The transfer connection is used for syncing DNS data with VinylDNS

If you do not have *any* keys, then you can leave this information empty.  VinylDNS will
assume a set of **default** keys that should provide the access VinylDNS needs to manage
your zone.

If you have an existing TSIG key that you are using for issuing DDNS updates to DNS,
and you wish to continue to use it, you *must* request that your zone be setup to
allow transfers from VinylDNS.

If you have an existing TSIG key that you are using for issuing DDNS updates,
and you no longer need the key, please request that the key is *revoked*.  Once
your key is revoked, you can leave the connections empty in which case VinylDNS
will assume the default keys and they should work.
