---
layout: docs
title: "Approve Batch Change"
section: "api"
---

# Approve Batch Change

Manually approves a batch change in pending review status given the batch change ID, resulting in revalidation and
submission for backend processing. Only system administrators (ie. support or super user) can manually review a batch
change. In the event that a batch change is approved and still encounters non-fatal errors, it will remain in manual
review state until a successful approval (**202** Accepted) or [rejection](../api/reject-batchchange) (**200** OK).

Note: If [manual review is disabled](../../operator/config-api#manual-review) in the VinylDNS instance,
users trying to access this endpoint will encounter a **404 Not Found** response since it will not exist. 


#### HTTP REQUEST

> POST /zones/batchrecordchanges/{id}/approve


#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | :------------ | ----------- | :---------- |
id            | string        | yes         | Unique identifier assigned to each created batch change. |
reviewComment | string        | no          | Optional approval explanation. |


#### EXAMPLE HTTP REQUEST
```
{
    "reviewComment": "Comments are optional."
}
```


#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
202           | **OK** Batch change is approved and is returned in response body. Batch change is submitted for backend processing. |
400           | **BadRequest** Batch change is not in pending approval status. |
403           | **Forbidden** User is not a system administrator (ie. support or super user) or is attempting to approve a scheduled batch prior to its scheduled due date. |
404           | **NotFound** Batch change does not exist.

Since we re-run validations upon successful approval, the [create batch error codes](../api/create-batchchange#http-response-types) still hold, so it is possible to see them as well.


#### HTTP RESPONSE ATTRIBUTES <a id="http-response-attributes" />

name          | type        | description |
 ------------ | :---------- | :---------- |
userId        | string      | The unique identifier of the user that created the batch change. |
userName      | string      | The username of the user that created the batch change. |
comments      | string      | Conditional: comments about the batch change, if provided. |
createdTimestamp | date-time      | The timestamp (UTC) when the batch change was created. |
changes       | Array of SingleChange | Array of single changes within a batch change. A *SingleChange* can either be a [SingleAddChange](../api/batchchange-model#singleaddchange-attributes) or a [SingleDeleteRRSetChange](../api/batchchange-model#singledeleterrsetchange-). |
status        | BatchChangeStatus | [Status of the batch change](../api/batchchange-model#batchchange-attributes). |
id            | string      | The unique identifier for this batch change. |
ownerGroupId  | string      | Conditional: Record ownership assignment, if provided. |
approvalStatus | BatchChangeApprovalStatus      | Whether the batch change is currently awaiting manual review. Will be **ManuallyApproved** status when approving. |
reviewerId    | string      | Unique identifier for the reviewer of the batch change. |
reviewerUserName  | string      | User name for the reviewer of the batch change. |
reviewComment | string      | Conditional: Comment from the reviewer of the batch change, if provided. |
reviewTimestamp | date-time  | The timestamp (UTC) of when the batch change was manually reviewed. |


#### EXAMPLE RESPONSE

```
{
    "userId": "vinyl",
    "userName": "vinyl201",
    "comments": "",
    "createdTimestamp": "2019-07-25T20:08:17Z",
    "changes": [
        {
            "changeType": "Add",
            "inputName": "approve.parent.com.",
            "type": "A",
            "ttl": 7200,
            "record": {
                "address": "1.1.1.1"
            },
            "status": "Pending",
            "recordName": "approve",
            "zoneName": "parent.com.",
            "zoneId": "876879e5-293d-4092-99ab-9cbdf50c1636",
            "validationErrors": [],
            "id": "a69cad97-994d-41e3-aed2-ec8c86a30ac5"
        }
    ],
    "status": "PendingProcessing",
    "id": "2343fa88-d4da-4377-986a-34ba4e8ca628",
    "ownerGroupId": "159a41c5-e67e-4951-b539-05f5ac788139",
    "reviewerId": "90c11ffc-5a71-4794-97c6-74d19c81af7d ",
    "reviewComment": "Good to go!",
    "reviewTimestamp": "2019-07-25T20:10:28Z",
    "approvalStatus": "ManuallyApproved"
}
```
