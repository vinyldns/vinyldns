---
layout: docs
title: "Generate Zone"
section: "api"
---

# Generate Zone

Creates a new zone on a DNS provider and saves a generated zone record in VinylDNS. You must be a member of the admin
group given in `groupId`, or a super user.

This does not connect VinylDNS to the zone. When the zone is generated, use [Create Zone](create-zone.html) with the same
zone name to manage its records in VinylDNS.

VinylDNS sends the request to the provider before saving anything. If the provider returns an error, nothing is saved.

#### HTTP REQUEST

> POST /zones/generate

#### HTTP REQUEST PARAMS

name           | type          | required?   | description |
 ------------- | ------------- | ----------- | :---------- |
zoneName       | string        | yes         | The name of the zone to create, ending with a dot |
groupId        | string        | yes         | The ID of the admin group for the zone. The group must exist and you must be a member. |
email          | string        | yes         | The contact email for the zone |
provider       | string        | yes         | The DNS provider to create the zone on. Must be one of [Get DNS Providers](get-dns-providers.html). |
providerParams | map           | depends on provider | Settings specific to the provider. Refer to [provider params](generated-zone-model.html#provider-params). |

#### EXAMPLE HTTP REQUEST

```json
{
  "groupId": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
  "email": "test@example.com",
  "provider": "powerdns",
  "zoneName": "example.com.",
  "providerParams": {
    "kind": "Master",
    "nameservers": ["ns1.example.com.", "ns2.example.com."]
  }
}
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
202           | **Accepted** - The zone was created on the provider. The generated zone is returned in the response body. |
400           | **Bad Request** - The request is invalid: a required field is missing, the zone name or email is invalid, the provider is not supported, the provider params do not match the provider's schema, or the admin group does not exist. Also returned if the provider responds with an error; the response body is then the provider's error body. |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - You are not a member of the admin group |
409           | **Conflict** - A generated zone or a VinylDNS zone with this name already exists |
500           | **Internal Server Error** - The provider could not be reached or timed out, or it is not configured correctly (for example a schema or endpoint is missing) |

#### HTTP RESPONSE ATTRIBUTES

Refer to the [generated zone model](generated-zone-model.html#generated-zone-attributes).

#### EXAMPLE RESPONSE

```json
{
  "groupId": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
  "email": "test@example.com",
  "provider": "powerdns",
  "zoneName": "example.com.",
  "status": "Active",
  "providerParams": {
    "kind": "Master",
    "nameservers": ["ns1.example.com.", "ns2.example.com."]
  },
  "response": {
    "responseCode": 201,
    "status": "Created",
    "message": {
      "id": "example.com.",
      "name": "example.com.",
      "kind": "Master"
    },
    "changeType": "Create"
  },
  "id": "a6c1d0e2-3f1b-4b7a-9a3f-1c2d3e4f5a6b",
  "created": "2026-10-22T14:03:11Z"
}
```

#### EXAMPLE ERROR RESPONSES

```text
Unsupported DNS provider: cloudflare
```

```text
JSON schema validation error: $: required property 'nameservers' not found
```

```text
Zone with name example.com. already exists. Please contact 9b22b686-54bc-47fb-a8f8-cdc48e6d04ae to request access.
```
