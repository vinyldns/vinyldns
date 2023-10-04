---
layout: docs
title: "Get Valid Email Domains"
section: "api"
---

# Get Group

Gets a list of valid email domains which are allowed while entering groups and zones email.

#### HTTP REQUEST

> GET groups/valid/domains

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The valid email domains are returned in the response body |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
404           | **Not Found** - The valid email domains are not found |

#### HTTP RESPONSE ATTRIBUTES

type               | description |
| -------------    | :---------- |
| Array of string  | The list of all valid email domains |

#### EXAMPLE RESPONSE

```json
[
  "gmail.com",
  "test.com"
]
```
