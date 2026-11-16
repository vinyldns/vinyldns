---
layout: docs
title:  "Groups"
section: "portal_menu"
---

## Groups

Groups are the primary way users are granted access to zones. Users can create groups for two purposes in VinylDNS: to be the zone admin group for a zone or to be assigned more limited access via [ACL rules](manage-access.html). Every zone in VinylDNS must have a zone admin group assigned. VinylDNS does not currently integrate with any services to generate pre-defined zone admin groups.

* [Create a group](create-a-group.html)
* [Manage membership](manage-membership.html)

### Find and view groups

Select *Groups* in the left navigation.

* *My Groups* lists groups you belong to. Super and support users can see all groups here.
* *All Groups* lists groups regardless of your membership. Viewing a group does not grant permission to modify it.

Enter part of a group name in the *Group Name* search box and press Enter or select the search button.
Group search matches names containing the text; wildcards are not supported.
Use *Refresh* to reload the lists and *Previous* or *Next* to move between pages when available.

Select a group name or *View* to open its details. The page shows the group email, description, and membership.
Select the copy icon beside *Group ID* to copy the identifier for use in API requests or troubleshooting.

[![Groups list with Edit and Delete actions highlighted](../img/portal/groups-current-annotated.png){:.screenshot}](../img/portal/groups-current-annotated.png)

### Group permissions

Group managers can edit group details, add or remove members, change managerial rights, and delete the group.
Super users can also manage groups. Ordinary members can view membership and change history but cannot make these changes.
Being a group manager is different from being a zone administrator: all members of a zone's admin group have full access
to that zone, not just the group managers.

### Edit a group

1. Find the group in *My Groups* or *All Groups* and select *Edit*.
1. In *Edit Group Details*, change the name, email, or optional description.
1. If *Valid Domains for Email* is displayed, expand it to check the allowed email domains.
   An existing invalid email must be corrected before any group update, including membership changes, can succeed.
1. Select *Update*. The button is enabled only after a change has been made and required fields are filled.

To change the group's members or managers, use [Manage membership](manage-membership.html) instead.

[![Edit Group Details form with Email and Update highlighted](../img/portal/group-edit-details-annotated.png){:.screenshot}](../img/portal/group-edit-details-annotated.png)

### Delete a group

1. Select *Delete* beside the group.
1. Review the group details in *Delete a Group*, then select *Yes* to confirm or *No* to cancel.

A group cannot be deleted while it is a zone admin group, owns record sets, or is referenced by a zone ACL rule.
Reassign those responsibilities or remove the relevant ACL rules before trying again.

[![Delete a Group confirmation dialog with Yes highlighted](../img/portal/group-delete-confirmation-annotated.png){:.screenshot}](../img/portal/group-delete-confirmation-annotated.png)

### Group change history

1. Open a group using its name or *View*.
1. Select *Change History*. This tab is available to group members, super users, and support users.
1. Review the time, group change ID, change type, change message, and user responsible for each change.
1. Select *View created group*, *View new group*, or *View old group* to inspect the corresponding read-only snapshot.

Use *Refresh* for the latest changes and *Previous* or *Next* to browse additional pages.

[![Group Change History tab highlighted above creation and description updates](../img/portal/group-change-history-annotated.png){:.screenshot}](../img/portal/group-change-history-annotated.png)

[![Groups main "My Groups" tab screenshot](../img/portal/groups-main.png){:.screenshot}](../img/portal/groups-main.png)

[![Groups main "All Groups" tab screenshot](../img/portal/groups-main-all.png){:.screenshot}](../img/portal/groups-main-all.png)
