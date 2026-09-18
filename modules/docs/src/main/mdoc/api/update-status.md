---
layout: docs
title: "Update Status"
section: "api"
---

# Update Status

Updates the system processing status. This endpoint is used to enable or disable background processing.

**Note:** This endpoint requires system administrator privileges.

#### HTTP REQUEST

> POST /status?processingDisabled={true|false}

#### EXAMPLE HTTP REQUEST

```http
POST /status?processingDisabled=true
```

#### HTTP REQUEST PARAMETERS

name                | type          | required?     | description |
 ------------------ | ------------- | ------------- | :---------- |
processingDisabled  | boolean       | yes           | Set to `true` to disable background processing, `false` to enable it |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - Status updated successfully |
401           | **Unauthorized** - The authentication information provided is invalid. Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user is not a system administrator |

#### HTTP RESPONSE ATTRIBUTES

name                | type          | description |
 ------------------ | ------------- | :---------- |
processingDisabled  | boolean       | The updated processing disabled state |
color               | string        | The current deployment color (blue or green) |
keyName             | string        | The name of the TSIG key used by the server |
version             | string        | The current version of the VinylDNS API |

#### EXAMPLE RESPONSE

```json
{
  "processingDisabled": true,
  "color": "blue",
  "keyName": "vinyldns.",
  "version": "0.21.3"
}
```
