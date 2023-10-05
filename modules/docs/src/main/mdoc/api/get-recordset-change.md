---
layout: docs
title: "Get RecordSet Change"
section: "api"
---

# Get RecordSet Change

RecordSet changes (Create, Update, Delete) are not immediately applied to the DNS backend; they are queued up for processing.  Most changes are applied within a few seconds.
When you submit a change for processing, the response is a Change model.  You can use the information in that change model in order to poll for the status of the change until it completes (status = Complete) or fails (status = Failed).

#### HTTP REQUEST

> GET /zones/{zoneId}/recordsets/{recordSetId}/changes/{recordChangeId}

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The record set change is returned in the response body
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - The zone, record set, or change was not found |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
zone          | map           | Contains information about the zone when the change was created |
recordSet     | map           | Contains the recordset model |
userId        | string        | The user ID that initiated the change |
changeType    | string        | Type of change requested (Create, Update, or Delete) |
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
    "account": "0215d410-9b7e-4636-89fd-b6b948a06347"
  },
  "userId": "0215d410-9b7e-4636-89fd-b6b948a06347",
  "changeType": "Create",
  "status": "Pending",
  "created": "2017-02-23T14:58:54Z",
  "id": "fef81f0b-f439-462d-88df-c773d3686c9b",
  "singleBatchChangeIds": []
}
```
