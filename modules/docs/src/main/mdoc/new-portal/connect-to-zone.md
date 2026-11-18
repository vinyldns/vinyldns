---
layout: docs
title:  "Connect to your Zone"
section: "new_portal_menu"
---

## Connect to your Zone

Once your zone is [setup for use with VinylDNS](../faq.html#1), you can use the VinylDNS portal to connect to it.

1. If you don't already have an admin group in VinylDNS for your zone select the Groups link in the navigation and [create an admin group](create-a-group.html) for your zone. Members of the group will have full access to the zone. See [Manage Access](manage-access.html) for more details.

2. Select the Zones link from the navigation, then click the Connect button. This will show the Connect to a Zone form.

[![Zones page with the Connect Zone action](../img/new-portal/zone-mainpage.png){:.screenshot}](../img/new-portal/zone-mainpage.png)

[![Connect to Zone dialog](../img/new-portal/connect-a-zone.png){:.screenshot}](../img/new-portal/connect-a-zone.png)

3. Enter the full name of the zone, example "ok."

4. Enter the email distribution list for the zone. This is typically a distribution list email for the team that owns the zone.

5. Select the admin group for the zone.

6. If you do not have any custom TSIG keys, you can leave the connection information empty.

7. If you do have custom TSIG keys, read the section on [Understand Connections](connections.html).

8. Click the Connect button at the bottom of the form.

9. You may have to click the Refresh button from the zone list to see your new zone.

[![Connected zone shown in the Zones list](../img/new-portal/myzones.png){:.screenshot}](../img/new-portal/myzones.png)

10. If you see error messages, please consult the [FAQ](../faq.html).
