---
layout: page
title: "Permissions Guide"
position: 6
---

# VinylDNS Permissions Guide

Vinyldns is about making DNS self-service _safe_.  There are a number of ways that you can govern access to your DNS infrastructure, from extremely restrictive, to extremely lax, and anywhere in between.

This guide attempts to explain the various options available for governing access to your VinylDNS installation.

# A few notes on VinylDNS Groups

Most of the access controls available in VinylDNS are managed by the way of `Groups`.  You can see the groups to which you belong to in the `Groups` tab in in the portal.

Groups are completely separate from LDAP, OIDC, or other Authorization mechanisms.  They are tied deeply into the permissions capability in VinylDNS.

Users can belong to many different groups, but typically they will belong to one "Primary" group.

Groups are completely self-service, so users can create new groups, add / remove users from their groups, and even delete groups.

We _strongly_ recommend creating a *VinylDNS Admin* group for DNS administrators.

# Permission Precedence

We work on the most restrictive access wins.  In general:

1. High Value Domain - Can't get past this step in VinylDNS
1. Zone Ownership - If the user owns a zone, the user can make any changes in that zone
1. Zone ACL - If the user was granted access to the zone via an ACL they can make the change
1. Shared Zone - If the zone is shared, and the record does not yet exist or has not yet been claimed
1. Record Owner - If the zone is shared, and the record is owned, the user has to be a member of the group that owns the record to make the change
1. Global ACL - If all the above fail, if the user has access to the record by way of a Global ACL, the user can make the change

# Permission Management

1. High Value Domains - config file
1. Zone Ownership - web portal / Manage Zone
1. Zone ACL - web portal / Manage Zone
1. Shared Zone web portal / Manage Zone
1. Record Owner - (implicitly via DNS Requests)
1. Global ACL - config file
1. Manual Review - config file

# Zone Ownership

The original way to govern access is via Zone Ownership and Zone ACLs.  When connecting to a zone in VinylDNS, you _must_ designate a VinylDNS group that will effectively "own" the Zone.  We refer to "Zone Owners" as any VinylDNS user that is a member of the VinylDNS Group on the Zone.  This can be changed subsequent to connecting at any time via the `Manage Zone` tab.

_Zone Owners_ have full rights on a zone.  They can manage the zone, abandon it, change connection information, and assign ACLs.

A `Zone ACL Rule` is a record level control that allows VinylDNS users who are **not** Zone Owners privileges to perform certain actions in the zone.  For example, you can **grant access to `A`, `AAAA`, `CNAME` records in Zone foo.baz.com to user Josh**

ACL rules provide an extremely flexible way to grant access to DNS records.  Each ACL Rule consists of the following:

- `recordMask` - this is a **regular expression** that is used to match on the **record name** (without the zone name).  For example, a record mask `www.*` would match **any** record that started with `www`
    - For `reverse` zones, this is a `CIDR` expression.  This allows you to grant access based on individual or contiguous IP address space.  For example, you could have a record mask of `100.100.100.100/16`
- `recordTypes` - types of DNS records that an ACL rule applies to.  Often times, special records like `NS` records you do not want to grant access to.  Add one or more record types to restrict the types of records you are granting access to.
- `accessLevel` - what kinds of permissions to give.  
    - `Create` allows a user to create new records, but not change or delete them.  
    - `Write` allows a user to create AND change records, but not delete them
    - `Delete` allows a user full permissions to create, change, and delete any records that match the rule
    - `NoAccess` explicitly negates other rules as an override.
- `user` - the user that the rule grants access to.  You can specify this **OR** a Group
- `group` - the group the rule grants access to.
- `description` - an optional description for the rule for personal audit purposes

Depending on your organization, you may choose to exclusively use _Zone Ownership_ for managing self-service.  This does come at a higher cost of administrative overhead than other options; however, it is the most restrictive and still very flexible.

# Shared Zone Ownership

Shared Zones were introduced in order to alleviate the administrative burden of Zone Ownership for large organizations.  If you mark a zone as `Shared` (via the Manage Zone tab or API), you effectively grant anyone who can login to VinylDNS permissions to manage records in that DNS Zone.  Read this section closely, as it can be a little confusing.

