---
layout: docs
title: "Get Zone Details"
section: "api"
---

# Get Zone Details

Retrieves common zone details including the admin group name. This is a lightweight alternative to [Get Zone by ID](get-zone-by-id.html) that includes the resolved admin group name.

#### HTTP REQUEST

> GET /zones/{zoneId}/details

#### EXAMPLE HTTP REQUEST

```http
GET /zones/0f2fcece-b4ee-4982-b671-e5946f7db81d/details
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - Successful lookup, the zone details are returned in the response body |
401           | **Unauthorized** - The authentication information provided is invalid. Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - Zone not found |

#### HTTP RESPONSE ATTRIBUTES

name            | type          | description |
 -------------- | ------------- | :---------- |
zone            | map           | The zone details object |

**Zone Details Attributes:**

name            | type          | description |
 -------------- | ------------- | :---------- |
name            | string        | The name of the zone |
email           | string        | The distribution email for the zone |
status          | string        | The zone status: **Active** or **Syncing** |
adminGroupId    | string        | The UUID of the administrators group for the zone |
adminGroupName  | string        | The name of the administrators group for the zone |

#### EXAMPLE RESPONSE

```json
{
  "zone": {
    "name": "example.com.",
    "email": "admin@example.com",
    "status": "Active",
    "adminGroupId": "6baa85ad-267f-44ff-b535-818b7d7a2467",
    "adminGroupName": "example-admin-group"
  }
}
```
