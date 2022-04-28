---
layout: docs
title: "Get user"
section: "api"
---

# Get user

Gets a user corresponding to the given identifier (user ID or username)

#### HTTP REQUEST

> GET /users/{userIdentifier}

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The user is returned in the response body |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
404           | **Not Found** - The user was not found |

#### HTTP RESPONSE ATTRIBUTES

name          | type   | description                                                                |
 ------------ |--------|:---------------------------------------------------------------------------|
id            | string | Unique UUID of the user                                                    |
userName      | string | The username of the user                                                   |
firstName     | string | The user's first name                                                      |
lastName      | string | The user's last name
created       | string | The timestamp (UTC) the user was created                                   |
email         | string | The email address associated with the user                                 |
lockStatus    | string | **Locked** or **Unlocked**                                                 |

#### EXAMPLE RESPONSE

```json
{
  "id": "ok",
  "userName": "ok",
  "firstName": "ok",
  "lastName": "ok",
  "email": "test@test.com",
  "created": "2022-04-28T18:48:14Z",
  "lockStatus": "Unlocked"
}
```
