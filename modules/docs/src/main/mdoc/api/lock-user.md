---
layout: docs
title: "Lock User"
section: "api"
---

# Lock User

Locks a user account, preventing them from authenticating and accessing VinylDNS.

**Note:** This endpoint requires superuser privileges.

#### HTTP REQUEST

> PUT /users/{userId}/lock

#### EXAMPLE HTTP REQUEST

```http
PUT /users/123e64c0-b34f-4c9b-9e0e-f7f7bcc16f2e/lock
```

#### HTTP RESPONSE TYPES

| Code | description                                                                                                                                                                                |
|------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 200  | **OK** - The user has been locked and their info is returned in the response body                                                                                                          |
| 401  | **Unauthorized** - The authentication information provided is invalid. Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
| 403  | **Forbidden** - The authenticated user does not have permission to lock users                                                                                                               |
| 404  | **Not Found** - The user was not found                                                                                                                                                     |

#### HTTP RESPONSE ATTRIBUTES

| name       | type   | description                                    |
|------------|--------|:-----------------------------------------------|
| id         | string | Unique UUID of the user                        |
| userName   | string | The username of the user                       |
| firstName  | string | The first name of the user                     |
| lastName   | string | The last name of the user                      |
| email      | string | The email address of the user                  |
| created    | string | The timestamp when the user was created        |
| lockStatus | string | The lock status of the user (will be "Locked") |

#### EXAMPLE RESPONSE

```json
{
  "id": "123e64c0-b34f-4c9b-9e0e-f7f7bcc16f2e",
  "userName": "jdoe",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "created": "2026-09-09T13:45:00Z",
  "lockStatus": "Locked"
}
```
