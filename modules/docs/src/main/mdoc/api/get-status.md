---
layout: docs
title: "Get Status"
section: "api"
---

# Get Status

Retrieves the current system status including processing state, deployment color, key name, and version.

**Note:** This endpoint does not require authentication.

#### HTTP REQUEST

> GET /status

#### EXAMPLE HTTP REQUEST

```http
GET /status
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - Status returned successfully |

#### HTTP RESPONSE ATTRIBUTES

name                | type          | description |
 ------------------ | ------------- | :---------- |
processingDisabled  | boolean       | Whether background processing is currently disabled |
color               | string        | The current deployment color (blue or green) |
keyName             | string        | The name of the TSIG key used by the server |
version             | string        | The current version of the VinylDNS API |

#### EXAMPLE RESPONSE

```json
{
  "processingDisabled": false,
  "color": "blue",
  "keyName": "vinyldns.",
  "version": "0.21.3"
}
```
