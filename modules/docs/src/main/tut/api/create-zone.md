---
layout: docs
title: "Create Zone"
section: "api"
---

# Create Zone

Connects user to an existing zone. User must be a member of the group that has access to the zone. Connection info is optional,
if no info is provided the default VinylDNS connections will be used. 

When attempting a zone connection, VinylDNS will attempt to perform a DDNS delete of a `TXT` record with name 
`vinyldns-ddns-connectivity-test` in the zone. Upon success, any existing record match will be deleted and the user will connect
to the zone; upon failure, zone connection will fail and an appropriate response will be returned to the user.

Note: Please do not create a `TXT` record named `vinyldns-ddns-connectivity-test`, as it will be deleted each time a user attempts
to connect to your zone when VinylDNS performs the DDNS delete of the record. 

#### HTTP REQUEST

> POST /zones

#### HTTP REQUEST PARAMS

**zone fields**  - adminGroupId, name, and email are required - refer to [zone model](../api/zone-model) |

#### EXAMPLE HTTP REQUEST
```
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
status        | string       | status of zone change |
zone          | map          | refer to [zone model](../api/zone-model)  |
created       | string        | the time (GMT) the change was initiated |
changeType    | string        | type of change requested (Create, Update, Sync, Delete); in this case Create |
userId        | string        | the user id that initiated the change |
id            | string        |  the id of the change.  This is not the id of the zone |

#### EXAMPLE RESPONSE

```
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
