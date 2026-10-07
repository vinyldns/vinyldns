---
layout: docs
title: "Update App Config"
section: "api"
---

# Update App Config

Updates the value of an existing runtime configuration entry. The key must already exist; use [Create App Config](create-appconfig.html) for new entries.

After a successful update the in-memory cache is automatically refreshed so the new value takes effect immediately without calling [Refresh App Config](refresh-appconfig.html).

#### HTTP REQUEST

> PUT /appconfig/{key}

#### HTTP REQUEST PARAMS

name  | type   | required | description |
----- | ------ | -------- | :---------- |
value | string | yes      | New configuration value |

#### EXAMPLE HTTP REQUEST

```json
{
  "key": "sync-delay",
  "value": "15000"
}
```

*Note: The `key` field in the request body is ignored; the key is taken from the URL path.*

#### HTTP RESPONSE TYPES

Code | description |
---- | :---------- |
200  | **OK** - The updated config entry is returned in the response body |
401  | **Unauthorized** - The authentication information provided is invalid |
403  | **Forbidden** - The user does not have the access required to perform the action |
404  | **Not Found** - No config entry with the specified key exists |
422  | **Unprocessable Entity** - The value is empty |

#### HTTP RESPONSE ATTRIBUTES

name      | type   | description |
--------- | ------ | :---------- |
key       | string | The configuration key |
value     | string | The updated configuration value |
createdAt | string | Timestamp (UTC) when the entry was originally created |
updatedAt | string | Timestamp (UTC) when the entry was last updated |

#### EXAMPLE RESPONSE

```json
{
  "key": "sync-delay",
  "value": "15000",
  "createdAt": "2026-04-08T12:46:19Z",
  "updatedAt": "2026-04-09T11:30:00Z"
}
```
