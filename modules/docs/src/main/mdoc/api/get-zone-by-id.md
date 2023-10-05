---
layout: docs
title: "Get Zone by ID"
section: "api"
---

# Get Zone by ID

Retrieves a zone with the matching zone ID.

#### HTTP REQUEST

> GET /zones/{zoneId}

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - Successful lookup, the zone is returned in the response body |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - Zone not found |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
zone          | map          | refer to [zone model](zone-model.html) |

#### EXAMPLE RESPONSE

```json
{
  "zone": {
    "status": "Active",
    "account": "6baa85ad-267f-44ff-b535-818b7d7a2467",
    "name": "system-test.",
    "created": "2016-12-28T18:12:09Z",
    "adminGroupId": "6baa85ad-267f-44ff-b535-818b7d7a2467",
    "email": "test@example.com",
    "connection": {
      "primaryServer": "127.0.0.1:5301",
      "keyName": "vinyl.",
      "name": "system-test.",
      "key": "OBF:1:B2cetOaRf1YAABAAek/w22XyKAleCRjA/hZO9fkNtNufPIRWTYHXviAk9GjrfcFOG9nNuB=="
    },
    "transferConnection": {
      "primaryServer": "127.0.0.1:5301",
      "keyName": "vinyl.",
      "name": "system-test.",
      "key": "OBF:1:PNt2k1nYkC0AABAAePpNMrDp+4C4GDbicWWlAqB5c4mKoKhvfpiWY1PfuRCVzSAeXydztB=="
    },
    "shared": true,
    "acl": {
      "rules": []
    },
    "id": "0f2fcece-b4ee-4982-b671-e5946f7db81d",
    "latestSync": "2016-12-16T15:27:26Z"
  }
}
```
