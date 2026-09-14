---
layout: docs
title:  "Delete a Group"
section: "portal_menu"
---
## Delete a group

To delete a group in VinylDNS, you must be a group admin. Groups can only be deleted if they meet certain conditions.

### Prerequisites

- You must be an admin of the group you want to delete

### Deletion Restrictions

A group cannot be deleted if any of the following conditions are true:

| Restriction | Resolution |
|-------------|------------|
| Group is the admin of a zone | Transfer zone admin ownership to another group first |
| Group owns record sets | Transfer record set ownership to another group first |
| Group has ACL rules on a zone | Remove the ACL rules referencing this group first |

### How to Delete a Group

1. In the Groups area of the VinylDNS Portal, locate the group you want to delete in the table.
1. Select the *Delete* button for the group.
1. Confirm the deletion when prompted.
1. If deletion is successful, the group will be removed from the table.

### Error Messages

If deletion fails, you may see one of the following error messages:

- **"{groupName} is the admin of a zone. Cannot delete. Please transfer the ownership to another group before deleting."** - The group is currently assigned as the admin group for one or more zones. You must first update those zones to use a different admin group.

- **"{groupName} is the owner for a record set including {recordSetId}. Cannot delete. Please transfer the ownership to another group before deleting."** - The group owns one or more record sets. You must first transfer ownership of those records to another group using the [Ownership Transfer](recordsets-ownership-transfer.html) feature.

- **"{groupName} has an ACL rule for a zone including {zoneId}. Cannot delete. Please transfer the ownership to another group before deleting."** - The group is referenced in ACL rules for one or more zones. You must first remove those ACL rules from the zone's [access management](manage-access.html).
