---
layout: docs
title:  "Abandon a Zone"
section: "new_portal_menu"
---

## Abandon a Zone

Abandoning a zone disconnects it from VinylDNS. It does **not** delete DNS records from the backend DNS zone. Abandonment is intended for situations such as a zone that was connected by mistake; it is not a substitute for deleting records.

### Before you begin

- You must have permission to manage the zone.
- A zone that contains records cannot be abandoned. Delete any records that should be removed before retrying. Records left in the backend remain after the zone is disconnected.
- Abandoning a zone removes its VinylDNS zone history. Review the zone and its records before confirming.

### Steps

1. Open **Zones**, select **My Zones**, and choose the zone to abandon.
2. Open the **Manage Zone** tab.
3. Select **Abandon Zone** at the bottom of the zone settings.
4. Review the confirmation dialog, then select **Abandon** to proceed. Select **Close** to cancel.
5. After the operation succeeds, VinylDNS returns you to the Zones area. The zone will no longer be managed as an active VinylDNS zone.

[![Manage Zone tab with the Abandon Zone action](../img/new-portal/manage-zone.png){:.screenshot}](../img/new-portal/manage-zone.png)

[![Abandon zone](../img/new-portal/abandon-a-zone.png){:.screenshot}](../img/new-portal/abandon-a-zone.png)

For details about the operation and its response, see the [Delete Zone API](../api/delete-zone.html).
