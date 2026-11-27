---
layout: docs
title: "Get Generated Zone by ID"
section: "api"
---

# Get Generated Zone by ID

Gets a generated zone by its ID. You must be a member of the zone's admin group, or a super or support user.

#### HTTP REQUEST

> GET /zones/generate/id/{generatedZoneId}

#### EXAMPLE HTTP REQUEST

```http
GET /zones/generate/id/a6c1d0e2-3f1b-4b7a-9a3f-1c2d3e4f5a6b
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The generated zone is returned in the response body |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - You are not a member of the zone's admin group |
404           | **Not Found** - No generated zone with this ID |

#### HTTP RESPONSE ATTRIBUTES

Refer to the [generated zone model](generated-zone-model.html#generated-zone-attributes).

#### EXAMPLE RESPONSE

Refer to the [generated zone example](generated-zone-model.html#generated-zone-example).
