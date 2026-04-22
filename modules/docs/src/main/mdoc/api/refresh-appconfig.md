---
layout: docs
title: "Refresh App Config"
section: "api"
---

# Refresh App Config

Reloads all runtime configuration from the database into the server's in-memory cache. Also re-reads `application.conf` from disk so any file-level changes (e.g. Akka settings) take effect without a full restart.

Returns only the **diff** — keys that were added, updated, or removed compared to the previous in-memory state. If nothing changed, all three sections will be empty.

*Note: Individual create, update, and delete operations already refresh the cache automatically. Use this endpoint after making direct database changes (e.g. via SQL) or after editing `application.conf`.*

#### HTTP REQUEST

> POST /appconfig/refresh

#### HTTP RESPONSE TYPES

Code | description |
---- | :---------- |
200  | **OK** - The cache was refreshed and the diff is returned in the response body |
401  | **Unauthorized** - The authentication information provided is invalid |
403  | **Forbidden** - The user does not have the access required to perform the action |

#### HTTP RESPONSE ATTRIBUTES

name    | type             | description |
------- | ---------------- | :---------- |
added   | map              | Keys that were present in the database but not in the previous cache (new entries) |
updated | map              | Keys whose values changed between the previous cache and the newly loaded database state |
removed | array of strings | Keys that were in the previous cache but no longer exist in the database |

#### EXAMPLE RESPONSE

```json
{
  "added": {
    "high-value-domains": "{\"regex-list\": [\"high-value-domain.*\"], \"ip-list\": []}"
  },
  "updated": {
    "sync-delay": "15000"
  },
  "removed": [
    "old-deprecated-key"
  ]
}
```

#### EXAMPLE RESPONSE — No Changes

```json
{
  "added": {},
  "updated": {},
  "removed": []
}
```
