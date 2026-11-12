---
layout: docs
title: "Search User"
section: "api"
---

# Search User

Searches for users whose username matches the given pattern. `*` is the wildcard
and may appear anywhere in the pattern; a pattern with no `*` matches usernames beginning with it. `%` and `_` are matched literally. Returns every matching user's details, including the groups they belong to.

#### HTTP REQUEST

> GET /users/search?pattern={pattern}&startFrom={startFrom}&maxItems={maxItems}

#### REQUEST PARAMETERS

| name      | type    | required | default | description                                             |
|-----------|---------|----------|---------|:--------------------------------------------------------|
| pattern   | string  | yes      |         | The pattern to search for; must be 2+ characters       |
| startFrom | integer | no       | 0       | The 0-based offset for result pagination               |
| maxItems  | integer | no       | 100     | Max results to return; must be between 1 and 100       |

#### EXAMPLE HTTP REQUEST

```http
GET /users/search?pattern=ok
```

#### HTTP RESPONSE TYPES

| Code | description                                                                                                                                                                                |
|------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 200  | **OK** - The matched users are returned in the response body as a JSON array (may be empty)                                                                                                |
| 400  | **Bad Request** - Pattern is less than 2 characters, is a bare wildcard (`%` or `*`), or maxItems is out of range                                                                        |
| 401  | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |

#### HTTP RESPONSE ATTRIBUTES

The response body is a JSON array of objects, one per matched user, each with the following attributes:

| name     | type   | description              |
|----------|--------|:-------------------------|
| id       | string | Unique UUID of the user  |
| userName | string | The username of the user |
| groupId  | Array of groupId's | The Group ID's of the user |
| groupMap | Object mapping groupId to group name | The Group ID's of the user mapped to their group names |


#### EXAMPLE RESPONSE

```json
[
  {
    "id": "ok",
    "userName": "ok",
    "groupId" : [
      "ok-group"
    ],
    "groupMap": {
      "ok-group": "ok-group-name"
    }
  }
]
```

#### EXAMPLE ERROR RESPONSE
```text
User matching doesntexistuser was not found
```
