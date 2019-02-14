---
layout: docs
title: "List Batch Changes"
section: "api"
---

# List Batch Changes

Retrieves the most recent 100 batch changes created by the user. This call will return a subset of the full information in each change, as detailed in the [attributes section](#response-attributes).

The max number of batch changes that are returned from a single request has been set to 100. 

#### HTTP REQUEST

> GET zones/batchrecordchanges

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - the batch change is returned in response body. |
403           | **Forbidden** - the user does not have the access required to perform the action. |

If there are no batch changes created by the user, a successful empty response body is returned.

#### HTTP RESPONSE ATTRIBUTES <a id="response-attributes" />

name          | type        | description |
 ------------ | :---------- | :---------- |
batchChanges  | Array of [BatchChangeSummary](#batchchangesummary-info) | Summary information for the most recent 100 batch changes created by the user. |


##### BatchChangeSummary <a id="batchchangesummary-info" />

name          | type        | description |
 ------------ | :---------- | :---------- |
userId        | string      | The unique identifier of the user that created the batch change. |
userName      | string      | The username of the user that created the batch change. |
comments      | date-time      | Optional comments about the batch change. |
createdTimestamp | date-time      | The timestamp (in GMT) when the batch change was created. |
totalChanges  | int         | The total number of single changes within the batch change. |
status        | BatchChangeStatus | **Pending** - at least one change in batch in still in pending state; **Complete** - all changes are in complete state; **Failed** - all changes are in failure state; **PartialFailure** - some changes have failed and the rest are complete. |
id            | string      | The unique identifier for this batch change. |
ownerGroupId  | string      | Ownership assignment. Required if any records in the batch change are in [shared zones](../api/zone-model#shared-zones) and are new or unowned. |


#### EXAMPLE RESPONSE

```
{
    "batchChanges": [
        {
            "userId": "vinyl", 
            "userName": "vinyl201", 
            "comments": "this is optional", 
            "createdTimestamp": "2018-05-11T18:12:13Z", 
            "totalChanges": 5, 
            "status": "Complete", 
            "id": "bd03175c-6fd7-419e-991c-3d5d1441d995",
            "ownerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
        }, 
        {
            "userId": "vinyl", 
            "userName": "vinyl201", 
            "comments": "this is optional", 
            "createdTimestamp": "2018-05-11T18:12:12Z", 
            "totalChanges": 10, 
            "status": "Complete", 
            "id": "c2ad84b0-e6de-4a70-aa28-e808d33deaa5"
        },
        {
            "userId": "vinyl", 
            "userName": "vinyl201", 
            "comments": "this is optional", 
            "createdTimestamp": "2018-05-11T18:12:12Z", 
            "totalChanges": 7, 
            "status": "Complete", 
            "id": "2b827a33-7c4f-4623-8dd9-277c6fba0e54"
        }
    ]
}
```
