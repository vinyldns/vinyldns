---
layout: docs
title: "List Zone Change Failures"
section: "api"
---

# List Zone Change Failures

Retrieves a list of failed zone changes.

#### HTTP REQUEST

> GET metrics/health/zonechangesfailure?startFrom={response.nextId}&maxItems={1 - 100}

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
startFrom     | int           | no          | In order to advance through pages of results, the startFrom is set to the `nextId` that is returned on the previous response.  It is up to the client to maintain previous pages if the client wishes to advance forward and backward.   If not specified, will return the first page of results |
maxItems      | int           | no          | The number of items to return in the page.  Valid values are 1 - 100. Defaults to 100 if not provided. |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **Accepted** - The zone changes will be returned in the response body|
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - Zone not found |

#### HTTP RESPONSE ATTRIBUTES

name                | type                         | description |
 ------------------ | ---------------------------- | :---------- |
failedZoneChanges   | array of zone changes        | Array of failed zone changes sorted by created time in descending order. Refer to [Zone Change](#zone-change) |
startFrom           | int                          | (optional) The startFrom parameter that was sent in on the HTTP request.  Will not be present if the startFrom parameter was not sent |
nextId              | int                          | (optional) The identifier to be passed in as the *startFrom* parameter to retrieve the next page of results.  If there are no results left, this field will not be present |
maxItems            | int                          | The maxItems parameter that was sent in on the HTTP request.  This will be 100 if not sent |

#### ZONE CHANGE ATTRIBUTES <a id="zone-change"></a>

name                | type          | description |
 -----------------  | ------------- | :---------- |
zone                | map           | Refer to [zone model](zone-model.html) |
status              | string        | The status of the change. Either Pending, Failed or Synced |
changeType          | string        | The type of change. Either Create, Update, Delete, Sync or AutomatedSync |
systemMessage       | string        | (optional) A message regarding the change.  Will not be present if the string is empty |
created             | string        | Millisecond timestamp that change was created
userId              | string        | User Id of user who made the change |
id                  | string        | Id of the group change |

#### EXAMPLE RESPONSE

```json
{
  "failedZoneChanges": [
    {
      "zone": {
        "name": "shared.",
        "email": "email",
        "status": "Active",
        "created": "2022-05-20T16:47:28Z",
        "updated": "2022-12-08T20:14:19Z",
        "id": "e6efbae3-ae2d-466d-bfa4-207aa276a024",
        "account": "system",
        "shared": true,
        "acl": {
          "rules": []
        },
        "adminGroupId": "shared-zone-group",
        "isTest": true
      },
      "userId": "6741d4df-81b7-40bd-9856-896c730c189b",
      "changeType": "Sync",
      "status": "Failed",
      "created": "2022-12-08T20:14:19Z",
      "id": "9ce7dc8b-a5ed-488d-82d4-8c7e7cf33285"
    },
    {
      "zone": {
        "name": "dummy.",
        "email": "test@test.com",
        "status": "Active",
        "created": "2020-11-17T18:50:46Z",
        "updated": "2022-10-17T10:13:46Z",
        "id": "d7e433df-ad84-4fbe-9f52-3b2f3665412a",
        "connection": {
          "name": "dummy.",
          "keyName": "vinyldns.",
          "key": "OBF:1:uFOhH4AH8xEAABAAlertajQQHrQZB91yWQz5lyBf4O88js2S6aWNMtAq5MS5Otysb4Z7iiO9DoGY9A6BrDQ52b8SOQyj0QpzgPe0CuI/pLW1s/rulmlvgubHkIl7dsYAaRH7SrmZfNBe4BSn02zuv/ATyWEy",
          "primaryServer": "96.115.238.13",
          "algorithm": "HMAC-MD5"
        },
        "transferConnection": {
          "name": "dummy.",
          "keyName": "vinyldns.",
          "key": "OBF:1:EonJvAJrMwQAABAAO+MPQq6fyNQcjnXUuV6YtvjeCGt8SEicWC6Ke9dLT1UmL4vAtlVg0nARl9rvhb1mxNndSf4ogx+/BvZx2AEvkTgCFxbsPMxJ/s6E/s6uaxa4sf/8+CpnR/1R0oYmfOaMSq04tgD+A+ym",
          "primaryServer": "96.115.238.13",
          "algorithm": "HMAC-MD5"
        },
        "account": "system",
        "shared": false,
        "acl": {
          "rules": []
        },
        "adminGroupId": "9945e0c5-41dd-42e9-a053-3f4dacf006c3",
        "latestSync": "2020-11-17T18:50:46Z",
        "isTest": true
      },
      "userId": "c1f17f3e-59cc-491d-a1f3-ee3f50f38a09",
      "changeType": "Sync",
      "status": "Failed",
      "created": "2022-10-17T10:13:46Z",
      "id": "1cd5876f-b896-4823-86a6-286f21fa6a16"
    }
  ],
  "nextId": 0,
  "startFrom": 0,
  "maxItems": 100
}
```
