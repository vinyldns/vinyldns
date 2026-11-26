---
layout: docs
title:  "Zones"
section: "portal_menu"
---

## Zones

Select *Zones* in the left navigation to browse connected zones and their contact information.

* *My Zones* lists zones you have access to through the zone admin group or ACL rules.
  Select a zone name or *View* to manage its records and, when authorized, its settings and access rules.
* *All Zones* is a reference list of private and shared zones, including zones you cannot manage.
  Use the listed email to contact a zone's owners if you need access. Seeing a zone in this list does not grant access.
* *Abandoned Zones* lists zones disconnected from VinylDNS. See [Abandoned zones](#abandoned-zones).

Use *Refresh* to reload the lists and *Previous* or *Next* to move between pages when available.
Use the zone-name search, *Search by admin group*, and *Hide PTR Zones* controls to narrow the active zone lists.
See [Search Zones](search-zones.html) for search behavior and examples.

**Note** you may have access to specific records in shared zones, but you are not permitted to access those zones in this area.
Those records are accessible via the [DNS Changes](./dns-changes.html) area of the portal.

* [Search Zones](./search-zones.html)
* [Connect to your zone](connect-to-zone.html)
* [Manage records](manage-records.html)
* [Manage access](manage-access.html)
* [Understand connections](connections.html)
* [RecordSets ownership transfer](recordsets-ownership-transfer.html)

### Zone details

Select a zone name or *View* to open its detail page. The copy icon beside *Zone ID* copies the identifier.
The available tabs are:

* *Manage Records*: view, search, create, update, and delete record sets according to your permissions.
* *Manage Zone*: view zone information and access rules, and administer the zone when authorized.
* *Record Change History*: review record-level changes and failures.
* *Zone Change History*: review zone-level changes when you have permission to view this tab.

### Manage zone settings

Members of the zone admin group and super users can update zone settings.
Users with only record-level ACL access cannot administer the zone.

1. Open the zone and select *Manage Zone*.
1. In *Zone Info*, update the *Zone Email*, *Admin Group*, or optional *DNS Backend Id* as needed.
   The admin group selector lists groups you belong to. Changing it transfers administrative access to the selected group.
1. If *Valid Domains for Email* is displayed, expand it to check the allowed email domains.
   Correct an invalid existing email before submitting a zone update.
1. If custom DNS settings are required, update *Connection Information* or *Transfer Connection*.
   Existing key secrets are hidden; enter a new secret only when replacing a key.
   See [Understand connections](connections.html) before changing these settings.
1. Select *Update Zone*, then *Yes* to confirm or *Cancel* to return to the form.
   Use *Reset* to discard unsaved changes and reload the zone.

The *Access* selector is shown when shared-zone management is enabled for the portal.
Changing between *Shared* and *Private* requires a super or support user; ordinary zone administrators cannot change it.
See [Manage access](manage-access.html) for shared-zone behavior and instructions for creating, updating, and deleting ACL rules.

Zone updates are processed asynchronously. After an accepted update, refresh the zone and check its status and
*Zone Change History* to confirm completion.

[![Zone settings with Manage Zone tab and Update Zone button highlighted](../img/portal/zone-settings-current-annotated.png){:.screenshot}](../img/portal/zone-settings-current-annotated.png)

[![Zone update with Yes and Cancel confirmation controls highlighted](../img/portal/zone-update-confirmation-annotated.png){:.screenshot}](../img/portal/zone-update-confirmation-annotated.png)

### Sync a zone

If records change directly on the DNS server, use *Sync Zone* in *Manage Records* to refresh VinylDNS's copy.
Confirm with *Yes*. The zone is unavailable for record changes until synchronization completes.
The button is shown to users with full zone access.

[![Sync Zone confirmation with Yes highlighted and synchronization warning](../img/portal/zone-sync-confirmation-annotated.png){:.screenshot}](../img/portal/zone-sync-confirmation-annotated.png)

#### Schedule zone sync

Super and support users can see the *Schedule Zone Sync* panel under *Manage Zone*.

1. Select the recurrence and time using the schedule controls.
1. Use UTC for the schedule. The *Convert to UTC time* link opens a helper for converting local times.
1. Select *Update Zone Sync Schedule* to save the schedule.
1. To remove an existing schedule, select *Remove Zone Sync Schedule* and submit the update.

### Zone change history

Open *Zone Change History* to review zone changes separately from record changes.
Each row includes the user, zone email, access setting, created and updated times, change type, and admin group.
Automated synchronization is shown as *Automated Sync*.
When a change contains ACL rules, select *ACL Rules* to inspect the rules associated with that change.

Use *Refresh* to load recent changes and *Previous* or *Next* to browse additional pages.

[![Zone Change History tab highlighted above zone-level changes and the responsible user](../img/portal/zone-change-history-annotated.png){:.screenshot}](../img/portal/zone-change-history-annotated.png)

### Abandon a zone

1. Open the zone and select *Manage Zone*.
1. Select *Abandon Zone*.
1. Review the confirmation and select *Abandon*, or select *Close* to cancel.

**Abandoning a zone disconnects it from VinylDNS; it does not delete the zone or its records from the DNS server.**
If records must be removed from DNS, delete them through *Manage Records* and confirm completion before abandoning the zone.
This action is available to zone administrators and super users.

[![Abandon Zone confirmation with Abandon highlighted and DNS-record preservation warning](../img/portal/zone-abandon-confirmation-annotated.png){:.screenshot}](../img/portal/zone-abandon-confirmation-annotated.png)

### Abandoned zones

On the *Zones* page, open *Abandoned Zones*. Its nested *My Zones* and *All Zones* tabs show disconnected zones
within your access or across the system, respectively.
The table includes the zone name, contact email, admin group, abandoning user, abandoned time, status, and access setting
when shared-zone display is enabled.

Search using the *Deleted Zone Name* box, select *Refresh* to reload, or use *Previous* and *Next* for more results.
This is a historical listing, not a restore action. To manage the DNS zone again, follow [Connect to your zone](connect-to-zone.html).

[![Abandoned Zones tab and abandonment details highlighted](../img/portal/zones-abandoned-current-annotated.png){:.screenshot}](../img/portal/zones-abandoned-current-annotated.png)

[![Zones page - My Zones tab](../img/portal/zones-my-zones.png){:.screenshot}](../img/portal/zones-my-zones.png)

[![Zones page - All Zones tab](../img/portal/zones-all-zones.png){:.screenshot}](../img/portal/zones-all-zones.png)

[![Zones page - My Abandoned Zones](../img/portal/zones-my-deleted-zones.png){:.screenshot}](../img/portal/zones-my-deleted-zones.png)

[![Zones page - All Abandoned Zones](../img/portal/zones-all-deleted-zones.png){:.screenshot}](../img/portal/zones-all-deleted-zones.png)