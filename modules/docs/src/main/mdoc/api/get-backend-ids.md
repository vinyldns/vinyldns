---
layout: docs
title: "Get Backend IDs"
section: "api"
---

# Get Backend IDs

Retrieves all available backend IDs that can be used when creating or updating zones. Backend IDs identify the DNS backend servers that VinylDNS can connect to.

#### HTTP REQUEST

> GET /zones/backendids

#### EXAMPLE HTTP REQUEST

```http
GET /zones/backendids
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - List of backend IDs returned successfully |
401           | **Unauthorized** - The authentication information provided is invalid. Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |

#### HTTP RESPONSE ATTRIBUTES

The response is an array of strings representing the available backend IDs.

#### EXAMPLE RESPONSE

```json
[
  "default-backend",
  "func-test-backend",
  "secondary-backend"
]
```

#### USAGE

When creating or updating a zone, you can specify a `backendId` to indicate which DNS backend server should be used for that zone. Use this endpoint to discover available backend IDs for your VinylDNS instance.

See the [Zone Model](zone-model.html) for more information about the `backendId` field.
