---
layout: docs
title: "Delete Group"
section: "api"
---

# Delete Group

Deletes a Group in VinylDNS.

#### HTTP REQUEST

> DELETE /groups/{groupId}

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The group has been delete and the group info is returned in the response body |
400           | **Bad Request** - The group could not be deleted |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - The group was not found |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
id            | string        | Unique UUID of the group |
name          | map           | The name of the group |
email         | string        | The email distribution list of the group |
description   | string        | The group description, the group will not have this attribute if it was not set |
created       | string        | The timestamp (UTC) the group was created |
status        | string        | **Active** or **Deleted**, in this case **Deleted** |
members       | Array of User ID objects        | IDs of members of the group including admins |
admins        | Array of User ID objects        | IDs of admins of the group |

#### EXAMPLE RESPONSE

```json
{
  "id": "6f8afcda-7529-4cad-9f2d-76903f4b1aca",
  "name": "some-group",
  "email": "test@example.com",
  "created": "2017-03-02T15:29:21Z",
  "status": "Deleted",
  "members": [
    {
      "id": "2764183c-5e75-4ae6-8833-503cd5f4dcb0"
    },
    {
      "id": "c8630ebc-0af2-4c9a-a0a0-d18c590ed03e"
    }
  ],
  "admins": [
    {
      "id": "2764183c-5e75-4ae6-8833-503cd5f4dcb0"
    }
  ]
}
```
