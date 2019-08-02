---
layout: docs
title: "Delete Zone"
section: "api"
---

# Delete Zone

Abandons an existing zone that has already been connected to.
The zone will be disconnected from VinylDNS, but the RecordSets still exist in the backend DNS zone.
To delete the RecordSets see [Delete RecordSet](../api/delete-recordset)

*Note: We do not recommend that you abandon zones, as your zone history will be lost after the Delete.  This endpoint is provided in certain situations where a zone was incorrectly started.*

#### HTTP REQUEST

> DELETE /zones/{zoneId}

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
202           | **Accepted** - The change has been queued and is returned in the response body |
400           | **Bad Request** - Zone was not empty and contains records |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - Zone not found |
409           | **Conflict** - Zone is unavailable |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
zone          | map           | Zone being deleted |
userId        | string        | The user id that initiated the change |
changeType    | string        | Type of change requested (Create, Update, Sync, Delete); in this case Delete |
created       | string        | The timestamp (UTC) the change was initiated |
id            | string        | The ID of the change.  This is not the ID of the zone |
status        | string        | The status of the zone change |

#### EXAMPLE RESPONSE

```
{
  "status": "Pending",
  "zone": {
    "status": "Deleted",
    "updated": "2016-12-28T18:45:53Z",
    "name": "443ad9ff-8f38-4540-b53f-e23a35fdfc28.",
    "adminGroupId": "test-group-id",
    "created": "2016-12-28T18:45:53Z",
    "account": "test_group",
    "email": "test@test.com",
    "shared": false,
    "acl": {
      "rules": []
    },
    "id": "4995e883-f314-4c5f-8ee8-75003ca08ab0"
  },
  "created": "2016-12-28T18:45:53Z",
  "changeType": "Delete",
  "userId": "vinyl",
  "id": "89c463e3-1615-42f7-8299-a0ca7ccea439"
}
```
