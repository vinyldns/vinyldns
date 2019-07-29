---
layout: docs
title: "List Batch Changes"
section: "api"
---

# List Batch Changes

Retrieves the most recent 100 batch changes created by the user. This call will return a subset of the full information in each change, as detailed in the [attributes section](#response-attributes).

The max number of batch changes that are returned from a single request has been set to 100. 

#### HTTP REQUEST

> GET zones/batchrecordchanges?startFrom={response.nextId}&maxItems={1-100}&ignoreAccess={true &#124; false}

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
startFrom     | int           | no          | In order to advance through pages of results, the startFrom is set to the `nextId` that is returned on the previous response. It is up to the client to maintain previous pages if the client wishes to advance forward and backward. If not specified, will return the first page of results. |
maxItems      | int           | no          | The number of items to return in the page. Valid values are 1 - 100.  Defaults to 100 if not provided. |
ignoreAccess  | boolean       | no          | Flag determining whether to retrieve only batch changes made by calling user or to retrieve all changes. Only affects system administrators (ie. support and super users). Defaults to `false` if not provided. |

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
startFrom     | int         | `startFrom` sent in request, will not be returned if not provided. |
nextId        | int         | `startFrom` parameter of next page request, will not be returned if record sets are exhausted. |
maxItems      | integer     | `maxItems` sent in request, default is 100. |
ignoreAccess  | boolean     | `ignoreAccess` sent in request, default is `false`. |


##### BatchChangeSummary <a id="batchchangesummary-info" />

name          | type        | description |
 ------------ | :---------- | :---------- |
userId        | string      | The unique identifier of the user that created the batch change. |
userName      | string      | The username of the user that created the batch change. |
comments      | string      | Conditional: comments about the batch change, if provided. |
createdTimestamp | date-time      | The timestamp (in GMT) when the batch change was created. |
totalChanges  | int         | The total number of single changes within the batch change. |
status        | BatchChangeStatus | **Pending** - at least one change in batch in still in pending state; **Complete** - all changes are in complete state; **Failed** - all changes are in failure state; **PartialFailure** - some changes have failed and the rest are complete. |
id            | string      | The unique identifier for this batch change. |
ownerGroupName | string      | Conditional: Record ownership assignment, if provided. |
approvalStatus | string      | Whether the batch change is currently awaiting manual review. Can be one of **AutoApproved**, **PendingReview**, **ManuallyApproved** or **Rejected**. |


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
            "ownerGroupName": "some owner group name",
            "approvalStatus": "AutoApproved"
        }, 
        {
            "userId": "vinyl", 
            "userName": "vinyl201", 
            "comments": "this is optional", 
            "createdTimestamp": "2018-05-11T18:12:12Z", 
            "totalChanges": 10, 
            "status": "Complete", 
            "ownerGroupName": "some owner group name",
            "approvalStatus": "ManuallyApproved"
        },
        {
            "userId": "vinyl", 
            "userName": "vinyl201", 
            "comments": "this is optional", 
            "createdTimestamp": "2018-05-11T18:12:12Z", 
            "totalChanges": 7, 
            "status": "Complete", 
            "id": "2b827a33-7c4f-4623-8dd9-277c6fba0e54",
            "approvalStatus": "ManuallyRejected"
        }
    ],
    "maxItems": 100,
    "ignoreAccess": false
}
```
