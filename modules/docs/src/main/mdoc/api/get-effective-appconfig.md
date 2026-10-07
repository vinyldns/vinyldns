---
layout: docs
title: "Get Effective App Config"
section: "api"
---

# Get Effective App Config

Returns the current **in-memory snapshot** of all configuration values that the server is actively using. This reflects the last time the cache was refreshed (at startup or via [Refresh App Config](refresh-appconfig.html)).

This is useful for verifying that a DB update has taken effect without having to cross-reference raw DB timestamps.

*Note: This differs from [List App Configs](list-appconfigs.html), which queries the database directly and includes timestamps. This endpoint reads from the live in-memory cache.*

#### HTTP REQUEST

> GET /appconfig/effective

#### HTTP RESPONSE TYPES

Code | description |
---- | :---------- |
200  | **OK** - The current in-memory config snapshot is returned as a flat key-value map |
401  | **Unauthorized** - The authentication information provided is invalid |
403  | **Forbidden** - The user does not have the access required to perform the action |

#### HTTP RESPONSE ATTRIBUTES

The response is a flat JSON object where each field is a config key mapped to its current string value.

#### EXAMPLE RESPONSE

```json
{
  "batch-change-limit": "1000",
  "backend.config-json": "{\"default-backend-id\": \"default\", ...}",
  "manual-batch-review-enabled": "true",
  "sync-delay": "10000",
  "use-recordset-cache": "false",
  "processing-disabled": "false"
}
```
