---
layout: docs
title: "Update Group"
section: "api"
---

# Update Group

Updates a Group in VinylDNS.

#### HTTP REQUEST

> PUT /groups/{groupId}

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
id            | string        | yes         | The ID of the group |
name          | string        | yes         | The name of the group. Should be one word, use hyphens if needed but no spaces |
email         | string        | yes         | The email distribution list for the group |
description   | string        | no          | A short description of the group, if more info is needed other than the name |
created       | date-time     | yes         | The timestamp (UTC) when the group was created |
status        | string        | yes         | **Active** or **Deleted**, should not be changed in an update, a delete request will handle deleting a group |
members       | Array of User ID objects    | yes         | Set of User IDs in the group |
admins        | Array of User ID objects    | yes         | Set of User IDs that are admins of the group. All admin user ids should also be in the members array |

#### EXAMPLE HTTP REQUEST

```json
{
  "id": "6f8afcda-7529-4cad-9f2d-76903f4b1aca",
  "name": "some-group",
  "email": "test@example.com",
  "created": "Thu Mar 02 2017 10:29:21",
  "status": "Active",
  "members": [
    {
      "id": "4764183c-5e75-4ae6-8833-503cd5f4dcb0"
    },
    {
      "id": "k8630ebc-0af2-4c9a-a0a0-d18c590ed03e"
    }
  ],
  "admins": [
    {
      "id": "4764183c-5e75-4ae6-8833-503cd5f4dcb0"
    }
  ]
}
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The group has been updated and the group info is returned in the response body |
400           | **Bad Request** - The group was invalid or a user id was not found |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - The group was not found |
409           | **Conflict** - The group already exists |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
id            | string        | Unique UUID of the group |
name          | map           | The name of the group |
email         | string        | The email distribution list of the group |
description   | string        | The group description, the group will not have this attribute if it was not included in the update request and already did not exist |
created       | string        | The timestamp (UTC) the group was created |
status        | string        | **Active** or **Deleted**, in this case **Active** |
members       | Array of User Id objects        | Ids of members of the group including admins |
admins        | Array of User Id objects        | Ids of admins of the group |

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
