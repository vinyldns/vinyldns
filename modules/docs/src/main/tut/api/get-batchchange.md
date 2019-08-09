---
layout: docs
title: "Get Batch Change"
section: "api"
---

# Get Batch Change

Retrieves a batch change given the batch change ID. Only the user who created a batch change will have access to get it.


#### HTTP REQUEST

> GET /zones/batchrecordchanges/{id}


#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | :------------ | ----------- | :---------- |
id            | string        | yes         | Unique identifier assigned to each created batch change. |


#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The batch change is returned in response body. |
403           | **Forbidden** - The user does not have the access required to perform the action. |
404           | **Not Found** - Batch change not found. |


#### HTTP RESPONSE ATTRIBUTES <a id="http-response-attributes" />

name          | type        | description |
 ------------ | :---------- | :---------- |
userId        | string      | The unique identifier of the user that created the batch change. |
userName      | string      | The username of the user that created the batch change. |
comments      | string      | Optional comments about the batch change. |
createdTimestamp | date-time      | The timestamp (UTC) when the batch change was created. |
changes       | Array of SingleChange | Array of single changes within a batch change. A *SingleChange* can either be a [SingleAddChange](../api/batchchange-model/#singleaddchange-attributes) or a [SingleDeleteRRSetChange](../api/batchchange-model/#singledeleterrsetchange-). |
status        | BatchChangeStatus | [Status of the batch change](../api/batchchange-model#batchchange-attributes). |
id            | string      | The unique identifier for this batch change. |


#### EXAMPLE RESPONSE

```
{
    "userId": "vinyl", 
    "userName": "vinyl201", 
    "comments": "this is optional", 
    "createdTimestamp": "2018-05-09T14:19:34Z", 
    "changes": [
        {
            "changeType": "Add", 
            "inputName": "parent.com.", 
            "type": "A", 
            "ttl": 200, 
            "record": {
                "address": "4.5.6.7"
            }, 
            "status": "Pending", 
            "recordName": "parent.com.", 
            "zoneName": "parent.com.", 
            "zoneId": "74e93bfc-7296-4b86-83d3-1ffcb0eb3d13",
            "recordChangeId": "a07299ce-5f81-11e8-9c2d-fa7ae01bbebc",
            "recordSetId": "a0729f00-5f81-11e8-9c2d-fa7ae01bbebc",
            "id": "7573ca11-3e30-45a8-9ba5-791f7d6ae7a7"
        },
        {
            "changeType": "DeleteRecordSet", 
            "inputName": "deleting.parent.com.", 
            "type": "CNAME", 
            "status": "Pending", 
            "recordName": "deleting", 
            "zoneName": "parent.com.", 
            "zoneId": "74e93bfc-7296-4b86-83d3-1ffcb0eb3d13",
            "recordChangeId": "bed15986-5f82-11e8-9c2d-fa7ae01bbebc",
            "recordSetId": "c089e52c-5f82-11e8-9c2d-fa7ae01bbebc",
            "id": "7573ca11-3e30-45a8-9ba5-791f7d6ae7a7"
        }
    ], 
    "status": "Pending", 
    "id": "02bd95f4-a32c-443b-82eb-54dbaa55b31a"
}
```
