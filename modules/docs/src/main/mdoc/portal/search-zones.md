---
layout: docs
title:  "Search Zones"
section: "portal_menu"
---
## Search Zones <a id="searchZones"></a>

The search box on the Zones page is designed to search on the zone name. It will search the "My Zones" and "All Zones" tabs simultaneously. 
For partial name matching use `*` in the search term.
Without `*` the search will be run for exact zone names. The trailing `.` is accounted for whether it's in the search term or not.

Examples:

Given a list of zone names: another.example.com., example.com., test.com., test.net., xyz.efg.

Search `test` returns: No Zones
Search `test.com` returns: test.com.
Search `test*` returns: test.com., test.net.
Search `*example` returns: example.com., another.example.com.
Search `*e*` returns: another.example.com., example.com., test.com., test.net., xyz.efg.

### Search by admin group

Select *Search by admin group* to search using the owning group's name instead of the zone name.
Enter the group name or choose an autocomplete suggestion, then press Enter or select the search button.
Clear the checkbox to return to searching by zone name.

[![Zones page with admin-group search controls highlighted](../img/portal/zones-admin-group-search-annotated.png){:.screenshot}](../img/portal/zones-admin-group-search-annotated.png)

### Hide reverse zones

Select *Hide PTR Zones* to exclude reverse DNS zones from the zone lists.
Clear the checkbox to include them again.

### Search abandoned zones

Open *Abandoned Zones* and enter a zone name in *Deleted Zone Name*.
This search supports exact names and the `*` wildcard.
Use the nested *My Zones* or *All Zones* tab to choose which abandoned-zone list to view.

[![Search zones My Zones tab](../img/portal/search-zones-my-zones.png){:.screenshot}](../img/portal/search-zones-my-zones.png)

[![Search zones All Zones tab](../img/portal/search-zones-all-zones.png){:.screenshot}](../img/portal/search-zones-all-zones.png)
