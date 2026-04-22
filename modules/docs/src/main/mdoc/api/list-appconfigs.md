---
layout: docs
title: "List App Configs"
section: "api"
---

# List App Configs

Returns all runtime configuration entries stored in the database, along with a total count.

#### HTTP REQUEST

> GET /appconfig

#### HTTP RESPONSE TYPES

Code | description |
---- | :---------- |
200  | **OK** - All config entries and the total count are returned in the response body |
401  | **Unauthorized** - The authentication information provided is invalid |
403  | **Forbidden** - The user does not have the access required to perform the action |

#### HTTP RESPONSE ATTRIBUTES

name    | type    | description |
------- | ------- | :---------- |
total   | integer | Total number of config entries in the database |
configs | array   | List of config entry objects (see fields below) |

**Config entry fields**

name      | type   | description |
--------- | ------ | :---------- |
key       | string | The configuration key |
value     | string | The configuration value |
createdAt | string | Timestamp (UTC) when the entry was created |
updatedAt | string | Timestamp (UTC) when the entry was last updated |

#### EXAMPLE RESPONSE

```json
{
  "total": 3,
  "configs": [
    {
      "key": "batch-change-limit",
      "value": "1000",
      "createdAt": "2026-04-08T12:46:19Z",
      "updatedAt": "2026-04-08T12:46:19Z"
    },
    {
      "key": "sync-delay",
      "value": "10000",
      "createdAt": "2026-04-08T12:46:19Z",
      "updatedAt": "2026-04-08T12:46:19Z"
    },
    {
      "key": "use-recordset-cache",
      "value": "false",
      "createdAt": "2026-04-08T12:46:19Z",
      "updatedAt": "2026-04-08T12:46:19Z"
    }
  ]
}
```
