---
layout: docs
title: "Create Zone"
section: "api"
---

# Create Zone

Connects user to an existing zone. User must be a member of the group that has access to the zone. Connection info is optional,
if no info is provided the default VinylDNS connections will be used.

#### HTTP REQUEST

> POST /zones

#### HTTP REQUEST PARAMS

**zone fields**  - adminGroupId, name, and email are required - refer to [zone model](zone-model.html) |

#### EXAMPLE HTTP REQUEST
```json
{
  "adminGroupId": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
  "name": "dummy.",
  "email": "test@example.com"
}
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
202           | **Accepted** - The zone change is queued and is returned in the response body |
400           | **Bad Request** - Connection failed, or group did not have access to the zone |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - the user does not have the access required to perform the action |
409           | **Conflict** - Zone already connected to |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
status        | string       | Status of zone change |
zone          | map          | Refer to [zone model](zone-model.html)  |
created       | string        | The timestamp (UTC) the change was initiated |
changeType    | string        | Type of change requested (Create, Update, Sync, Delete); in this case Create |
userId        | string        | The user id that initiated the change |
id            | string        | The ID of the change.  This is not the ID of the zone |

#### EXAMPLE RESPONSE

```json
{
  "status": "Pending",
  "zone": {
    "status": "Pending",
    "account": "test_group",
    "name": "488e6063-7832-40f6-87d3-87dae50c690a.",
    "created": "2016-12-28T18:00:32Z",
    "adminGroupId": "test-group-id",
    "email": "test@test.com",
    "shared": false,
    "acl": {
      "rules": [

      ]
    },
    "id": "8ba20b72-cfdb-49d3-9216-9100aeaee7fc"
  },
  "created": "2016-12-28T18:00:32Z",
  "changeType": "Create",
  "userId": "vinyl",
  "id": "dd449c27-bed5-4cd5-95e6-4c54fb20d930"
}
```
