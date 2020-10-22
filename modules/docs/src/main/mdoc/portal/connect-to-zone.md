---
layout: docs
title:  "Connect to your Zone"
section: "portal_menu"
---
## Connect to your Zone <a id="connectingZone"></a>
Once your zone is [setup for use with VinylDNS](../faq.md#1), you can use the VinylDNS portal to connect to it.

1. If you don't already have an admin group in VinylDNS for your zone select the Groups link in the navigation and [create an admin group](create-a-group.md) for your zone. Members of the group will have full access to the zone. See [Manage Access](manage-access.md) for more details.
1. Select the Zones link from the navigation, then click the *Connect* button.  This will show the *Connect to a Zone*
  form. [![Zones main screenshot](../img/portal/zone-main.png){: .screenshot}](../img/portal/zone-main.png) [![Connect to zone form screenshot](../img/portal/connect-to-zone.png){: .screenshot}](../img/portal/connect-to-zone.png)
1. Enter the full name of the zone, example "test.sys.example.com"
1. Enter the email distribution list for the zone.  This is typically a distribution list
  email for the team that owns the zone.
1. Select the admin group for the zone.
1. If you do not have any custom TSIG keys, you can leave the connection information empty.
1. If you do have custom TSIG keys, read the section on [Understand Connections](connections.md).
1. Click the *Connect* button at the bottom of the form.
1. You may have to click the <i>Refresh</i> button from the zone list to see your new zone.
[![Created zone listed screenshot](../img/portal/zone-list.png){: .screenshot}](../img/portal/zone-list.png)
1. If you see error messages, please consult the [FAQ](../faq.md).
