---
layout: docs
title: "Delete RecordSet"
section: "api"
---

# Delete RecordSet

Delete a RecordSet in a specified zone.

#### HTTP REQUEST

> DELETE /zones/{zoneId}/recordsets/{recordSetId}

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
202           | **Accepted** - the delete is valid and has been accepted for processing; the record set change resource is returned in the response body |
400           | **Bad Request** - the zone being updated is not active; typically because the connection information does not exist for the zone |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - Zone or RecordSet not found |
409           | **Conflict** - There is an existing pending change against this zone |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
zone          | map           | Contains information about the zone when the change was created |
recordSet     | map           | Contains the recordset model |
userId        | string        | The user ID that initiated the change |
changeType    | string        | Type of change requested (Create, Update, Delete); in this case Delete |
created       | string        | The timestamp (UTC) the change was initiated |
id            | string        | The ID of the change.  This is not the ID of the recordset |

#### EXAMPLE RESPONSE

```json
{
  "zone": {
    "name": "vinyl.",
    "email": "test@test.com",
    "status": "Active",
    "created": "2017-02-23T14:52:44Z",
    "updated": "2017-02-23T15:12:33Z",
    "id": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
    "account": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
    "shared": false,
    "acl": {
      "rules": []
    },
    "adminGroupId": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
    "latestSync": "2017-02-23T15:12:33Z"
  },
  "recordSet": {
    "type": "A",
    "zoneId": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
    "name": "foo",
    "ttl": 38400,
    "status": "PendingDelete",
    "created": "2017-02-23T15:12:33Z",
    "updated": "2017-02-23T15:18:27Z",
    "records": [
      {
        "address": "2.2.2.2"
      }
    ],
    "id": "da57c384-d6e8-4166-986d-2ca9d483f760",
    "account": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae"
  },
  "userId": "0215d410-9b7e-4636-89fd-b6b948a06347",
  "changeType": "Delete",
  "status": "Pending",
  "created": "2017-02-23T15:18:27Z",
  "updates": {
    "type": "A",
    "zoneId": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
    "name": "foo",
    "ttl": 38400,
    "status": "Active",
    "created": "2017-02-23T15:12:33Z",
    "records": [
      {
        "address": "2.2.2.2"
      }
    ],
    "id": "da57c384-d6e8-4166-986d-2ca9d483f760",
    "account": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae"
  },
  "id": "c46cf622-285f-4f1b-b5b2-993a5a51ea5b"
}
```
