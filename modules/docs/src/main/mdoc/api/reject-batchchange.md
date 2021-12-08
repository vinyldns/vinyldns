---
layout: docs
title: "Reject Batch Change"
section: "api"
---

# Reject Batch Change

Manually rejects a batch change in pending review status given the batch change ID, resulting in immediate failure. Only
system administrators (i.e., support or super user) can manually review a batch change.

Note: If [manual review is disabled](../operator/config-api.html#manual-review) in the VinylDNS instance,
users trying to access this endpoint will encounter a **404 Not Found** response since it will not exist. 


#### HTTP REQUEST

> POST /zones/batchrecordchanges/{id}/reject


#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | :------------ | ----------- | :---------- |
id            | string        | yes         | Unique identifier assigned to each created batch change. |
reviewComment | string        | no          | Optional rejection explanation. |


#### EXAMPLE HTTP REQUEST
```json
{
    "reviewComment": "Comments are optional."
}
```


#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** Batch change is rejected and is returned in response body. |
400           | **BadRequest** Batch change is not in pending approval status. |
403           | **Forbidden** User is not a system administrator (ie. support or super user). |
404           | **NotFound** Batch change does not exist. |

#### HTTP RESPONSE ATTRIBUTES <a id="http-response-attributes" />

name          | type        | description |
 ------------ | :---------- | :---------- |
userId        | string      | The unique identifier of the user that created the batch change. |
userName      | string      | The username of the user that created the batch change. |
comments      | string      | Conditional: comments about the batch change, if provided. |
createdTimestamp | date-time      | The timestamp (UTC) when the batch change was created. |
changes       | Array of SingleChange | Array of single changes within a batch change. A *SingleChange* can either be a [SingleAddChange](batchchange-model.html#singleaddchange-attributes) or a [SingleDeleteRRSetChange](batchchange-model.html#singledeleterrsetchange-attributes). |
status        | BatchChangeStatus | [Status of the batch change](batchchange-model.html#batchchange-attributes). |
id            | string      | The unique identifier for this batch change. |
ownerGroupId  | string      | Conditional: Record ownership assignment, if provided. |
approvalStatus | BatchChangeApprovalStatus      | Whether the batch change is currently awaiting manual review. Will be **Rejected** status when rejecting. |
reviewerId    | string      | Unique identifier for the reviewer of the batch change. |
reviewerUserName  | string      | User name for the reviewer of the batch change. |
reviewComment | string      | Conditional: Comment from the reviewer of the batch change, if provided. |
reviewTimestamp | date-time  | The timestamp (UTC) of when the batch change was manually reviewed. |


#### EXAMPLE RESPONSE

```json
{
    "userId": "vinyl", 
    "userName": "vinyl201", 
    "comments": "this is optional", 
    "createdTimestamp": "2019-07-25T20:22:53Z",
    "changes": [
        {
            "changeType": "Add",
            "inputName": "reject.parent.com.",
            "type": "A",
            "ttl": 7200,
            "record": {
                "address": "1.2.3.4"
            },
            "status": "Rejected",
            "recordName": "",
            "zoneName": "",
            "zoneId": "",
            "validationErrors": [
                {
                    "errorType": "ZoneDiscoveryError",
                    "message": "Zone Discovery Failed: zone for \"reject.parent.com.\" does not exist in VinylDNS. If zone exists, then it must be connected to in VinylDNS."
                }
            ],
            "id": "db811a02-5b0f-44ad-8ad9-8ecac7ba6bb4"
        }
    ],
    "status": "Failed",
    "id": "50e1b48b-80fa-41e0-96ef-72438abc31ec",
    "ownerGroupId": "159a41c5-e67e-4951-b539-05f5ac788139",
    "reviewerId": "90c11ffc-5a71-4794-97c6-74d19c81af7d ",
    "reviewComment": "We can't make *this* change - are you out of your mind?!",
    "reviewTimestamp": "2019-07-25T20:33:41Z",
    "approvalStatus": "Rejected"
}
```
