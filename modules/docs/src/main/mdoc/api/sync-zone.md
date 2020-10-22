---
layout: docs
title: "Sync Zone"
section: "api"
---

# Sync Zone

Used to sync VinylDNS zone info with existing zone info.  When a sync is performed, a zone transfer is initiated with the backend DNS server.
The backend data is compared to the existing data in VinylDNS.  If there are any differences, the _backend DNS Server_ is considered
the source of truth and will overwrite the data in VinylDNS.  All changes will be recorded in VinylDNS so they can be seen in the
zone history.

While the zone is syncing, the zone will be _unavailable_ for updates (read-only).

We have done some testing on how long syncs take.  These will vary with usage:

- 1000 records ~ 1 second
- 10,000 records ~ 10 seconds
- 100,000 records ~ 6 minutes

Please keep these numbers in mind when you perform syncs.

#### HTTP REQUEST

> POST /zones/{zoneId}/sync

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - Successful lookup, the zone is returned in the response body |
400           | **Bad Request** - invalid sync state, a sync has been performed recently, or zone is inactive |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - the user does not have the access required to perform the action |
404           | **Not Found** - Zone not found |
409           | **Conflict** - Zone has a pending update |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
status        | string        | Sync status |
zone          | map           | Refer to [zone model](zone-model.md) |
created       | string        | The timestamp (UTC) the sync was initiated |
changeType    | string        | Type of change requested (Create, Update, Sync, Delete); in this case Sync |
userId        | string        | The user ID that initiated the change |
id            | string        | The ID of the change.  This is not the id of the zone |

#### EXAMPLE RESPONSE

```
{
  "status": "Pending",
  "zone": {
    "status": "Syncing",
    "updated": "2016-12-28T19:22:02Z",
    "name": "sync-test.",
    "adminGroupId": "cf00d1e4-46f1-493a-a3be-0ae79dd306a5",
    "created": "2016-12-28T19:22:01Z",
    "account": "cf00d1e4-46f1-493a-a3be-0ae79dd306a5",
    "email": "test@test.com",
    "shared": false,
    "acl": {
      "rules": []
    },
    "id": "621a13df-a2e3-4394-84c0-3eb3a664dff4"
  },
  "created": "2016-12-28T19:22:02Z",
  "changeType": "Sync",
  "userId": "ok",
  "id": "03f1ee91-9053-4346-8b53-e0f6042600f2"
}
```
