---
layout: docs
title: "Update Zone"
section: "api"
---

# Update Zone

Updates an existing zone that has already been connected to.  Used to update the ACL rules or zone level meta data like the zone connection or email.

#### HTTP REQUEST

> PUT /zones/{zoneId}

#### HTTP REQUEST PARAMS

**Zone fields** - Refer to [zone model](zone-model.html).

#### EXAMPLE HTTP REQUEST

```json
{
  "name": "vinyl.",
  "email": "update@update.com",
  "status": "Active",
  "created": "2017-02-23T14:52:44Z",
  "updated": "2017-02-23T19:05:33Z",
  "id": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
  "account": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
  "shared": false,
  "acl": {
    "rules": []
  },
  "adminGroupId": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
  "latestSync": "2017-02-23T19:05:33Z",
  "adminGroupName": "test",
  "hiddenKey": "",
  "hiddenTransferKey": ""
}
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **Accepted** - The zone change is returned in the response body|
400           | **Bad Request** - connection failed |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - the user does not have the access required to perform the action |
404           | **Not Found** - Zone not found |
409           | **Conflict** - Zone has a pending update |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
zone          | map           | Zone sent with update request. Refer to [zone model](zone-model.html)  |
userId        | string        | The user id that initiated the change |
changeType    | string        | Type of change requested (Create, Update, Sync, Delete); in this case Update |
created       | string        | The timestamp (UTC) the change was initiated |
id            | string        | The ID of the change.  This is not the ID of the zone |
status        | string        | The status of the zone change

#### EXAMPLE RESPONSE

```json
{
  "zone": {
    "name": "vinyl.",
    "email": "update@update.com",
    "status": "Active",
    "created": "2017-02-23T14:52:44Z",
    "updated": "2017-02-23T19:23:26Z",
    "id": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
    "account": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
    "shared": false,
    "acl": {
      "rules": []
    },
    "adminGroupId": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
    "latestSync": "2017-02-23T19:05:33Z"
  },
  "userId": "0215d410-9b7e-4636-89fd-b6b948a06347",
  "changeType": "Update",
  "status": "Pending",
  "created": "2017-02-23T19:23:26Z",
  "id": "d1fcd28d-61fe-4c24-ac0b-4377d66d50db"
}
```
