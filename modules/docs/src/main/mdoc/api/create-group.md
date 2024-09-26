---
layout: docs
title: "Create Group"
section: "api"
---

# Create Group

Creates a Group in VinylDNS.

#### HTTP REQUEST

> POST /groups

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
name          | string        | yes         | The name of the group. Should be one word, use hyphens if needed but no spaces |
email         | string        | yes         | The email distribution list for the group |
description   | string        | no          | A short description of the group if more info is needed other than the name |
members       | Array of User id objects        | yes         | Set of User ids in the group |
admins        | Array of User id objects        | yes         | Set of User ids that are admins of the group. All admin user ids should also be in the members array |

#### EXAMPLE HTTP REQUEST

```json
{
  "name": "some-group",
  "email": "test@example.com",
  "description": "an example group", 
  "members": [
    {
      "id": "2764183c-5e75-4ae6-8833-503cd5f4dcb0"
    }
  ],
  "admins": [
    {
      "id": "2764183c-5e75-4ae6-8833-503cd5f4dcb0"
    }
  ]
}
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The group has been created and the group info is returned in the response body |
400           | **Bad Request** - The group was invalid |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
404           | **Not Found** - A user id was not found |
409           | **Conflict** - A group with the same name already exists |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
id            | string        | Unique UUID of the group |
name          | string           | The name of the group |
email         | string        | The email distribution list of the group |
description   | string        | The group description, the group will not have this attribute if it was not included in the create request |
created       | string        | The timestamp (UTC) the group was created |
status        | string        | **Active** or **Deleted**, in this case **Active** |
members       | Array of User ID objects        | IDs of members of the group including admins |
admins        | Array of User ID objects        | IDs of admins of the group |

#### EXAMPLE RESPONSE

```json
{
  "id": "6f8afcda-7529-4cad-9f2d-76903f4b1aca",
  "name": "some-group",
  "email": "test@example.com",
  "description": "an example group",
  "created": "2017-03-02T15:29:21Z",
  "status": "Active",
  "members": [
    {
      "id": "2764183c-5e75-4ae6-8833-503cd5f4dcb0"
    }
  ],
  "admins": [
    {
      "id": "2764183c-5e75-4ae6-8833-503cd5f4dcb0"
    }
  ]
}
```
