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

[![Search zones My Zones tab](../img/portal/search-zones-my-zones.png){:.screenshot}](../img/portal/search-zones-my-zones.png)

[![Search zones All Zones tab](../img/portal/search-zones-all-zones.png){:.screenshot}](../img/portal/search-zones-all-zones.png)
