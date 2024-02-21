---
layout: docs
title: "Get RecordSet Count"
section: "api"
---

# Get RecordSet Count

Gets the count of total recordsets in a specified zone

#### HTTP REQUEST

> GET /zones/{zoneId}/recordsetcount

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The total record set count in a zone is returned |
401           | **Unauthorized** - The authentication information provided is invalid. Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - The zone with the id specified was not found |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
count         | integer       | Total count of recordsets in a zone |

#### EXAMPLE RESPONSE

```json
{
  "count": 10
}
```
