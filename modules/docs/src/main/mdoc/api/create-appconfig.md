---
layout: docs
title: "Create App Config"
section: "api"
---

# Create App Config

Creates a single runtime configuration entry in the database. The key must be unique; use [Update App Config](update-appconfig.html) to change an existing value.

After a successful create the in-memory cache is automatically refreshed so the new value takes effect immediately without calling [Refresh App Config](refresh-appconfig.html).

#### HTTP REQUEST

> POST /appconfig

#### HTTP REQUEST PARAMS

name  | type   | required | description |
----- | ------ | -------- | :---------- |
key   | string | yes      | Unique configuration key (e.g. `sync-delay`, `batch-change-limit`) |
value | string | yes      | Configuration value stored as a string |

#### EXAMPLE HTTP REQUEST

```json
{
  "key": "sync-delay",
  "value": "15000"
}
```

#### HTTP RESPONSE TYPES

Code | description |
---- | :---------- |
201  | **Created** - The config entry was created and is returned in the response body |
401  | **Unauthorized** - The authentication information provided is invalid |
403  | **Forbidden** - The user does not have the access required to perform the action |
409  | **Conflict** - A config entry with that key already exists |
422  | **Unprocessable Entity** - The key or value is empty |

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
  "value": "15000",
  "createdAt": "2026-04-09T10:00:00Z",
  "updatedAt": "2026-04-09T10:00:00Z"
}
```
