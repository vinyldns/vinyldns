---
layout: docs
title: "Ownership Transfer Model"
section: "api"
---

# Ownership Transfer Model

#### Table of Contents

- [Ownership Transfer Attributes](#ownership-transfer-attributes)
- [Ownership Transfer Example](#ownership-transfer-example)

#### OWNERSHIP TRANSFER ATTRIBUTES <a id="ownership-transfer-attributes"></a>

1. Users can claim unowned records for themselves. The user's group will be auto-assigned to the record when the user requests ownership with their group ID.
2. Users can request ownership transfer for records already assigned to a group. An ownership transfer request will be sent to the members of the record's current owner group for their approval in VinylDNS. The request will also notify the approvers by email.

field         | type        | required?  | description |
------------- | :---------- | :--------- |:----------- |
ownerShipTransferStatus        | OwnerShipTransferStatus   | yes   | Ownership transfer status for this RecordSet change. Must be one of: **AutoApproved**, **Cancelled**, **ManuallyApproved**, **ManuallyRejected**, **Requested** or **PendingReview**. |
requestedOwnerGroupId          | string    | yes  | UUID of the group |

#### OWNERSHIP TRANSFER EXAMPLE <a id="ownership-transfer-example"></a>

```json
{
  "recordSetGroupChange" : {
    "ownerShipTransferStatus": "Requested",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```

#### EXAMPLE HTTP REQUEST AND RESPONSE

Requested `ownerShipTransferStatus` in RecordSets:

```json
{
  "recordSetGroupChange" : {
    "ownerShipTransferStatus": "Requested",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```

Cancelled `ownerShipTransferStatus` in RecordSets:

```json
{
  "recordSetGroupChange" : {
    "ownerShipTransferStatus": "Cancelled",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```

Approved `ownerShipTransferStatus` in RecordSets:

```json
{
  "recordSetGroupChange" : {
    "ownerShipTransferStatus": "ManuallyApproved",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```
```json
{
  "recordSetGroupChange" : {
    "ownerShipTransferStatus": "AutoApproved",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```

Rejected `ownerShipTransferStatus` in RecordSets:

```json
{
  "recordSetGroupChange" : {
    "ownerShipTransferStatus": "ManuallyRejected",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```
