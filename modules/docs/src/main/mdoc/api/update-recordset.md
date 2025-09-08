---
layout: docs
title: "Update RecordSet"
section: "api"
---

# Update RecordSet

Updates a RecordSet.  This performs a delete of the old record, and inserts the new record.
<br><br>

#### HTTP REQUEST

> PUT /zones/{zoneId}/recordsets/{recordSetId}

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
zoneId        | string        | yes         | id of the zone where the recordset belongs, this value **must** match the zoneId of the existing recordSet |
id            | string        | yes         | the id of the recordset being updated |
name          | string        | yes         | the name of the recordset being updated |
type          | string        | yes         | the type of recordset |
ttl           | integer       | yes         | the TTL in seconds |
records       | array of record data | yes  | record data for recordset, see [RecordSet Model](recordset-model.html) |
ownerGroupId  | string        | sometimes*          | Record ownership assignment, applicable if the recordset is in a [shared zone](zone-model.html#shared-zones) |
recordSetGroupChange | OwnerShipTransfer| sometimes†   | Record ownership transfer, requesting ownership to be transferred from one group to another. See [Ownership Transfer Model](ownership-transfer-model.html#ownership-transfer-example)

* If a RecordSet has an ownerGroupId you must include that value in the update request, otherwise the update will remove the ownerGroupId value.

† If you want to change RecordSet ownership using an Ownership Transfer Request, or if the RecordSet has an active Ownership Transfer Request in the PendingReview state, you must include a [recordSetGroupChange](recordset-model.html) value in the update request, otherwise the update will remove the recordSetGroupChange value.

#### EXAMPLE HTTP REQUEST
```json
{
  "id": "dd9c1120-0594-4e61-982e-8ddcbc8b2d21",
  "name": "already-exists",
  "type": "A",
  "ttl": 38400,
  "records": [
    {
      "address": "6.5.4.3"
    }
  ],
  "zoneId": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
  "ownerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe",
  "recordSetGroupChange" : "None"
}
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
202           | **Accepted** - the update is valid and has been accepted for processing; the record set change resource is returned in the response body |
400           | **Bad Request** - the zone being updated is not active; typically because the connection information does not exist for the zone |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - Zone or RecordSet not found |
409           | **Conflict** - There is an existing pending change against this record set |
422           | **Unprocessable Entity** |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
zone          | map           | Contains information about the zone when the change was created |
recordSet     | map           | Contains the recordset model |
updates       | map           | New data to overwrite current record set
userId        | string        | The user id that initiated the change |
changeType    | string        | Type of change requested (Create, Update, Delete); in this case Update |
created       | string        | The timestamp (UTC) the change was initiated |
id            | string        | The ID of the change.  This is not the ID of the recordset |
status        | RecordSetChangeStatus        | The status of the change (Pending, Complete, or Failed) |
singleBatchChangeIds |  array of SingleBatchChange ID objects  | If the recordset change was part of a batch change, the IDs of the single changes that comprise the recordset change

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
      "rules": [

      ]
    },
    "adminGroupId": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
    "latestSync": "2017-02-23T15:12:33Z"
  },
  "recordSet": {
    "type": "A",
    "zoneId": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
    "name": "already-exists",
    "ttl": 38400,
    "status": "PendingUpdate",
    "created": "2017-02-23T15:12:41Z",
    "updated": "2017-02-23T15:12:41Z",
    "records": [
      {
        "address": "6.6.6.1"
      }
    ],
    "id": "dd9c1120-0594-4e61-982e-8ddcbc8b2d21",
    "account": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae"
  },
  "userId": "0215d410-9b7e-4636-89fd-b6b948a06347",
  "changeType": "Update",
  "status": "Pending",
  "created": "2017-02-23T15:12:41Z",
  "updates": {
    "type": "A",
    "zoneId": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
    "name": "already-exists",
    "ttl": 38400,
    "status": "Active",
    "created": "2017-02-23T15:12:33Z",
    "records": [
      {
        "address": "6.6.6.6"
      }
    ],
    "id": "dd9c1120-0594-4e61-982e-8ddcbc8b2d21",
    "account": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
    "ownerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe",
    "recordSetGroupChange" : "None"
  },
  "id": "df69bc45-2942-4fb7-813c-4dd21cfad7fa",
  "singleBatchChangeIds": []
}
```
