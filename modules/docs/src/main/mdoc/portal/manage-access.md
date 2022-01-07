---
layout: docs
title:  "Manage Access"
section: "portal_menu"
---

## Manage Access to Zones and Records <a id="access"></a>

### Full Access

Members of a zone admin group have *full* access to all records and permissions in the zone. Each zone is limited to one
admin group. Typically, this should be a limited set of users. If you wish to add other users to a group you can do so
in the [Groups](manage-membership.html) section of the portal.

### Limited Access

If you don't want a user to have full access to a zone you can use ACL rules to give them more granular access. With ACL
rules, the zone admins can grant individual users or groups read, write or delete access to all records in the zone, or
a subset of record names and/or types.

1. Go to the desired zone
1. Select the Manage Zone tab
1. Select the Create ACL rule button
1. Fill in the form
1. Submit the form

[![ACL rule form screenshot](../img/portal/create-acl-rule.png){:.screenshot}](../img/portal/create-acl-rule.png)

### Shared Zones

The shared zone feature is designed to allow more granular record ownership and management in a flexible way. Super
users can mark zones as 'shared' which then allow any users to create new records or claim existing unowned records in
zones. Zone administrators can assign records in a shared zone to specific groups by designating a group when creating
the record set or when updating existing records in the portal. Users who are not zone administrators can create new
records in shared zones, or claim and modify unowned records in shared zones, through
the [DNS Changes](dns-changes.html) interface.
