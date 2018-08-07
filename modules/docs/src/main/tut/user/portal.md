---
layout: docs
title:  "Portal documentation"
section: "user_menu"
---

## Portal documentation

#### Table of contents

1. [Connecting to your Zone](#connectingZone)
2. [Managing Records](#managingRecords)
3. [Understanding Connections](#understandingConnections)

#### Connecting to your Zone <a id="connectingZone"></a>
Once you have your zone ready, you can use VinylDNS to connect to it.

1. Create an Admin Group for your zone.  Members of the zone admin group have _full_ access
to all records and permissions in the zone.  Typically, this should be a limited set of
users.
2. From the Zones screen, click the *Connect* button.  This will show the zone connect
form.
3. Enter the full name of the zone, example "test.sys.example.com"
4. Enter the email distribution list for the zone.  This is typically a distribution list
email for the team that owns the zone.
5. Select the Admin Group for the zone.
6. If you do not have any custom TSIG keys, you can leave the connection information _empty_
7. If you do have custom TSIG keys, read the section below on *Understanding Connections*
8. Click the *Connect* button at the bottom of the form.
9. You _may_ have to click the *Refresh* button from the zone list to see your new zone.
10. If you see error messages, please consult the FAQ.

#### Managing Records <a id="managingRecords"></a>
In the *Manage Records* tab in your zone, you can create, update, and delete
existing records.

The Record View lists _Record Sets_, which are records that have the same
name but different record data.  Not all record types support record sets.

When you make any change, it will be issued _immediately_ upon confirming
the change to the DNS backend.

If for any reason the change failed, you can view the change in the "Recent Changes"
at the top of the screen, or look at the "Change History" to see what went wrong.
The "Additional Info" on the change will contain details of the failure.

#### Understanding Connections <a id="understandingConnections"></a>
VinylDNS provides the ability to specify 2 different connections to the backend DNS servers.

- The primary connection is used for issuing DNS updates
- The transfer connection is used for syncing DNS data with VinylDNS

If you do not have _any_ keys, then you can leave this information empty.  VinylDNS will
assume a set of **default** keys that should provide the access VinylDNS needs to manage
your zone.

If you have an existing TSIG key that you are using for issuing DDNS updates to DNS,
and you wish to continue to use it, you _must_ request that your zone be setup to
allow transfers from VinylDNS.

If you have an existing TSIG key that you are using for issuing DDNS updates,
and you no longer need the key, please request that the key is _revoked_.  Once
your key is revoked, you can leave the connections empty in which case VinylDNS
will assume the default keys and they should work.
