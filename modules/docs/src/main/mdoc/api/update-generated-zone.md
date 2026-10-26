---
layout: docs
title: "Update Generated Zone"
section: "api"
---

# Update Generated Zone

Updates a generated zone on its DNS provider and in VinylDNS. The zone is found by `zoneName`. You must be a member of
the zone's current admin group, or a super user. If you change `groupId`, you must also be a member of the new group
(super users are exempt).

The whole zone is replaced: `providerParams` in the request replaces the stored params, so any param you leave out is
removed. Send all the params you want to keep.

The provider cannot be changed. `provider` must match the provider the zone was generated on.

#### HTTP REQUEST

> PUT /zones/generate

#### HTTP REQUEST PARAMS

name           | type          | required?   | description |
 ------------- | ------------- | ----------- | :---------- |
zoneName       | string        | yes         | The name of the generated zone to update, ending with a dot. It cannot be changed. |
groupId        | string        | yes         | The ID of the admin group. The group must exist. |
email          | string        | yes         | The contact email for the zone |
provider       | string        | yes         | Must be the zone's current provider |
providerParams | map           | depends on provider | Settings specific to the provider. Refer to [provider params](generated-zone-model.html#provider-params). |

#### EXAMPLE HTTP REQUEST

```json
{
  "groupId": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
  "email": "new-contact@example.com",
  "provider": "powerdns",
  "zoneName": "example.com.",
  "providerParams": {
    "kind": "Native"
  }
}
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
202           | **Accepted** - The zone was updated on the provider. The updated generated zone is returned in the response body. |
400           | **Bad Request** - The request is invalid: a required field is missing, the zone name or email is invalid, the provider is different from the zone's provider, the provider params do not match the provider's schema, or the admin group does not exist. Also returned if the provider responds with an error; the response body is then the provider's error body. |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - You are not a member of the zone's admin group, or of the new admin group |
404           | **Not Found** - No generated zone with this name |
500           | **Internal Server Error** - The provider could not be reached or timed out, or it is not configured correctly (for example a schema or endpoint is missing) |

#### HTTP RESPONSE ATTRIBUTES

Refer to the [generated zone model](generated-zone-model.html#generated-zone-attributes).

#### EXAMPLE RESPONSE

```json
{
  "groupId": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
  "email": "new-contact@example.com",
  "provider": "powerdns",
  "zoneName": "example.com.",
  "status": "Active",
  "providerParams": {
    "kind": "Native"
  },
  "response": {
    "responseCode": 204,
    "status": "No Content",
    "changeType": "Update"
  },
  "id": "a6c1d0e2-3f1b-4b7a-9a3f-1c2d3e4f5a6b",
  "created": "2026-10-22T14:03:11Z",
  "updated": "2026-10-22T15:20:45Z"
}
```

#### EXAMPLE ERROR RESPONSES

```text
Cannot change the DNS provider of an existing generated zone (current: 'powerdns', requested: 'bind').
```

Sending a param that the provider's update schema does not allow, such as `nameservers` for PowerDNS:

```text
JSON schema validation error: $: property 'nameservers' is not defined in the schema and the schema does not allow additional properties
```
