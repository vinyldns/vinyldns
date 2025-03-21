---
layout: docs
title:  "RecordSets Ownership Transfer"
section: "portal_menu"
---
## RecordSets Ownership Transfer <a id="RecordSetsOwnerShipTransfer"></a>
The Ownership Transfer feature allows a user to transfer a RecordSet in a [shared zone](zone-model.html#shared-zones) from one owner group to another by request/approval.

## Ownership Transfer Features
- In the Manage Records tab of a shared zone, the user can request ownership of an Unowned record by clicking the *Request* button then selecting their group name from the dropdown. Requests for ownership of unowned records are automatically approved.
- The same *Request* button can be used to request ownership of owned records. These requests will notify the existing owner group by email for their approval.
- Approvers can view pending Ownership Transfer requests in the Manage Records tab of the Zone page, then either approve or reject the request.

## Requesting Ownership Transfer
To request Ownership Transfer of an unowned RecordSet in a shared zone, go to the *Zone* page in the VinylDNS Portal and select the *Request* button that corresponds with the records for which you want to request ownership. 

[![Request Ownership transfer Records screenshot](../img/portal/ownership-transfer-request.png){: .screenshot}](../img/portal/ownership-transfer-request.png)

Select your owner group and submit the request.

[![Request Ownership transfer screenshot](../img/portal/ownership-transfer-request-page.png){: .screenshot}](../img/portal/ownership-transfer-request.png)

To request Ownership Transfer for a RecordSet with an existing owner group in a shared zone, go to the *Zone* page in the VinylDNS Portal and select the *Request* button that corresponds with the records for which you want to request ownership. Select your group in the Record Owner Group Request dropdown, select Requested in the Record Owner Group Status dropdown, then submit the request.

[![Request Ownership transfer screenshot](../img/portal/owned-ownership-request-page.png){: .screenshot}](../img/portal/ownership-transfer-request.png)

## Approving/Rejecting Ownership Transfer
An approver can approve or reject the requested Ownership Transfer in a shared zone on the *Zone* page of the VinylDNS Portal by selecting the *Close Request* button that corresponds with the records they want to approve or reject.

[![Approve Ownership transfer screenshot](../img/portal/ownership-transfer-page.png){: .screenshot}](../img/portal/ownership-transfer-page.png)

Select either Approve or Reject in the Record Owner Group Status, then submit.

[![Approve Ownership transfer screenshot](../img/portal/ownership-transfer-approver-page.png){: .screenshot}](../img/portal/ownership-transfer-approver-page.png)
