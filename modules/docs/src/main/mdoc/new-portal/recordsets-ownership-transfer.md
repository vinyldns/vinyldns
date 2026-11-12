---
layout: docs
title:  "RecordSets Ownership Transfer"
section: "new_portal_menu"
---

## RecordSets Ownership Transfer

The Ownership Transfer feature allows a user to transfer a RecordSet in a [shared zone](../api/zone-model.html#shared-zones) from one owner group to another by request/approval.

## Ownership Transfer Features

- In the Manage Records tab of a shared zone, the user can request ownership of an Unowned record by clicking the *Claim Ownership* button then selecting their group name from the dropdown. Requests for ownership of unowned records are automatically approved.
- The *Request Transfer* button can be used to request ownership of owned records. These requests will notify the existing owner group by email for their approval.
- Approvers can view pending Ownership Transfer requests in the Manage Records tab of the Zone page, then either approve or reject the request.
- Users who made a request can withdraw it with the *Cancel Request* option while it is pending.

## Requesting Ownership Transfer

To request Ownership Transfer of an unowned RecordSet in a shared zone, go to the *Zone* page in the VinylDNS Portal and select the *Claim Ownership* button that corresponds with the records for which you want to request ownership.

In the *Claim Record Ownership* dialog, select your owner group under *Assign ownership to* and submit the request.

[![Requesting ownership of an unowned record](../img/new-portal/ownership-request.png){:.screenshot}](../img/new-portal/ownership-request.png)

To request Ownership Transfer for a RecordSet with an existing owner group in a shared zone, go to the *Zone* page in the VinylDNS Portal and select the *Request Transfer* button that corresponds with the records for which you want to request ownership. In the *Request Ownership Transfer* dialog, select your group under *Request transfer to* and submit the request.
The record then shows a *Pending Review* status until the owner group approves or rejects the request.

[![Ownership transfer request pending review](../img/new-portal/ownership-rerequest.png){:.screenshot}](../img/new-portal/ownership-rerequest.png)

## Approving/Rejecting Ownership Transfer

An approver can approve or reject the requested Ownership Transfer in a shared zone on the *Zone* page of the VinylDNS Portal by opening the ownership actions menu (shown with the *Pending Review* status) for the records they want to approve or reject.

Select either *Approve* or *Reject* from the menu. The record then shows its final status: *Approved*, *Rejected* or *Cancelled*.

[![Ownership transfer approval and close request actions](../img/new-portal/ownership-close-request.png){:.screenshot}](../img/new-portal/ownership-close-request.png)

## Cancelling an Ownership Transfer Request

If you have submitted an ownership transfer request that is pending review, you can cancel it before the current owner group approves or rejects it.

To cancel a pending request:

1. Navigate to the Zone page in the VinylDNS Portal.
2. Search for and select the relevant zone.
3. Open the Records section and locate the record with the pending ownership transfer request.
4. Select the ownership actions menu (shown with the *Pending Review* status) for that record.
5. Select *Cancel Request*.
6. Submit the change.

**Note:** You can only cancel requests that are in "Pending Review" status. Once a request has been approved or rejected, it cannot be cancelled.

## Email Notifications

VinylDNS automatically sends email notifications for ownership transfer requests:

- **When a transfer is requested:** Members of both the current owner group and the requesting group receive an email notifying them that a transfer request is pending review. The email includes a link to review the request in VinylDNS.
- **When a transfer is approved:** Members of both the previous owner group and the new owner group receive an email confirming the approval.
- **When a transfer is rejected:** Members of the requesting group receive an email notifying them that the request was rejected.
- **When a transfer is cancelled:** Members of the current owner group receive an email notifying them that the pending request was cancelled.

**Note:** Email notifications are not sent for auto-approved transfers (when claiming unowned records).
