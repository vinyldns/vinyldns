---
layout: docs
title:  "Manual Review & Scheduling"
section: "portal_menu"
---

## DNS Changes: Manual Review & Scheduling

<span class="important">**Configuration Note:**</span> DNS Change manual review and scheduling are configured features in VinylDNS. Check with your VinylDNS administrators to determine if they are enabled in your instance.**

* [Manual Review](#manual-review)
* [Scheduling](#scheduling)
* [Filter by Open Requests](#filtering)
* [Cancelling DNS Changes](#cancelling)
* [Reviewing Pending DNS Changes (administrators only)](#reviewing)

## Manual Review <a id="manual-review" />

If a DNS Change is submitted with only [non-fatal errors](../api/batchchange-errors.md#non-fatal-errors) you will be notified to either correct those errors or submit your DNS Change for manual review.
If you submit the DNS Change for manual review a VinylDNS administrator will determine if your request can be approved or if it needs to be rejected. 
After the review your DNS Change will include the review details, including the review status, reviewer name, review time and review comment, if provided.

[![New DNS Change form with non-fatal errors](../img/portal/dns-change-non-fatal-errors.png){: .screenshot}](../img/portal/dns-change-non-fatal-errors.png)

[![New DNS Change reviewed](../img/portal/dns-change-reviewed.png){: .screenshot}](../img/portal/dns-change-reviewed.png)

## Scheduling <a id="scheduling" />

VinylDNS processes DNS Changes immediately, unless they have a Request Date and Time. The day and time must be in the future. The portal accepts and returns the Request Date and Time as your local time.
A VinylDNS administrator will review the DNS Change after the requested time and either approve or reject it for processing. 
[![New DNS Change form with scheduling field](../img/portal/dns-change-schedule-annotated.png){: .screenshot}](../img/portal/dns-change-schedule-annotated.png)

## Filter by Open Requests <a id="filtering" />

If you have many DNS Changes you may find it helpful to filter your list of requests by those that are currently open. 
In the top right corner of the DNS Changes table is a checkbox labeled "View Open Requests Only". If the checkbox is selected the DNS Changes list will be limited to only Pending Review and Scheduled DNS Changes.

[![DNS Changes listed filtered by open requests](../img/portal/dns-change-open-requests-filter.png){: .screenshot}](../img/portal/dns-change-open-requests-filter.png)

## Cancelling DNS Changes <a id="cancelling" />

Users can cancel any DNS Change they create if it has a review status of "Pending Review". Either select the "Cancel" button in the main DNS Changes list or the "Cancel" button in the DNS Change Detail page. A modal will appear for the user to confirm the cancellation.

[![DNS Change cancel prompt](../img/portal/dns-change-cancel-prompt-annotated.png){: .screenshot}](../img/portal/dns-change-cancel-prompt-annotated.png)

[![Cancelled DNS Change](../img/portal/dns-change-cancelled.png){: .screenshot}](../img/portal/dns-change-cancelled.png)

## Reviewing Pending DNS Changes <a id="reviewing" />

VinylDNS administrators can use the portal to review DNS Changes.

In the DNS Changes view there are two tabs, "My Requests" and "All Requests". "My Requests" are only your own DNS Changes.
"All Requests" are requests by everyone in the VinylDNS instance. Both tabs can be filtered by open requests.

[![DNS Changes admin view](../img/portal/dns-changes-admin-view.png){: .screenshot}](../img/portal/dns-changes-admin-view.png)

On the detail page for a DNS change that is pending review, administrators will see a review section beneath the list of single changes. 
The administrator can provide a comment, then choose Approve or Reject and finally confirm their choice. 
If the DNS Change is approved and there are no new errors or it's rejected then the review is completed and the DNS Change status and review information is updated. 
If the DNS Change still has errors after the approval attempt, the page will display the new errors and the administrator needs to address those or reject the DNS change.

[![DNS Change detail page with review section](../img/portal/dns-change-review.png){: .screenshot}](../img/portal/dns-change-review.png)

[![DNS Change with errors after approval attempt](../img/portal/dns-change-approval-with-errors.png){: .screenshot}](../img/portal/dns-change-approval-with-errors.png)
