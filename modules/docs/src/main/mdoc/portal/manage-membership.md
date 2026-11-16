---
layout: docs
title:  "Manage Membership"
section: "portal_menu"
---
## Manage membership
If you create a group in VinylDNS you are automatically made a member and group manager of the group. To manage the members of the group select the *View* button next to the desired group to view the Membership page.

The membership table is on the *Manage Groups* tab. Only group managers and super users can add or remove members
or change managerial rights. Other users can view the membership table without editing it.
The *Status* column shows each member's lock status.

All group updates require a valid group email. If an update fails because the existing email is invalid,
[edit the group](groups.html#edit-a-group) to correct it before retrying.

#### Add members:
In the text box over the table enter the username of the person you want to add to the group. If you want to make them a group manager check the *Is Group Manager?* checkbox. Select the *Add Group Member* button.

The user must already exist in VinylDNS. If the username cannot be found, ask the user to sign in to the portal first.

#### Delete members:
Select the *Delete* button in the row of the user you wish to remove.

#### Change managerial rights:
Toggle the switch under the *Group Manager* column in the row of the user. The switch to the right and green means the user is a group manager. If the switch is to the left and red it means the user is not a group manager.

#### Relinquish managerial rights:
You can relinquish your managerial rights if there is at least one other group manager in the group. If that condition is met you can toggle the *Group Manager* switch for yourself.

#### Remove yourself from a group:
If you wish to remove yourself entirely from a group there must be at least one other manager of the group. If that condition is met you can select the *Delete* button that corresponds with your username and you will be removed from the group entirely.

Membership and managerial-right changes appear in the group's [Change History](groups.html#group-change-history).

[![Manage Groups tab with User Name, Add Group Member, and Group Manager controls highlighted](../img/portal/group-membership-current-annotated.png){:.screenshot}](../img/portal/group-membership-current-annotated.png)

[![Group membership screenshot](../img/portal/group-membership.png){: .screenshot}](../img/portal/group-membership.png)
