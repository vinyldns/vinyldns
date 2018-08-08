---
layout: docs
title:  "Connecting to your Zone"
section: "portal_menu"
---

## Connecting to your Zone <a id="connectingZone"></a>
Once your zone is setup for use with VinylDNS, you can use the VinylDNS portal to connect to it.

<ol>
  <li> Select the Groups link in the navigation and create an Admin Group for your zone.
  <details>
    <summary><strong>Screenshots</strong></summary>
    <p><img src="../img/portal/groups-main.png" alt="Groups screenshot"
        class="screenshot"/></p>
    <p><img src="../img/portal/create-group.png" alt="Create group modal screenshot"
        class="screenshot"/></p>
    <p><img src="../img/portal/groups-listed.png" alt="Created group listed screenshot"
        class="screenshot"/></p>
  </details>
  </li>
  <li> Select the Zones link from the navigation, then click the <i>Connect</i> button.  This will show the zone connect
  form.
  <details>
  <summary><strong>Screenshots</strong></summary>
  <p><img src="../img/portal/zone-main.png" alt="Zones main screenshot"
      class="screenshot"/></p>
  <p><img src="../img/portal/connect-to-zone.png" alt="Connect to zone form screenshot"
      class="screenshot"/></p>
  </details>
  </li>
  <li> Enter the full name of the zone, example "test.sys.example.com"</li>
  <li> Enter the email distribution list for the zone.  This is typically a distribution list
  email for the team that owns the zone.</li>
  <li> Select the Admin Group for the zone.</li>
  <li> If you do not have any custom TSIG keys, you can leave the connection information empty.</li>
  <li> If you do have custom TSIG keys, read the section below on <i>Understanding Connections</i></li>
  <li> Click the <i>Connect</i> button at the bottom of the form.</li>
  <li> You may have to click the <i>Refresh</i> button from the zone list to see your new zone.
  <details>
  <summary><strong>Screenshot</strong></summary>
  <p><img src="../img/portal/zone-list.png" alt="Created zone listed screenshot"
      class="screenshot"/></p>
  </details>
  </li>
  <li> If you see error messages, please consult the FAQ.</li>
</ol>
