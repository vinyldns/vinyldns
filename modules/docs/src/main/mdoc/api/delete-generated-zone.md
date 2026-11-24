---
layout: docs
title: "Delete Generated Zone"
section: "api"
---

# Delete Generated Zone

Deletes a generated zone from its DNS provider, then removes the generated zone record from VinylDNS. You must be a
member of the zone's admin group, or a super user.

If the provider returns an error, the record is kept in VinylDNS.

This does not remove a VinylDNS zone you connected to with [Create Zone](create-zone.html). To remove that, use
[Delete Zone](delete-zone.html).

#### HTTP REQUEST

> DELETE /zones/generate/{generatedZoneId}

#### EXAMPLE HTTP REQUEST

```http
DELETE /zones/generate/a6c1d0e2-3f1b-4b7a-9a3f-1c2d3e4f5a6b
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
202           | **Accepted** - The zone was deleted. The deleted generated zone is returned in the response body, with the provider's delete response. |
400           | **Bad Request** - The zone's provider is no longer configured, or the provider responded with an error (the response body is then the provider's error body) |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - You are not a member of the zone's admin group |
404           | **Not Found** - No generated zone with this ID |
500           | **Internal Server Error** - The provider could not be reached or timed out, or it is not configured correctly (for example the delete endpoint is missing) |

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
    "responseCode": 204,
    "status": "No Content",
    "changeType": "Delete"
  },
  "id": "a6c1d0e2-3f1b-4b7a-9a3f-1c2d3e4f5a6b",
  "created": "2026-10-22T14:03:11Z"
}
```
