---
layout: docs
title:  "Manage Records"
section: "new_portal_menu"
---

## Manage Records

There are currently two ways to manage records in the VinylDNS portal. This covers managing individual records through their associated zone. To manage multiple records at once, possibly across zones, and records in shared zones you can use the [DNS Changes](dns-changes.html) area of the portal.

### Access

Only zone administrators and users with ACL rules can manage records this way.

### Supported record types

`A`, `AAAA`, `CNAME`, `DS`, `MX`, `NAPTR`, `NS`, `PTR`, `SRV`, `SSHFP`, and `TXT`

To manage records in a zone go to the Zones section of the portal and select the View button that corresponds with the zone you want to manage. The Manage Records tab will be the active tab by default. Once you are in the Manage Records tab of the zone, you can create new records sets or update or delete existing record sets.

[![Manage Records tab with record list and actions](../img/new-portal/manage-records.png){:.screenshot}](../img/new-portal/manage-records.png)

The Records pane, below the Recent Record Changes pane, lists record sets. Record sets are records that have the same name but different record data. Not all record types support record sets.

When you make any change, it will be immediately queued for processing. Typically processing happens in a second or two. You may need to select the Refresh button if you don't see the change reflected in the portal.

If for any reason the change failed, you can view the change in the Recent Record Changes pane at the top of the screen, or look at the Change History tab to see what went wrong. The Additional Info column will contain details of the change failure.

[![Recent Record Changes pane](../img/new-portal/recent-record-changes.png){:.screenshot}](../img/new-portal/recent-record-changes.png)

[![Record Changes history](../img/new-portal/record-changes-main.png){:.screenshot}](../img/new-portal/record-changes-main.png)

## Zone Changes

The Zone Changes tab in a zone shows the change history of all the DNS record changes made within this zone. You can review the history of who made changes, when they were made, and their status.

[![Zone Changes tab and zone change history](../img/new-portal/zone-changes.png){:.screenshot}](../img/new-portal/zone-changes.png)

## Manage Zone

The Manage Zone tab provides administrative controls for the zone. From here, zone administrators can:

- View and manage zone details
- Configure zone access and ACL rules
- Set up zone connections and TSIG keys
- Manage zone ownership and admin groups

[![Manage Zone details and configuration controls](../img/new-portal/manage-zone.png){:.screenshot}](../img/new-portal/manage-zone.png)

### Record sorting and filtering

It is now possible to sort records in VinylDNS by record name ascending/descending order in addition to filtering the record types that will be displayed, providing more ease and control when navigating your DNS records. To change the sort order for record name, click on the Name header in the Records table to toggle between ascending and descending order. To filter record types, click on the Filter By Record Type link to reveal the record type options and adjust your selection.

[![Record type and TTL filters in Manage Records](../img/new-portal/records-filter.png){:.screenshot}](../img/new-portal/records-filter.png)

When searching for a record name using the search bar, exact matches are returned; however it is possible to use `*` to fuzzy match on desired patterns (e.g. `foo*` will match `foo` and `foobar`).

### Sync Zones

If you make changes to the records in a zone outside of VinylDNS you must manually sync the zone so VinylDNS is aware of those records. Use the Sync Zone button in the Records pane.

[![Sync Zone action in the Records pane](../img/new-portal/sync-zone.png){:.screenshot}](../img/new-portal/sync-zone.png)

### Zone Scheduler

The Zone Scheduler lets zone administrators configure an automatic zone sync schedule. Choose the days and UTC time for the sync, then save the schedule. VinylDNS syncs the zone according to the configured schedule.

[![Zone sync scheduler settings](../img/new-portal/zone-schedular.png){:.screenshot}](../img/new-portal/zone-schedular.png)

[![Updated zone sync schedule](../img/new-portal/update-zone-schedule.png){:.screenshot}](../img/new-portal/update-zone-schedule.png)
