---
layout: docs
title: "Get User"
section: "api"
---

# Get User

Gets a user corresponding to the given identifier (user ID or username).

#### HTTP REQUEST

> GET /users/{userIdentifier}

#### HTTP RESPONSE TYPES

| Code | description                                                                                                                                                                                |
|------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 200  | **OK** - The user is returned in the response body                                                                                                                                         |
| 401  | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
| 404  | **Not Found** - The user was not found                                                                                                                                                     |

#### HTTP RESPONSE ATTRIBUTES

| name     | type   | description              |
|----------|--------|:-------------------------|
| id       | string | Unique UUID of the user  |
| userName | string | The username of the user |
| groupId  | Array of groupId's | The Group ID's of the user |


#### EXAMPLE RESPONSE

```json
{
  "id": "ok",
  "userName": "ok",
  "groupId" : [
    {
      "id": "ok-group"
    }
  ]
}
```
