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

1. Users can claim ownership of Unowned records. When a user requests ownership with their group ID, the user's group will be automatically assigned to the record.
2. Users can request ownership transfer for records that are already assigned to a group. When a user initiates an ownership transfer, a request is sent to the members of the record's current owner group. These members must review and approve the request in VinylDNS. Approvers will also receive an email notification about the pending transfer.

field         | type        | required?  | description |
------------- | :---------- | :--------- |:----------- |
ownershipTransferStatus        | OwnershipTransferStatus   | yes   | Ownership transfer status for this RecordSet change. Must be one of: **AutoApproved**, **Cancelled**, **ManuallyApproved**, **ManuallyRejected**, **Requested**, or **PendingReview**. Values are case-sensitive. [See descriptions below.](#ownershiptransferstatus-values) |
requestedOwnerGroupId          | string    | yes  | UUID of the group to which ownership is being transferred; required when initiating or processing an ownership transfer. |

##### OwnershipTransferStatus Values

| Value              | Description                                                                 |
|--------------------|-----------------------------------------------------------------------------|
| AutoApproved       | Ownership transfer was automatically approved.                              |
| Cancelled          | Ownership transfer request was cancelled.                                   |
| ManuallyApproved   | Ownership transfer was manually approved by a group member.                 |
| ManuallyRejected   | Ownership transfer was manually rejected by a group member.                 |
| Requested          | Ownership transfer has been requested and is awaiting approval.             |
| PendingReview      | Ownership transfer is pending review by the current owner group.            |

#### OWNERSHIP TRANSFER EXAMPLE <a id="ownership-transfer-example"></a>

```json
{
  "recordSetGroupChange" : {
    "ownershipTransferStatus": "Requested",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```

#### EXAMPLE HTTP REQUEST AND RESPONSE

Requested `ownershipTransferStatus` in RecordSets:

```json
{
  "recordSetGroupChange" : {
    "ownershipTransferStatus": "Requested",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```

Cancelled `ownershipTransferStatus` in RecordSets:

```json
{
  "recordSetGroupChange" : {
    "ownershipTransferStatus": "Cancelled",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```

Approved `ownershipTransferStatus` in RecordSets:

```json
{
  "recordSetGroupChange" : {
    "ownershipTransferStatus": "ManuallyApproved",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```
```json
{
  "recordSetGroupChange" : {
    "ownershipTransferStatus": "AutoApproved",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```

Rejected `ownershipTransferStatus` in RecordSets:

```json
{
  "recordSetGroupChange" : {
    "ownershipTransferStatus": "ManuallyRejected",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```
