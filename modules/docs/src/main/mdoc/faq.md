---
layout: page
title: "FAQ"
section: "user_menu"
position: 6
---

# VinylDNS FAQ

## Frequently Asked Questions

1. [Can I create a zone in VinylDNS?](#1)
2. [How do I know my zone ID?](#2)
3. [How can I create a record with the same name as my zone?](#3)
4. [When I try to connect to my zone, I am seeing REFUSED](#4)
5. [When I try to connect to my zone, I am seeing NOTAUTH](#5)
6. [When I try to connect to my zone, I am seeing "dotted host" errors](#6)
7. [When I try to connect to my zone, I am seeing "invalid name server" errors](#7)
8. [How do I get API credentials?](#8)
9. [How are requests authenticated to the VinylDNS API?](#9)
10. [Why am I not able to view the Change History tab on a Group?](#10)


### 1. Can I create a zone in VinylDNS? <a id="1"></a>
To get started with VinylDNS, you _must have an existing DNS zone_.  VinylDNS currently does not create zones, rather it connects
to existing zones.

### 2. How do I know my zone ID? <a id="2"></a>
When viewing your zone in the portal, the zone ID is listed in the *Manage Zone* tab
of your zone. This ID is also present in the URL (if on that page it’s the ID after `/zones/`).

### 3. How can I create a record with the same name as my zone? <a id="3"></a>
To create a record with the same name as your zone, you have to use the special
`@` character for the record name when you create your record set.

You cannot create `CNAME` records with `@` as those are not supported.  While some DNS services like 
Route 53 support an ALIAS record type that _does_ support a `CNAME` style `@`, ALIAS are not an official standard yet.  
All other record types should be fine using the `@` symbol.

### 4. When I try to connect to my zone, I am seeing REFUSED <a id="4"></a>
When VinylDNS connects to a zone, it first validates that the zone is suitable
for use in VinylDNS.  To do so, it tests that the connections work, and
that the zone data is valid.

_REFUSED_ indicates that VinylDNS could not do a zone transfer to load the DNS
records for examination.  A few reasons for this are:

1. The Transfer Connection you entered is invalid.  Please verify that the TSIG
information you entered works.  You can attempt to do a `dig` and request
a zone transfer from the command line.
2. You did not setup a Transfer Connection, but the VinylDNS default keys
do not have transfer access to your zone.  Steps must be taken _outside of VinylDNS_.

### 5. When I try to connect to my zone, I am seeing NOTAUTH <a id="5"></a>
_NOTAUTH_ indicates that the primary connection that VinylDNS uses to validate
the zone is not working.  The reasons are:

1. The Connection you entered is invalid.  Please verify that the TSIG
information you entered works.
2. You did not setup a Connection, but the VinylDNS default keys do not have
update access to your zone.  Steps must be taken _outside of VinylDNS_.

### 6. When I try to connect to my zone, I am seeing "dotted host" errors <a id="6"></a>
VinylDNS validates zones upon connect.  One validation is to make sure that
there are no "dotted" host.

A "dotted host" is a record label inside of a zone that has a "dot" in it's
name, but is not part of a subdomain of the zone.

For example, "foo.bar.example.com" is _invalid_, and considered a "dotted host",
if it lives inside of the "example.com" DNS zone.  For this to be a valid record,
this label would need to be a record named "foo" inside of the "bar.example.com" zone.

You _will not_ be able to use VinylDNS for zones with dotted hosts until they are remediated.
All remediation steps must be taken _outside of VinylDNS_.

- If possible, use dashes instead of dots.  In the example, you can have "foo-bar.example.com"

### 7. When I try to connect to my zone, I am seeing "invalid Name Server" errors <a id="7"></a>
One of the validations VinylDNS performs is to make sure the name servers that are in use
in the zone are in a list of approved name servers.  If your zone is hosted on
name servers that are not in this list, you will not be able to use VinylDNS to manage your zone.

### 8. How do I get API credentials? <a id="8"></a>
After logging in to the portal, click your username at the top right and select *Download API Credentials*.

If you need new API credentials select *Regenerate Credentials*. This will invalidate your previous credentials.
If you use any VinylDNS tools beyond the portal you will need to provide those tools with your new credentials.

### 9. How are requests authenticated to the VinylDNS API? <a id="9"></a>
Refer to [API Authentication](api/auth-mechanism.html).

### 10. Why am I not able to view the Change History tab on a Group? <a id="10"></a>
To view a group's change history, you should be a member or admin of that group. Only individuals who are part of the 
group can view the change history.
