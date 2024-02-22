---
layout: docs
title: "List Group Admins"
section: "api"
---

# List Group Admins

Retrieves a group's admins.

#### HTTP REQUEST

> GET /groups/{groupId}/admins

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The admins have been returned in the response body|
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
404           | **Not Found** - The group was not found |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
admins        | Array of Users | Refer to [membership model](membership-model.html) |

#### EXAMPLE RESPONSE

```json
{
  "admins": [
    {
      "userName": "jdoe201",
      "firstName": "john",
      "created": "2017-03-02T16:39:02Z",
      "lastName": "doe",
      "email": "john_doe@example.com",
      "id": "2764183c-5e75-4ae6-8833-503cd5f4dcb0"
    },
    {
      "userName": "jdoe202",
      "firstName": "jane",
      "created": "2017-03-02T16:50:02Z",
      "lastName": "doe",
      "email": "jane_doe@example.com",
      "id": "1764183c-5e75-4ae6-8833-503cd5f4dcb4"
    }
  ]
}
```
