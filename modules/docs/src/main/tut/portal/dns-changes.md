---
layout: docs
title:  "DNS Changes"
section: "portal_menu"
---

## DNS Changes
DNS Changes is an alternative to submitting individual RecordSet changes and provides the following:

* The ability to include records of multiple record types across multiple zones.
* Input names are entered as fully-qualified domain names (or IP addresses for **PTR** records), so users don't have to think in record/zone context.

#### Access
* Access permissions will follow existing rules (admin group or ACL access). Note that an update (delete and add of the same record name, zone and record type combination) requires **Write** or **Delete** access.
* <span class="important">**NEW**</span> **Records in shared zones.** All users are permitted to create new records or update unowned records in shared zones.

#### Supported record types
* Current supported record types for DNS change are: **A**, **AAAA**, **CNAME**, **PTR**, **TXT**, and **MX**.
* Additionally, there are **A+PTR** and **AAAA+PTR** types that will be processed as separate A (or AAAA) and PTR changes in the VinylDNS backend. Deletes for **A+PTR** and **AAAA+PTR** require Input Name and Record Data.
* Supported record types for records in shared zones may vary.
Contact your VinylDNS administrators to find the allowed record types.
This does not apply to zone administrators or users with specific ACL access rules.

#### Requirements
* DNS change requests must contain at least one change.
* The maximum number of single changes within a DNS change varies by instance of VinylDNS. Contact your VinylDNS administrators to find the DNS change limit for your instance.
* To update an existing record, you must delete the record first and add all expected records within the same request; a delete and add of the same record set within a DNS change request will be processed as an update.
* When creating a new record in a shared zone, or updating an existing unowned record, a record owner group is required. Once the owner group is assigned only users in that group, zone admins, and users with ACL permissions can modify the record.

---
### Create a DNS Change
1. Go to the DNS Changes section of the site.
1. Select the *New DNS Change* button.
1. Add a description.
1. Add record changes in one of two ways:
 - Select the *Add a Change* button to add additional rows for data entry as needed.
 - Select the *Import CSV* button to choose and upload a CSV file of the record changes. See [DNS Change CSV Import](#dns-change-csv-import) for more information.
1. Select the submit button. Confirm your submission.
 - If your submission was successful you'll redirect to the DNS Change summary page where you will see the status of the DNS Change request overall and of the individual records in the DNS Change.
 - If there are errors in the DNS Change you will remain on the form with prompts to correct errors before you attempt to submit again.

[![DNS Changes main page screenshot](../img/portal/dns-change-main-annotated.png){: .screenshot}](../img/portal/dns-change-main-annotated.png)
[![New DNS Change form screenshot](../img/portal/dns-change-new-annotated.png){: .screenshot}](../img/portal/dns-change-new-annotated.png)
[![Submitted DNS Change screenshot](../img/portal/dns-change-summary.png){: .screenshot}](../img/portal/dns-change-summary.png)

#### DNS Change CSV Import
[Download a sample CSV here](../static/dns-changes-csv-sample.csv)
* The header row is required. The order of the columns is `Change Type, Record Type, Input Name, TTL, Record Data`.
* The TTL field is optional for each record, but the column is still required. If TTL is empty VinylDNS will use the existing TTL value for record updates or the default TTL value for new records.

### Review a DNS Change
You can review your submitted DNS Change requests by selecting the linked Batch ID or View button for the DNS Change on the main page of the DNS Change section in the portal.

[![List of DNS Change requests screenshot](../img/portal/dns-change-list-annotated.png){: .screenshot}](../img/portal/dns-change-annotated.png)
