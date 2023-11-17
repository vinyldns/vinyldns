---
layout: docs
title: "List Batch Changes"
section: "api"
---

# List Batch Changes

Retrieves the most recent 100 batch changes created by the user. This call will return a subset of the full information in each change, as detailed in the [attributes section](#response-attributes).

The max number of batch changes that are returned from a single request has been set to 100. 

#### HTTP REQUEST

> GET zones/batchrecordchanges?startFrom={response.nextId}&maxItems={1-100}&ignoreAccess={true &#124; false}&approvalStatus={batchChangeApprovalStatus}&userName={submitterUserName}&dateTimeRangeStart={dateTimeRangeStart}&dateTimeRangeEnd={dateTimeRangeEnd}

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
startFrom     | int           | no          | In order to advance through pages of results, the startFrom is set to the `nextId` that is returned on the previous response. It is up to the client to maintain previous pages if the client wishes to advance forward and backward. If not specified, will return the first page of results. |
maxItems      | int           | no          | The number of items to return in the page. Valid values are 1 - 100.  Defaults to 100 if not provided. |
ignoreAccess  | boolean       | no          | Flag determining whether to retrieve only batch changes made by calling user or to retrieve all changes. Only affects system administrators (ie. support and super users). Defaults to `false` if not provided. |
approvalStatus| BatchChangeApprovalStatus| no | Filter batch changes based on approval status. Can be one of **AutoApproved**, **PendingReview**, **ManuallyApproved**, **Rejected**, or **Cancelled**. |
userName      | string        | no          | Filter batch changes based on submitter user name |
dateTimeRangeStart    | string        | no          | Start date time value to filter batch changes based on date time range |
dateTimeRangeEnd      | string        | no          | End date time value to filter batch changes based on date time range |

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
approvalStatus | BatchChangeApprovalStatus | `approvalStatus` sent in request, will not be returned if not provided. |
userName      | string      | `userName` sent in request, will not be returned if not provided. |
dateTimeRangeStart | string | `dateTimeRangeStart` sent in request, will not be returned if not provided. |
dateTimeRangeEnd   | string | `dateTimeRangeEnd` sent in request, will not be returned if not provided. |

##### BatchChangeSummary <a id="batchchangesummary-info" />

name          | type        | description |
 ------------ | :---------- | :---------- |
userId        | string      | The unique identifier of the user that created the batch change. |
userName      | string      | The username of the user that created the batch change. |
comments      | string      | Conditional: comments about the batch change, if provided. |
createdTimestamp | date-time      | The timestamp (UTC) when the batch change was created. |
totalChanges  | int         | The total number of single changes within the batch change. |
status        | BatchChangeStatus | [Status of the batch change](batchchange-model.html#batchchange-attributes). |
id            | string      | The unique identifier for this batch change. |
ownerGroupName | string      | Conditional: Record ownership assignment, if provided. |
approvalStatus | BatchChangeApprovalStatus      | Whether the batch change is currently awaiting manual review. Can be one of **AutoApproved**, **PendingReview**, **ManuallyApproved**, **Rejected**, or **Cancelled**. |


#### EXAMPLE RESPONSE

```json
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
            "ownerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe",
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
            "id": "743cbd16-5440-4cf7-bca9-20319df9b651",
            "ownerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe",
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
            "approvalStatus": "Rejected",
            "reviewerId": "270ba4b3-f5eb-4043-a283-1a6cec0993f3",
            "reviewerName": "some reviewer",
            "reviewTimestamp": "2018-05-13T13:12:10Z"
        }
    ],
    "maxItems": 100,
    "ignoreAccess": false
}
```
