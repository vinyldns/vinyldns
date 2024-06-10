---
layout: docs
title: "Ownership Transfer Model"
section: "api"
---

# Ownership transfer Model

#### Table of Contents

- [Ownership transfer Attributes](#ownership-transfer-attributes)
- [Ownership transfer Example](#ownership-transfer-example)

#### Ownership transfer ATTRIBUTES <a id="ownership-transfer-attributes"></a>

1. User can claim their own group for the unowned record by their own. Group will be Auto-assigned to the record, once user requested with group id.
2. User can request their own group for the record already assigned. Once requested, the request will be send to the existing assigned group members(Approvers). The request will be notified in email.

field         | type        | required?  | description |
------------- | :---------- | :--------- |:----------- |
ownerShipTransferStatus        | OwnerShipTransferStatus   | yes   | Ownership transfer Status for this RecordSet change. Can be one of: **AutoApproved**, **Cancelled**, **ManuallyApproved**, **ManuallyRejected**, **Requested** or **PendingReview**. |
requestedOwnerGroupId          | string    | yes  | UUID of the group |

#### Ownership transfer EXAMPLE <a id="ownership-transfer-example"></a>

```json
{
  "recordSetGroupChange" : {
    "ownerShipTransferStatus": "Request",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```

#### EXAMPLE HTTP REQUEST AND RESPONSE

Requested `ownerShipTransferStatus` in Recordsets:

```json
{
  "recordSetGroupChange" : {
    "ownerShipTransferStatus": "Requested",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```

Cancelled `ownerShipTransferStatus` in Recordsets:

```json
{
  "recordSetGroupChange" : {
    "ownerShipTransferStatus": "Cancelled",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```

Approved `ownerShipTransferStatus` in Recordsets:

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

Rejected `ownerShipTransferStatus` in Recordsets:

```json
{
  "recordSetGroupChange" : {
    "ownerShipTransferStatus": "ManuallyRejected",
    "requestedOwnerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
  }
}
```