**Shared Zones** still have a Zone Owner group that has full access.  This will typically be the VinylDNS Admin group / DNS administrators.  However, they open up access to everyone.

This is very useful for universally "shared" DNS zones, in particular IP space.  Often times, IP space is not allocated to groups, but blocks (i.e. Reverse Zones) are shared across the enterprise.  It is common to use **Shared Zones** as a way to allow rather unfettered access to IP space.

Shared Zones **still have restrictions**.  It wouldn't be safe to allow _anyone_ the ability to manage _anyone else's_ DNS records.  Therefore, we use a **Record Ownership** model that works in concert with **Shared Zones** to ensure that the user/group that _created_ a DNS record, is the only group that can update or delete it.  You can think of Record Ownership like a land grab.  If I create it, I own it.

**All** changes to _Shared_ zones are made via the **DNS Changes** tab.  Here is where users create one or more DNS record changes.  When users submit changes here, they assign a "Record Owner Group" to the DNS change.  As you have just learned, this Record Owner Group will be the group that _owns_ any new records created as part of the batch change.

In addition to shared zones, you can configure which record types you allow broad access to.  Certain record types like `NS` maybe sensitive to be "world writable".  You can configure the allowable record types via config as shown below:

```yaml
shared-approved-types = ["A", "AAAA", "CNAME", "PTR", "TXT"]
```

# Global ACL Rules

Global ACL Rules provide an _override_ mechanism that applies for specific groups and FQDNs across ALL of VinylDNS.  These were created as a means of overriding **Record Ownership**.

The use case that fits here is when

1. You want to use Shared Zones
2. You have group(s) that must override the record ownership model.  For example, one group may _create_ or _provision_ a DNS record, and another group may _update_ or _decommission_ that record.

Global ACL rules are _configured_ in VinylDNS (i.e. they are config file entries not available in the UI yet).  The configuration entry is `global-acl-rules`.  An example entry follows

```yaml
global-acl-rules = [
    {
        group-ids: ['global-acl-group-id'],
        fqdn-regex-list: ['.*shared.']
    }
]    
```

The `group-ids` are one or more VinylDNS group id (UUID) for the groups that you want to grant access to.  The `fqdn-regex-list` is a list of regular expressions matched against the **FQDN** (i.e. record name and zone name).

# High Value Domains

High value domains are currently _configured_ in VinylDNS.  Sometimes, certain domain names are just too valuable that you do not want them to be even touched by VinylDNS.  See the operator guide for more information on configuring high value domains.

# High Value IP Addresses

Similarly to forward FQDN records, you can also set certain IP addresses as "off limits, do not touch in VinylDNS".  Also done via the configuration file.

# Manual Review

VinylDNS provides the ability for have DNS changes made via the `DNS Change` screen to be **manually reviewed** before being applied.  This is another tool in the permissions toolkit to help you govern self-service.  Note: if you enable manual review, then someone will need to login to VinylDNS and _Approve_ or _Reject_ the changes that are submitted.  This can also be accomplished via scripting against the API if desired.

In addition, Manual Review allows you to override certain error conditions.  For example, if you create a DNS change for a Zone that does not yet exist in VinylDNS, it will fail before the user can submit.  But if you have `manual-batch-review-enabled = true`, then those DNS changes can be sent to manual review, where a DNS administrator can create the DNS zone, add it to VinylDNS (marking it as shared or otherwise creating ACL access), and then _re submit_ the change to complete processing.

To turn on manual review, set the `manual-batch-review-enabled = true` in your application configuration file.

In addition, you can specify certain zones that are **ALWAYS** manually reviewed.  Even with **Shared Zones** turned on, you can require manual inspection to certain FQDNs.  This is also done via configuration.  The following snippet shows the configuration for zones that are **ALWAYS** manually reviewed:

```yaml
  manual-review-domains = {
    domain-list = [".*imporant.biz.com"]
    ip-list = ["1.2.3.4"]
    zone-name-list = [
        "zone.requires.review.",
        "foo.com.",
        "baz.com.",
        "curl.bz."        
    ]
  }
  ```
