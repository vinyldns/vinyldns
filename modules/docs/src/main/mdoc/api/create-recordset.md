---
layout: docs
title: "Create RecordSet"
section: "api"
---

# Create RecordSet

Creates a RecordSet in a specified zone.

#### HTTP REQUEST

> POST /zones/{zoneId}/recordsets

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
zoneId        | string        | yes         | id of the zone where the recordset belongs |
name          | string        | yes         | the name of the recordset being updated |
type          | string        | yes         | the type of recordset |
ttl           | integer       | yes         | the TTL in seconds |
records       | array of record data | yes  | record data for recordset, see [RecordSet Model](recordset-model.html) |
ownerGroupId  | string        | no          | Record ownership assignment, applicable if the recordset is in a [shared zone](zone-model.html#shared-zones) |

#### EXAMPLE HTTP REQUEST
```json
{
  "name": "foo",
  "type": "A",
  "ttl": 300,
  "records": [
    {
      "address": "10.10.10.10"
    }
  ],
  "zoneId": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
  "ownerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
}
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
202           | **Accepted** - The record set is valid and has been accepted for processing; the record set change resource is returned |
400           | **Bad Request** - The zone specified is not Active; typically because the zone has no connection information |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** -  the zone with the id specified was not found |
409           | **Conflict** - A record set with the same name and type already exists in the zone |
422           | **Unprocessable Entity** |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
zone          | map           | Contains information about the zone when the change was created |
recordSet     | map           | Contains the recordset model |
userId        | string        | The user id that initiated the change |
changeType    | string        | Type of change requested (Create, Update, Delete); in this case Create |
created       | string        | The timestamp (UTC) the change was initiated |
id            | string        | The ID of the change.  This is not the ID of the recordset |
status        | RecordSetChangeStatus        | The status of the change (Pending, Complete, or Failed) |
singleBatchChangeIds |  array of SingleBatchChange Id objects  | If the recordset change was part of a batch change, the IDs of the single changes that comprise the recordset change

#### EXAMPLE RESPONSE

```json
{
  "zone": {
    "name": "vinyl.",
    "email": "test@test.com",
    "status": "Active",
    "created": "2017-02-23T14:52:44Z",
    "id": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
    "account": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
    "shared": false,
    "acl": {
      "rules": [

      ]
    },
    "adminGroupId": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae"
  },
  "recordSet": {
    "type": "A",
    "zoneId": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
    "name": "foo",
    "ttl": 300,
    "status": "Pending",
    "created": "2017-02-23T14:58:54Z",
    "records": [
      {
        "address": "10.10.10.10"
      }
    ],
    "id": "9a41b99c-8e67-445f-bcf3-f9c7cd1f2357",
    "account": "0215d410-9b7e-4636-89fd-b6b948a06347",
    "ownerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe",
    "ownerGroupName": "Shared Group"
  },
  "userId": "0215d410-9b7e-4636-89fd-b6b948a06347",
  "changeType": "Create",
  "status": "Pending",
  "created": "2017-02-23T14:58:54Z",
  "id": "fef81f0b-f439-462d-88df-c773d3686c9b",
  "singleBatchChangeIds": []
}
```
