---
layout: docs
title: "Get App Config"
section: "api"
---

# Get App Config

Retrieves a single runtime configuration entry by its key.

#### HTTP REQUEST

> GET /appconfig/{key}

#### HTTP RESPONSE TYPES

Code | description |
---- | :---------- |
200  | **OK** - The config entry is returned in the response body |
401  | **Unauthorized** - The authentication information provided is invalid |
403  | **Forbidden** - The user does not have the access required to perform the action |
404  | **Not Found** - No config entry with the specified key exists |

#### HTTP RESPONSE ATTRIBUTES

name      | type   | description |
--------- | ------ | :---------- |
key       | string | The configuration key |
value     | string | The configuration value |
createdAt | string | Timestamp (UTC) when the entry was created |
updatedAt | string | Timestamp (UTC) when the entry was last updated |

#### EXAMPLE RESPONSE

```json
{
  "key": "sync-delay",
  "value": "10000",
  "createdAt": "2026-04-08T12:46:19Z",
  "updatedAt": "2026-04-08T12:46:19Z"
}
```
