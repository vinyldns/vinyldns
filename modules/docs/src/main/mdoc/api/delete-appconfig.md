---
layout: docs
title: "Delete App Config"
section: "api"
---

# Delete App Config

Deletes a runtime configuration entry by its key. After deletion the in-memory cache is automatically refreshed so the removed key is no longer active.

*Note: Deleting a key that has a hardcoded default in the application will cause the server to fall back to that default value on the next read.*

#### HTTP REQUEST

> DELETE /appconfig/{key}

#### HTTP RESPONSE TYPES

Code | description |
---- | :---------- |
204  | **No Content** - The config entry was deleted successfully |
401  | **Unauthorized** - The authentication information provided is invalid |
403  | **Forbidden** - The user does not have the access required to perform the action |
404  | **Not Found** - No config entry with the specified key exists |
