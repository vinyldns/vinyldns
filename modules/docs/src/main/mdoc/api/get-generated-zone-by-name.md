---
layout: docs
title: "Get Generated Zone by Name"
section: "api"
---

# Get Generated Zone by Name

Gets a generated zone by its name. You must be a member of the zone's admin group, or a super or support user.
A trailing dot is added to the name if it is missing.

#### HTTP REQUEST

> GET /zones/generate/name/{zoneName}

#### EXAMPLE HTTP REQUEST

```http
GET /zones/generate/name/example.com.
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The generated zone is returned in the response body |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - You are not a member of the zone's admin group |
404           | **Not Found** - No generated zone with this name |

#### HTTP RESPONSE ATTRIBUTES

Refer to the [generated zone model](generated-zone-model.html#generated-zone-attributes).

#### EXAMPLE RESPONSE

Refer to the [generated zone example](generated-zone-model.html#generated-zone-example).
