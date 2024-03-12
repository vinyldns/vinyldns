---
layout: docs
title: "Get Group"
section: "api"
---

# Get Group

Gets a group that you are a part of.

#### HTTP REQUEST

> GET /groups/{groupId}

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The group is returned in the response body |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
404           | **Not Found** - The group was not found |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
id            | string        | Unique UUID of the group |
name          | map           | The name of the group |
email         | string        | The email distribution list of the group |
description   | string        | The group description, the group may not have this field if it was not set |
created       | string        | The timestamp (UTC) the group was created |
status        | string        | **Active** or **Deleted** |
members       | Array of User Id objects        | Ids of members of the group including admins |
admins       | Array of User Id objects        | Ids of admins of the group |

#### EXAMPLE RESPONSE

```json
{
  "id": "6f8afcda-7529-4cad-9f2d-76903f4b1aca",
  "name": "some-group",
  "email": "test@example.com",
  "created": "2017-03-02T15:29:21Z",
  "status": "Active",
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
