---
layout: docs
title: "Unlock User"
section: "api"
---

# Unlock User

Unlocks a previously locked user account, restoring their ability to authenticate and access VinylDNS.

**Note:** This endpoint requires system administrator privileges.

#### HTTP REQUEST

> PUT /users/{userId}/unlock

#### EXAMPLE HTTP REQUEST

```http
PUT /users/123e64c0-b34f-4c9b-9e0e-f7f7bcc16f2e/unlock
```

#### HTTP RESPONSE TYPES

| Code | description                                                                                                                                                                                |
|------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 200  | **OK** - The user has been unlocked and their info is returned in the response body                                                                                                        |
| 401  | **Unauthorized** - The authentication information provided is invalid. Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
| 403  | **Forbidden** - The user is not a system administrator                                                                                                                                     |
| 404  | **Not Found** - The user was not found                                                                                                                                                     |

#### HTTP RESPONSE ATTRIBUTES

| name       | type   | description                                        |
|------------|--------|:---------------------------------------------------|
| id         | string | Unique UUID of the user                            |
| userName   | string | The username of the user                           |
| lockStatus | string | The lock status of the user (will be "Unlocked")   |

#### EXAMPLE RESPONSE

```json
{
  "id": "123e64c0-b34f-4c9b-9e0e-f7f7bcc16f2e",
  "userName": "jdoe",
  "lockStatus": "Unlocked"
}
```
